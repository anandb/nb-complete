package github.anandb.netbeans.manager.strategy;

import java.util.List;

import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionConfigOption;

public interface UIHandler {
    void displayMessage(ProcessedMessage pm);
    void updateConfig(List<SessionConfigOption> options);
    void refreshSessions();
    void updateUsage(long used, long size);
}
