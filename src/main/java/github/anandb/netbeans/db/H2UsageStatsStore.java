package github.anandb.netbeans.db;

import github.anandb.netbeans.contract.ModelListControl;
import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.contract.SessionStore;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.AvailableModel;
import github.anandb.netbeans.model.MigrationReport;
import github.anandb.netbeans.model.SessionMetadata;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.ToolTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.ModelIdNormalizer;
import github.anandb.netbeans.support.PreferenceKeys;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.openide.modules.Places;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 * Embedded H2 implementation of {@link UsageStatsStore}, {@link SessionStore},
 * {@link PinnedMessageControl} and {@link ModelListControl}. One database lives
 * under the NetBeans user directory, so captured rows survive IDE restarts and
 * are readable with no harness process spawned. It is also the durable home of
 * the input history, the per-session metadata, the per-session pins and the
 * per-harness model lists that used to live in {@code NbPreferences}.
 *
 * <p>Every database operation runs on a single-threaded daemon executor, so a
 * write from the event dispatch thread enqueues and returns without I/O. Because
 * the executor is FIFO, a query submitted after a write observes that write.</p>
 */
@ServiceProviders({
    @ServiceProvider(service = UsageStatsStore.class),
    @ServiceProvider(service = SessionStore.class),
    @ServiceProvider(service = PinnedMessageControl.class),
    @ServiceProvider(service = ModelListControl.class)
})
public class H2UsageStatsStore
        implements UsageStatsStore, SessionStore, PinnedMessageControl, ModelListControl {

    private static final Logger LOG = Logger.from(H2UsageStatsStore.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    /** Legacy preference values the two new migrations decode. */
    private static final TypeReference<List<String>> LIST_OF_STRINGS = new TypeReference<>() { };
    private static final TypeReference<List<AvailableModel>> MODEL_LIST = new TypeReference<>() { };

    private static final String SCHEMA_VERSION_KEY = "schema_version";

    /** Idempotent DDL, run on every open. */
    private static final String SCHEMA_SCRIPT = "schema.sql";

    /** Set once the legacy {@code NbPreferences} data has been copied in. */
    private static final String PREFS_MIGRATED_KEY = "prefs_migrated";

    /** Set once {@code pinnedMessages.*} keys have been copied into {@code pinned_message}. */
    private static final String PINNED_MIGRATED_KEY = "pinned_migrated";

    /** Set once {@code modelList.*} keys have been copied into {@code harness_model}. */
    private static final String MODELS_MIGRATED_KEY = "models_migrated";

    /** Input-history cap, matching the value {@code MessageHistory} enforced. */
    private static final int HISTORY_CAP = 1024;

    /** Retention cap on the skip reasons a {@link MigrationReport} carries. */
    private static final int MAX_REPORT_REASONS = 10;

    // Legacy NbPreferences layout, read once by the migration. Kept as literals
    // here because these names have no producer left in the codebase.
    /** Child node holding {@code sessionId -> SessionMetadata JSON}. */
    private static final String LEGACY_META_NODE = "sessmeta";
    /** Child node holding {@code sessionId -> position} for locally-created sessions. */
    private static final String LEGACY_IDS_NODE = "sessids";
    /** Root keys holding per-session values, folded into the metadata rows. */
    private static final String LEGACY_TITLE_PREFIX = "session_title_";
    private static final String LEGACY_HIDDEN_PREFIX = "session_hidden_";
    private static final String LEGACY_USAGE_PREFIX = "session_usage_";
    /** Root keys holding the comma-joined locally-created id list. */
    private static final String LEGACY_LOCAL_SESSIONS_KEY = "gemini_local_sessions";
    /** Root keys holding a session's pinned message ids as a JSON array. */
    private static final String LEGACY_PINNED_PREFIX = "pinnedMessages.";
    /** Root keys holding a harness's model list as a JSON array. */
    private static final String LEGACY_MODEL_PREFIX = "modelList.";
    /** Root keys superseded by the store; no code reads them. */
    private static final List<String> OBSOLETE_KEYS = List.of("caveman.enabled");

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
     * reporting is retained for two days before its row is dropped; existing rows
     * are stamped at upgrade time.
     * Version 8 adds {@code tool_call_usage.duration_ms} and {@code .status}: one
     * row now records one finished MCP tool execution instead of a turn's per-tool
     * counts. The columns are additive and defaulted, so version-7 rows survive and
     * their {@code call_count} still sums correctly with the new rows.
     */
    private static final int SCHEMA_VERSION = 8;

    /** How long a model the harness stops reporting is kept before its row is dropped. */
    static final long MODEL_RETENTION_MS = TimeUnit.DAYS.toMillis(2);

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

    private static final long DAY_MILLIS = 86_400_000L;

    private final String jdbcUrl;
    private final Object initLock = new Object();
    private final ExecutorService dbExecutor;
    private volatile Connection connection;

    /**
     * Set when {@link #open()} failed. H2 refuses a second process on the same
     * database file ("Database may be already in use"), and retrying cannot fix
     * that while the other process lives. Every operation therefore returns
     * empty and logs nothing; only {@link #retryAfterFailure()} can re-arm the
     * attempt, and only once ({@link #MAX_OPEN_ATTEMPTS}). The preferences stay
     * untouched, so the next launch tries again regardless.
     */
    private volatile boolean unavailable;

    /** Open attempts made so far; only {@link #connection()} writes it, under {@link #initLock}. */
    private int openAttempts;

    /**
     * Input history, newest last. An immutable value published by a single
     * reference assignment, so a reader from the EDT always sees either the
     * previous list or the new one — never a half-replaced list.
     */
    private volatile List<String> history = List.of();

    /**
     * Metadata per harness bucket, newest session first. Each bucket is an
     * immutable snapshot replaced wholesale on write, so readers iterate without
     * locking and a write never mutates a map a reader already holds.
     */
    private final Map<String, Map<String, SessionMetadata>> metadata = new ConcurrentHashMap<>();

    /**
     * True once the metadata cache has been populated from the database. Until
     * then a caller's record is derived from nothing, so its blank fields mean
     * "unknown" rather than "clear" — see {@link #saveSessionMetadata}.
     */
    private volatile boolean metadataLoaded;

    /**
     * Pinned message ids per session, as immutable snapshots. Reads come from the
     * EDT and that is the whole reason the database is not consulted: the cache is
     * populated by {@link #load()} on the database thread.
     */
    private final Map<String, Set<String>> pins = new ConcurrentHashMap<>();

    /**
     * Sessions whose pins were written after the last cache load. A write that
     * races startup must not be replaced by the older database snapshot, so
     * {@link #loadPins()} leaves these sessions alone.
     */
    private final Set<String> pinsDirty = ConcurrentHashMap.newKeySet();

    /**
     * Last known model list per harness with the time each model was last reported.
     * Immutable snapshots, same convention as the pin cache.
     */
    private final Map<String, List<TimedModel>> models = new ConcurrentHashMap<>();

    /** Harnesses whose model list was written after the last cache load. */
    private final Set<String> modelsDirty = ConcurrentHashMap.newKeySet();

    /** Serialises the read-modify-write of one session's pin set on the caller thread. */
    private final Object pinsLock = new Object();

    /** Serialises the read-modify-write of one harness's model list on the caller thread. */
    private final Object modelsLock = new Object();

    /** A cached model and the time the harness last reported it. */
    private record TimedModel(AvailableModel model, long lastSeen) { }

    /** Service-provider constructor: stores the database under the user directory. */
    public H2UsageStatsStore() {
        this(defaultJdbcUrl());
    }

    /**
     * Test constructor.
     *
     * @param jdbcUrl an H2 URL, e.g. {@code jdbc:h2:mem:test;DB_CLOSE_DELAY=-1}
     */
    H2UsageStatsStore(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "usage-stats-db");
            t.setDaemon(true);
            return t;
        });
    }

    /** Absolute H2 URL for the per-user database file. */
    private static String defaultJdbcUrl() {
        File userDir = Places.getUserDirectory();
        if (userDir == null) {
            // No configured user directory. Without this guard
            // `new File(null, "beanbot")` yields a *relative* path and the database
            // is created in the process working directory, which is how one ended
            // up in the repository root. Fall back to a temporary location, as the
            // platform itself does for caches.
            userDir = new File(System.getProperty("java.io.tmpdir"), "nbcache");
        }
        File dir = new File(userDir, "beanbot");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOG.log(Level.WARNING, "Could not create usage-stats directory: {0}", dir.getAbsolutePath());
        }
        String path = new File(dir, "usage-stats").getAbsolutePath().replace('\\', '/');
        return "jdbc:h2:" + path;
    }

    @Override
    public void recordUsageUpdate(UsageUpdateRow row) {
        if (row == null) {
            return;
        }
        runDb(() -> {
            try (PreparedStatement ps = connection().prepareStatement(Sql.get("insert_usage_update"))) {
                bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setLong(6, row.used());
                setNullableLong(ps, 7, row.size());
                // Harnesses repeat the session's cumulative cost per notification
                // (pi-acp re-sends the running total several times per turn).
                // Storing the delta against the spend already recorded for the
                // session keeps SUM(cost_amount) equal to the true spend.
                ps.setDouble(8, costDelta(row));
                ps.setString(9, row.costCurrency() != null ? row.costCurrency() : "USD");
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to record usage_update row", e);
            }
        });
    }

    /** Opens the H2 database, migrating or dropping legacy tables. */
    private Connection open() throws SQLException {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            LOG.log(Level.WARNING, "H2 driver not on the classpath", e);
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
                LOG.log(Level.FINE, "Failed to close abandoned H2 connection", closed);
            }
            throw e;
        }
    }

    /** Tables carrying a model id, in the order they were introduced. */
    private static final String[] CAPTURE_TABLES = {"usage_update", "prompt_usage", "message_event", "tool_call_usage"};

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
        LOG.log(Level.INFO, "Canonicalised {0} stored model id(s)", stale.size());
    }

    /**
     * Difference between this row's cumulative cost and the spend already
     * recorded for the same session. The stored rows carry per-row deltas,
     * whose sum equals the session's cumulative spend even across restarts.
     * The first row of a session stores the full amount; a session with no
     * attribution stores the full amount too.
     */
    private double costDelta(UsageUpdateRow row) throws SQLException {
        Attribution attribution = row.attribution();
        if (attribution == null || attribution.sessionId() == null) {
            return row.costAmount();
        }
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_session_cost"))) {
            ps.setString(1, attribution.sessionId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row.costAmount() - rs.getDouble(1) : row.costAmount();
            }
        }
    }

    @Override
    public void recordPromptUsage(PromptUsageRow row) {
        if (row == null) {
            return;
        }
        runDb(() -> {
            try (PreparedStatement ps = connection().prepareStatement(Sql.get("insert_prompt_usage"))) {
                bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setLong(6, row.inputTokens());
                ps.setLong(7, row.outputTokens());
                ps.setLong(8, row.totalTokens());
                setNullableLong(ps, 9, row.thoughtTokens());
                setNullableLong(ps, 10, row.cachedReadTokens());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to record prompt usage row", e);
            }
        });
    }

    @Override
    public void recordMessage(MessageEvent event) {
        if (event == null) {
            return;
        }
        runDb(() -> {
            try (PreparedStatement ps = connection().prepareStatement(Sql.get("insert_message_event"))) {
                bindAttribution(ps, 1, event.attribution());
                ps.setLong(5, event.capturedAt());
                ps.setString(6, event.kind() != null ? event.kind().name() : "ASSISTANT");
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to record message event", e);
            }
        });
    }

    @Override
    public void recordToolCall(ToolCallExecution row) {
        if (row == null || row.toolName() == null || row.toolName().isBlank()) {
            return;
        }
        runDb(() -> {
            try (PreparedStatement ps = connection().prepareStatement(Sql.get("insert_tool_call_usage"))) {
                bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setString(6, row.toolName());
                // One row per execution; keeping the column at 1 leaves a SUM over
                // pre-version-8 turn aggregates correct.
                ps.setLong(7, 1);
                ps.setLong(8, Math.max(row.durationMs(), 0));
                ps.setString(9, row.status() != null ? row.status().name().toLowerCase(Locale.ROOT) : "ok");
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to record tool-call usage row", e);
            }
        });
    }

    // ---- SessionStore: input history and session metadata ----
    //
    // Both datasets live in memory and are populated by load(); reads never touch
    // the database, so they are safe from the EDT. Every database and preference
    // access below runs on dbExecutor, whose single thread is therefore the only
    // serialisation the migration needs.

    @Override
    public void load() {
        call(() -> {
            loadHistory();
            loadMetadata();
            loadPins();
            loadModels();
            return null;
        }, null);
    }

    @Override
    public MigrationReport migrateLegacyPrefs() {
        MigrationReport report = call(this::runPrefsMigration, MigrationReport.none());
        // Rows the migration just wrote must be readable without a restart.
        load();
        return report;
    }

    /**
     * Spends the one retry an explicit user action may request after the startup
     * open failed. Reopens on the database thread and finishes the preference
     * migration the failed open skipped.
     *
     * <p>The in-memory datasets are deliberately not reloaded: this session kept
     * writing to them while the database was unreachable, and a load would
     * replace them with older rows — dropping input history typed in the
     * meantime. They persist from this point on, and the next launch loads the
     * complete set.</p>
     */
    @Override
    public void retryAfterFailure() {
        synchronized (initLock) {
            if (connection != null || openAttempts >= MAX_OPEN_ATTEMPTS) {
                return;
            }
            // Re-arm exactly one attempt; ensureDatabase() reports the outcome.
            unavailable = false;
        }
        call(this::runPrefsMigration, MigrationReport.none());
    }

    @Override
    public List<String> inputHistory() {
        return history;
    }

    @Override
    public void appendInputHistory(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<String> current = history;
        if (!current.isEmpty() && current.get(current.size() - 1).equals(text)) {
            return;
        }
        List<String> next = new ArrayList<>(current);
        next.add(text);
        while (next.size() > HISTORY_CAP) {
            next.remove(0);
        }
        history = List.copyOf(next);
        runDb(() -> {
            try (PreparedStatement ps = connection().prepareStatement(Sql.get("insert_input_history"))) {
                ps.setString(1, text);
                ps.executeUpdate();
                pruneHistory();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to append an input history entry", e);
            }
        });
    }

    @Override
    public Map<String, SessionMetadata> sessionMetadata(String harnessId) {
        Map<String, SessionMetadata> bucket = metadata.get(SessionStore.bucketName(harnessId));
        return bucket != null ? bucket : Map.of();
    }

    @Override
    public void saveSessionMetadata(String harnessId, String sessionId, SessionMetadata sessionMetadata) {
        if (sessionId == null || sessionId.isEmpty() || sessionMetadata == null) {
            return;
        }
        String bucket = SessionStore.bucketName(harnessId);
        putInMemory(bucket, sessionId, sessionMetadata);
        // Sampled here, not in the task: a record built before the cache loaded
        // carries blanks that mean "unknown", and the stored row must fill them in
        // rather than be overwritten with nulls. Seeing the flag later would be too
        // late, since a load completing in between would hide the race.
        boolean fillFromStored = !metadataLoaded;
        runDb(() -> {
            try {
                SessionMetadata effective = fillFromStored
                        ? fillBlanksFromStored(bucket, sessionId, sessionMetadata)
                        : sessionMetadata;
                upsertSessionMeta(bucket, sessionId, effective, null);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to save session metadata", e);
            }
        });
    }

    /**
     * Bulk form of {@link #saveSessionMetadata}: one snapshot rebuild in memory and
     * one transaction on the database thread, instead of a round trip per session.
     */
    @Override
    public void saveSessionMetadataBulk(String harnessId, Map<String, SessionMetadata> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        String bucket = SessionStore.bucketName(harnessId);
        Map<String, SessionMetadata> snapshot = new LinkedHashMap<>(updates);
        putAllInMemory(bucket, snapshot);
        boolean fillFromStored = !metadataLoaded;
        runDb(() -> {
            try {
                upsertSessionMetaBatch(bucket, snapshot, fillFromStored);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to save session metadata batch", e);
            }
        });
    }

    /**
     * Fills a record's unknown fields from the stored row. Used only for a write
     * that raced startup, where the caller could not have known the saved values;
     * the stored hidden flag wins for the same reason.
     */
    private SessionMetadata fillBlanksFromStored(String bucket, String sessionId, SessionMetadata meta)
            throws SQLException {
        SessionMetadata stored = storedMetadata(bucket, sessionId);
        if (stored == null) {
            return meta;
        }
        return new SessionMetadata(
                meta.title() != null ? meta.title() : stored.title(),
                meta.usage() != null ? meta.usage() : stored.usage(),
                stored.hidden(),
                meta.cwd() != null ? meta.cwd() : stored.cwd());
    }

    /** One session's stored row, or null when it has none. */
    private SessionMetadata storedMetadata(String bucket, String sessionId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_session_meta_row"))) {
            ps.setString(1, bucket);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String title = rs.getString(1);
                boolean hidden = rs.getBoolean(2);
                String cwd = rs.getString(3);
                String usage = usageString(rs, 4, 5);
                return new SessionMetadata(title, usage, hidden, cwd);
            }
        }
    }

    // ---- SessionStore memory ----

    /**
     * Replaces one bucket with a new immutable snapshot holding {@code meta}.
     * A session already present keeps its position; one not seen before becomes
     * the newest for its harness, which is how the dropped id lists ordered it.
     */
    private void putInMemory(String bucket, String sessionId, SessionMetadata meta) {
        this.metadata.compute(bucket, (key, current) -> {
            LinkedHashMap<String, SessionMetadata> next = new LinkedHashMap<>();
            boolean replaced = false;
            if (current != null) {
                for (Map.Entry<String, SessionMetadata> entry : current.entrySet()) {
                    if (sessionId.equals(entry.getKey())) {
                        next.put(entry.getKey(), meta);
                        replaced = true;
                    } else {
                        next.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (replaced) {
                return Collections.unmodifiableMap(next);
            }
            LinkedHashMap<String, SessionMetadata> withNew = new LinkedHashMap<>();
            withNew.put(sessionId, meta);
            withNew.putAll(next);
            return Collections.unmodifiableMap(withNew);
        });
    }

    /**
     * Bulk form of {@link #putInMemory}: one snapshot rebuild for the whole batch.
     * Existing sessions keep their position; new ones become the newest, in batch
     * order.
     */
    private void putAllInMemory(String bucket, Map<String, SessionMetadata> updates) {
        this.metadata.compute(bucket, (key, current) -> {
            LinkedHashMap<String, SessionMetadata> existing = new LinkedHashMap<>();
            if (current != null) {
                for (Map.Entry<String, SessionMetadata> entry : current.entrySet()) {
                    SessionMetadata replacement = updates.get(entry.getKey());
                    existing.put(entry.getKey(), replacement != null ? replacement : entry.getValue());
                }
            }
            LinkedHashMap<String, SessionMetadata> added = new LinkedHashMap<>();
            for (Map.Entry<String, SessionMetadata> entry : updates.entrySet()) {
                if (current == null || !current.containsKey(entry.getKey())) {
                    added.put(entry.getKey(), entry.getValue());
                }
            }
            if (added.isEmpty()) {
                return Collections.unmodifiableMap(existing);
            }
            LinkedHashMap<String, SessionMetadata> withNew = new LinkedHashMap<>(added);
            withNew.putAll(existing);
            return Collections.unmodifiableMap(withNew);
        });
    }

    // ---- SessionStore database ----

    private void loadHistory() throws SQLException {
        List<String> loaded = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_input_history"));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loaded.add(rs.getString(1));
            }
        }
        // One assignment, so a reader never observes an empty intermediate state.
        history = List.copyOf(loaded);
    }

    /** Reads every row, oldest first per harness so the exposed order is newest-first. */
    private void loadMetadata() throws SQLException {
        Map<String, LinkedHashMap<String, SessionMetadata>> byHarness = new LinkedHashMap<>();
        String sql = Sql.get("select_all_session_meta");
        try (PreparedStatement ps = connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String bucket = SessionStore.bucketName(rs.getString(1));
                String sessionId = rs.getString(2);
                String usage = usageString(rs, 6, 7);
                byHarness.computeIfAbsent(bucket, k -> new LinkedHashMap<>())
                        .put(sessionId, new SessionMetadata(
                                rs.getString(3), usage, rs.getBoolean(4), rs.getString(5)));
            }
        }
        for (Map.Entry<String, LinkedHashMap<String, SessionMetadata>> entry : byHarness.entrySet()) {
            List<Map.Entry<String, SessionMetadata>> ordered = new ArrayList<>(entry.getValue().entrySet());
            Collections.reverse(ordered);
            LinkedHashMap<String, SessionMetadata> newestFirst = new LinkedHashMap<>();
            for (Map.Entry<String, SessionMetadata> row : ordered) {
                newestFirst.put(row.getKey(), row.getValue());
            }
            metadata.put(entry.getKey(), Collections.unmodifiableMap(newestFirst));
        }
        metadataLoaded = true;
    }

    private void upsertSessionMeta(String bucket, String sessionId, SessionMetadata meta,
                                   Long explicitOrdinal) throws SQLException {
        Long ordinal = explicitOrdinal != null ? explicitOrdinal : existingOrdinal(bucket, sessionId);
        if (ordinal == null) {
            ordinal = nextOrdinal(bucket);
        }
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("merge_session_meta"))) {
            bindSessionMeta(ps, bucket, sessionId, meta, ordinal);
            ps.executeUpdate();
        }
    }

    /**
     * Writes a batch of metadata rows in one transaction, resolving each session's
     * ordinal from a single read of the bucket. New sessions take the ordinals
     * after the current maximum, in batch order.
     */
    private void upsertSessionMetaBatch(String bucket, Map<String, SessionMetadata> updates,
            boolean fillFromStored) throws SQLException {
        Connection conn = connection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(Sql.get("merge_session_meta"))) {
            Map<String, Long> ordinals = storedOrdinals(bucket);
            long highest = 0L;
            for (long value : ordinals.values()) {
                highest = Math.max(highest, value);
            }
            for (Map.Entry<String, SessionMetadata> entry : updates.entrySet()) {
                SessionMetadata meta = fillFromStored
                        ? fillBlanksFromStored(bucket, entry.getKey(), entry.getValue())
                        : entry.getValue();
                Long ordinal = ordinals.get(entry.getKey());
                if (ordinal == null) {
                    ordinal = ++highest;
                }
                bindSessionMeta(ps, bucket, entry.getKey(), meta, ordinal);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /** Binds one {@code merge_session_meta} row. */
    private static void bindSessionMeta(PreparedStatement ps, String bucket, String sessionId,
            SessionMetadata meta, long ordinal) throws SQLException {
        Long[] usage = parseUsage(meta.usage());
        ps.setString(1, bucket);
        ps.setString(2, sessionId);
        ps.setString(3, meta.title());
        ps.setBoolean(4, meta.hidden());
        ps.setString(5, meta.cwd());
        setNullableLong(ps, 6, usage[0]);
        setNullableLong(ps, 7, usage[1]);
        ps.setLong(8, ordinal);
    }

    /** Every stored ordinal in a bucket, keyed by session id. */
    private Map<String, Long> storedOrdinals(String bucket) throws SQLException {
        Map<String, Long> ordinals = new HashMap<>();
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_session_ordinals"))) {
            ps.setString(1, bucket);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ordinals.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return ordinals;
    }

    /** The stored ordinal for a session, or null when it has no row yet. */
    private Long existingOrdinal(String bucket, String sessionId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_session_ordinal"))) {
            ps.setString(1, bucket);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Next free ordinal in a bucket; the newest session carries the highest. */
    private long nextOrdinal(String bucket) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_next_ordinal"))) {
            ps.setString(1, bucket);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1L;
            }
        }
    }

    /** Drops all but the newest {@link #HISTORY_CAP} history rows. */
    private void pruneHistory() throws SQLException {
        Long oldestKept = null;
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("prune_history_seq"))) {
            ps.setInt(1, HISTORY_CAP - 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    oldestKept = rs.getLong(1);
                }
            }
        }
        if (oldestKept == null) {
            return;
        }
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("delete_input_history_before"))) {
            ps.setLong(1, oldestKept);
            ps.executeUpdate();
        }
    }

    /**
     * Rebuilds the "{@code used,size}" string the UI parses from the two stored
     * columns. Both absent reads back as null, which is what an unset usage was.
     */
    private static String usageString(ResultSet rs, int usedIndex, int sizeIndex) throws SQLException {
        long used = rs.getLong(usedIndex);
        boolean usedAbsent = rs.wasNull();
        long size = rs.getLong(sizeIndex);
        boolean sizeAbsent = rs.wasNull();
        if (usedAbsent && sizeAbsent) {
            return null;
        }
        return (usedAbsent ? 0 : used) + "," + (sizeAbsent ? 0 : size);
    }

    /** Splits the "{@code used,size}" string into the two columns; nulls when unusable. */
    private static Long[] parseUsage(String usage) {
        if (usage == null || usage.isBlank()) {
            return new Long[]{null, null};
        }
        String[] parts = usage.split(",", -1);
        return new Long[]{
            parseLongOrNull(parts.length > 0 ? parts[0] : null),
            parseLongOrNull(parts.length > 1 ? parts[1] : null)};
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- PinnedMessageControl and ModelListControl ----
    //
    // Both datasets are per-session/per-harness and grow with every session or
    // harness, which is what pushed them out of the preferences file. Reads are
    // memory-only (the caches are primed by load()); writes enqueue on dbExecutor.

    private void loadPins() throws SQLException {
        Map<String, LinkedHashSet<String>> loaded = new HashMap<>();
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_pins"));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loaded.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>())
                        .add(rs.getString(2));
            }
        }
        loaded.forEach((sessionId, ids) -> {
            if (!pinsDirty.contains(sessionId)) {
                pins.put(sessionId, Collections.unmodifiableSet(ids));
            }
        });
    }

    private void loadModels() throws SQLException {
        Map<String, List<TimedModel>> loaded = new LinkedHashMap<>();
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_models"));
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loaded.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(new TimedModel(new AvailableModel(rs.getString(2), rs.getString(3), rs.getString(4)),
                                rs.getLong(5)));
            }
        }
        // No retention pass here: the window is measured from a harness's most
        // recent report, so rows loaded after a long break must survive until
        // that harness reports again.
        loaded.forEach((harnessId, list) -> {
            if (!modelsDirty.contains(harnessId)) {
                models.put(harnessId, List.copyOf(list));
            }
        });
    }

    @Override
    public boolean isPinned(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) {
            return false;
        }
        Set<String> pinned = pins.get(sessionId);
        return pinned != null && pinned.contains(messageId);
    }

    @Override
    public void setPinned(String sessionId, String messageId, boolean pinned) {
        if (sessionId == null || messageId == null) {
            return;
        }
        synchronized (pinsLock) {
            Set<String> current = pins.getOrDefault(sessionId, Set.of());
            if (current.contains(messageId) == pinned) {
                return;
            }
            LinkedHashSet<String> updated = new LinkedHashSet<>(current);
            if (pinned) {
                updated.add(messageId);
            } else {
                updated.remove(messageId);
            }
            pins.put(sessionId, Collections.unmodifiableSet(updated));
            pinsDirty.add(sessionId);
        }
        long now = System.currentTimeMillis();
        runDb(() -> {
            try {
                // Row-level delta, never a snapshot replace: a write that races the
                // startup load must not resurrect rows the load has not seen yet.
                if (pinned) {
                    try (PreparedStatement ps = connection().prepareStatement(Sql.get("merge_pinned_message"))) {
                        ps.setString(1, sessionId);
                        ps.setString(2, messageId);
                        ps.setLong(3, now);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = connection().prepareStatement(Sql.get("delete_pin"))) {
                        ps.setString(1, sessionId);
                        ps.setString(2, messageId);
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to persist the pin state of a message", e);
            }
        });
    }

    @Override
    public void loadSession(String sessionId) {
        // load() primed every session's pins from the database at startup, so
        // there is nothing per-session left to read. Kept for the interface.
    }

    @Override
    public void retainPinned(String sessionId, Set<String> activeMessageIds) {
        if (sessionId == null || activeMessageIds == null) {
            return;
        }
        Set<String> kept;
        synchronized (pinsLock) {
            Set<String> current = pins.get(sessionId);
            if (current == null || activeMessageIds.containsAll(current)) {
                return;
            }
            LinkedHashSet<String> remaining = new LinkedHashSet<>(current);
            remaining.retainAll(activeMessageIds);
            kept = Collections.unmodifiableSet(remaining);
            pins.put(sessionId, kept);
            pinsDirty.add(sessionId);
        }
        LOG.info("Pinned-message cleanup for session {0}: kept {1}",
                sessionId, kept.size());
        Set<String> snapshot = kept;
        runDb(() -> {
            try {
                replacePins(sessionId, snapshot);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to prune stale pinned messages", e);
            }
        });
    }

    @Override
    public void unloadSession(String sessionId) {
        // Intentionally no eviction: the cache is small (ids only) and dropping it
        // would make the next read a blocking database call from the EDT.
    }

    @Override
    public List<AvailableModel> getModels(String harnessId) {
        if (harnessId == null || harnessId.isBlank()) {
            return List.of();
        }
        List<TimedModel> cached = models.get(harnessId);
        if (cached == null) {
            return List.of();
        }
        return cached.stream().map(TimedModel::model).toList();
    }

    @Override
    public void updateModels(String harnessId, List<AvailableModel> list) {
        updateModels(harnessId, list, System.currentTimeMillis());
    }

    /**
     * Records {@code list} as the harness's current models. Included models are
     * stamped with {@code now}; models the harness stopped reporting are kept
     * until {@link #MODEL_RETENTION_MS} has elapsed since they were last seen,
     * measured from this newest report. Package-private so tests can supply the
     * clock.
     */
    void updateModels(String harnessId, List<AvailableModel> list, long now) {
        if (harnessId == null || harnessId.isBlank() || list == null || list.isEmpty()) {
            return;
        }
        List<TimedModel> merged;
        synchronized (modelsLock) {
            merged = mergeModels(harnessId, list, now);
            models.put(harnessId, merged);
        }
        modelsDirty.add(harnessId);
        runDb(() -> {
            try {
                replaceModels(harnessId, merged);
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to persist the model list for harness " + harnessId, e);
            }
        });
    }

    /** Incoming models first, then the harness's previously known models that are still
     *  inside the retention window; expired models are dropped. Caller holds {@code modelsLock}. */
    private List<TimedModel> mergeModels(String harnessId, List<AvailableModel> list, long now) {
        long cutoff = now - MODEL_RETENTION_MS;
        List<TimedModel> merged = new ArrayList<>(list.size() + 4);
        Set<String> seen = new HashSet<>();
        for (AvailableModel model : list) {
            if (model == null || model.modelId() == null || !seen.add(model.modelId())) {
                continue;
            }
            merged.add(new TimedModel(model, now));
        }
        for (TimedModel previous : models.getOrDefault(harnessId, List.of())) {
            String modelId = previous.model().modelId();
            if (modelId != null && !seen.contains(modelId) && previous.lastSeen() >= cutoff) {
                seen.add(modelId);
                merged.add(previous);
            }
        }
        return List.copyOf(merged);
    }

    @Override
    public void flush() {
        // Writes are already queued on the executor; a barrier makes shutdown wait
        // for them instead of dropping the tail of the queue with the daemon thread.
        awaitQueuedWrites();
    }

    /**
     * Closes the connection and stops the writer so the database file is
     * unlocked before this module's classloader can be discarded by an in-place
     * upgrade. Idempotent.
     */
    @Override
    public void close() {
        shutdown();
    }

    private void awaitQueuedWrites() {
        try {
            Future<?> barrier = dbExecutor.submit(() -> null);
            barrier.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            LOG.log(Level.WARNING, "Timed out waiting for queued store writes", e);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // shutdown() already ran: nothing can be queued behind us.
            LOG.log(Level.FINE, "Store already shut down; skipping the flush barrier", e);
        }
    }

    /** Rewrites one session's pin rows in a transaction; used by the stale-pin cleanup. */
    private void replacePins(String sessionId, Set<String> pinned) throws SQLException {
        Connection conn = connection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement delete = conn.prepareStatement(Sql.get("delete_session_pins"))) {
                delete.setString(1, sessionId);
                delete.executeUpdate();
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement insert = conn.prepareStatement(Sql.get("merge_pinned_message"))) {
                for (String messageId : pinned) {
                    insert.setString(1, sessionId);
                    insert.setString(2, messageId);
                    insert.setLong(3, now);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /** Rewrites one harness's model rows in a transaction, preserving list order. */
    private void replaceModels(String harnessId, List<TimedModel> list) throws SQLException {
        Connection conn = connection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement delete = conn.prepareStatement(Sql.get("delete_harness_models"))) {
                delete.setString(1, harnessId);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = conn.prepareStatement(Sql.get("insert_harness_model"))) {
                int position = 0;
                for (TimedModel row : list) {
                    AvailableModel model = row.model();
                    insert.setString(1, harnessId);
                    insert.setInt(2, position++);
                    insert.setString(3, model.modelId());
                    insert.setString(4, model.name());
                    insert.setString(5, model.description());
                    insert.setLong(6, row.lastSeen());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    // ---- One-shot preference migration ----

    /**
     * Copies the legacy preference data into the tables, once per database.
     *
     * <p>Containment is per entry: a value that will not parse is counted in the
     * report and its key left in place, while everything else migrates. A node
     * that cannot be listed costs only that node. Nothing here propagates an
     * exception, so a damaged legacy file can never block startup.</p>
     *
     * <p>Order matters: rows are written first, then the marker, then the
     * preference data is deleted — and only when the marker was recorded, so a
     * database that cannot be written never costs the user their data.</p>
     */
    private MigrationReport runPrefsMigration() {
        if (!ensureDatabase()) {
            // Nothing was read or written, so there is nothing to report. Saying
            // "some data could not be migrated" here would blame the user's
            // preferences for a database another process is holding open.
            return MigrationReport.none();
        }
        List<String> reasons = new ArrayList<>();
        int[] migrated = {0};
        int[] skipped = {0};
        Preferences root = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);

        if (!isMigrated(PREFS_MIGRATED_KEY)) {
            Set<String> bucketsToKeep = new LinkedHashSet<>();
            migrateHistory(root, migrated, skipped, reasons);
            List<String> metaNodes = childNodeNames(root, LEGACY_META_NODE, reasons);
            List<String> idNodes = childNodeNames(root, LEGACY_IDS_NODE, reasons);
            Map<String, Map<String, SessionMetadata>> byHarness =
                    readLegacyMetadata(root, metaNodes, skipped, reasons, bucketsToKeep);
            Map<String, Map<String, Long>> legacyIds = readLegacyIds(root, idNodes, reasons);
            foldLegacyRootKeys(root, byHarness, skipped, reasons);
            bucketsToKeep.addAll(
                    writeMigratedMetadata(byHarness, legacyIds, migrated, skipped, reasons));

            boolean recorded = markMigrated(PREFS_MIGRATED_KEY);
            if (recorded) {
                deleteLegacyPreferences(root, metaNodes, idNodes, bucketsToKeep, reasons);
            } else {
                LOG.log(Level.WARNING,
                        "Preference migration could not record its marker; leaving preferences untouched");
            }
        }

        // Pins and model lists moved in a later release and carry their own markers:
        // an installation that ran the first migration already has PREFS_MIGRATED_KEY
        // set, so the guard above would otherwise strand them in preferences forever.
        migratePinnedMessages(root, migrated, skipped, reasons);
        migrateModelLists(root, migrated, skipped, reasons);

        MigrationReport report = new MigrationReport(migrated[0], skipped[0], reasons);
        if (report.hasSkips()) {
            LOG.log(Level.WARNING, "Preference migration moved {0} entries and skipped {1}",
                    new Object[]{report.migrated(), report.skipped()});
        } else if (report.migrated() > 0) {
            LOG.log(Level.INFO, "Preference migration moved {0} entries into the database",
                    report.migrated());
        }
        return report;
    }

    private boolean isMigrated(String marker) {
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("select_meta_value"))) {
            ps.setString(1, marker);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "true".equalsIgnoreCase(rs.getString(1));
            }
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Could not read the migration marker; treating as not migrated", e);
            return false;
        }
    }

    /** @return true when the database accepted the marker, so the sources may be deleted. */
    private boolean markMigrated(String marker) {
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("merge_meta_value"))) {
            ps.setString(1, marker);
            ps.setString(2, "true");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not record the preference migration marker", e);
            return false;
        }
    }

    /**
     * Copies {@code pinnedMessages.<sessionId>} JSON arrays into {@code pinned_message}.
     * Rows first, then the marker, then the keys — and only for sessions whose rows
     * were written, so an unwritable database never costs the user their pins.
     */
    private void migratePinnedMessages(Preferences root, int[] migrated, int[] skipped,
                                       List<String> reasons) {
        if (isMigrated(PINNED_MIGRATED_KEY)) {
            return;
        }
        Map<String, List<String>> parsed = new LinkedHashMap<>();
        for (String key : rootKeys(root, reasons)) {
            if (!key.startsWith(LEGACY_PINNED_PREFIX)) {
                continue;
            }
            String sessionId = key.substring(LEGACY_PINNED_PREFIX.length());
            try {
                List<String> ids = MAPPER.readValue(root.get(key, "[]"), LIST_OF_STRINGS);
                parsed.put(sessionId, ids == null ? List.of() : ids);
            } catch (IOException | RuntimeException e) {
                skipped[0]++;
                addReason(reasons, "pinned messages: " + sessionId + ": " + e.getMessage());
            }
        }
        List<String> written = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
            try {
                Set<String> ids = new LinkedHashSet<>(entry.getValue());
                ids.remove(null);
                replacePins(entry.getKey(), ids);
                written.add(entry.getKey());
                migrated[0]++;
            } catch (SQLException e) {
                skipped[0]++;
                addReason(reasons, "pinned messages: " + entry.getKey() + ": could not be stored");
            }
        }
        if (!markMigrated(PINNED_MIGRATED_KEY)) {
            LOG.log(Level.WARNING, "Could not record the pinned-message migration marker");
            return;
        }
        for (String sessionId : written) {
            root.remove(LEGACY_PINNED_PREFIX + sessionId);
        }
        flushPreferences(root, reasons);
    }

    /**
     * Copies {@code modelList.<harnessId>} JSON arrays into {@code harness_model}.
     * Same order and containment as the pinned migration.
     */
    private void migrateModelLists(Preferences root, int[] migrated, int[] skipped,
                                   List<String> reasons) {
        if (isMigrated(MODELS_MIGRATED_KEY)) {
            return;
        }
        Map<String, List<AvailableModel>> parsed = new LinkedHashMap<>();
        for (String key : rootKeys(root, reasons)) {
            if (!key.startsWith(LEGACY_MODEL_PREFIX)) {
                continue;
            }
            String harnessId = key.substring(LEGACY_MODEL_PREFIX.length());
            try {
                List<AvailableModel> models = MAPPER.readValue(root.get(key, "[]"), MODEL_LIST);
                if (models == null) {
                    models = List.of();
                }
                models.removeIf(Objects::isNull);
                parsed.put(harnessId, models);
            } catch (IOException | RuntimeException e) {
                skipped[0]++;
                addReason(reasons, "model list: " + harnessId + ": " + e.getMessage());
            }
        }
        long now = System.currentTimeMillis();
        List<String> written = new ArrayList<>();
        for (Map.Entry<String, List<AvailableModel>> entry : parsed.entrySet()) {
            try {
                replaceModels(entry.getKey(), stampModels(entry.getValue(), now));
                written.add(entry.getKey());
                migrated[0]++;
            } catch (SQLException e) {
                skipped[0]++;
                addReason(reasons, "model list: " + entry.getKey() + ": could not be stored");
            }
        }
        if (!markMigrated(MODELS_MIGRATED_KEY)) {
            LOG.log(Level.WARNING, "Could not record the model-list migration marker");
            return;
        }
        for (String harnessId : written) {
            root.remove(LEGACY_MODEL_PREFIX + harnessId);
        }
        flushPreferences(root, reasons);
    }

    /** Wraps migrated models with one observation time. */
    private static List<TimedModel> stampModels(List<AvailableModel> list, long now) {
        List<TimedModel> rows = new ArrayList<>(list.size());
        for (AvailableModel model : list) {
            if (model != null) {
                rows.add(new TimedModel(model, now));
            }
        }
        return List.copyOf(rows);
    }

    private static List<String> rootKeys(Preferences root, List<String> reasons) {
        try {
            return List.of(root.keys());
        } catch (BackingStoreException e) {
            addReason(reasons, "preference keys: could not be listed: " + e.getMessage());
            return List.of();
        }
    }

    private static void flushPreferences(Preferences root, List<String> reasons) {
        try {
            root.flush();
        } catch (BackingStoreException e) {
            addReason(reasons, "preference keys: could not be flushed: " + e.getMessage());
        }
    }

    private void migrateHistory(Preferences root, int[] migrated, int[] skipped, List<String> reasons) {
        if (historyRowCount() > 0) {
            // Rows are already there, so a previous run inserted them — the absent
            // marker only means that run was interrupted before it finished. Plain
            // INSERTs are not absorbed by an upsert, so importing again would
            // duplicate every entry.
            return;
        }
        Map<Integer, String> byPosition = new TreeMap<>();
        try {
            for (String key : root.keys()) {
                if (!key.startsWith(PreferenceKeys.INPUT_HISTORY_PREFIX)
                        || key.equals(PreferenceKeys.INPUT_HISTORY_COUNT)) {
                    continue;
                }
                String value = root.get(key, null);
                if (value == null) {
                    continue;
                }
                Integer position = parseIntOrNull(key.substring(PreferenceKeys.INPUT_HISTORY_PREFIX.length()));
                if (position == null) {
                    skipped[0]++;
                    addReason(reasons, "history entry " + key + ": unreadable position");
                    continue;
                }
                byPosition.put(position, value);
            }
        } catch (BackingStoreException e) {
            skipped[0]++;
            addReason(reasons, "input history: could not be listed: " + e.getMessage());
            return;
        }
        // Position 0 is the oldest, so writing in position order keeps the
        // auto-increment sequence newest-last, which is the exposed order.
        List<String> entries = new ArrayList<>(byPosition.values());
        while (entries.size() > HISTORY_CAP) {
            entries.remove(0);
        }
        for (String entry : entries) {
            try (PreparedStatement ps = connection().prepareStatement(Sql.get("insert_input_history"))) {
                ps.setString(1, entry);
                ps.executeUpdate();
                migrated[0]++;
            } catch (SQLException e) {
                skipped[0]++;
                addReason(reasons, "history entry: " + e.getMessage());
            }
        }
    }

    /** Rows currently in the history table; 0 also means the table cannot be read. */
    private long historyRowCount() {
        try (PreparedStatement ps = connection().prepareStatement(Sql.get("count_input_history"));
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Could not count input history rows; treating as empty", e);
            return 0;
        }
    }

    /** Child node names belonging to one legacy family, e.g. {@code sessmeta} and {@code sessmeta_x}. */
    private static List<String> childNodeNames(Preferences root, String family, List<String> reasons) {
        List<String> names = new ArrayList<>();
        try {
            for (String name : root.childrenNames()) {
                if (name.equals(family) || name.startsWith(family + "_")) {
                    names.add(name);
                }
            }
        } catch (BackingStoreException e) {
            addReason(reasons, family + " nodes: could not be listed: " + e.getMessage());
        }
        return names;
    }

    /** Maps a legacy node name back to its harness bucket. */
    private static String bucketFromNodeName(String nodeName, String family) {
        if (nodeName.equals(family) || nodeName.equals(family + "_")) {
            return SessionStore.NO_HARNESS;
        }
        return nodeName.substring(family.length() + 1);
    }

    /** Parses every legacy metadata entry, skipping only the entries that will not parse. */
    private Map<String, Map<String, SessionMetadata>> readLegacyMetadata(
            Preferences root, List<String> metaNodes, int[] skipped, List<String> reasons,
            Set<String> bucketsToKeep) {
        Map<String, Map<String, SessionMetadata>> byHarness = new LinkedHashMap<>();
        for (String nodeName : metaNodes) {
            Preferences node = root.node(nodeName);
            String bucket = bucketFromNodeName(nodeName, LEGACY_META_NODE);
            Map<String, SessionMetadata> sessions =
                    byHarness.computeIfAbsent(bucket, k -> new LinkedHashMap<>());
            try {
                for (String sessionId : node.keys()) {
                    String json = node.get(sessionId, null);
                    if (json == null) {
                        continue;
                    }
                    try {
                        sessions.put(sessionId, MAPPER.readValue(json, SessionMetadata.class));
                    } catch (Exception e) {
                        skipped[0]++;
                        bucketsToKeep.add(bucket);
                        addReason(reasons, nodeName + "/" + sessionId + ": " + e.getMessage());
                    }
                }
            } catch (BackingStoreException e) {
                skipped[0]++;
                bucketsToKeep.add(bucket);
                addReason(reasons, nodeName + ": could not be listed: " + e.getMessage());
            }
        }
        return byHarness;
    }

    /** Old per-harness session ordering: session id to position, 0 being the newest. */
    private Map<String, Map<String, Long>> readLegacyIds(Preferences root, List<String> idNodes,
                                                         List<String> reasons) {
        Map<String, Map<String, Long>> byHarness = new LinkedHashMap<>();
        for (String nodeName : idNodes) {
            Preferences node = root.node(nodeName);
            String bucket = bucketFromNodeName(nodeName, LEGACY_IDS_NODE);
            Map<String, Long> ids = byHarness.computeIfAbsent(bucket, k -> new LinkedHashMap<>());
            try {
                for (String sessionId : node.keys()) {
                    ids.put(sessionId, parseLongOrNull(node.get(sessionId, null)));
                }
            } catch (BackingStoreException e) {
                addReason(reasons, nodeName + ": ids could not be listed: " + e.getMessage());
            }
        }
        return byHarness;
    }

    /**
     * Folds the root-level per-session keys into the metadata they describe.
     * They predate the harness id, so a key is applied to every bucket that
     * already holds that session; one held by no bucket lands in the bucket for
     * an unconfigured harness, which is where the old reader would have put it.
     */
    private static void foldLegacyRootKeys(Preferences root,
                                           Map<String, Map<String, SessionMetadata>> byHarness,
                                           int[] skipped, List<String> reasons) {
        try {
            for (String key : root.keys()) {
                String prefix = key.startsWith(LEGACY_TITLE_PREFIX) ? LEGACY_TITLE_PREFIX
                        : key.startsWith(LEGACY_HIDDEN_PREFIX) ? LEGACY_HIDDEN_PREFIX
                        : key.startsWith(LEGACY_USAGE_PREFIX) ? LEGACY_USAGE_PREFIX : null;
                if (prefix == null) {
                    continue;
                }
                String value = root.get(key, null);
                if (value == null) {
                    continue;
                }
                applyLegacyValue(byHarness, key.substring(prefix.length()), prefix, value);
            }
        } catch (BackingStoreException e) {
            skipped[0]++;
            addReason(reasons, "per-session keys: could not be listed: " + e.getMessage());
        }
    }

    private static void applyLegacyValue(Map<String, Map<String, SessionMetadata>> byHarness,
                                         String sessionId, String prefix, String value) {
        boolean matched = false;
        for (Map<String, SessionMetadata> sessions : byHarness.values()) {
            SessionMetadata current = sessions.get(sessionId);
            if (current != null) {
                matched = true;
                sessions.put(sessionId, withLegacyValue(current, prefix, value));
            }
        }
        if (!matched) {
            Map<String, SessionMetadata> fallback =
                    byHarness.computeIfAbsent(SessionStore.NO_HARNESS, k -> new LinkedHashMap<>());
            SessionMetadata current = fallback.get(sessionId);
            if (current == null) {
                current = new SessionMetadata(null, null, false, null);
            }
            fallback.put(sessionId, withLegacyValue(current, prefix, value));
        }
    }

    private static SessionMetadata withLegacyValue(SessionMetadata current, String prefix, String value) {
        if (LEGACY_TITLE_PREFIX.equals(prefix)) {
            return new SessionMetadata(value, current.usage(), current.hidden(), current.cwd());
        }
        if (LEGACY_USAGE_PREFIX.equals(prefix)) {
            return new SessionMetadata(current.title(), value, current.hidden(), current.cwd());
        }
        return new SessionMetadata(current.title(), current.usage(), Boolean.parseBoolean(value), current.cwd());
    }

    /**
     * Writes every legacy session, numbering rows so the newest ends up highest.
     *
     * @return the buckets in which at least one row failed to write, so their
     *         preference data is not deleted
     */
    private Set<String> writeMigratedMetadata(Map<String, Map<String, SessionMetadata>> byHarness,
                                              Map<String, Map<String, Long>> legacyIds,
                                              int[] migrated, int[] skipped, List<String> reasons) {
        Set<String> failedBuckets = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, SessionMetadata>> entry : byHarness.entrySet()) {
            String bucket = entry.getKey();
            long ordinal = 0;
            for (String sessionId : orderLegacySessions(entry.getValue(), legacyIds.get(bucket))) {
                ordinal++;
                SessionMetadata meta = entry.getValue().get(sessionId);
                try {
                    upsertSessionMeta(bucket, sessionId,
                            meta != null ? meta : new SessionMetadata(null, null, false, null), ordinal);
                    migrated[0]++;
                } catch (SQLException e) {
                    skipped[0]++;
                    // The row is not in the database, so its preference entry must
                    // survive: deleting it would destroy the only copy.
                    failedBuckets.add(bucket);
                    addReason(reasons, bucket + "/" + sessionId + ": " + e.getMessage());
                }
            }
        }
        return failedBuckets;
    }

    /**
     * Ordered oldest first, matching ascending ordinals. The dropped id lists
     * stored 0 for the newest session, so ordering by descending position puts
     * the oldest first and the newest on the highest ordinal. Sessions the list
     * never mentioned sort to the newest end.
     */
    private static List<String> orderLegacySessions(Map<String, SessionMetadata> sessions,
                                                    Map<String, Long> legacyIds) {
        List<String> ordered = new ArrayList<>(sessions.keySet());
        ordered.sort(Comparator.comparingLong((String id) -> {
            Long position = legacyIds != null ? legacyIds.get(id) : null;
            return position != null ? position : -1L;
        }).reversed());
        return ordered;
    }

    /**
     * Removes the migrated and superseded preference data. A bucket is kept, in
     * both node families, when anything in it was not written: the metadata node
     * still holds what could not be recovered, and the id node still holds the
     * ordering that metadata node needs for a later attempt.
     */
    private void deleteLegacyPreferences(Preferences root, List<String> metaNodes,
                                         List<String> idNodes, Set<String> bucketsToKeep,
                                         List<String> reasons) {
        for (String nodeName : idNodes) {
            if (!bucketsToKeep.contains(bucketFromNodeName(nodeName, LEGACY_IDS_NODE))) {
                removeNode(root, nodeName, reasons);
            }
        }
        for (String nodeName : metaNodes) {
            if (!bucketsToKeep.contains(bucketFromNodeName(nodeName, LEGACY_META_NODE))) {
                removeNode(root, nodeName, reasons);
            }
        }
        try {
            for (String key : root.keys()) {
                if (isLegacyRootKey(key)) {
                    root.remove(key);
                }
            }
            for (String key : OBSOLETE_KEYS) {
                root.remove(key);
            }
            root.flush();
        } catch (BackingStoreException e) {
            addReason(reasons, "preference keys: could not be cleaned up: " + e.getMessage());
        }
    }

    private static void removeNode(Preferences root, String nodeName, List<String> reasons) {
        try {
            root.node(nodeName).removeNode();
        } catch (BackingStoreException e) {
            addReason(reasons, nodeName + ": could not be removed: " + e.getMessage());
        }
    }

    /** Root keys superseded by the store. */
    private static boolean isLegacyRootKey(String key) {
        return key.startsWith(PreferenceKeys.INPUT_HISTORY_PREFIX)
                || key.equals(PreferenceKeys.INPUT_HISTORY_COUNT)
                || key.startsWith(LEGACY_TITLE_PREFIX)
                || key.startsWith(LEGACY_HIDDEN_PREFIX)
                || key.startsWith(LEGACY_USAGE_PREFIX)
                || key.equals(LEGACY_LOCAL_SESSIONS_KEY)
                || key.startsWith(LEGACY_LOCAL_SESSIONS_KEY + "_");
    }

    /** Retains at most {@link #MAX_REPORT_REASONS} reasons, so a mass failure cannot flood memory. */
    private static void addReason(List<String> reasons, String reason) {
        if (reasons.size() < MAX_REPORT_REASONS) {
            reasons.add(reason);
        }
    }

    @Override
    public UsageSummary query(int days, String projectDir, long now) {
        int window = Math.max(days, 1);
        return call(() -> runQuery(window, projectDir, now), UsageSummary.empty());
    }

    @Override
    public List<GroupTotals> queryGrouped(int days, String projectDir, long now, GroupBy groupBy) {
        int window = Math.max(days, 1);
        GroupBy key = groupBy != null ? groupBy : GroupBy.MODEL;
        String column = key == GroupBy.HARNESS ? "harness_id" : "model_id";
        return call(() -> runGrouped(window, projectDir, now, column), List.of());
    }

    @Override
    public List<ToolTotals> queryToolTotals(int days, String projectDir, long now) {
        int window = Math.max(days, 1);
        return call(() -> runToolTotals(window, projectDir, now), List.of());
    }

    /**
     * Per-tool totals over the window, most-called first: one row per tool_name,
     * the sum of each execution's call_count and how many of those did not end OK.
     */
    private List<ToolTotals> runToolTotals(int days, String projectDir, long now) throws SQLException {
        long cutoff = now - days * DAY_MILLIS;
        Sql.Statement statement = windowQuery("tool_totals", cutoff, projectDir);
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                List<ToolTotals> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new ToolTotals(rs.getString(1), rs.getLong(2), rs.getLong(3)));
                }
                return rows;
            }
        }
    }


    /**
     * Grouped aggregation across the three tables; the statement and its notes live in
     * {@code queries.sql} under {@code grouped_totals}.
     */
    private List<GroupTotals> runGrouped(int days, String projectDir, long now, String column)
            throws SQLException {
        long cutoff = now - days * DAY_MILLIS;
        Sql.Statement statement = Sql.render("grouped_totals", Map.of(
                "scope", scopeSlot(cutoff, projectDir),
                "column", Sql.Slot.of(column)));

        List<GroupTotals> rows = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = "(unknown)".equals(rs.getString(1)) ? null : rs.getString(1);
                    rows.add(new GroupTotals(key,
                            rs.getLong("sessions"), rs.getLong("messages"), rs.getLong("tool_calls"),
                            rs.getLong("input_tokens"), rs.getLong("output_tokens"),
                            rs.getLong("cached_read_tokens"), rs.getDouble("cost")));
                }
            }
        }
        return rows;
    }


    // ---- Query implementation (executor thread only) ----

    private UsageSummary runQuery(int days, String projectDir, long now) throws SQLException {
        long cutoff = now - days * DAY_MILLIS;

        long sessions = countScope("session_id", cutoff, projectDir);
        long messages = countMessages(cutoff, projectDir);
        long dayCount = countDays(cutoff, projectDir);
        double totalCost = sumCost(cutoff, projectDir);
        long[] tokens = sumTokens(cutoff, projectDir);
        double[] perSession = perSessionTokenSums(cutoff, projectDir);

        double avgTokens = perSession.length == 0 ? 0 : average(perSession);
        double medianTokens = perSession.length == 0 ? 0 : median(perSession);
        double avgCostPerDay = dayCount == 0 ? 0 : totalCost / dayCount;

        return new UsageSummary(sessions, messages, dayCount, totalCost, avgCostPerDay,
                avgTokens, medianTokens, tokens[0], tokens[1], tokens[2]);
    }

    private long countScope(String column, long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = Sql.render("count_scope_rows", Map.of(
                "scope", scopeSlot(cutoff, projectFilter),
                "column", Sql.Slot.of(column)));
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countMessages(long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = windowQuery("count_messages", cutoff, projectFilter);
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countDays(long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = Sql.render("count_days", Map.of(
                "scope", scopeSlot(cutoff, projectFilter),
                "dayExpr", Sql.Slot.of("FLOOR(captured_at / " + DAY_MILLIS + ".0)")));
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private double sumCost(long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = windowQuery("sum_cost", cutoff, projectFilter);
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    private long[] sumTokens(long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = windowQuery("sum_tokens", cutoff, projectFilter);
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)};
                }
                return new long[3];
            }
        }
    }

    /**
     * Per-session token sums for the Avg/Median Tokens/Session roll-up; the statement
     * and its notes live in {@code queries.sql} under {@code per_session_totals}.
     */
    private double[] perSessionTokenSums(long cutoff, String projectFilter) throws SQLException {
        List<Double> sums = new ArrayList<>();
        Sql.Statement statement = windowQuery("per_session_totals", cutoff, projectFilter);
        try (PreparedStatement ps = connection().prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sums.add(rs.getDouble(1));
                }
            }
        }
        double[] result = new double[sums.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = sums.get(i);
        }
        return result;
    }

    private static double average(double[] values) {
        double total = 0;
        for (double v : values) {
            total += v;
        }
        return total / values.length;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int mid = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[mid];
        }
        return (sorted[mid - 1] + sorted[mid]) / 2d;
    }

    // ---- Binding helpers ----

    /** The window predicate every aggregate shares; the project filter is optional. */
    private static String scopePredicate(String projectDir) {
        return projectDir != null ? "captured_at >= ? AND project = ?" : "captured_at >= ?";
    }

    /** A {@code {{scope}}} slot, with the parameters it binds at every occurrence. */
    private static Sql.Slot scopeSlot(long cutoff, String projectFilter) {
        return projectFilter != null
                ? Sql.Slot.of(scopePredicate(projectFilter), cutoff, projectFilter)
                : Sql.Slot.of(scopePredicate(null), cutoff);
    }

    /** Renders a statement whose only slot is its window predicate. */
    private static Sql.Statement windowQuery(String name, long cutoff, String projectFilter) {
        return Sql.render(name, Map.of("scope", scopeSlot(cutoff, projectFilter)));
    }

    /** Renders a statement whose only slot is a capture table name; binds stay with the caller. */
    private static String tableSql(String name, String table) {
        return Sql.sql(name, Map.of("table", Sql.Slot.of(table)));
    }

    /** Binds a rendered statement's parameters in order. */
    private static void bind(PreparedStatement ps, int start, Sql.Statement statement) throws SQLException {
        int index = start;
        for (Object param : statement.params()) {
            ps.setObject(index++, param);
        }
    }

    /**
     * Binds the four attribution columns. The model id is canonicalised here
     * rather than by each caller: every captured row from every table passes
     * through this method, so this is the only place a model id can enter the
     * database, and harnesses report the same model with different separators.
     */
    private static void bindAttribution(PreparedStatement ps, int start, Attribution attribution) throws SQLException {
        String sessionId = attribution != null ? attribution.sessionId() : null;
        ps.setString(start, sessionId);
        ps.setString(start + 1, attribution != null ? attribution.harnessId() : null);
        ps.setString(start + 2, ModelIdNormalizer.normalize(attribution != null ? attribution.modelId() : null));
        ps.setString(start + 3, attribution != null ? attribution.project() : null);
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    // ---- Connection lifecycle ----

    private Connection connection() throws SQLException {
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
    private void runDb(Runnable task) {
        dbExecutor.execute(() -> {
            if (!ensureDatabase()) {
                return;
            }
            task.run();
        });
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
    private boolean ensureDatabase() {
        if (unavailable) {
            return false;
        }
        try {
            connection();
            return true;
        } catch (SQLException e) {
            LOG.log(Level.WARNING,
                    "Usage-stats database unavailable ({0}); capture disabled until the next restart: {1}",
                    jdbcUrl, e.getMessage());
            LOG.log(Level.FINE, "Usage-stats database open failed", e);
            return false;
        }
    }


    /**
     * Upgrades older capture schemas. Version 1 rows contained replayed
     * duplicates and per-row gross costs whose semantics produced inflated
     * totals; those rows are unrecoverable, so they are dropped once.
     * Version 2 rows are valid but store cost_amount as DOUBLE; version 3
     * keeps the data and widens the column to NUMERIC(20,6) so cent-level
     * display no longer drifts with accumulating floating-point error.
     * Runs once per database on open.
     *
     * @param dbVersion the version read before any of this ran, so the caller can
     *                  also decide what to do after the tables exist
     */
    /**
     * Applies the data migrations a stored database needs, one branch per released
     * version; the statements themselves live in {@code queries.sql}.
     *
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
            LOG.log(Level.INFO, "Reset usage-stats store to schema version {0}", SCHEMA_VERSION);
        } else {
            LOG.log(Level.INFO, "Upgraded usage-stats store from schema version {0} to {1}",
                    new Object[]{dbVersion, SCHEMA_VERSION});
        }
        return dropped;
    }

    /** Reads the recorded schema version, or 0 for a legacy database. */
    private static int readSchemaVersion(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(Sql.get("select_meta_value"))) {
            ps.setString(1, SCHEMA_VERSION_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Integer.parseInt(rs.getString(1));
                }
            }
        } catch (SQLException | NumberFormatException e) {
            LOG.log(Level.FINE, "No usable schema version recorded; treating as legacy", e);
        }
        return 0;
    }

    /** Runs a database callable on the executor, returning {@code fallback} on failure. */
    private <T> T call(Callable<T> task, T fallback) {
        if (unavailable) {
            return fallback;
        }
        Future<T> future = dbExecutor.submit(() -> ensureDatabase() ? task.call() : fallback);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException e) {
            LOG.log(Level.WARNING, "Usage-stats query failed", e.getCause());
            return fallback;
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
    private static void closeWithRetry(Connection conn) {
        for (int attempt = 1; attempt <= CLOSE_ATTEMPTS; attempt++) {
            try {
                conn.close();
                return;
            } catch (SQLException e) {
                if (attempt == CLOSE_ATTEMPTS) {
                    LOG.log(Level.WARNING,
                            "Could not close the usage-stats database after " + CLOSE_ATTEMPTS + " attempts", e);
                    return;
                }
                LOG.log(Level.FINE, "Usage-stats database close failed; retrying", e);
                try {
                    Thread.sleep(CLOSE_RETRY_DELAY_MS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOG.log(Level.WARNING,
                            "Interrupted while closing the usage-stats database; it may stay locked", interrupted);
                    return;
                }
            }
        }
    }
}
