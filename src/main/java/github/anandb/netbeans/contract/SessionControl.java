package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

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

    /** Loads an existing session by ID. Returns true if load starts successfully. */
    boolean loadSession(String sessionId);

    /**
     * Whether this server process can load sessions at all. False once the agent
     * answered {@code session/load} with JSON-RPC "method not found" (some
     * harnesses advertise {@code loadSession} but do not implement it); the UI
     * then keeps the current session instead of selecting one that can never load.
     */
    boolean canLoadSessions();

    /** Loads an existing session, optionally marking it as a startup load. Returns true if load starts successfully. */
    boolean loadSession(String sessionId, boolean isStartup);

    /** Sets a configuration option for a session. */
    CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value);

    /** Switches the active agent mode for a session (ACP session/set_mode). */
    CompletableFuture<Void> setSessionMode(String sessionId, String modeId);

    /** Switches the model for a session (ACP session/set_model; Hermes-style agents). */
    CompletableFuture<Void> setSessionModel(String sessionId, String modelId);

    /** Renames a session. */
    void renameSession(String sessionId, String newTitle);

    /** Registers a handler invoked after session/new response but before the
     *  preamble is sent. The handler receives (sessionId, configOptions) and
     *  runs on the async thread — it MUST block until configuration is complete.
     *  Intended for showing a modal config dialog before preamble proceeds. */
    void setBeforePreambleHandler(
            BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> handler);

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

    /**
     * Hides or unhides several sessions in one go. The default delegates to
     * {@link #setHidden}; implementations should override it so a bulk change
     * costs one lock acquisition and one store write instead of N.
     */
    default void setHiddenBulk(Collection<String> sessionIds, boolean hidden) {
        if (sessionIds == null) {
            return;
        }
        for (String sessionId : sessionIds) {
            setHidden(sessionId, hidden);
        }
    }

    /** Persists context usage (used, size) for a session so it survives reload. */
    void setContextUsage(String sessionId, long used, long size);

    /** Schedules a manual reconnect prompt to be sent on next session load. */
    void scheduleManualReconnectPrompt();
}
