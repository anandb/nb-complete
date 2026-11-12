package github.anandb.netbeans.support;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;

/**
 * Builds the prompt-usage row shared by the {@code session/prompt} result and Grok's
 * {@code _x.ai/session_notification} frames. Token fields that are absent stay null;
 * {@code totalTokens} falls back to input + output.
 */
public final class PromptUsageRows {

    private PromptUsageRows() {
    }

    public static PromptUsageRow from(Attribution attribution, JsonNode usage) {
        long input = usage.path("inputTokens").asLong(0);
        long output = usage.path("outputTokens").asLong(0);
        long total = usage.hasNonNull("totalTokens") ? usage.get("totalTokens").asLong() : input + output;
        Long thought = number(usage, "thoughtTokens");
        if (thought == null) {
            thought = number(usage, "reasoningTokens");
        }
        return new PromptUsageRow(attribution, System.currentTimeMillis(),
                input, output, total, thought, number(usage, "cachedReadTokens"));
    }

    private static Long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }
}
