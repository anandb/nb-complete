package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionUpdate;
import java.util.List;

/** Listener for session lifecycle events. */
public interface SessionListener {

    /** Fired when the list of available sessions changes. */
    void onSessionListUpdated(List<Session> sessions);

    /** Fired when a new session has been created and is ready. */
    void onSessionStarted(String sessionId);

    /** Fired when a session finishes loading from the server. */
    void onSessionLoaded(String sessionId, List<SessionConfigOption> configOptions, boolean isStartup);

    /** Fired to show/hide a loading indicator. */
    void onSessionLoading(boolean isLoading);

    /** Fired when a session operation fails. */
    void onSessionError(String message);

    /** Fired for every SSE session/update notification. */
    void onSessionUpdate(SessionUpdate update);
}
