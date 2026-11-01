package github.anandb.netbeans.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Level;

import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.Logger;

/**
 * Write side of the capture tables: the four {@code record*} entry points plus
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
                ps.setLong(6, row.used());
                UsageQueries.setNullableLong(ps, 7, row.size());
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
     */
    private static double costDelta(Connection conn, UsageUpdateRow row) throws SQLException {
        Attribution attribution = row.attribution();
        if (attribution == null || attribution.sessionId() == null) {
            return row.costAmount();
        }
        try (PreparedStatement ps = conn.prepareStatement(Sql.get("select_session_cost"))) {
            ps.setString(1, attribution.sessionId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row.costAmount() - rs.getDouble(1) : row.costAmount();
            }
        }
    }

    void recordPromptUsage(PromptUsageRow row) {
        if (row == null) {
            return;
        }
        db.runDb(() -> {
            try (PreparedStatement ps = db.connection().prepareStatement(Sql.get("insert_prompt_usage"))) {
                UsageQueries.bindAttribution(ps, 1, row.attribution());
                ps.setLong(5, row.capturedAt());
                ps.setLong(6, row.inputTokens());
                ps.setLong(7, row.outputTokens());
                ps.setLong(8, row.totalTokens());
                UsageQueries.setNullableLong(ps, 9, row.thoughtTokens());
                UsageQueries.setNullableLong(ps, 10, row.cachedReadTokens());
                ps.executeUpdate();
            } catch (SQLException e) {
                db.log().log(Level.WARNING, "Failed to record prompt usage row", e);
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
                ps.setLong(8, Math.max(row.durationMs(), 0));
                ps.setString(9, row.status() != null ? row.status().name().toLowerCase(Locale.ROOT) : "ok");
                ps.executeUpdate();
            } catch (SQLException e) {
                db.log().log(Level.WARNING, "Failed to record tool-call usage row", e);
            }
        });
    }
}
