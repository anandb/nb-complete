package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

public class AgentMessageChunkStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(AgentMessageChunkStrategy.class);
    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return "agent_message_chunk".equals(reclassifiedType);
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        String msgId = update.messageId();
        String text = extractText(update.content());
        ProcessedMessage target = new ProcessedMessage();
        target.setMessageType(MessageType.valueOf(update.type()));
        target.setText(text);
        target.setMessageId(msgId);
        target.setKind(update.kind());
        target.setRawText(text);
        target.setStreaming(true);

        if (target.messageType() != null) {
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
