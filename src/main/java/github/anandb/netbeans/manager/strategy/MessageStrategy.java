package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

public class MessageStrategy implements DataExtractionStrategy {
    private static final Logger LOG = new Logger(MessageStrategy.class);

    @Override
    public boolean canHandle(SessionUpdate update) {
        return "message".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        ProcessedMessage target = new ProcessedMessage();
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
        if (text.isEmpty()) {
            LOG.warn("MessageStrategy: empty text for msgId={0}, role={1}, skipping display",
                new Object[]{msg.id(), role});
            return;
        }
        target.setRole(role);
        target.setText(text);
        target.setMessageId(msg.id());
        target.setKind(null);
        target.setRawText(text);
        target.setStreaming(false);

        if (target.role() != null) {
            handler.displayMessage(target);
        }
    }

    private void appendParts(StringBuilder sb, java.util.List<Message.ContentPart> parts, String separator) {
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
