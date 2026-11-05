package github.anandb.netbeans.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ModelIdNormalizer;

/**
 * Connection lifecycle and schema upgrades for the usage-stats database. Owns
 * the JDBC URL, the single connection, the open-attempt budget, the
 * single-threaded executor every database operation runs on, and the DDL/data
 * migrations applied on open. Domain code reaches it through the facade's thin
 * delegates; it never calls back into the datasets.
 */
final class DbLifecycle {

    private static final String SCHEMA_VERSION_KEY = "schema_version";

    /** Idempotent DDL, run on every open. */
    private static final String SCHEMA_SCRIPT = "schema.sql";

    /**
     * Bumped when capture semantics change in a way that makes earlier rows
     * unsound. Version 1 (no version row) counted replayed history on every
     * {@code session/load}, duplicating message totals and usage rows; those
     * rows cannot be repaired, so they are dropped once on upgrade to 2.
     * Version 3 preserves the version-2 rows and widens {@code cost_amount}
     * from DOUBLE to NUMERIC(20,6); per-row deltas summed below cent error
     * no longer drift with sum-time floating-point accumulation.
     * Version 4 rewrites stored model ids to the canonical separator, so rows
     * captured before that change do not appear as a second group.
     * Version 5 adds the {@code tool_call_usage} table (per-tool MCP call
     * counts dumped at end of turn); purely additive, no earlier row changes.
     * Version 6 adds {@code pinned_message} and {@code harness_model}, the two
     * datasets that outgrew preference values; purely additive.
     * Version 7 adds {@code harness_model.last_seen} so a model the harness stops
     * reporting is retained for one hour before its row is dropped; existing rows
     * are stamped at upgrade time.
     * Version 8 adds {@code tool_call_usage.duration_ms} and {@code .status}: one
     * row now records one finished MCP tool execution instead of a turn's per-tool
     * counts. The columns are additive and defaulted, so version-7 rows survive and
     * their {@code call_count} still sums correctly with the new rows.
     * Version 9 adds the {@code message_usage} table for goose's per-message
     * {@code message_usage} custom notification; purely additive, so released
     * rows are untouched and no migration branch is needed.
     */
    private static final int SCHEMA_VERSION = 9;

    /**
     * Open attempts allowed per process: the startup attempt plus the single
     * retry an explicit user action may request. Further attempts cannot help
     * while another process holds the file, and would only spam the log.
     */
    private static final int MAX_OPEN_ATTEMPTS = 2;

    /** Close attempts before giving up; a failed close keeps the file locked. */
    private static final int CLOSE_ATTEMPTS = 3;

    /** Base backoff between close attempts; grows linearly (50, 100, ...). */
    private static final long CLOSE_RETRY_DELAY_MS = 50L;

    /** Tables carrying a model id, in the order they were introduced. */
    private static final String[] CAPTURE_TABLES = {"usage_update", "prompt_usage", "message_event", "tool_call_usage",
            "message_usage"};

    private final Logger log;
    private final String jdbcUrl;
    private final Object initLock = new Object();
    private final ExecutorService dbExecutor;
    private volatile Connection connection;

    /**
     * Set when {@link #open()} failed. H2 refuses a second process on the same
     * database file ("Database may be already in use"), and retrying cannot fix
     * that while the other process lives. Every operation therefore returns
     * empty and logs nothing; only the facade's retry can re-arm the attempt,
     * and only once. The preferences stay untouched, so the next launch
     * tries again regardless.
     */
    private volatile boolean unavailable;

    /** Open attempts made so far; only {@link #connection()} writes it, under {@link #initLock}. */
    private int openAttempts;

    DbLifecycle(Logger log, String jdbcUrl) {
        this.log = log;
        this.jdbcUrl = jdbcUrl;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "usage-stats-db");
            t.setDaemon(true);
            return t;
        });
    }

    /** Opens the H2 database, migrating or dropping legacy tables. */
    private Connection open() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            log.log(Level.WARNING, "H2 driver not on the classpath", e);
        }
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try {
            Sql.runScript(conn, SCHEMA_SCRIPT);
            int dbVersion = readSchemaVersion(conn);
            if (applySchemaMigration(conn, dbVersion)) {
                // The legacy reset dropped the capture tables the script just created.
                Sql.runScript(conn, SCHEMA_SCRIPT);
            }
            if (dbVersion >= 2 && dbVersion < SCHEMA_VERSION) {
                // After the tables exist, so a database that predates one of them
                // cannot make this fail. A version below 2 had them dropped above,
                // so there is nothing to rewrite.
                normalizeStoredModelIds(conn);
            }
            return conn;
        } catch (SQLException e) {
            // Close on failure: a leaked connection keeps the .mv.db file lock
            // held and every connection() retry leaks another one.
            try {
                conn.close();
            } catch (SQLException closed) {
                log.log(Level.FINE, "Failed to close abandoned H2 connection", closed);
            }
            throw e;
        }
    }

    /**
     * Rewrites stored model ids to the canonical separator, once, for a database
     * created before version 4. Harnesses report the same model as
     * {@code provider:model} or {@code provider/model}, and rows captured under
     * both forms would otherwise show as two groups forever.
     *
     * <p>Rows are rewritten through {@link ModelIdNormalizer} rather than by a SQL
     * {@code REPLACE}, so the rule applied on capture and the rule applied here
     * cannot drift apart.</p>
     */
    private void normalizeStoredModelIds(Connection conn) throws SQLException {
        List<String> stale = new ArrayList<>();
        for (String table : CAPTURE_TABLES) {
            try (PreparedStatement ps = conn.prepareStatement(
                    tableSql("normalize_select_model_ids", table));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String value = rs.getString(1);
                    if (value != null && !stale.contains(value)) {
                        stale.add(value);
                    }
                }
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        for (String value : stale) {
            String canonical = ModelIdNormalizer.normalize(value);
            if (canonical == null || canonical.equals(value)) {
                continue;
            }
            for (String table : CAPTURE_TABLES) {
                try (PreparedStatement ps = conn.prepareStatement(
                        tableSql("normalize_update_model_id", table))) {
                    ps.setString(1, canonical);
                    ps.setString(2, value);
                    ps.executeUpdate();
                }
            }
        }
        log.log(Level.INFO, "Canonicalised {0} stored model id(s)", stale.size());
    }

    /** Renders a statement whose only slot is a capture table name; binds stay with the caller. */
    static String tableSql(String name, String table) {
        return Sql.sql(name, Map.of("table", Sql.Slot.of(table)));
    }

    Connection connection() throws SQLException {
        Connection local = connection;
        if (local != null) {
            return local;
        }
        synchronized (initLock) {
            if (connection != null) {
                return connection;
            }
            if (unavailable) {
                throw new SQLException("Usage-stats database is unavailable for this session", "90020", 90020);
            }
            openAttempts++;
            try {
                connection = open();
                return connection;
            } catch (SQLException e) {
                unavailable = true;
                throw e;
            }
        }
    }

    /**
     * Queues a database task unless the database already failed to open.
     * The check runs on the executor, so tasks queued behind the one that
     * discovers the failure are dropped instead of retrying and logging.
     */
    void runDb(Runnable task) {
        try {
            dbExecutor.execute(() -> {
                if (!ensureDatabase()) {
                    return;
                }
                task.run();
            });
        } catch (RejectedExecutionException e) {
            // shutdown() already ran: a late capture has nowhere to go, and the
            // caller (often the session pipeline) must not see it as a failure.
            log.log(Level.FINE, "Store already shut down; dropping a queued write", e);
        }
    }

    /**
     * Performs this process's single open attempt. A failure is reported once,
     * here, because every later operation is gated by {@link #unavailable}.
     * The message names the file and the underlying cause; the stack trace is
     * demoted to FINE so a database another process holds open does not look
     * like a plugin crash on every launch.
     *
     * @return true when the database can be used in this session
     */
    boolean ensureDatabase() {
        if (unavailable) {
            return false;
        }
        try {
            connection();
            return true;
        } catch (SQLException e) {
            log.log(Level.WARNING,
                    "Usage-stats database unavailable ({0}); capture disabled until the next restart: {1}",
                    jdbcUrl, e.getMessage());
            log.log(Level.FINE, "Usage-stats database open failed", e);
            return false;
        }
    }

    /**
     * Re-arms the single retry an explicit user action may request after the
     * startup open failed.
     *
     * @return true when the retry was armed and the caller should proceed
     */
    boolean tryRearmRetry() {
        synchronized (initLock) {
            if (connection != null || openAttempts >= MAX_OPEN_ATTEMPTS) {
                return false;
            }
            // Re-arm exactly one attempt; ensureDatabase() reports the outcome.
            unavailable = false;
            return true;
        }
    }

    /** True when the database failed to open and no retry is in flight. */
    boolean isUnavailable() {
        return unavailable;
    }

    /** JDBC URL this lifecycle opens; used only in the unavailable log line. */
    String jdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Applies the data migrations a stored database needs, one branch per released
     * version; the statements themselves live in {@code queries.sql}. Version 1 rows
     * contained replayed duplicates and per-row gross costs whose semantics produced
     * inflated totals; those rows are unrecoverable, so they are dropped once.
     * Version 2 rows are valid but store cost_amount as DOUBLE; version 3 keeps the
     * data and widens the column to NUMERIC(20,6). Runs once per database on open.
     *
     * @param dbVersion the version read before any of this ran, so the caller can
     *                  also decide what to do after the tables exist
     * @return true when the legacy reset dropped the capture tables, so the schema script
     *         must run again to recreate them
     */
    private boolean applySchemaMigration(Connection conn, int dbVersion) throws SQLException {
        if (dbVersion >= SCHEMA_VERSION) {
            return false;
        }
        boolean dropped = false;
        if (dbVersion < 2) {
            try (Statement st = conn.createStatement()) {
                for (String table : CAPTURE_TABLES) {
                    st.execute(tableSql("drop_capture_table", table));
                }
            }
            dropped = true;
        }
        if (dbVersion == 2) {
            try (Statement st = conn.createStatement()) {
                st.execute(Sql.get("alter_usage_update_cost"));
            }
        }
        if (dbVersion == 6) {
            try (Statement st = conn.createStatement()) {
                st.execute(Sql.get("add_harness_model_last_seen"));
            }
            try (PreparedStatement ps = conn.prepareStatement(Sql.get("backfill_harness_model_last_seen"))) {
                ps.setLong(1, System.currentTimeMillis());
                ps.executeUpdate();
            }
            try (Statement st = conn.createStatement()) {
                st.execute(Sql.get("alter_harness_model_last_seen_not_null"));
            }
        }
        if (dbVersion >= 2 && dbVersion < 8) {
            // Version-7 rows predate per-execution capture: their call_count is a
            // turn aggregate and their outcome is unknown, so they keep 'ok' and
            // duration 0 rather than being rewritten or dropped.
            try (Statement st = conn.createStatement()) {
                st.execute(Sql.get("add_tool_call_usage_duration"));
                st.execute(Sql.get("add_tool_call_usage_status"));
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(Sql.get("merge_meta_value"))) {
            ps.setString(1, SCHEMA_VERSION_KEY);
            ps.setString(2, String.valueOf(SCHEMA_VERSION));
            ps.executeUpdate();
        }
        if (dbVersion < 2) {
            log.log(Level.INFO, "Reset usage-stats store to schema version {0}", SCHEMA_VERSION);
        } else {
            log.log(Level.INFO, "Upgraded usage-stats store from schema version {0} to {1}",
                    new Object[]{dbVersion, SCHEMA_VERSION});
        }
        return dropped;
    }

    /**
     * Reads the recorded schema version. Only an <em>absent</em> row means legacy:
     * the legacy branch drops every capture table, so a version that cannot be read
     * must fail the open instead of being mistaken for one. Returning 0 for a read
     * error would wipe the user's usage history over a transient fault.
     */
    private int readSchemaVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(Sql.get("select_meta_value"))) {
            ps.setString(1, SCHEMA_VERSION_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    int version;
                    try {
                        version = Integer.parseInt(value == null ? "" : value.trim());
                    } catch (NumberFormatException e) {
                        version = -1;
                    }
                    if (version < 1) {
                        // An absent row is the legacy marker; an explicit non-positive
                        // value is damage, and reading it as legacy drops every table.
                        throw new SQLException(
                                "Unreadable usage-stats schema version: " + value, "90020", 90020);
                    }
                    return version;
                }
            }
        }
        return 0;
    }

    /** Runs a database callable on the executor, returning {@code fallback} on failure. */
    <T> T call(Callable<T> task, T fallback) {
        if (unavailable) {
            return fallback;
        }
        Future<T> future;
        try {
            future = dbExecutor.submit(() -> ensureDatabase() ? task.call() : fallback);
        } catch (RejectedExecutionException e) {
            // shutdown() already ran: a late read is not an error, it has nowhere to go.
            log.log(Level.FINE, "Store already shut down; answering from the fallback", e);
            return fallback;
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException e) {
            log.log(Level.WARNING, "Usage-stats query failed", e.getCause());
            return fallback;
        }
    }

    /** Barrier so shutdown waits for queued writes instead of dropping them with the daemon thread. */
    void awaitQueuedWrites() {
        try {
            Future<?> barrier = dbExecutor.submit(() -> null);
            barrier.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            log.log(Level.WARNING, "Timed out waiting for queued store writes", e);
        } catch (RejectedExecutionException e) {
            // shutdown() already ran: nothing can be queued behind us.
            log.log(Level.FINE, "Store already shut down; skipping the flush barrier", e);
        }
    }

    /** Closes the connection and executor synchronously. Idempotent. Package-private; for tests. */
    synchronized void shutdown() {
        if (dbExecutor.isShutdown()) {
            return;
        }
        call(() -> {
            Connection local = connection;
            if (local != null) {
                closeWithRetry(local);
                connection = null;
            }
            return null;
        }, null);
        dbExecutor.shutdown();
    }

    /**
     * Closes the connection, retrying a few times. H2 releases the file lock
     * inside {@code close()}, and a close that throws leaves the new module of
     * an in-place upgrade locked out; the retry window covers a file channel
     * that is still draining on Windows.
     */
    private void closeWithRetry(Connection conn) {
        for (int attempt = 1; attempt <= CLOSE_ATTEMPTS; attempt++) {
            try {
                conn.close();
                return;
            } catch (SQLException e) {
                if (attempt == CLOSE_ATTEMPTS) {
                    log.log(Level.WARNING,
                            "Could not close the usage-stats database after " + CLOSE_ATTEMPTS + " attempts", e);
                    return;
                }
                log.log(Level.FINE, "Usage-stats database close failed; retrying", e);
                try {
                    Thread.sleep(CLOSE_RETRY_DELAY_MS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    log.log(Level.WARNING,
                            "Interrupted while closing the usage-stats database; it may stay locked", interrupted);
                    return;
                }
            }
        }
    }
}
