package github.anandb.netbeans.manager.strategy;

import org.apache.commons.lang3.exception.ExceptionUtils;
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

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.contract.UpdateDispatcher;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.support.ToolDataExtractor;
import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ModelRecords.MessageClassification;
import github.anandb.netbeans.model.ModelRecords.PlanEntry;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.ToolCallData;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageKind;
import github.anandb.netbeans.model.UsageRecords.MessageUsageRow;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.SteerEchoes;

import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.apache.commons.lang3.Strings;

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

    /** Guards CAP-6 message counting against re-counting the same streamed message id. */
    private static final Cache<String, Boolean> COUNTED_MESSAGES =
        Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(20_000)
            .build();

    /** Guards per-message usage capture against a repeated notification for one message id. */
    private static final Cache<String, Boolean> CAPTURED_USAGE =
        Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(20_000)
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
    @Override
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
            case "config_option_update" -> {
                if (update.update() != null) {
                    handler.updateConfig(update.update().configOptions());
                }
            }

            case "usage_update" -> {
                if (update.update() != null) {
                    handler.updateUsage(update.update().used(), update.update().size());
                    captureUsageUpdate(update);
                }
            }

            case "message_usage" -> captureMessageUsage(update);

            case "status_message" -> {
                JsonNode notice = update.statusMessage();
                String message = notice != null ? notice.path("message").asText("") : "";
                if (isBlank(message)) {
                    LOG.fine("Ignoring status_message without a message, update={0}", update);
                    return;
                }
                handler.showStatus(message);
            }

            case "session_info_update" -> {
                // Session data is merged into SessionManager's cache before strategy
                // dispatch. Do not refresh the list for retry metadata updates.
                notifyQueuedSteer(update, handler);
            }

            case "current_mode_update" -> {
                // Mode changes are reflected through config_option_update
                // (category="mode"); nothing to render from this payload.
            }

            case "available_commands_update" -> {
                // Already handled by ProcessManager notification handler;
                // nothing to display or update here.
            }

            case "agent_thought_chunk" -> {
                String text = extractText(update.content());
                captureMessage(update, MessageKind.THOUGHT);
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
                captureMessage(update, MessageKind.USER);
                String userSessionId = update.params() != null ? update.params().sessionId() : null;
                // The echo is the turn consuming the steer: whatever was queued has landed.
                handler.onSteerQueued(userSessionId, update.messageId(), false);
                if (SteerEchoes.consume(userSessionId, update.messageId())) {
                    LOG.fine("Skipping echoed steer {0}: already rendered locally", update.messageId());
                    return;
                }
                // `_session/steering` reports an outcome instead of a message id, so that echo is
                // matched by text; only the steer we drew locally is registered here.
                if (SteerEchoes.consumeText(userSessionId, text)) {
                    LOG.fine("Skipping text-matched echoed steer for session {0}", userSessionId);
                    return;
                }
                handler.displayMessage(buildStreamingMessage(update, text));
            }

            case "agent_message_chunk" -> {
                String text = extractText(update.content());
                captureMessage(update, MessageKind.ASSISTANT);
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

                if (ToolDataExtractor.isDetailLessToolCall(update)) {
                    LOG.fine("Ignoring tool_call: no detail beyond the tool name, update={0}", update);
                    return;
                }

                final String sessionId = update.params() != null ? update.params().sessionId() : null;
                final String messageId = update.messageId();
                if (isBlank(messageId) || isBlank(sessionId)) {
                    LOG.fine("Ignoring tool_call: blank messageId or sessionId, update={0}", update);
                    return;
                }
                final String command = update.command();
                final String kind = update.kind();
                Map<String, ToolCallData> sessionMap = TOOL_CALL_CACHE.get(sessionId, k -> new ConcurrentHashMap<>());
                ToolCallData fresh = new ToolCallData(messageId, command);
                ToolCallData existing = sessionMap.putIfAbsent(messageId, fresh);
                if (existing == null) {
                    captureMessage(update, MessageKind.TOOL);
                }
                ToolCallData data = existing != null ? existing : fresh;
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
                    if (Strings.CS.contains(target.rawText(), "Could not find oldString in the file.")) {
                        LOG.fine("Ignoring tool_call: Failed Edit, update={0}", update);
                        return;
                    } else if (Strings.CS.contains(target.rawText(), "No changes to apply: oldString and newString are identical")) {
                        LOG.fine("Ignoring tool_call: Failed Edit, update={0}", update);
                        return;
                    }

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
                    LOG.warn("Failed to deserialize plan entries: {0}", ExceptionUtils.getMessage(e));
                    return;
                }
                if (entries.isEmpty()) {
                    LOG.fine("Ignoring plan update: empty entries, update={0}", update);
                    return;
                }
                entries = new ArrayList<>(entries);
                entries.sort(Comparator.comparingInt(e -> priorityWeight(e.priority())));
                // The plan renders in the slideout above the message thread
                // (ui/PlanPanel), not as a chat bubble.
                handler.updatePlan(entries);
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

    /** Detects plan tool calls by inspecting the output structure. */
    static boolean isPlanToolCall(SessionUpdate update) {
        if (!"completed".equals(update.status())) return false;
        JsonNode rawOutput = update.update() != null ? update.update().rawOutput() : null;
        if (rawOutput == null || !rawOutput.has("output")) return false;
        JsonNode output = rawOutput.get("output");
        if (!output.isTextual()) return false;
        try {
            String outText = output.asText().trim();
            if (!outText.startsWith("[")) return false;
            // Guard: next non-whitespace char must be '{' (plan entries are [{...}])
            // This avoids JsonParseException on git log lines like "[main abc123]..."
            int idx = 1;
            while (idx < outText.length() && outText.charAt(idx) <= ' ') idx++;
            if (idx >= outText.length() || outText.charAt(idx) != '{') return false;
            JsonNode arr = MAPPER.readTree(outText);
            if (!arr.isArray() || arr.isEmpty()) return false;
            JsonNode first = arr.get(0);
            return first.has("content") && first.has("status") && first.has("priority");
        } catch (Exception e) {
            LOG.warn("Skipping unparseable tool_call update: {0}", ExceptionUtils.getMessage(e));
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

            // For edit tools, include the file path in the body
            String filePath = null;
            if (update.update() != null && update.update().rawInput() != null
                    && update.update().rawInput().isObject()) {
                JsonNode ri = update.update().rawInput();
                filePath = ri.has("path") ? ri.get("path").asText(null) : null;
            }
            String pathPrefix = isNotBlank(filePath) ? filePath + "\n\n" : "";

            text = data.setText(new StringBuilder()
                    .append(pathPrefix)
                    .append(isNotBlank(effectiveCommand) ? "$ " : "")
                    .append(abbreviate(effectiveCommand, 80))
                    .append(isNotBlank(effectiveCommand) ? "\n\n" : "")
                    .append(firstNonNull(ToolDataExtractor.contentText(update.content()), ""))
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
                .status(defaultString(update.status()))
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

    /**
     * Captures a {@code usage_update} notification as one stats row. The cost
     * object may be absent; such a row is still recorded with 0 USD, because a
     * free or cost-less update is never skipped.
     */
    private static void captureUsageUpdate(SessionUpdate update) {
        if (!captureAllowed()) {
            return;
        }
        UsageStatsStore store = Lookup.getDefault().lookup(UsageStatsStore.class);
        if (store == null) {
            return;
        }
        String sessionId = update.params() != null ? update.params().sessionId() : null;
        if (isBlank(sessionId)) {
            return;
        }
        SessionUpdate.UpdateData data = update.update();
        JsonNode cost = data.cost();
        double amount = cost != null && cost.hasNonNull("amount") ? cost.get("amount").asDouble(0) : 0;
        String currency = cost != null && cost.hasNonNull("currency")
                ? cost.get("currency").asText("USD") : "USD";
        store.recordUsageUpdate(new UsageUpdateRow(attribution(sessionId), System.currentTimeMillis(),
                data.used() != null ? data.used() : 0, data.size(), amount, currency));
    }

    /**
     * Records one goose {@code message_usage} notification. Session replay sends these
     * again on load, so the load window is skipped like every other capture, and a
     * repeated message id inside a live turn is deduped. Compaction usage carries no
     * message id and is stored, since it is real spend.
     */
    private static void captureMessageUsage(SessionUpdate update) {
        if (!captureAllowed()) {
            return;
        }
        JsonNode usage = update.usage();
        String sessionId = update.params() != null ? update.params().sessionId() : null;
        if (usage == null || !usage.isObject() || isBlank(sessionId)) {
            return;
        }
        UsageStatsStore store = Lookup.getDefault().lookup(UsageStatsStore.class);
        if (store == null) {
            return;
        }
        String messageId = update.messageId();
        if (isNotBlank(messageId)
                && CAPTURED_USAGE.asMap().putIfAbsent(sessionId + '|' + messageId, Boolean.TRUE) != null) {
            return;
        }
        long input = usage.path("inputTokens").asLong(0);
        long output = usage.path("outputTokens").asLong(0);
        long total = usage.hasNonNull("totalTokens") ? usage.get("totalTokens").asLong() : input + output;
        store.recordMessageUsage(new MessageUsageRow(attribution(sessionId, update), System.currentTimeMillis(),
                messageId, input, output, total,
                optLong(usage, "cacheReadTokens"), optLong(usage, "cacheWriteTokens"),
                usage.hasNonNull("cost") ? usage.get("cost").asDouble() : null,
                optLong(usage, "elapsedMs"), optLong(usage, "timeToFirstTokenMs"),
                usage.path("isCompaction").asBoolean(false)));
    }

    /** Reads an optional numeric field as a {@code Long}; absent or non-numeric is null. */
    private static Long optLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    /**
     * Counts one user, assistant, tool or thought update for the overview
     * message total. A repeated {@code messageId} is counted once, so a chatty
     * streaming harness does not inflate the count.
     */
    private static void captureMessage(SessionUpdate update, MessageKind kind) {
        if (!captureAllowed()) {
            return;
        }
        UsageStatsStore store = Lookup.getDefault().lookup(UsageStatsStore.class);
        if (store == null) {
            return;
        }
        String sessionId = update.params() != null ? update.params().sessionId() : null;
        if (isBlank(sessionId) || !firstCount(sessionId, kind, update.messageId())) {
            return;
        }
        store.recordMessage(new MessageEvent(attribution(sessionId, update), System.currentTimeMillis(), kind));
    }

    /** True the first time a (session, kind, messageId) triple is seen. */
    private static boolean firstCount(String sessionId, MessageKind kind, String messageId) {
        if (isBlank(messageId)) {
            return true;
        }
        String key = sessionId + '|' + kind + '|' + messageId;
        return COUNTED_MESSAGES.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    /**
     * False only while the plugin is loading a session and the harness is
     * replaying history. Replayed updates are not new events: counting them
     * makes the overview message total climb on every reload, and recording
     * their {@code usage_update} rows re-sums cost and tokens already captured.
     * Replay chunks carry no {@code messageId}, so the per-id dedup cannot
     * filter them — the load window is the only reliable signal.
     */
    static boolean shouldCapture(SessionState state) {
        return state != SessionState.LOADING;
    }

    private static boolean captureAllowed() {
        SessionQuery sessions = Lookup.getDefault().lookup(SessionQuery.class);
        return shouldCapture(sessions != null ? sessions.getCurrentState() : null);
    }

    /**
     * Resolves the session's harness, model and project at capture time. Harness
     * and model come from the in-memory current values for the session the
     * message belongs to, so a message from a previously open session is never
     * stamped with the wrong harness or model.
     */
    private static Attribution attribution(String sessionId) {
        SessionQuery sessions = Lookup.getDefault().lookup(SessionQuery.class);
        if (sessions == null) {
            return new Attribution(sessionId, null, null, null);
        }
        return new Attribution(sessionId, sessions.getHarnessId(),
                sessions.getSessionModelId(sessionId), sessions.getSessionDirectory(sessionId));
    }

    /**
     * Attribution for a message-bearing update: the harness reports which model actually
     * answered in {@code _meta.goose.inference}, which can differ from the dropdown value
     * (a resolved variant, or a sub-agent on another model). Falls back to the session's
     * current model when the harness is silent.
     */
    private static Attribution attribution(String sessionId, SessionUpdate update) {
        Attribution base = attribution(sessionId);
        String resolved = resolvedModel(update);
        return resolved == null ? base
                : new Attribution(base.sessionId(), base.harnessId(), resolved, base.project());
    }

    /** {@code _meta.goose.inference.resolvedModel}, falling back to the requested model. */
    private static String resolvedModel(SessionUpdate update) {
        JsonNode meta = update.meta();
        if (meta == null || !meta.isObject()) {
            return null;
        }
        JsonNode inference = meta.path("goose").path("inference");
        if (!inference.isObject()) {
            return null;
        }
        String resolved = inference.path("resolvedModel").asText("");
        if (isNotBlank(resolved)) {
            return resolved;
        }
        String requested = inference.path("requestedModel").asText("");
        return isNotBlank(requested) ? requested : null;
    }
    /**
     * goose accepts a steer into the running turn and announces it on {@code session_info_update}
     * as {@code _meta.goose.queuedSteer {messageId, runId}}. Until the matching
     * {@code user_message_chunk} echo arrives the turn has not consumed the text, so the bubble
     * is drawn as queued rather than pretending it landed.
     */
    private static void notifyQueuedSteer(SessionUpdate update, UIHandler handler) {
        JsonNode meta = update.meta();
        if (meta == null || !meta.isObject()) {
            return;
        }
        JsonNode queued = meta.path("goose").path("queuedSteer");
        if (!queued.isObject()) {
            return;
        }
        String messageId = queued.path("messageId").asText("");
        if (isBlank(messageId)) {
            LOG.fine("Ignoring queuedSteer without a messageId: {0}", queued);
            return;
        }
        handler.onSteerQueued(update.params() != null ? update.params().sessionId() : null, messageId, true);
    }

}
