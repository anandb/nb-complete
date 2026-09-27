package github.anandb.netbeans.model;

/**
 * Data-only rows captured from ACP usage payloads plus the aggregate the
 * token-stats panel renders. Leaf records; no imports beyond the JDK.
 *
 * <p>Two wire shapes feed the store: a {@code usage_update} notification
 * (context gauge plus reported cost) and the {@code usage} object of a
 * {@code session/prompt} result (per-turn tokens). A third row kind records
 * the message counts of CAP-6.</p>
 */
public final class UsageRecords {

    private UsageRecords() {
    }

    /** Which kind of session update a captured message event came from. */
    public enum MessageKind {
        USER,
        ASSISTANT,
        TOOL,
        THOUGHT
    }

    /**
     * Session, harness, model and project a row is attributed to. Harness and
     * model are resolved for the session the message belongs to at capture
     * time, never from current UI state at query time.
     */
    public record Attribution(String sessionId, String harnessId, String modelId, String project) {
    }

    /**
     * One {@code usage_update} notification. {@code used} is a gauge sample of
     * context occupancy and is never treated as tokens consumed. {@code size}
     * may be null (or zero) because context capacity varies per session.
     */
    public record UsageUpdateRow(Attribution attribution, long capturedAt, long used, Long size,
                                 double costAmount, String costCurrency) {
    }

    /**
     * One {@code session/prompt} result {@code usage} object. {@code thoughtTokens}
     * and {@code cachedReadTokens} are optional and stay null when the harness
     * omitted them; the report renders that absence as zero.
     */
    public record PromptUsageRow(Attribution attribution, long capturedAt, long inputTokens,
                                 long outputTokens, long totalTokens, Long thoughtTokens,
                                 Long cachedReadTokens) {
    }

    /** One counted user, assistant, tool or thought update (CAP-6). */
    public record MessageEvent(Attribution attribution, long capturedAt, MessageKind kind) {
    }

    /**
     * Aggregated figures for the OVERVIEW and COST &amp; TOKENS blocks.
     * Token columns are summed per field; {@code totalTokens} is never
     * substituted into them.
     */
    public record UsageSummary(long sessions, long messages, long days,
                               double totalCost, double avgCostPerDay,
                               double avgTokensPerSession, double medianTokensPerSession,
                               long inputTokens, long outputTokens, long cachedReadTokens) {

        /** The all-zero summary used when no store is available. */
        public static UsageSummary empty() {
            return new UsageSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * One grouped row of the breakdown tables (grouped by model or by agent),
     * rendered after the summary totals. One per group key; the key is null
     * for rows whose attribution was unknown at capture time.
     */
    public record GroupTotals(String groupKey, long sessions, long messages, long toolCalls,
                              long inputTokens, long outputTokens, long cachedReadTokens,
                              double cost) {
    }
}
