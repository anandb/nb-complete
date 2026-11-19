package github.anandb.netbeans.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.logging.Level;

import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageUsageRow;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.Logger;

/**
 * Write side of the capture tables: the five {@code record*} entry points plus
 * the cost-delta read they depend on. Runs on the lifecycle's executor via the
 * supplied runner; SQL failures are logged, never thrown.
 */
final class UsageCapture {

    /** Minimal seam over the facade: a connection, the executor runner, and the log. */
    interface Db {
        Connection connection() throws SQLException;

        void runDb(Runnable task);

        Logger log();
    }

    private final Db db;

    UsageCapture(Db db) {
        this.db = db;
    }

    void recordUsageUpdate(UsageUpdateRow row) {
        if (row == null) {
            return;
        }
        db.runDb(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_usage_update"))) {
                UsageQueries.bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setLong(6, UsageQueries.nonNegative(row.used()));
                UsageQueries.setNullableLong(ps, 7, UsageQueries.nonNegative(row.size()));
                // Harnesses repeat the session's cumulative cost per notification
                // (pi-acp re-sends the running total several times per turn).
                // Storing the delta against the spend already recorded for the
                // session keeps SUM(cost_amount) equal to the true spend.
                ps.setDouble(8, costDelta(db.connection(), row));
                ps.setString(9, row.costCurrency() != null ? row.costCurrency() : "USD");
                ps.executeUpdate();
            } catch (SQLException e) {
                db.log().log(Level.WARNING, "Failed to record usage_update row", e);
            }
        });
    }

    /**
     * Difference between this row's cumulative cost and the spend already
     * recorded for the same session. The stored rows carry per-row deltas,
     * whose sum equals the session's cumulative spend even across restarts.
     * The first row of a session stores the full amount; a session with no
     * attribution stores the full amount too.
     *
     * <p>Neither the stored spend nor the result is allowed to be negative: a harness
     * that reports an amount that is negative, or lower than what it already reported
     * (Claude has done both), means no new spend, not a refund.</p>
     */
    private static double costDelta(Connection conn, UsageUpdateRow row) throws SQLException {
        Attribution attribution = row.attribution();
        if (attribution == null || attribution.sessionId() == null) {
            return Math.max(row.costAmount(), 0d);
        }
        String sessionId = attribution.sessionId();
        double stored = 0;
        try (PreparedStatement ps = conn.prepareStatement(Sql.get("select_session_cost"))) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stored = rs.getDouble(1);
                }
            }
        }
        return Math.max(row.costAmount() - Math.max(stored, 0d), 0d);
    }

    void recordPromptUsage(PromptUsageRow row) {
        if (row == null) {
            return;
        }
        db.runDb(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_prompt_usage"))) {
                UsageQueries.bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setLong(6, UsageQueries.nonNegative(row.inputTokens()));
                ps.setLong(7, UsageQueries.nonNegative(row.outputTokens()));
                ps.setLong(8, UsageQueries.nonNegative(row.totalTokens()));
                UsageQueries.setNullableLong(ps, 9, UsageQueries.nonNegative(row.thoughtTokens()));
                UsageQueries.setNullableLong(ps, 10, UsageQueries.nonNegative(row.cachedReadTokens()));
                ps.executeUpdate();
            } catch (SQLException e) {
                db.log().log(Level.WARNING, "Failed to record prompt usage row", e);
            }
        });
    }

    void recordMessageUsage(MessageUsageRow row) {
        if (row == null) {
            return;
        }
        db.runDb(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_message_usage"))) {
                UsageQueries.bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setString(6, row.messageId());
                ps.setLong(7, UsageQueries.nonNegative(row.inputTokens()));
                ps.setLong(8, UsageQueries.nonNegative(row.outputTokens()));
                ps.setLong(9, UsageQueries.nonNegative(row.totalTokens()));
                UsageQueries.setNullableLong(ps, 10, UsageQueries.nonNegative(row.cacheReadTokens()));
                UsageQueries.setNullableLong(ps, 11, UsageQueries.nonNegative(row.cacheWriteTokens()));
                UsageQueries.setNullableDouble(ps, 12, UsageQueries.nonNegative(row.cost()));
                UsageQueries.setNullableLong(ps, 13, UsageQueries.nonNegative(row.elapsedMs()));
                UsageQueries.setNullableLong(ps, 14, UsageQueries.nonNegative(row.timeToFirstTokenMs()));
                ps.setBoolean(15, row.compaction());
                ps.executeUpdate();
            } catch (SQLException e) {
                db.log().log(Level.WARNING, "Failed to record message_usage row", e);
            }
        });
    }

    void recordMessage(MessageEvent event) {
        if (event == null) {
            return;
        }
        db.runDb(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_message_event"))) {
                UsageQueries.bindAttribution(ps, 1, event.attribution());
                ps.setLong(5, event.capturedAt());
                ps.setString(6, event.kind() != null ? event.kind().name() : "ASSISTANT");
                ps.executeUpdate();
            } catch (SQLException e) {
                db.log().log(Level.WARNING, "Failed to record message event", e);
            }
        });
    }

    void recordToolCall(ToolCallExecution row) {
        if (row == null || row.toolName() == null || row.toolName().isBlank()) {
            return;
        }
        db.runDb(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_tool_call_usage"))) {
                UsageQueries.bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setString(6, row.toolName());
                // One row per execution; keeping the column at 1 leaves a SUM over
                // pre-version-8 turn aggregates correct.
                ps.setLong(7, 1);
                ps.setLong(8, UsageQueries.nonNegative(row.durationMs()));
                ps.setString(9, row.status() != null ? row.status().name().toLowerCase(Locale.ROOT) : "ok");
                ps.executeUpdate();
            } catch (SQLException e) {
                db.log().log(Level.WARNING, "Failed to record tool-call usage row", e);
            }
        });
    }

    /**
     * Deletes every captured row from the capture tables, so every aggregate reads zero.
     * One transaction: a store that reads between two deletes would otherwise see a
     * half-cleared history. A still-active session reports its cumulative cost again on
     * its next {@code usage_update}, which is intended: a reset is a fresh start, not a
     * baseline to measure against. Runs on the lifecycle's executor via the caller.
     */
    static void resetAll(DbLifecycle lifecycle) throws SQLException {
        Connection conn = lifecycle.connection();
        DbLifecycle.inTransaction(conn, () -> {
            try (Statement st = conn.createStatement()) {
                for (String table : DbLifecycle.captureTables()) {
                    st.executeUpdate(DbLifecycle.tableSql("delete_all_capture_rows", table));
                }
            }
        });
    }
}
