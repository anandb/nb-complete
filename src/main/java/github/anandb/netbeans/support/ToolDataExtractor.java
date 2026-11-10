package github.anandb.netbeans.support;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.ModelRecords.MessageClassification;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.pattern.HtmlCommentPattern;
import github.anandb.netbeans.pattern.MetadataBlockPattern;
import github.anandb.netbeans.pattern.McpTitlePrefixPattern;
import github.anandb.netbeans.pattern.ToolTitlePrefixPattern;
import github.anandb.netbeans.pattern.SkillContentPattern;
import github.anandb.netbeans.pattern.CompressedContextPattern;
import github.anandb.netbeans.pattern.PathTagPattern;
import github.anandb.netbeans.pattern.FilePathJsonPattern;

import org.apache.commons.lang3.tuple.Pair;

import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.toRootLowerCase;

public final class ToolDataExtractor {
    private static final Logger LOG = Logger.from(ToolDataExtractor.class);

    /** A tool kind longer than this is not a kind: fall back to the generic label. */
    private static final int MAX_KIND_CHARS = 40;
    /**
     * Safety cap for a tool title, so a pathological argument (a 4 KB command) cannot become a
     * 4 KB label. It is NOT the display width: the header label elides to its real width with
     * font metrics, which is why this is far longer than any header.
     */
    private static final int MAX_TITLE_CHARS = 300;

    /**
     * goose reports no {@code kind} on the initial tool_call; it puts the kind in the
     * title instead ("edit · &lt;path&gt;", "shell · &lt;command&gt;"). MCP tools arrive as
     * "nb: &lt;tool name&gt;".
     */
    /** rawInput keys that carry the target file of a read/edit tool call. */
    private static final String[] RAW_INPUT_PATH_KEYS = {"path", "filePath", "file_path"};

    /** Tool kinds that Claude Code sends with nothing but the tool name. */
    private static final Set<String> DETAIL_LESS_KINDS = Set.of("read", "edit", "other", "execute");

    private static final List<Pair<String, Function<String, Matcher>>> TOOL_CONTENT_PATTERNS = List.of(
        Pair.of("skill", SkillContentPattern::matcher),
        Pair.of("context", CompressedContextPattern::matcher),
        Pair.of("", PathTagPattern::matcher),
        Pair.of("", FilePathJsonPattern::matcher)
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

        if (defaultString(title).startsWith("nb_")) {
            kind = "mcp";
        } else if (isBlank(kind)) {
            kind = titleKind(defaultString(title));
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

    public static String stripMetadata(String text) {
        return MetadataBlockPattern.stripFrom(HtmlCommentPattern.stripFrom(text));
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

        String identifier = extractIdentifier(update);
        if (isBlank(identifier) && rawText != null) {
            for (var entry : TOOL_CONTENT_PATTERNS) {
                Matcher m = entry.getValue().apply(rawText);
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

        tag = tag.length() < MAX_KIND_CHARS ? tag : "Tool";

        // Some special handling - look for a better way
        if ("context".equals(tag) && identifier.startsWith("Compressed")) {
            identifier = toRootLowerCase(identifier);
        }

        return tag + " " + abbreviateMiddle(defaultString(identifier), "...", MAX_TITLE_CHARS);
    }

    /**
     * Detects a tool call update that carries nothing but the tool name. Claude Code
     * emits these for {@code read}/{@code edit}/{@code execute} before the arguments
     * are known ({@code rawInput:{}}, empty content, generic title), which used to
     * render an empty bubble titled like "edit Edit". Such updates are not displayed;
     * a later update that does carry a path, a command or content still renders
     * normally.
     */
    public static boolean isDetailLessToolCall(SessionUpdate update) {
        SessionUpdate.UpdateData ud = update != null ? update.update() : null;
        if (ud == null) return false;
        String kind = toRootLowerCase(defaultString(ud.kind()));
        if (!DETAIL_LESS_KINDS.contains(kind)) return false;
        if (isNotBlank(update.command())) return false;
        if (isNotBlank(contentText(ud.content()))) return false;
        // A title that is the bare tool name (single token, e.g. "Edit" or "execute")
        // adds no detail; a descriptive title keeps the bubble.
        String title = defaultString(ud.title()).strip();
        if (isNotBlank(title) && !title.equalsIgnoreCase(kind) && title.indexOf(' ') >= 0) return false;
        JsonNode rawInput = ud.rawInput();
        if (rawInput != null && rawInput.isObject()) {
            for (String key : RAW_INPUT_PATH_KEYS) {
                JsonNode value = rawInput.get(key);
                if (value != null && isNotBlank(value.asText(null))) return false;
            }
        }
        return isBlank(extractFirstLocationPath(ud));
    }

    /** Concatenates the display text of a tool-call content structure. */
    public static String contentText(JsonNode content) {
        if (content == null) return null;
        if (content.isTextual()) return content.asText();
        if (content.has("text")) return content.get("text").asText();
        StringBuilder sb = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode part : content) {
                JsonNode inner = part.get("content");
                if (inner != null && inner.has("text")) {
                    sb.append(inner.get("text").asText());
                } else if ("text".equals(part.path("type").asText()) && part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
        }
        return sb.toString();
    }

    private static String extractIdentifier(SessionUpdate update) {
        SessionUpdate.UpdateData ud = update != null ? update.update() : null;
        JsonNode ri = ud != null ? ud.rawInput() : null;

        String path = null;
        if (ri != null && ri.isObject()) {
            path = firstNonBlank(
                ri.has("path") ? ri.get("path").asText(null) : null,
                ri.has("filePath") ? ri.get("filePath").asText(null) : null
            );
        }
        boolean genericEdit = ud != null && "edit".equalsIgnoreCase(defaultString(ud.kind()))
                && "Editing files".equalsIgnoreCase(defaultString(ud.title()).strip());
        if (path == null && genericEdit) {
            path = extractFirstDiffPath(ud.content());
        }
        // Fallback: extract from locations array (PI agent format)
        if (path == null && ud != null) {
            path = extractFirstLocationPath(ud);
        }
        String title = ud != null ? titleDetail(ud.title()) : null;
        String toolName = ri != null && ri.has("tool") ? ri.get("tool").asText(null) : null;

        return firstNonBlank(path, title, toolName, "");
    }

    private static String extractFirstDiffPath(JsonNode content) {
        if (content == null || !content.isArray()) return null;
        for (JsonNode item : content) {
            JsonNode path = item.path("path");
            if ("diff".equals(item.path("type").asText()) && path.isTextual()
                    && isNotBlank(path.asText())) {
                return path.asText();
            }
        }
        return null;
    }

    /** The kind goose puts in its title prefix, or null when the title carries none. */
    private static String titleKind(String title) {
        Matcher m = ToolTitlePrefixPattern.matcher(defaultString(title));
        if (m.find()) {
            return m.group(1);
        }
        return McpTitlePrefixPattern.matcher(defaultString(title)).find() ? "mcp" : null;
    }

    /** The detail half of goose's "&lt;kind&gt; · &lt;detail&gt;" title, so the kind is not repeated. */
    private static String titleDetail(String title) {
        String text = defaultString(title);
        Matcher m = ToolTitlePrefixPattern.matcher(text);
        if (m.find()) {
            return text.substring(m.end());
        }
        Matcher mcp = McpTitlePrefixPattern.matcher(text);
        return mcp.find() ? text.substring(mcp.end()) : text;
    }

    private static String extractFirstLocationPath(SessionUpdate.UpdateData ud) {
        JsonNode locations = ud.locations();
        if (locations != null && locations.isArray() && locations.size() > 0) {
            JsonNode first = locations.get(0);
            if (first.has("path") && first.get("path").isTextual()) {
                return first.get("path").asText();
            }
        }
        return null;
    }

    public static String getLocalEchoText(String commandText) {
        LOG.info("Local echo check for command: {0}", commandText);
        if (commandText.startsWith("/dcp") || commandText.startsWith("/compact")) {
            return commandText.replace("/", "");
        }

        return null;
    }
}
