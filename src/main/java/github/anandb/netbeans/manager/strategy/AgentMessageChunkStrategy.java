package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.manager.ToolMetadataExtractor;
import github.anandb.netbeans.manager.ToolParamsExtractor;
import github.anandb.netbeans.model.MessageClassification;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

public class AgentMessageChunkStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(AgentMessageChunkStrategy.class);
    @Override
    public boolean canHandle(SessionUpdate update) {
        return "agent_message_chunk".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, ProcessedMessage target, UIHandler handler) {
        String msgId = update.messageId();
        String text = extractText(update.content());

        MessageClassification classification = ToolParamsExtractor.classify("assistant", text, update.kind());
        target.setRole(classification.role());
        target.setText(text);
        target.setMessageId(msgId);
        target.setKind(classification.kind());
        target.setRawText(text);
        target.setStreaming(true);

        if ("tool".equals(classification.role())) {
            target.setToolTitle(ToolMetadataExtractor.extractToolTitle(msgId, text, update.kind()));
        }

        if (target.role() != null) {
            handler.displayMessage(target);
        }
    }

    static String extractText(JsonNode content) {
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

}
