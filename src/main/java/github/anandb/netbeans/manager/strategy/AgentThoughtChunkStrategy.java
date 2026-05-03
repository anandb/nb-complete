package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

public class AgentThoughtChunkStrategy implements DataExtractionStrategy {
    @Override
    public boolean canHandle(SessionUpdate update) {
        return "agent_thought_chunk".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        ProcessedMessage target = new ProcessedMessage();
        target.setMessageType(MessageType.valueOf(update.type()));
        target.setText(AgentMessageChunkStrategy.extractText(update.content()));
        target.setMessageId(update.messageId());
        target.setKind(update.kind());
        target.setRawText(target.text());
        target.setStreaming(true);
        handler.displayMessage(target);
    }
}
