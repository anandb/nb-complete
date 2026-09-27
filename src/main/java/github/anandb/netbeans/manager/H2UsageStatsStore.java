package github.anandb.netbeans.manager;

import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.Logger;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.openide.modules.Places;
import org.openide.util.lookup.ServiceProvider;

/**
 * Embedded H2 implementation of {@link UsageStatsStore}. One database lives
 * under the NetBeans user directory, so captured rows survive IDE restarts and
 * are readable with no harness process spawned.
 *
 * <p>Every database operation runs on a single-threaded daemon executor, so a
 * write from the event dispatch thread enqueues and returns without I/O. Because
 * the executor is FIFO, a query submitted after a write observes that write.</p>
 */
@ServiceProvider(service = UsageStatsStore.class)
public class H2UsageStatsStore implements UsageStatsStore {

    private static final Logger LOG = Logger.from(H2UsageStatsStore.class);

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

    private static final String SCHEMA_VERSION_KEY = "schema_version";

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
                st.execute("CREATE INDEX IF NOT EXISTS idx_usage_update_ts ON usage_update (captured_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_prompt_usage_ts ON prompt_usage (captured_at)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_message_event_ts ON message_event (captured_at)");
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
