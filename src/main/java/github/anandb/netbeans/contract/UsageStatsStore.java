package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageUsageRow;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.ToolTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;

import java.util.List;

/**
 * Write port for ACP usage capture and read port for the aggregated stats panel.
 *
 * <p>Implementations must keep persistence off the calling thread: the write
 * methods enqueue and return, so an EDT caller is never blocked on I/O. The
 * query method may block on the database and is documented for background use.</p>
 */
public interface UsageStatsStore {

    /** Records a {@code usage_update} notification as one row. Never blocks on I/O. */
    void recordUsageUpdate(UsageUpdateRow row);

    /** Records the {@code usage} object of a {@code session/prompt} result as one row. Never blocks on I/O. */
    void recordPromptUsage(PromptUsageRow row);

    /**
     * Called from an explicit user action (opening or refreshing the token-stats
     * panel) to let a persistent store that failed to open at startup try once
     * more. Implementations that cannot fail do nothing, and an implementation
     * that already has its database or has spent its attempts must ignore it.
     * A successful retry finishes the startup work the failed open skipped.
     *
     * <p>May block on the database; background-thread use.</p>
     */
    default void retryAfterFailure() {
    }

    /**
     * Releases the database and stops its writer. Called when the module is
     * disabled, including an in-place plugin upgrade: the old module's
     * classloader must let go of the database file before the new one opens it.
     * Idempotent; a store that keeps nothing open may do nothing.
     */
    void close();

    /** Records one counted message update. Never blocks on I/O. */
    void recordMessage(MessageEvent event);

    /**
     * Records one goose {@code message_usage} notification as one row: the
     * per-message token counts, cache traffic, cost and timing. Never blocks on
     * I/O. A message without an id (compaction) is still stored.
     */
    default void recordMessageUsage(MessageUsageRow row) {
    }

    /**
     * Records one finished MCP tool execution as one row: tool name, end time,
     * duration and outcome, with the attribution resolved at capture time. Never
     * blocks on I/O.
     */
    void recordToolCall(ToolCallExecution row);

    /**
     * Aggregates the captured rows in scope.
     *
     * @param days       day window ending at {@code now}; must be at least 1
     * @param projectDir restrict to rows captured for this project directory,
     *                   or {@code null} for all projects
     * @param now        window end in epoch milliseconds
     * @return the aggregated figures, never null
     */
    UsageSummary query(int days, String projectDir, long now);

    /**
     * Aggregates the captured rows in scope, grouped by capture attribution —
     * the breakdown tables rendered after the summary totals in the
     * token-stats panel. Tool-call counts come from the registry's own
     * tool-call stream (one {@code MessageKind.TOOL} event per new toolCallId),
     * not from any harness-reported field.
     *
     * @param groupBy    dimension for the breakdown: per model or per agent
     *                   (harness); never null
     * @param days       day window ending at {@code now}; must be at least 1
     * @param projectDir restrict to rows captured for this project directory,
     *                   or {@code null} for all projects
     * @param now        window end in epoch milliseconds
     * @return one row per group key present in the window, never null. The
     *         key is null when attribution was unknown at capture time.
     *         May block briefly on the database; background-thread use.
     */
    List<GroupTotals> queryGrouped(int days, String projectDir, long now, GroupBy groupBy);

    /**
     * Per-tool MCP call totals over the window, most-called first, rendered as
     * the tool list and histogram in the token-stats panel. One row records one
     * finished execution, so {@code failures} counts the executions whose
     * outcome was not OK.
     *
     * @param days       day window ending at {@code now}; must be at least 1
     * @param projectDir restrict to rows captured for this project directory,
     *                   or {@code null} for all projects
     * @param now        window end in epoch milliseconds
     * @return one row per tool invoked in the window, never null. May block
     *         briefly on the database; background-thread use.
     */
    List<ToolTotals> queryToolTotals(int days, String projectDir, long now);

    /** Which attribution column a {@code queryGrouped} call groups on. */
    enum GroupBy {
        MODEL,
        HARNESS
    }
}
