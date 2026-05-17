package github.anandb.netbeans.manager.strategy;

import java.util.List;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class MessageStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(MessageStrategy.class);

    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return "message".equals(reclassifiedType);
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        Message msg = update.message();
        if (msg == null) return;

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
            LOG.warn("MessageStrategy: empty text for msgId={0}, role={1}, skipping display",
                new Object[]{msg.id(), role});
            return;
        }

        MessageType msgType = "user".equals(role) ? MessageType.user_message_chunk : MessageType.agent_message_chunk;
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
}
