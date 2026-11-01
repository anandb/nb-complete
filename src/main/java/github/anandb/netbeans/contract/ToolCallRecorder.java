package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.UsageRecords.ToolCallStatus;

/**
 * Capture port for MCP {@code tools/call} executions. {@code mcp/} reports every
 * finished execution here; the {@code manager/} implementation resolves the
 * attribution and hands the row to {@link UsageStatsStore}.
 *
 * <p>An implementation must never block the caller and must never throw: a tool
 * results in the harness are more important than a captured row.</p>
 */
public interface ToolCallRecorder {

    /**
     * Records one finished tool execution.
     *
     * @param toolName      the requested tool name, ignored when null or blank
     * @param capturedAt    epoch milliseconds when the execution ended
     * @param durationMs    wall-clock duration of the execution; negative values
     *                      are stored as zero
     * @param status        how the execution ended; null is ignored
     */
    void record(String toolName, long capturedAt, long durationMs, ToolCallStatus status);
}
