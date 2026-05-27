package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import github.anandb.netbeans.manager.ToolDataExtractor;
import github.anandb.netbeans.model.ModelRecords.MessageClassification;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.ToolCallData;
import github.anandb.netbeans.support.MapperSupplier;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ToolCallUpdateStrategy implements DataExtractionStrategy {

    public static void invalidateSession(String sessionId) {
        TOOL_CALL_CACHE.invalidate(sessionId);
    }

    private static final Cache<String, Map<String, ToolCallData>> TOOL_CALL_CACHE;

    static {
        TOOL_CALL_CACHE = Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .maximumSize(1024)
            .recordStats()
            .build();
    }

    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return "tool_call_update".equals(reclassifiedType) || "tool_call".equals(reclassifiedType);
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        if (isPlanToolCall(update)) {
            return;
        }

        final String sessionId = update.params().sessionId();
        final String messageId = update.messageId();
        if (isBlank(messageId) || isBlank(sessionId)) {
            return;
        }

        final String command = update.command();
        final String kind = update.kind();

        Map<String, ToolCallData> sessionMap = TOOL_CALL_CACHE.get(sessionId, k -> new ConcurrentHashMap<>());
        ToolCallData data = sessionMap.computeIfAbsent(messageId, k -> new ToolCallData(k, command));
        // If already completed, this is a spurious duplicate — ignore it
        if (data.isDone()) {
            return;
        }
        ProcessedMessage target = processToolMessage(data, command, update, kind, messageId);
        // Keep the entry in the map as a sentinel so duplicates are caught above
        handler.displayMessage(target);
    }

    private String extractContentText(JsonNode content) {
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

    private boolean isPlanToolCall(SessionUpdate update) {
        if (!"completed".equals(update.status())) return false;
        JsonNode rawOutput = update.update() != null ? update.update().rawOutput() : null;
        if (rawOutput == null || !rawOutput.has("output")) return false;
        JsonNode output = rawOutput.get("output");
        if (!output.isTextual()) return false;
        try {
            String outText = output.asText().trim();
            if (!outText.startsWith("[")) return false;
            JsonNode arr = MapperSupplier.get().readTree(outText);
            if (!arr.isArray() || arr.isEmpty()) return false;
            JsonNode first = arr.get(0);
            return first.has("content") && first.has("status") && first.has("priority");
        } catch (Exception e) {
            return false;
        }
    }

    private ProcessedMessage processToolMessage(ToolCallData data, final String command, SessionUpdate update,
                                                 String kind, final String messageId) {
        String tt;
        String text;
        MessageClassification m;
        synchronized(data) {
            String effectiveCommand = data.setCommand(command);
            data.setStatus(defaultIfBlank(update.status(), "completed"));

            text = data.setText(new StringBuilder()
                    .append(isNotBlank(effectiveCommand) ? "$ " : "")
                    .append(abbreviate(effectiveCommand, 80))
                    .append(isNotBlank(effectiveCommand) ? "\n\n" : "")
                    .append(firstNonNull(extractContentText(update.content()), ""))
                    .toString());
            m = ToolDataExtractor.classify(update.update().type(), text, kind, update.update().title());
            tt = data.setTitle(ToolDataExtractor.extractToolTitle(defaultString(messageId), text, m, update));
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
}
