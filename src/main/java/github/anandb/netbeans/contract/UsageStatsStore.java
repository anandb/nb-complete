package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;

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

    /** Records one counted message update. Never blocks on I/O. */
    void recordMessage(MessageEvent event);

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
}
