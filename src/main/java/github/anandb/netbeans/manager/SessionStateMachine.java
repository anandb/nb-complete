package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.support.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SessionStateMachine {
    private static final Logger LOG = new Logger(SessionStateMachine.class);

    private volatile SessionState state = SessionState.IDLE;
    private final List<Consumer<SessionState>> listeners = new CopyOnWriteArrayList<>();

    public SessionState getState() {
        return state;
    }

    public void addListener(Consumer<SessionState> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<SessionState> listener) {
        listeners.remove(listener);
    }

    public synchronized boolean transitionTo(SessionState newState) {
        SessionState current = this.state;
        if (!current.canTransitionTo(newState)) {
            LOG.warn("Invalid state transition: {0} -> {1}", new Object[]{current, newState});
            return false;
        }
        this.state = newState;
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
                LOG.warn("State listener threw exception: {0}", e.getMessage());
            }
        }
    }
}
