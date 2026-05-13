package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.ToolDataExtractor;
import github.anandb.netbeans.model.MessageClassification;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ToolCallUpdateStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(ToolCallUpdateStrategy.class);

    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return "tool_call_update".equals(reclassifiedType) || "tool_call".equals(reclassifiedType);
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        if (isPlanToolCall(update)) {
            return;
        }

        String messageId = update.messageId() != null ? update.messageId() : update.toolCallId();
        String command = update.update().rawInput() != null ? defaultString(update.update().rawInput().command()) : "";
        String text = new StringBuilder()
                      .append(abbreviate(command, 80))
                      .append(isNotBlank(command) ? "$ " : "")
                      .append(isNotBlank(command) ? "\n\n" : "")
                      .append(firstNonNull(extractContentText(update.content()), update.status(), ""))
                      .toString();

        MessageClassification m = ToolDataExtractor.classify(update.update().type(), text, update.kind());
        String tt = ToolDataExtractor.extractToolTitle(defaultString(messageId), text, m, update);

        ProcessedMessage target = new ProcessedMessage.Builder()
                .messageType(m.type())
                .text(text)
                .messageId(defaultString(messageId))
                .kind(update.kind())
                .toolTitle(tt)
                .rawText(text)
                .streaming(true)
                .build();
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
}
