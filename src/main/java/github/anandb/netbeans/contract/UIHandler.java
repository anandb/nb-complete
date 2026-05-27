package github.anandb.netbeans.contract;

import java.util.List;

import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionConfigOption;

/** Receives processed content and dispatches to UI thread for rendering. */
public interface UIHandler {

    void displayMessage(ProcessedMessage pm);

    void updateConfig(List<SessionConfigOption> options);

    void refreshSessions();

    void updateUsage(long used, long size);
}
