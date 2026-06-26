package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.contract.UpdateDispatcher;
import github.anandb.netbeans.support.ToolDataExtractor;
import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ModelRecords.MessageClassification;
import github.anandb.netbeans.model.ModelRecords.PlanEntry;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.ToolCallData;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@ServiceProvider(service = UpdateDispatcher.class)
public class StrategyRegistry implements UpdateDispatcher {
    private static final Logger LOG = Logger.from(StrategyRegistry.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private static final Cache<String, Map<String, ToolCallData>> TOOL_CALL_CACHE =
        Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .maximumSize(1024)
            .recordStats()
            .build();

    public StrategyRegistry() {
    }

    public static StrategyRegistry getInstance() {
        UpdateDispatcher dispatcher = Lookup.getDefault().lookup(UpdateDispatcher.class);
        if (dispatcher instanceof StrategyRegistry reg) {
            return reg;
        }
        throw new IllegalStateException(
                "StrategyRegistry not found in Lookup — @ServiceProvider registration is broken");
    }

    /** Invalidates the tool-call deduplication cache for a given session. */
    public static void invalidateSession(String sessionId) {
        TOOL_CALL_CACHE.invalidate(sessionId);
    }

    /** Dispatches a SessionUpdate to the appropriate extraction logic via a type switch. */
    public void handle(SessionUpdate update, UIHandler handler) {
        String type = update.type();
        if (type == null) {
            LOG.warn("Received a message with NULL type {0}", update);
            LOG.fine("Unknown update payload: {0}", update);
            return;
        }

        type = defaultIfBlank(reClassify(update), update.type());
        if (hasAnnotationFilter(update) && shouldSkip(update.content())) {
            LOG.fine("Ignoring message type={0}: annotation filter matched, update={1}", type, update);
            return;
        }

        LOG.fine("Processing update type: {0}", type);

        switch (type) {
            case "config_options_update" ->
                handler.updateConfig(update.update().configOptions());

            case "usage_update" -> {
                if (update.update() != null) {
                    handler.updateUsage(update.update().used(), update.update().size());
                }
            }

            case "session_info_update" ->
                handler.refreshSessions();

            case "available_commands_update" -> {
                // Already handled by ProcessManager notification handler;
                // nothing to display or update here.
            }

            case "agent_thought_chunk" -> {
                String text = extractText(update.content());
                String tTitle = SubAgentTitleResolver.resolve(null,
                        update.params() != null ? update.params().sessionId() : null,
                        "Sub-Agent", "Thinking");
                handler.displayMessage(new ProcessedMessage.Builder()
                        .messageType(MessageType.valueOf(update.type()))
                        .text(text)
                        .messageId(update.messageId())
                        .kind(update.kind())
                        .toolTitle(tTitle)
                        .rawText(text)
                        .streaming(true)
                        .build());
            }

            case "user_message_chunk" -> {
                String text = extractText(update.content());
                if (isBlank(text)) {
                    LOG.fine("Ignoring user_message_chunk: empty text, update={0}", update);
                    return;
                }
                handler.displayMessage(buildStreamingMessage(update, text));
            }

            case "agent_message_chunk" -> {
                String text = extractText(update.content());
                ProcessedMessage msg = buildStreamingMessage(update, text);
                if (msg.messageType() != null) {
                    handler.displayMessage(msg);
                }
            }

            case "tool_call_update", "tool_call" -> {
                if (isPlanToolCall(update)) {
                    LOG.fine("Ignoring tool_call: plan tool call, update={0}", update);
                    return;
                }
                final String sessionId = update.params().sessionId();
                final String messageId = update.messageId();
                if (isBlank(messageId) || isBlank(sessionId)) {
                    LOG.fine("Ignoring tool_call: blank messageId or sessionId, update={0}", update);
                    return;
                }
                final String command = update.command();
                final String kind = update.kind();
                Map<String, ToolCallData> sessionMap = TOOL_CALL_CACHE.get(sessionId, k -> new ConcurrentHashMap<>());
                ToolCallData data = sessionMap.computeIfAbsent(messageId, k -> new ToolCallData(k, command));
                // Wrap isDone() check + process + shouldDisplay under the same
                // lock to prevent TOCTOU: a concurrent update could set status
                // to "completed" between the isDone() guard and the process call,
                // and shouldDisplay() reads lastDisplayedText unsynchronized.
                // Java's synchronized is reentrant, so the nested lock inside
                // processToolMessage is safe.
                synchronized (data) {
                    if (data.isDone()) {
                        LOG.fine("Ignoring tool_call: already completed, update={0}", update);
                        return;
                    }
                    ProcessedMessage target = processToolMessage(data, command, update, kind, messageId);
                    if (data.shouldDisplay(target.text())) {
                        handler.displayMessage(target);
                    }
                }
            }

            case "message" -> {
                Message msg = update.message();
                if (msg == null) {
                    LOG.fine("Ignoring message: null message object, update={0}", update);
                    return;
                }
                String role = msg.type() != null ? msg.type() : "assistant";
                StringBuilder sb = new StringBuilder();
                if ("user".equals(role) && msg.prompt() != null) {
                    if (msg.prompt().text() != null) sb.append(msg.prompt().text());
                    appendParts(sb, msg.prompt().parts(), "\n");
                } else if (msg.completion() != null) {
                    if (msg.completion().text() != null) sb.append(msg.completion().text());
                    appendParts(sb, msg.completion().parts(), "\n\n");
                }
                String text = sb.toString().stripTrailing();
                if (isBlank(text)) {
                    LOG.warn("Message: empty text for msgId={0}, role={1}, skipping display",
                        new Object[]{msg.id(), role});
                    return;
                }
                MessageType msgType = "user".equals(role)
                    ? MessageType.user_message_chunk
                    : MessageType.agent_message_chunk;
                ProcessedMessage target = new ProcessedMessage.Builder()
                        .messageType(msgType)
                        .text(text)
                        .messageId(msg.id())
                        .rawText(text)
                        .streaming(false)
                        .build();
                if (target.messageType() != null) {
                    handler.displayMessage(target);
                }
            }

            case "plan" -> {
                JsonNode entriesNode = update.update() != null ? update.update().entries() : null;
                if (entriesNode == null || !entriesNode.isArray()) {
                    LOG.fine("Ignoring plan update: no entries array, update={0}", update);
                    return;
                }
                List<PlanEntry> entries;
                try {
                    entries = MAPPER.convertValue(entriesNode, new TypeReference<List<PlanEntry>>() {});
                } catch (Exception e) {
                    LOG.warn("Failed to deserialize plan entries: {0}", e.getMessage());
                    return;
                }
                if (entries.isEmpty()) {
                    LOG.fine("Ignoring plan update: empty entries, update={0}", update);
                    return;
                }
                entries = new ArrayList<>(entries);
                entries.sort(Comparator.comparingInt(e -> priorityWeight(e.priority())));
                StringBuilder sb = new StringBuilder();
                sb.append("## Plan\n\n");
                for (PlanEntry entry : entries) {
                    sb.append("- ").append(statusIcon(entry.status())).append(" ").append(entry.content()).append("\n");
                }
                ProcessedMessage target = new ProcessedMessage.Builder()
                        .messageType(MessageType.agent_message_chunk)
                        .text(sb.toString())
                        .streaming(false)
                        .build();
                handler.displayMessage(target);
            }

            default -> {
                LOG.warn("Received unknown ACP session update type: {0}", type);
                LOG.fine("Unknown update payload: {0}", update);
            }
        }
    }

    public boolean hasAnnotationFilter(SessionUpdate update) {
        String t = update.type();
        return MessageType.agent_message_chunk.name().equals(t)
            || MessageType.agent_thought_chunk.name().equals(t)
            || MessageType.user_message_chunk.name().equals(t);
    }

    // ---------------------------------------------------------------
    // Annotation filtering
    // ---------------------------------------------------------------

    private boolean shouldSkip(JsonNode content) {
        if (content == null || !content.isObject() || !content.has("annotations")) return false;
        JsonNode annotations = content.get("annotations");

        if (annotations == null || !annotations.isObject()) return false;
        if (annotations.has("audience") && annotations.get("audience").isArray()) {
            for (JsonNode aud : annotations.get("audience")) {
                if ("assistant".equals(aud.asText()))
                    return true;
            }
        }

        if (annotations.has("tags") && annotations.get("tags").isArray()) {
            for (JsonNode tag : annotations.get("tags")) {
                if ("hidden".equals(tag.asText()))
                    return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------
    // Type reclassification
    // ---------------------------------------------------------------

    private String reClassify(SessionUpdate update) {
        String type = update.type();
        if (update.update() == null) {
            return type;
        }
        try {
            JsonNode content = update.content();
            String text = (content != null && content.isObject() && content.has("text"))
                    ? content.get("text").asText()
                    : MAPPER.writeValueAsString(content);
            var meta = ToolDataExtractor.classify(update.update().type(), text, update.kind(), update.update().title());
            if (meta.type() != update.update().type()) {
                type = meta.type().name();
            }
        } catch (JsonProcessingException e) {
            LOG.fine("Problem converting node to json {0}", update.messageId());
        }
        return type;
    }

    // ---------------------------------------------------------------
    // Extraction helpers
    // ---------------------------------------------------------------

    /** Builds a streaming ProcessedMessage from a SessionUpdate and extracted text. */
    private static ProcessedMessage buildStreamingMessage(SessionUpdate update, String text) {
        return new ProcessedMessage.Builder()
                .messageType(MessageType.valueOf(update.type()))
                .text(text)
                .messageId(update.messageId())
                .kind(update.kind())
                .rawText(text)
                .streaming(true)
                .build();
    }

    /** Extracts plain text from a JsonNode content structure. */
    private static String extractText(JsonNode content) {
        if (content == null) return "";
        if (content.isTextual()) return content.asText();
        if (content.has("text")) return content.get("text").asText();

        StringBuilder sb = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode part : content) {
                if (part.has("type") && "text".equals(part.get("type").asText()) && part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
        }

        String result = sb.toString();
        if (result.isEmpty()) {
            LOG.fine("Could not extract non-empty text from content: {0}", content);
        }
        return result;
    }

    /** Extracts text from tool-call-style nested content structures. */
    private static String extractContentText(JsonNode content) {
        if (content == null) return null;
        StringBuilder sb = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode part : content) {
                if (part.has("content")) {
                    JsonNode inner = part.get("content");
                    if (inner.has("text")) sb.append(inner.get("text").asText());
                } else if (part.has("type") && "text".equals(part.get("type").asText())) {
                    if (part.has("text")) sb.append(part.get("text").asText());
                }
            }
        } else if (content.has("text")) {
            sb.append(content.get("text").asText());
        }
        return sb.toString();
    }

    /** Detects plan tool calls by inspecting the output structure. */
    private static boolean isPlanToolCall(SessionUpdate update) {
        if (!"completed".equals(update.status())) return false;
        JsonNode rawOutput = update.update() != null ? update.update().rawOutput() : null;
        if (rawOutput == null || !rawOutput.has("output")) return false;
        JsonNode output = rawOutput.get("output");
        if (!output.isTextual()) return false;
        try {
            String outText = output.asText().trim();
            if (!outText.startsWith("[")) return false;
            JsonNode arr = MAPPER.readTree(outText);
            if (!arr.isArray() || arr.isEmpty()) return false;
            JsonNode first = arr.get(0);
            return first.has("content") && first.has("status") && first.has("priority");
        } catch (Exception e) {
            return false;
        }
    }

    /** Builds a ProcessedMessage for a tool call, using the dedup cache for state accumulation. */
    private static ProcessedMessage processToolMessage(ToolCallData data, final String command,
                                                        SessionUpdate update, String kind, final String messageId) {
        String tt;
        String text;
        MessageClassification m;
        synchronized (data) {
            String effectiveCommand = data.setCommand(command);
            data.setStatus(defaultIfBlank(update.status(), "completed"));

            text = data.setText(new StringBuilder()
                    .append(isNotBlank(effectiveCommand) ? "$ " : "")
                    .append(abbreviate(effectiveCommand, 80))
                    .append(isNotBlank(effectiveCommand) ? "\n\n" : "")
                    .append(firstNonNull(extractContentText(update.content()), ""))
                    .toString());
            m = ToolDataExtractor.classify(update.update().type(), text, kind, update.update().title());
            String extractedTitle = ToolDataExtractor.extractToolTitle(defaultString(messageId), text, m, update);
            extractedTitle = SubAgentTitleResolver.resolve(extractedTitle,
                    update.params() != null ? update.params().sessionId() : null,
                    "Sub-Agent", extractedTitle);
            tt = data.setTitle(extractedTitle);
            kind = data.setKind(kind);
        }

        return new ProcessedMessage.Builder()
                .messageType(m.type())
                .text(text)
                .messageId(defaultString(messageId))
                .kind(kind)
                .toolTitle(tt)
                .rawText(text)
                .streaming(true)
                .status(defaultString(update.update().status()))
                .build();
    }

    /** Appends ContentPart display text to a StringBuilder with a separator. */
    private void appendParts(StringBuilder sb, List<Message.ContentPart> parts, String separator) {
        if (parts == null) return;
        for (Message.ContentPart part : parts) {
            String pt = part.getDisplayText();
            if (pt != null && !pt.isEmpty()) {
                if (sb.length() > 0) sb.append(separator);
                sb.append(pt);
            }
        }
    }

    /** Maps a priority string to a sort weight (1 = highest). */
    private static int priorityWeight(String priority) {
        if (priority == null) return 3;
        return switch (priority.toLowerCase()) {
            case "high" -> 1;
            case "medium" -> 2;
            case "low" -> 3;
            default -> 3;
        };
    }

    /** Maps a plan status string to a display icon. */
    private static String statusIcon(String status) {
        if (status == null) return "\u25CB";
        return switch (status.toLowerCase()) {
            case "completed" -> "\u2705";
            case "in_progress", "in-progress", "inprogress" -> "\u25C9";
            case "pending" -> "\u23F3";
            case "cancelled", "canceled" -> "\u274C";
            case "failed" -> "\u26A0";
            default -> "\u25CB";
        };
    }
}
