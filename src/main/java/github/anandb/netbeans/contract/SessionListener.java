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

    /** Fired to report session loading progress (0-100). */
    default void onSessionProgress(int percent) {}

    /** Fired when a session operation fails. */
    void onSessionError(String message);

    /** Fired for every SSE session/update notification. */
    void onSessionUpdate(SessionUpdate update);

    /** Fired when the preamble has been sent (or skipped because empty).
     *  Only called for new sessions. Used to hide the session-loading
     *  progress bar once the preamble response completes. */
    default void onPreambleDone() {}

    /** Fired when the preamble starts being posted. UI should show a
     *  bubble indicating that the preamble is being sent.
     * @param sessionId the session posting it; consumers that render into the
     *                  thread must re-check it on the EDT, since the user can
     *                  switch sessions before the callback runs. */
    default void onPreambleStarted(String sessionId) {}

    /** Fired when a new session was created but no startup prompt was sent
     * (preamble disabled or empty). Only called for new sessions — never on
     * reload. UI should show a session-started indicator so the empty thread
     * still confirms the session is live.
     * @param sessionId the session whose preamble was skipped; consumers that
     *                  render into the thread must re-check it on the EDT, since
     *                  the user can switch sessions before the callback runs. */
    default void onPreambleSkipped(String sessionId) {}

    /** Fired when a session has been renamed locally. UI should update the
     *  dropdown display without reloading the entire conversation. */
    default void onSessionRenamed(String sessionId) {}

    /** Fired when all open projects have been closed. The UI should clear
     *  the input area since there is no active project context. */
    default void onAllProjectsClosed() {}

    /** Fired when an internal message (e.g. warmup, preamble) is sent. */
    default void onInternalMessageSent() {}

    /** Fired when an internal message completes. */
    default void onInternalMessageDone() {}
}
