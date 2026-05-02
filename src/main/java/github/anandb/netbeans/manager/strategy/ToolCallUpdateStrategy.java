package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.ToolMetadataExtractor;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.defaultString;

public class ToolCallUpdateStrategy implements DataExtractionStrategy {
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
        String text = defaultIfBlank(extractContentText(update.content()), update.status());
        if (text == null) text = "";
        String tt = ToolMetadataExtractor.extractToolTitle(messageId, text, update.kind());
        target.setRole("tool");
        target.setText(text);
        target.setMessageId(defaultString(messageId));
        target.setKind(update.kind());
        target.setRawText(text);
        target.setToolTitle(tt);
        target.setStreaming(true);
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
