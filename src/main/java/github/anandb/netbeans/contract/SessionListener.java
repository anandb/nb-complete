package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionUpdate;
import java.util.List;

public interface SessionListener {
    void onSessionListUpdated(List<Session> sessions);
    void onSessionStarted(String sessionId);
    void onSessionLoaded(String sessionId, List<SessionConfigOption> configOptions, boolean isStartup);
    void onSessionLoading(boolean isLoading);
    void onSessionError(String message);
    void onSessionUpdate(SessionUpdate update);
}
