package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.support.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SessionStateMachine {
    private static final Logger LOG = Logger.from(SessionStateMachine.class);

    private final List<Consumer<SessionState>> listeners = new CopyOnWriteArrayList<>();
    private final Object _lock = new Object();
    private volatile SessionState state = SessionState.IDLE;

    public SessionState getState() {
        return state;
    }

    public void addListener(Consumer<SessionState> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<SessionState> listener) {
        listeners.remove(listener);
    }

    public boolean transitionTo(SessionState newState) {
        return doTransition(null, newState);
    }

    public boolean transitionToIf(SessionState expectedCurrent, SessionState newState) {
        return doTransition(expectedCurrent, newState);
    }

    public boolean doTransition(SessionState expectedCurrent, SessionState newState) {
        SessionState current;
        synchronized(_lock) {
            current = this.state;
            if (expectedCurrent != null && current != expectedCurrent) {
                LOG.fine("Skipping Transition to {0}: expected current {1} but was {2}",
                        new Object[]{newState, expectedCurrent, current});
                return false;
            }

            if (!current.canTransitionTo(newState)) {
                LOG.warn("Invalid state transition: {0} -> {1}", new Object[]{current, newState});
                return false;
            }

            this.state = newState;
        }

        // Notify outside the monitor: listener callbacks run arbitrary code
        // (and may acquire other locks, so holding onto the lock here risks deadlock.
        LOG.fine("State transition: {0} -> {1}", new Object[]{current, newState});
        notifyListeners(newState);
        return true;
    }

    public boolean canSendMessage() {
        return state.canSendMessage();
    }

    public boolean canLoadSession() {
        return state.canLoadSession();
    }

    public boolean canStopMessage() {
        return state.canStopMessage();
    }

    private void notifyListeners(SessionState newState) {
        for (Consumer<SessionState> listener : listeners) {
            try {
                listener.accept(newState);
            } catch (Exception e) {
                LOG.warn("State listener threw exception: {0}", e.getMessage(), e);
            }
        }
    }
}
