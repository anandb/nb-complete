package github.anandb.netbeans.manager.strategy;

import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.SessionUpdate;

public class UsageUpdateStrategy implements DataExtractionStrategy {
    @Override
    public boolean canHandle(SessionUpdate update) {
        return "usage_update".equals(update.type());
    }

    @Override
    public void extract(SessionUpdate update, UIHandler handler) {
        if (update.update() != null) {
            handler.updateUsage(update.update().used(), update.update().size());
        }
    }
}
