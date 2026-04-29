package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.manager.ToolMetadataExtractor;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

public class DataChunkReclassifyStrategy implements DataExtractionStrategy {
    @Override
    public boolean canHandle(SessionUpdate update) {
        return "agent_data_chunk".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, ProcessedMessage target, UIHandler handler) {
        String msgId = update.messageId();
        String text = AgentMessageChunkStrategy.extractText(update.content());
        String tt = ToolMetadataExtractor.extractToolTitle(msgId, text, update.kind());
        target.setRole("tool");
        target.setText(text);
        target.setMessageId(msgId);
        target.setKind("data_chunk");
        target.setRawText(text);
        target.setToolTitle(tt);
        target.setStreaming(true);
        handler.displayMessage(target);
    }
}
