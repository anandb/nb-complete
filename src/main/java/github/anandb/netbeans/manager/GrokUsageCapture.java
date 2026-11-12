package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.support.UsageTurnDedup;
import org.openide.util.Lookup;

/**
 * Captures Grok Build's per-turn usage. Grok never sends a standard ACP
 * {@code usage_update}: usage arrives only in {@code _x.ai/session_notification}
 * {@code turn_completed} frames and in the {@code session/prompt} result
 * {@code _meta.usage}. Both describe the same turn, so {@link UsageTurnDedup}
 * keeps a turn from being recorded twice.
 *
 * <p>{@code response_completed} is per model call, not per turn; summing it with
 * {@code turn_completed} (the turn aggregate) would double-count, so only the
 * aggregate is recorded.</p>
 */
final class GrokUsageCapture {

    private GrokUsageCapture() {
    }

    /** Handles one {@code _x.ai/session_notification} params node. */
    static void onNotification(JsonNode params) {
        if (params == null) {
            return;
        }
        JsonNode update = params.get("update");
        if (update == null || !update.isObject()) {
            return;
        }
        if (!"turn_completed".equals(update.path("sessionUpdate").asText(""))) {
            return;
        }
        capture(params.path("sessionId").asText(""), update.get("usage"),
                update.path("prompt_id").asText(""));
    }

    /** Records one per-turn usage object, deduped by (session, turn). Absent usage writes nothing. */
    static void capture(String sessionId, JsonNode usage, String turnId) {
        if (usage == null || !usage.isObject() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (!UsageTurnDedup.firstCapture(sessionId, turnId)) {
            return;
        }
        UsageStatsStore store = Lookup.getDefault().lookup(UsageStatsStore.class);
        if (store == null) {
            return;
        }
        SessionQuery sessions = Lookup.getDefault().lookup(SessionQuery.class);
        Attribution attribution = new Attribution(sessionId,
                sessions != null ? sessions.getHarnessId() : null,
                sessions != null ? sessions.getSessionModelId(sessionId) : null,
                sessions != null ? sessions.getSessionDirectory(sessionId) : null);
        long input = usage.path("inputTokens").asLong(0);
        long output = usage.path("outputTokens").asLong(0);
        long total = usage.hasNonNull("totalTokens") ? usage.get("totalTokens").asLong() : input + output;
        store.recordPromptUsage(new PromptUsageRow(attribution, System.currentTimeMillis(),
                input, output, total, optLong(usage, "reasoningTokens"), optLong(usage, "cachedReadTokens")));
    }

    private static Long optLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }
}
