package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.SessionUpdate;

public class SessionInfoUpdateStrategy implements DataExtractionStrategy {
    @Override
    public boolean canHandle(SessionUpdate update, String reclassifiedType) {
        return "session_info_update".equals(reclassifiedType);
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        handler.refreshSessions();
    }
}
