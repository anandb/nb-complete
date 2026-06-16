package github.anandb.netbeans.support;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.ModelRecords.MessageClassification;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import org.apache.commons.lang3.tuple.Pair;

import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.toRootLowerCase;

public final class ToolDataExtractor {
    private static final Logger LOG = Logger.from(ToolDataExtractor.class);

    /** Max chars for tool title before truncation.
     *  Updated by UI panes based on their actual width and font metrics. */
    private static volatile int maxTitleLength = 60;

    public static int getMaxTitleLength() { return maxTitleLength; }

    /** Set max tool-title characters, derived from pane width and font size.
     *  Call from UI when pane is resized. */
    public static void setMaxTitleLength(int max) {
        maxTitleLength = Math.max(20, max);
    }

    private static final Pattern[] METADATA_PATTERNS = {
        Pattern.compile("<!--.*?-->", Pattern.DOTALL),
        Pattern.compile("<metadata>.*?</metadata>", Pattern.DOTALL)
    };

    private static final List<Pair<String, Pattern>> TOOL_CONTENT_PATTERNS = List.of(
        Pair.of("skill", Pattern.compile("<skill_content name=\"([^\"]{2,})\"")),
        Pair.of("context", Pattern.compile("(Compressed [\\d]+ messages) into")),
        Pair.of("", Pattern.compile("<path>([^<]{10,})</path>")),
        Pair.of("", Pattern.compile("\"filePath\":([^\"]{10,})\""))
    );

    private ToolDataExtractor() {}

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

    public static MessageClassification classify(MessageType type, String text, String kind, String title) {
        if (isDcpCleanup(text)) {
            return new MessageClassification(MessageType.tool_call_update, "context cleanup");
        }

        if (isCavemanFiller(text)) {
            return new MessageClassification(MessageType.tool_call_update, "skill");
        }

        if (defaultString(title).startsWith("nb_")) {
            kind = "mcp";
        }

        if ("think".equals(kind)) {
            kind = "tool";
        }

        return new MessageClassification(type, kind);
    }

    public static MessageClassification classify(MessageType type, String text, String kind) {
        return classify(type, text, kind, "");
    }

    public static boolean isDcpCleanup(String text) {
        return text != null && (
            text.contains("▣ DCP") ||
            text.contains("DCP Sweep") ||
            text.contains("DCP Commands") ||
            text.contains("DCP Context Analysis") ||
            text.contains("DCP Statistics")
        );
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

    public static String extractToolTitle(String messageId, String rawText,
                                           MessageClassification mc, SessionUpdate update) {
        String tag = defaultString(messageId);
        String kind = (mc != null) ? mc.kind() : null;
        int pos = tag.indexOf(':');
        if (pos >= 0 && pos < tag.length()) {
            tag = tag.substring(0, pos);
        } else {
            tag = firstNonBlank(kind, "Tool");
        }

        String title = update != null ? update.update().title() : null;
        String identifier = firstNonBlank(title, "");
        if (isBlank(identifier)) {
            for (var entry : TOOL_CONTENT_PATTERNS) {
                Matcher m = entry.getValue().matcher(rawText);
                if (m.find()) {
                    identifier = m.group(1);
                    if (isNotBlank(entry.getKey())) {
                        // Change tag based on content
                        tag = entry.getKey();
                    }
                    break;
                }
            }
        }

        int maxLen = getMaxTitleLength();
        tag = tag.length() < maxLen ? tag : "Tool";

        // Some special handling - look for a better way
        if ("context".equals(tag) && identifier.startsWith("Compressed")) {
            identifier = toRootLowerCase(identifier);
        }

        return tag + " " + abbreviateMiddle(defaultString(identifier), "...", maxLen);
    }

    public static String getLocalEchoText(String commandText) {
        LOG.info("Local echo check for command: {0}", commandText);
        if (commandText.startsWith("/dcp") || commandText.startsWith("/compact")) {
            return commandText.replace("/", "");
        }

        return null;
    }
}
