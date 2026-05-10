package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class UserMessageChunkStrategy implements DataExtractionStrategy {
    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return "user_message_chunk".equals(reclassifiedType);
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        String text = AgentMessageChunkStrategy.extractText(update.content());
        if (isBlank(text)) {
            return;
        }
        ProcessedMessage target = new ProcessedMessage.Builder()
                .messageType(MessageType.valueOf(update.type()))
                .text(text)
                .messageId(update.messageId())
                .kind(update.kind())
                .rawText(text)
                .streaming(true)
                .build();
        handler.displayMessage(target);
    }
}
