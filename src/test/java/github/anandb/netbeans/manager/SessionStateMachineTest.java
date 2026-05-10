package github.anandb.netbeans.manager;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.SessionState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStateMachineTest {

    private SessionStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new SessionStateMachine();
    }

    @Test
    void initialStateIsIdle() {
        assertEquals(SessionState.IDLE, machine.getState());
    }

    @Test
    void validTransitionFromIdleToLoading() {
        assertTrue(machine.transitionTo(SessionState.LOADING));
        assertEquals(SessionState.LOADING, machine.getState());
    }

    @Test
    void invalidTransitionFromIdleToStreaming() {
        assertFalse(machine.transitionTo(SessionState.STREAMING));
        assertEquals(SessionState.IDLE, machine.getState());
    }

    @Test
    void fullSessionLifecycle() {
        assertTrue(machine.transitionTo(SessionState.LOADING));
        assertTrue(machine.transitionTo(SessionState.STREAMING));
        assertEquals(SessionState.STREAMING, machine.getState());
    }

    @Test
    void loadingErrorReturnsToIdle() {
        assertTrue(machine.transitionTo(SessionState.LOADING));
        assertTrue(machine.transitionTo(SessionState.IDLE));
        assertEquals(SessionState.IDLE, machine.getState());
    }

    @Test
    void sessionSwitchFromStreaming() {
        machine.transitionTo(SessionState.LOADING);
        machine.transitionTo(SessionState.STREAMING);
        assertTrue(machine.transitionTo(SessionState.LOADING));
        assertEquals(SessionState.LOADING, machine.getState());
    }

    @Test
    void stopMessageFlow() {
        machine.transitionTo(SessionState.LOADING);
        machine.transitionTo(SessionState.STREAMING);
        assertTrue(machine.transitionTo(SessionState.STOPPING));
        assertTrue(machine.transitionTo(SessionState.STREAMING));
        assertEquals(SessionState.STREAMING, machine.getState());
    }

    @Test
    void closeSessionFromStreaming() {
        machine.transitionTo(SessionState.LOADING);
        machine.transitionTo(SessionState.STREAMING);
        assertTrue(machine.transitionTo(SessionState.IDLE));
        assertEquals(SessionState.IDLE, machine.getState());
    }

    @Test
    void cannotSendMessageWhileLoading() {
        machine.transitionTo(SessionState.LOADING);
        assertFalse(machine.canSendMessage());
    }

    @Test
    void canSendMessageWhileStreaming() {
        machine.transitionTo(SessionState.LOADING);
        machine.transitionTo(SessionState.STREAMING);
        assertTrue(machine.canSendMessage());
    }

    @Test
    void cannotLoadSessionWhileLoading() {
        machine.transitionTo(SessionState.LOADING);
        assertFalse(machine.canLoadSession());
    }

    @Test
    void cannotLoadSessionWhileStopping() {
        machine.transitionTo(SessionState.LOADING);
        machine.transitionTo(SessionState.STREAMING);
        machine.transitionTo(SessionState.STOPPING);
        assertFalse(machine.canLoadSession());
    }

    @Test
    void listenerReceivesStateChanges() {
        List<SessionState> observed = new ArrayList<>();
        machine.addListener(observed::add);

        machine.transitionTo(SessionState.LOADING);
        machine.transitionTo(SessionState.STREAMING);

        assertEquals(List.of(SessionState.LOADING, SessionState.STREAMING), observed);
    }

    @Test
    void listenerNotCalledOnInvalidTransition() {
        List<SessionState> observed = new ArrayList<>();
        machine.addListener(observed::add);

        machine.transitionTo(SessionState.STREAMING); // invalid from IDLE

        assertTrue(observed.isEmpty());
    }

    @Test
    void removedListenerNoLongerReceivesEvents() {
        List<SessionState> observed = new ArrayList<>();
        java.util.function.Consumer<SessionState> listener = observed::add;
        machine.addListener(listener);
        machine.transitionTo(SessionState.LOADING);

        machine.removeListener(listener);
        machine.transitionTo(SessionState.STREAMING);

        assertEquals(List.of(SessionState.LOADING), observed);
    }
}
