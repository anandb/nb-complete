package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionUpdate;
import java.util.List;

/**
 * Listener for session lifecycle events.
 * Receives notifications when sessions are created, loaded, updated, or encounter errors.
 */
public interface SessionListener {

    /**
     * Fired when the list of available sessions changes.
     *
     * @param sessions the updated list of sessions
     */
    void onSessionListUpdated(List<Session> sessions);

    /**
     * Fired when a new session has been created and is ready.
     *
     * @param sessionId the created session id
     */
    void onSessionStarted(String sessionId);

    /**
     * Fired when a session finishes loading from the server, delivering initial config options.
     *
     * @param sessionId    the session id
     * @param configOptions the initial configuration options
     * @param isStartup    whether this load occurred at startup
     */
    void onSessionLoaded(String sessionId, List<SessionConfigOption> configOptions, boolean isStartup);

    /**
     * Fired to show/hide a loading indicator.
     *
     * @param isLoading true to show loading, false to hide
     */
    void onSessionLoading(boolean isLoading);

    /**
     * Fired when a session operation (create, load, delete) fails.
     *
     * @param message the error message
     */
    void onSessionError(String message);

    /**
     * Fired for every SSE session/update notification from the server.
     *
     * @param update the session update payload
     */
    void onSessionUpdate(SessionUpdate update);
}
