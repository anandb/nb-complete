package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.ToolMetadataExtractor;
import github.anandb.netbeans.manager.ToolParamsExtractor;
import github.anandb.netbeans.model.MessageClassification;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.defaultString;

public class ToolCallUpdateStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(ToolCallUpdateStrategy.class);

    @Override
    public boolean canHandle(SessionUpdate update) {
        return "tool_call_update".equals(update.type()) || "tool_call".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        if (isPlanToolCall(update)) {
            return;
        }

        ProcessedMessage target = new ProcessedMessage();
        String messageId = update.messageId() != null ? update.messageId() : update.toolCallId();
        String text = firstNonNull(extractContentText(update.content()), update.status(), "");
        target.setText(text);
        target.setMessageId(defaultString(messageId));
        target.setKind(update.kind());
        target.setRawText(text);
        target.setStreaming(true);
        //todo: avoid calling multiple times.
        MessageClassification m = ToolParamsExtractor.classify(update.update().type(),
                                                               text, update.kind());
        String tt = ToolMetadataExtractor.extractToolTitle(target, m.kind());
        target.setToolTitle(tt);
        target.setMessageType(m.type());
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
        String outText = output.asText().trim();
        if (!outText.startsWith("[")) return false;
        return outText.contains("\"content\"") && outText.contains("\"status\"");
    }
}
