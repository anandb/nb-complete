package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.MessageClassification;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.Logger;

public final class ToolParamsExtractor {
    private static final Logger LOG = new Logger(ToolParamsExtractor.class);

    private ToolParamsExtractor() {
    }

    public static String extractToolCallId(JsonNode params) {
        if (params.has("toolCallId")) {
            return params.get("toolCallId").asText();
        } else if (params.has("tool_call_id")) {
            return params.get("tool_call_id").asText();
        } else if (params.has("toolCall")) {
            JsonNode tc = params.get("toolCall");
            if (tc.has("toolCallId")) {
                return tc.get("toolCallId").asText();
            } else if (tc.has("id")) {
                return tc.get("id").asText();
            }
        } else if (params.has("tool_call")) {
            JsonNode tc = params.get("tool_call");
            if (tc.has("id")) {
                return tc.get("id").asText();
            }
        }
        return null;
    }

    public static MessageClassification classify(MessageType type, String text, String kind) {
        if (isDcpCleanup(text)) {
            return new MessageClassification(MessageType.tool_call_update, "context cleanup");
        }
        if (isCavemanFiller(text)) {
            return new MessageClassification(MessageType.tool_call_update, "caveman");
        }

        return new MessageClassification(type, kind);
    }

    public static boolean isDcpCleanup(String text) {
        return text != null && (text.contains("▣ DCP") || text.contains("  DCP Sweep  "));
    }

    public static boolean isCavemanFiller(String text) {
        return text != null && (text.contains("Respond terse like smart caveman"));
    }
}
