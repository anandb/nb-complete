package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.Session;
import java.util.concurrent.CompletableFuture;

/**
 * Write operations for session lifecycle management.
 * UI layer should depend on this interface rather than the concrete SessionManager.
 */
public interface SessionControl extends SessionQuery {

    /** Registers a session lifecycle listener. */
    void addSessionListener(SessionListener listener);

    /** Unregisters a session lifecycle listener. */
    void removeSessionListener(SessionListener listener);

    /** Creates a new session in the given directory. */
    CompletableFuture<Session> createSession(String cwd);

    /** Loads an existing session by ID. */
    void loadSession(String sessionId);

    /** Loads an existing session, optionally marking it as a startup load. */
    void loadSession(String sessionId, boolean isStartup);

    /** Deletes a session by ID. */
    CompletableFuture<Void> deleteSession(String sessionId);

    /** Sets a configuration option for a session. */
    CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value);

    /** Renames a session. */
    void renameSession(String sessionId, String newTitle);

    /** Creates a new session in the given directory (convenience). */
    void createNewSession(String explicitCwd);

    /** Closes the current session. */
    void closeSession();

    /** Refreshes the session list from the server. */
    void refreshSessions();

    /** Re-filters the cached session list into the dropdown without querying server or reloading. */
    void refreshSessionList();

    /** Stops the current message stream. */
    void stopCurrentMessage();

    /** Notifies that the current turn has ended. */
    void onTurnEnded();

    /** Returns true if the state machine allows sending a new message. */
    boolean canSendMessage();

    /** Returns true if the state machine allows stopping the current message. */
    boolean canStopMessage();

    /** Forces the current message to cancel, transitioning directly to IDLE. */
    void forceCancelCurrentMessage();

    /** Toggles the hidden attribute for a session in local storage. */
    void setHidden(String sessionId, boolean hidden);
}
