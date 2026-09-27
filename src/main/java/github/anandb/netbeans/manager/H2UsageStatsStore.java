package github.anandb.netbeans.manager;

import github.anandb.netbeans.contract.SessionStore;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.MigrationReport;
import github.anandb.netbeans.model.SessionMetadata;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PreferenceKeys;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.openide.modules.Places;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 * Embedded H2 implementation of {@link UsageStatsStore} and {@link SessionStore}.
 * One database lives under the NetBeans user directory, so captured rows survive
 * IDE restarts and are readable with no harness process spawned. It is also the
 * durable home of the input history and the per-session metadata that used to
 * live in {@code NbPreferences}.
 *
 * <p>Every database operation runs on a single-threaded daemon executor, so a
 * write from the event dispatch thread enqueues and returns without I/O. Because
 * the executor is FIFO, a query submitted after a write observes that write.</p>
 */
@ServiceProviders({
    @ServiceProvider(service = UsageStatsStore.class),
    @ServiceProvider(service = SessionStore.class)
})
public class H2UsageStatsStore implements UsageStatsStore, SessionStore {

    private static final Logger LOG = Logger.from(H2UsageStatsStore.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private static final String CREATE_USAGE_UPDATE = "CREATE TABLE IF NOT EXISTS usage_update ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "session_id VARCHAR(512), harness_id VARCHAR(128), model_id VARCHAR(512), project VARCHAR(2048),"
            + "captured_at BIGINT NOT NULL, used_tokens BIGINT NOT NULL, size_tokens BIGINT,"
            + "cost_amount NUMERIC(20,6) NOT NULL, cost_currency VARCHAR(16))";

    private static final String CREATE_PROMPT_USAGE = "CREATE TABLE IF NOT EXISTS prompt_usage ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "session_id VARCHAR(512), harness_id VARCHAR(128), model_id VARCHAR(512), project VARCHAR(2048),"
            + "captured_at BIGINT NOT NULL, input_tokens BIGINT NOT NULL, output_tokens BIGINT NOT NULL,"
            + "total_tokens BIGINT NOT NULL, thought_tokens BIGINT, cached_read_tokens BIGINT)";

    private static final String CREATE_MESSAGE_EVENT = "CREATE TABLE IF NOT EXISTS message_event ("
            + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + "session_id VARCHAR(512), harness_id VARCHAR(128), model_id VARCHAR(512), project VARCHAR(2048),"
            + "captured_at BIGINT NOT NULL, kind VARCHAR(16) NOT NULL)";

    private static final String INSERT_USAGE_UPDATE = "INSERT INTO usage_update "
            + "(session_id, harness_id, model_id, project, captured_at, used_tokens, size_tokens,"
            + " cost_amount, cost_currency) VALUES (?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_PROMPT_USAGE = "INSERT INTO prompt_usage "
            + "(session_id, harness_id, model_id, project, captured_at, input_tokens, output_tokens,"
            + " total_tokens, thought_tokens, cached_read_tokens) VALUES (?,?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_MESSAGE_EVENT = "INSERT INTO message_event "
            + "(session_id, harness_id, model_id, project, captured_at, kind) VALUES (?,?,?,?,?,?)";

    private static final String CREATE_META = "CREATE TABLE IF NOT EXISTS usage_meta ("
            + "meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(64))";

    /** Chat input history. {@code seq} is append order, so the newest has the highest. */
    private static final String CREATE_INPUT_HISTORY = "CREATE TABLE IF NOT EXISTS input_history ("
            + "seq BIGINT AUTO_INCREMENT PRIMARY KEY, text CLOB NOT NULL)";

    /**
     * Per-session metadata, replacing the {@code sessmeta_<harness>} nodes and the
     * {@code sessids_<harness>} id lists. {@code ordinal} carries the session
     * ordering those lists encoded: within a harness the newest session has the
     * highest ordinal, which is what makes {@link #sessionMetadata} newest-first.
     */
    private static final String CREATE_SESSION_META = "CREATE TABLE IF NOT EXISTS session_meta ("
            + "harness_id VARCHAR(128) NOT NULL, session_id VARCHAR(512) NOT NULL,"
            + " title CLOB, hidden BOOLEAN NOT NULL DEFAULT FALSE, cwd VARCHAR(2048),"
            + " used_tokens BIGINT, size_tokens BIGINT, ordinal BIGINT NOT NULL,"
            + " PRIMARY KEY (harness_id, session_id))";

    private static final String SCHEMA_VERSION_KEY = "schema_version";

    /** Set once the legacy {@code NbPreferences} data has been copied in. */
    private static final String PREFS_MIGRATED_KEY = "prefs_migrated";

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
     */
    private static final int SCHEMA_VERSION = 3;

    private static final long DAY_MILLIS = 86_400_000L;

    private final String jdbcUrl;
    private final Object initLock = new Object();
    private final ExecutorService dbExecutor;
    private volatile Connection connection;

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
        File dir = new File(Places.getUserDirectory(), "beanbot");
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
        dbExecutor.execute(() -> {
            try (PreparedStatement ps = connection().prepareStatement(INSERT_USAGE_UPDATE)) {
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
            try (Statement st = conn.createStatement()) {
                st.execute(CREATE_META);
            }
            applySchemaMigration(conn);
            try (Statement st = conn.createStatement()) {
                st.execute(CREATE_USAGE_UPDATE);
                st.execute(CREATE_PROMPT_USAGE);
                st.execute(CREATE_MESSAGE_EVENT);
                st.execute(CREATE_INPUT_HISTORY);
                st.execute(CREATE_SESSION_META);
                st.execute("CREATE INDEX IF NOT EXISTS idx_usage_update_ts ON usage_update (captured_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_prompt_usage_ts ON prompt_usage (captured_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_message_event_ts ON message_event (captured_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_session_meta_harness ON session_meta (harness_id)");
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
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT COALESCE(SUM(cost_amount), 0) FROM usage_update WHERE session_id = ?")) {
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
        dbExecutor.execute(() -> {
            try (PreparedStatement ps = connection().prepareStatement(INSERT_PROMPT_USAGE)) {
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
        dbExecutor.execute(() -> {
            try (PreparedStatement ps = connection().prepareStatement(INSERT_MESSAGE_EVENT)) {
                bindAttribution(ps, 1, event.attribution());
                ps.setLong(5, event.capturedAt());
                ps.setString(6, event.kind() != null ? event.kind().name() : "ASSISTANT");
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Failed to record message event", e);
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
        dbExecutor.execute(() -> {
            try (PreparedStatement ps = connection().prepareStatement(
                    "INSERT INTO input_history (text) VALUES (?)")) {
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
        dbExecutor.execute(() -> {
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
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT title, hidden, cwd, used_tokens, size_tokens FROM session_meta"
                + " WHERE harness_id = ? AND session_id = ?")) {
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

    // ---- SessionStore database ----

    private void loadHistory() throws SQLException {
        List<String> loaded = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT text FROM input_history ORDER BY seq");
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
        String sql = "SELECT harness_id, session_id, title, hidden, cwd, used_tokens, size_tokens"
                + " FROM session_meta ORDER BY harness_id, ordinal";
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
        Long[] usage = parseUsage(meta.usage());
        try (PreparedStatement ps = connection().prepareStatement(
                "MERGE INTO session_meta (harness_id, session_id, title, hidden, cwd,"
                + " used_tokens, size_tokens, ordinal) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, bucket);
            ps.setString(2, sessionId);
            ps.setString(3, meta.title());
            ps.setBoolean(4, meta.hidden());
            ps.setString(5, meta.cwd());
            setNullableLong(ps, 6, usage[0]);
            setNullableLong(ps, 7, usage[1]);
            ps.setLong(8, ordinal);
            ps.executeUpdate();
        }
    }

    /** The stored ordinal for a session, or null when it has no row yet. */
    private Long existingOrdinal(String bucket, String sessionId) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT ordinal FROM session_meta WHERE harness_id = ? AND session_id = ?")) {
            ps.setString(1, bucket);
            ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Next free ordinal in a bucket; the newest session carries the highest. */
    private long nextOrdinal(String bucket) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT COALESCE(MAX(ordinal), 0) + 1 FROM session_meta WHERE harness_id = ?")) {
            ps.setString(1, bucket);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1L;
            }
        }
    }

    /** Drops all but the newest {@link #HISTORY_CAP} history rows. */
    private void pruneHistory() throws SQLException {
        Long oldestKept = null;
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT seq FROM input_history ORDER BY seq DESC LIMIT 1 OFFSET ?")) {
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
        try (PreparedStatement ps = connection().prepareStatement(
                "DELETE FROM input_history WHERE seq < ?")) {
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
        if (isPrefsMigrated()) {
            return MigrationReport.none();
        }
        List<String> reasons = new ArrayList<>();
        int[] migrated = {0};
        int[] skipped = {0};
        Set<String> bucketsToKeep = new LinkedHashSet<>();
        Preferences root = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);

        migrateHistory(root, migrated, skipped, reasons);
        List<String> metaNodes = childNodeNames(root, LEGACY_META_NODE, reasons);
        List<String> idNodes = childNodeNames(root, LEGACY_IDS_NODE, reasons);
        Map<String, Map<String, SessionMetadata>> byHarness =
                readLegacyMetadata(root, metaNodes, skipped, reasons, bucketsToKeep);
        Map<String, Map<String, Long>> legacyIds = readLegacyIds(root, idNodes, reasons);
        foldLegacyRootKeys(root, byHarness, skipped, reasons);
        bucketsToKeep.addAll(
                writeMigratedMetadata(byHarness, legacyIds, migrated, skipped, reasons));

        boolean recorded = markPrefsMigrated();
        if (recorded) {
            deleteLegacyPreferences(root, metaNodes, idNodes, bucketsToKeep, reasons);
        } else {
            LOG.log(Level.WARNING,
                    "Preference migration could not record its marker; leaving preferences untouched");
        }

        MigrationReport report = new MigrationReport(migrated[0], skipped[0], reasons);
        if (report.hasSkips()) {
            LOG.log(Level.WARNING, "Preference migration moved {0} entries and skipped {1}",
                    new Object[]{report.migrated(), report.skipped()});
        } else {
            LOG.log(Level.INFO, "Preference migration moved {0} entries into the database",
                    report.migrated());
        }
        return report;
    }

    private boolean isPrefsMigrated() {
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT meta_value FROM usage_meta WHERE meta_key = ?")) {
            ps.setString(1, PREFS_MIGRATED_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "true".equalsIgnoreCase(rs.getString(1));
            }
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Could not read the migration marker; treating as not migrated", e);
            return false;
        }
    }

    /** @return true when the database accepted the marker, so the sources may be deleted. */
    private boolean markPrefsMigrated() {
        try (PreparedStatement ps = connection().prepareStatement(
                "MERGE INTO usage_meta (meta_key, meta_value) VALUES (?, ?)")) {
            ps.setString(1, PREFS_MIGRATED_KEY);
            ps.setString(2, "true");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Could not record the preference migration marker", e);
            return false;
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
            try (PreparedStatement ps = connection().prepareStatement(
                    "INSERT INTO input_history (text) VALUES (?)")) {
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
        try (PreparedStatement ps = connection().prepareStatement(
                "SELECT COUNT(*) FROM input_history");
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


    /**
     * Grouped aggregation across the three tables, keyed to the distinct set
     * of group values seen in the window. Each table contributes only the
     * figures it owns (prompt_usage: token columns; usage_update: cost
     * deltas; message_event: messages, tool calls and the session count).
     * The group key source is a single UNION of the three attribution
     * columns, so a group key appears once even when several tables carry it.
     */
    private List<GroupTotals> runGrouped(int days, String projectDir, long now, String column)
            throws SQLException {
        long cutoff = now - days * DAY_MILLIS;
        String scope = "captured_at >= ?" + (projectDir != null ? " AND project = ?" : "");
        String groupExpr = "COALESCE(" + column + ", '(unknown)')";

        // One COALESCE-populated group set, then three LEFT JOINed aggregates.
        // A single source of group keys avoids the double session counting a
        // UNION of per-branch COUNT(DISTINCT session_id) produces (the same
        // session appears in two branches when both recorded rows).
        String groupSet =
                "SELECT DISTINCT COALESCE(ids, '(unknown)') AS grp FROM ("
                + "SELECT " + column + " AS ids FROM prompt_usage WHERE " + scope
                + " UNION SELECT " + column + " FROM usage_update WHERE " + scope
                + " UNION SELECT " + column + " FROM message_event WHERE " + scope + ")";
        String tokensByGroup =
                "SELECT COALESCE(" + column + ", '(unknown)') AS grp,"
                + " COALESCE(SUM(input_tokens), 0) AS input_tokens,"
                + " COALESCE(SUM(output_tokens), 0) AS output_tokens,"
                + " COALESCE(SUM(cached_read_tokens), 0) AS cached_read_tokens"
                + " FROM prompt_usage WHERE " + scope + " GROUP BY " + groupExpr + "";
        String costByGroup =
                "SELECT COALESCE(" + column + ", '(unknown)') AS grp,"
                + " COALESCE(SUM(cost_amount), 0.0) AS cost"
                + " FROM usage_update WHERE " + scope + " GROUP BY " + groupExpr + "";
        String activityByGroup =
                "SELECT COALESCE(" + column + ", '(unknown)') AS grp,"
                + " COUNT(DISTINCT CASE WHEN kind IN ('USER', 'ASSISTANT', 'THOUGHT') THEN session_id END) AS sessions,"
                + " SUM(CASE WHEN kind <> 'TOOL' THEN 1 ELSE 0 END) AS messages,"
                + " SUM(CASE WHEN kind = 'TOOL' THEN 1 ELSE 0 END) AS tool_calls"
                + " FROM message_event WHERE " + scope + " GROUP BY " + groupExpr + "";

        String sql = "SELECT g.grp,"
                + " COALESCE(a.sessions, 0) AS sessions,"
                + " COALESCE(a.messages, 0) AS messages,"
                + " COALESCE(a.tool_calls, 0) AS tool_calls,"
                + " COALESCE(t.input_tokens, 0) AS input_tokens,"
                + " COALESCE(t.output_tokens, 0) AS output_tokens,"
                + " COALESCE(t.cached_read_tokens, 0) AS cached_read_tokens,"
                + " COALESCE(c.cost, 0.0) AS cost"
                + " FROM (" + groupSet + ") g"
                + " LEFT JOIN (" + tokensByGroup + ") t ON t.grp = g.grp"
                + " LEFT JOIN (" + costByGroup + ") c ON c.grp = g.grp"
                + " LEFT JOIN (" + activityByGroup + ") a ON a.grp = g.grp"
                + " ORDER BY g.grp";

        List<GroupTotals> rows = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bindScopeParams(ps, 1, cutoff, projectDir, 6);
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


    /** Binds a scope predicate's parameters starting at {@code start}; returns the next free index. */
    private static int bindScopeParams(PreparedStatement ps, int start, long cutoff,
                                       String projectFilter, int repetitions) throws SQLException {
        int index = start;
        for (int i = 0; i < repetitions; i++) {
            ps.setLong(index++, cutoff);
            if (projectFilter != null) {
                ps.setString(index++, projectFilter);
            }
        }
        return index;
    }
    // ---- Query implementation (executor thread only) ----

    private UsageSummary runQuery(int days, String projectDir, long now) throws SQLException {
        long cutoff = now - days * DAY_MILLIS;
        String scope = "captured_at >= ?" + (projectDir != null ? " AND project = ?" : "");
        String projectFilter = projectDir;

        long sessions = countScope("session_id", scope, cutoff, projectFilter);
        long messages = countMessages(scope, cutoff, projectFilter);
        long dayCount = countDays(scope, cutoff, projectFilter);
        double totalCost = sumCost(scope, cutoff, projectFilter);
        long[] tokens = sumTokens(scope, cutoff, projectFilter);
        double[] perSession = perSessionTokenSums(scope, cutoff, projectFilter);

        double avgTokens = perSession.length == 0 ? 0 : average(perSession);
        double medianTokens = perSession.length == 0 ? 0 : median(perSession);
        double avgCostPerDay = dayCount == 0 ? 0 : totalCost / dayCount;

        return new UsageSummary(sessions, messages, dayCount, totalCost, avgCostPerDay,
                avgTokens, medianTokens, tokens[0], tokens[1], tokens[2]);
    }

    private long countScope(String column, String scope, long cutoff, String projectFilter) throws SQLException {
        String sql = "SELECT COUNT(*) FROM ("
                + "SELECT " + column + " FROM usage_update WHERE " + scope
                + " UNION SELECT " + column + " FROM prompt_usage WHERE " + scope
                + " UNION SELECT " + column + " FROM message_event WHERE " + scope + ") scope_rows";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bindScope(ps, 1, cutoff, projectFilter, 3);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countMessages(String scope, long cutoff, String projectFilter) throws SQLException {
        String sql = "SELECT COUNT(*) FROM message_event WHERE " + scope;
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bindScope(ps, 1, cutoff, projectFilter, 1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countDays(String scope, long cutoff, String projectFilter) throws SQLException {
        String dayExpr = "FLOOR(captured_at / " + DAY_MILLIS + ".0)";
        String sql = "SELECT COUNT(*) FROM ("
                + "SELECT DISTINCT " + dayExpr + " FROM usage_update WHERE " + scope
                + " UNION SELECT DISTINCT " + dayExpr + " FROM prompt_usage WHERE " + scope
                + " UNION SELECT DISTINCT " + dayExpr + " FROM message_event WHERE " + scope + ") day_rows";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bindScope(ps, 1, cutoff, projectFilter, 3);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private double sumCost(String scope, long cutoff, String projectFilter) throws SQLException {
        String sql = "SELECT COALESCE(SUM(cost_amount), 0) FROM usage_update WHERE " + scope;
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bindScope(ps, 1, cutoff, projectFilter, 1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    private long[] sumTokens(String scope, long cutoff, String projectFilter) throws SQLException {
        String sql = "SELECT COALESCE(SUM(input_tokens), 0), COALESCE(SUM(output_tokens), 0),"
                + " COALESCE(SUM(cached_read_tokens), 0) FROM prompt_usage WHERE " + scope;
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bindScope(ps, 1, cutoff, projectFilter, 1);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)};
                }
                return new long[3];
            }
        }
    }

    /**
     * Per-session token sums for the Avg/Median Tokens/Session roll-up. The four
     * stored token fields are summed here and only here; the reported Input,
     * Output and Cache Read columns each stay a single-field sum.
     */
    private double[] perSessionTokenSums(String scope, long cutoff, String projectFilter) throws SQLException {
        String sql = "SELECT SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)"
                + " + COALESCE(thought_tokens, 0) + COALESCE(cached_read_tokens, 0))"
                + " FROM prompt_usage WHERE " + scope + " GROUP BY session_id";
        List<Double> sums = new ArrayList<>();
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bindScope(ps, 1, cutoff, projectFilter, 1);
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

    private static void bindScope(PreparedStatement ps, int start, long cutoff,
                                  String projectFilter, int repetitions) throws SQLException {
        int index = start;
        for (int i = 0; i < repetitions; i++) {
            ps.setLong(index++, cutoff);
            if (projectFilter != null) {
                ps.setString(index++, projectFilter);
            }
        }
    }

    private static void bindAttribution(PreparedStatement ps, int start, Attribution attribution) throws SQLException {
        String sessionId = attribution != null ? attribution.sessionId() : null;
        ps.setString(start, sessionId);
        ps.setString(start + 1, attribution != null ? attribution.harnessId() : null);
        ps.setString(start + 2, attribution != null ? attribution.modelId() : null);
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
            if (connection == null) {
                connection = open();
            }
            return connection;
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
     */
    private void applySchemaMigration(Connection conn) throws SQLException {
        int dbVersion = readSchemaVersion(conn);
        if (dbVersion >= SCHEMA_VERSION) {
            return;
        }
        if (dbVersion < 2) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS usage_update");
                st.execute("DROP TABLE IF EXISTS prompt_usage");
                st.execute("DROP TABLE IF EXISTS message_event");
            }
        }
        if (dbVersion == 2) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE usage_update ALTER COLUMN cost_amount NUMERIC(20,6)");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "MERGE INTO usage_meta (meta_key, meta_value) VALUES (?, ?)")) {
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
    }

    /** Reads the recorded schema version, or 0 for a legacy database. */
    private static int readSchemaVersion(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT meta_value FROM usage_meta WHERE meta_key = ?")) {
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
        Future<T> future = dbExecutor.submit(task);
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
    void shutdown() {
        if (dbExecutor.isShutdown()) {
            return;
        }
        call(() -> {
            Connection local = connection;
            if (local != null) {
                local.close();
            }
            return null;
        }, null);
        dbExecutor.shutdown();
    }
}
