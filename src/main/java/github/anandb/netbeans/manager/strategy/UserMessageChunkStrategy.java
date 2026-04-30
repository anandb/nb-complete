package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

public class UserMessageChunkStrategy implements DataExtractionStrategy {
    @Override
    public boolean canHandle(SessionUpdate update) {
        return "user_message_chunk".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, ProcessedMessage target, UIHandler handler) {
        target.setRole("user");
        target.setText(AgentMessageChunkStrategy.extractText(update.content()));
        target.setMessageId(update.messageId());
        target.setKind(update.kind());
        target.setRawText(target.text());
        target.setStreaming(true);
        handler.displayMessage(target);
    }
}
