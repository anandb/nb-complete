package github.anandb.netbeans.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionUpdate;

/**
 * Listener registry and fan-out for session lifecycle events.
 * Extracted from SessionManager so the facade only owns state transitions.
 */
final class SessionEventBus {

    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();

    void add(SessionListener listener) {
        listeners.add(listener);
    }

    void remove(SessionListener listener) {
        listeners.remove(listener);
    }

    void sessionLoading(boolean loading) {
        snapshot().forEach(l -> l.onSessionLoading(loading));
    }

    void sessionUpdate(SessionUpdate update) {
        snapshot().forEach(l -> l.onSessionUpdate(update));
    }

    void sessionListUpdated(List<Session> sessions) {
        snapshot().forEach(l -> l.onSessionListUpdated(sessions));
    }

    void sessionStarted(String sessionId) {
        snapshot().forEach(l -> l.onSessionStarted(sessionId));
    }

    void sessionLoaded(String sessionId, List<SessionConfigOption> options, boolean isStartup) {
        snapshot().forEach(l -> l.onSessionLoaded(sessionId, options, isStartup));
    }

    void sessionProgress(int percent) {
        snapshot().forEach(l -> l.onSessionProgress(percent));
    }

    void sessionError(String message) {
        snapshot().forEach(l -> l.onSessionError(message));
    }

    void sessionRenamed(String sessionId) {
        snapshot().forEach(l -> l.onSessionRenamed(sessionId));
    }

    void allProjectsClosed() {
        snapshot().forEach(SessionListener::onAllProjectsClosed);
    }

    void preambleStarted(String sessionId) {
        snapshot().forEach(l -> l.onPreambleStarted(sessionId));
    }

    void preambleSkipped(String sessionId) {
        snapshot().forEach(l -> l.onPreambleSkipped(sessionId));
    }

    void preambleDone() {
        snapshot().forEach(SessionListener::onPreambleDone);
    }

    void internalMessageSent() {
        snapshot().forEach(SessionListener::onInternalMessageSent);
    }

    void internalMessageDone() {
        snapshot().forEach(SessionListener::onInternalMessageDone);
    }

    /** A copy keeps a listener that mutates the registry from causing a CME mid-fan-out. */
    private List<SessionListener> snapshot() {
        return new ArrayList<>(listeners);
    }
}
