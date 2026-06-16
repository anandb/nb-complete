package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionState;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only queries on the current session state.
 * UI layer should depend on this interface rather than the concrete SessionManager.
 */
public interface SessionQuery {

    /** Returns the current active session ID, or null if no session is active. */
    String getCurrentSessionId();

    /** Returns the working directory of the current session, or null. */
    String getCurrentSessionDirectory();

    /** Returns the current session state. */
    SessionState getCurrentState();

    /** Returns available config options for the given session. */
    CompletableFuture<List<SessionConfigOption>> loadSessionFromServer(String sessionId, String cwd);

    /** Returns a custom title for a session, falling back to the default. */
    String getCustomTitle(String sessionId, String defaultTitle);

    /** Returns true if the given sessionId is a sub-agent/descendant of the current active session. */
    boolean isDescendantOfCurrent(String sessionId);

    /** Returns true if the session is marked as hidden locally. */
    boolean isHidden(String sessionId);
}

