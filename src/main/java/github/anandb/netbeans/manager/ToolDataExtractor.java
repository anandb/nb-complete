package github.anandb.netbeans.manager;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.MessageClassification;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.support.Logger;
import org.apache.commons.lang3.tuple.Pair;

import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.toRootLowerCase;

public final class ToolDataExtractor {
    private static final Logger LOG = new Logger(ToolDataExtractor.class);

    private static final Pattern[] METADATA_PATTERNS = {
        Pattern.compile("<!--.*?-->", Pattern.DOTALL),
        Pattern.compile("<metadata>.*?</metadata>", Pattern.DOTALL),
    };

    private static final List<Pair<String, Pattern>> TOOL_CONTENT_PATTERNS = List.of(
        Pair.of("skill", Pattern.compile("<skill_content name=\"([^\"]{2,})\"")),
        Pair.of("context", Pattern.compile("(Compressed [\\d]+ messages) into")),
        Pair.of("", Pattern.compile("<path>([^<]{10,})</path>"))
    );

    private ToolDataExtractor() {
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

    public static String stripMetadata(String text) {
        String stripped = text;
        if (isNotBlank(stripped)) {
            for (Pattern pattern : METADATA_PATTERNS) {
                stripped = pattern.matcher(stripped).replaceAll("");
            }
        }
        return stripped;
    }

    public static String extractToolTitle(String messageId, String rawText, String kind) {
        String tag = defaultString(messageId);

        int pos = tag.indexOf(':');
        if (pos >= 0 && pos < tag.length()) {
            tag = tag.substring(0, pos);
        } else {
            tag = firstNonBlank(kind, "Tool");
        }

        String identifier = "";
        for (var entry : TOOL_CONTENT_PATTERNS) {
            Matcher m = entry.getValue().matcher(rawText);
            if (m.find()) {
                identifier = m.group(1);
                if (isNotBlank(entry.getKey())) {
                    tag = entry.getKey();
                }
                break;
            }
        }

        tag = tag.length() < 60 ? tag : "Tool";

        // Some special handling - look for a better way
        if ("context".equals(tag) && identifier.startsWith("Compressed")) {
            identifier = toRootLowerCase(identifier);
        }

        return tag + " " + abbreviateMiddle(identifier, "...", 60);
    }

    public static String extractToolTitle(ProcessedMessage pm, String kind) {
        return extractToolTitle(pm.messageId(), pm.rawText(), kind);
    }

    public static String getLocalEchoText(String commandText) {
        LOG.info("Local echo check for command: {0}", commandText);
        if (commandText.startsWith("/dcp") || commandText.startsWith("/compact")) {
            return commandText.replace("/", "");
        }

        return null;
    }
}
