package github.anandb.netbeans.manager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.MessageClassification;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;

public final class ToolParamsExtractor {
    private static final Logger LOG = new Logger(ToolParamsExtractor.class);
    private static final Pattern[] TOOL_CONTENT_PATTERNS = {
        Pattern.compile("<skill_content name=\"([^\"]{2,})\""),
        Pattern.compile("<path>([^<]{10,})</path>")
    };

    private ToolParamsExtractor() {
    }

    public static String extractToolTitle(SessionUpdate.UpdateData update, String messageId, String rawText) {
        return extractToolTitle(messageId, rawText, null);
    }

    public static String extractToolTitle(String messageId, String rawText) {
        return extractToolTitle(messageId, rawText, null);
    }

    public static String extractToolTitle(String messageId, String rawText, String kind) {
        String identifier = "";
        String tag = defaultString(messageId);

        int pos = tag.indexOf(':');
        if (pos > 0 && pos < tag.length()) {
            tag = tag.substring(0, pos);
        } else {
            tag = firstNonBlank(kind, "Tool");
        }

        for (var pattern : TOOL_CONTENT_PATTERNS) {
            Matcher m = pattern.matcher(rawText);
            if (m.find()) {
                identifier = m.group(1);
                break;
            }
        }

        LOG.fine("Identifier {0}/{1}", identifier, rawText);
        tag = tag.length() < 60 ? tag : "Tool";
        return tag + " " + abbreviateMiddle(identifier, "...", 60);
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

    public static MessageClassification classify(String role, String text, String kind) {
        if (isDcpCleanup(text)) {
            return new MessageClassification("tool", "context cleanup");
        }
        return new MessageClassification(role, kind);
    }

    public static boolean isDcpCleanup(String text) {
        return text != null && (text.contains("▣ DCP") || text.contains("  DCP Sweep  "));
    }
}
