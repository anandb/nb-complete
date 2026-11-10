package github.anandb.netbeans.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.ToolTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.support.ModelIdNormalizer;

/**
 * Read-side aggregates over the capture tables. Stateless: every method takes
 * the connection (always the lifecycle's, on its executor) as a parameter, so
 * this class owns no fields and needs no synchronisation. The facade keeps the
 * {@code call()} entry points and delegates here.
 */
final class UsageQueries {

    /** Days are stored as epoch millis; the window predicate compares against this. */
    private static final long DAY_MILLIS = 86_400_000L;

    private UsageQueries() {}

    static UsageSummary query(Connection conn, int days, String projectDir, long now) throws SQLException {
        int window = Math.max(days, 1);
        return runQuery(conn, window, projectDir, now);
    }

    static List<GroupTotals> queryGrouped(Connection conn, int days, String projectDir, long now, String column)
            throws SQLException {
        int window = Math.max(days, 1);
        return runGrouped(conn, window, projectDir, now, column);
    }

    static List<ToolTotals> queryToolTotals(Connection conn, int days, String projectDir, long now) throws SQLException {
        int window = Math.max(days, 1);
        return runToolTotals(conn, window, projectDir, now);
    }

    /**
     * Per-tool totals over the window, most-called first: one row per tool_name,
     * the sum of each execution's call_count and how many of those did not end OK.
     */
    private static List<ToolTotals> runToolTotals(Connection conn, int days, String projectDir, long now) throws SQLException {
        long cutoff = now - days * DAY_MILLIS;
        Sql.Statement statement = windowQuery("tool_totals", cutoff, projectDir);
        try (PreparedStatement ps = conn.prepareStatement(statement.sql())) {
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
    private static List<GroupTotals> runGrouped(Connection conn, int days, String projectDir, long now, String column)
            throws SQLException {
        long cutoff = now - days * DAY_MILLIS;
        Sql.Statement statement = Sql.render("grouped_totals", Map.of(
                "scope", scopeSlot(cutoff, projectDir),
                "column", Sql.Slot.of(column)));

        List<GroupTotals> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(statement.sql())) {
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

    private static UsageSummary runQuery(Connection conn, int days, String projectDir, long now) throws SQLException {
        long cutoff = now - days * DAY_MILLIS;

        long sessions = countScope(conn, "session_id", cutoff, projectDir);
        long messages = countMessages(conn, cutoff, projectDir);
        long dayCount = countDays(conn, cutoff, projectDir);
        double totalCost = sumCost(conn, cutoff, projectDir);
        long[] tokens = sumTokens(conn, cutoff, projectDir);
        double[] perSession = perSessionTokenSums(conn, cutoff, projectDir);

        double avgTokens = perSession.length == 0 ? 0 : average(perSession);
        double medianTokens = perSession.length == 0 ? 0 : median(perSession);
        double avgCostPerDay = dayCount == 0 ? 0 : totalCost / dayCount;

        return new UsageSummary(sessions, messages, dayCount, totalCost, avgCostPerDay,
                avgTokens, medianTokens, tokens[0], tokens[1], tokens[2]);
    }

    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rs) throws SQLException;
    }

    /** Runs a rendered statement, binding its slots, and reads the first row. */
    private static <T> T queryOne(Connection conn, Sql.Statement statement,
            RowReader<T> reader, T fallback) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(statement.sql())) {
            bind(ps, 1, statement);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? reader.read(rs) : fallback;
            }
        }
    }

    private static long countScope(Connection conn, String column, long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = Sql.render("count_scope_rows", Map.of(
                "scope", scopeSlot(cutoff, projectFilter),
                "column", Sql.Slot.of(column)));
        return queryOne(conn, statement, rs -> rs.getLong(1), 0L);
    }

    private static long countMessages(Connection conn, long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = windowQuery("count_messages", cutoff, projectFilter);
        return queryOne(conn, statement, rs -> rs.getLong(1), 0L);
    }

    private static long countDays(Connection conn, long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = Sql.render("count_days", Map.of(
                "scope", scopeSlot(cutoff, projectFilter),
                "dayExpr", Sql.Slot.of("FLOOR(captured_at / " + DAY_MILLIS + ".0)")));
        return queryOne(conn, statement, rs -> rs.getLong(1), 0L);
    }

    private static double sumCost(Connection conn, long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = windowQuery("sum_cost", cutoff, projectFilter);
        return queryOne(conn, statement, rs -> rs.getDouble(1), 0d);
    }

    private static long[] sumTokens(Connection conn, long cutoff, String projectFilter) throws SQLException {
        Sql.Statement statement = windowQuery("sum_tokens", cutoff, projectFilter);
        return queryOne(conn, statement,
                rs -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)}, new long[3]);
    }

    /**
     * Per-session token sums for the Avg/Median Tokens/Session roll-up; the statement
     * and its notes live in {@code queries.sql} under {@code per_session_totals}.
     */
    private static double[] perSessionTokenSums(Connection conn, long cutoff, String projectFilter) throws SQLException {
        List<Double> sums = new ArrayList<>();
        Sql.Statement statement = windowQuery("per_session_totals", cutoff, projectFilter);
        try (PreparedStatement ps = conn.prepareStatement(statement.sql())) {
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

    /** Binds a rendered statement's parameters in order. */
    static void bind(PreparedStatement ps, int start, Sql.Statement statement) throws SQLException {
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
    static void bindAttribution(PreparedStatement ps, int start, Attribution attribution) throws SQLException {
        String sessionId = attribution != null ? attribution.sessionId() : null;
        ps.setString(start, sessionId);
        ps.setString(start + 1, attribution != null ? attribution.harnessId() : null);
        ps.setString(start + 2, ModelIdNormalizer.normalize(attribution != null ? attribution.modelId() : null));
        ps.setString(start + 3, attribution != null ? attribution.project() : null);
    }

    static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.NUMERIC);
        } else {
            ps.setDouble(index, value);
        }
    }
}
