package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

public class SessionInfoUpdateStrategy implements DataExtractionStrategy {
    @Override
    public boolean canHandle(SessionUpdate update) {
        return "session_info_update".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, ProcessedMessage target, UIHandler handler) {
        handler.refreshSessions();
    }
}
