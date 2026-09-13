package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.SessionState;

/**
 * P0-002: Session state machine transitions.
 * Validates the state machine correctly handles all valid transitions
 * and rejects invalid ones.
 */
@Tag("P0")
@Tag("StateMachine")
class SessionStateMachineTest {

    private SessionStateMachine stateMachine;

    @BeforeAll
    static void checkEnvironment() {
        // Ensure we have enough threads for concurrent tests
        assumeTrue(Runtime.getRuntime().availableProcessors() >= 1,
                "Need at least 1 CPU core for state machine tests");
        // Ensure we have enough memory for concurrent tests
        Runtime runtime = Runtime.getRuntime();
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        assumeTrue(freeMemory > 10,
                "Need at least 10MB free memory for state machine tests");
    }

    @BeforeEach
    void setUp() {
        stateMachine = new SessionStateMachine();
    }

    @Test
    void initialStateIsIdle() {
        assertEquals(SessionState.IDLE, stateMachine.getState());
    }

    @Test
    void idleToLoading() {
        assertTrue(stateMachine.transitionTo(SessionState.LOADING));
        assertEquals(SessionState.LOADING, stateMachine.getState());
    }

    @Test
    void loadingToStreaming() {
        stateMachine.transitionTo(SessionState.LOADING);
        assertTrue(stateMachine.transitionTo(SessionState.STREAMING));
        assertEquals(SessionState.STREAMING, stateMachine.getState());
    }

    @Test
    void streamingToStopping() {
        stateMachine.transitionTo(SessionState.LOADING);
        stateMachine.transitionTo(SessionState.STREAMING);
        assertTrue(stateMachine.transitionTo(SessionState.STOPPING));
        assertEquals(SessionState.STOPPING, stateMachine.getState());
    }

    @Test
    void stoppingToIdle() {
        stateMachine.transitionTo(SessionState.LOADING);
        stateMachine.transitionTo(SessionState.STREAMING);
        stateMachine.transitionTo(SessionState.STOPPING);
        assertTrue(stateMachine.transitionTo(SessionState.IDLE));
        assertEquals(SessionState.IDLE, stateMachine.getState());
    }

    @Test
    void fullCycleIdleToIdle() {
        // IDLE -> LOADING -> STREAMING -> STOPPING -> IDLE
        assertTrue(stateMachine.transitionTo(SessionState.LOADING));
        assertTrue(stateMachine.transitionTo(SessionState.STREAMING));
        assertTrue(stateMachine.transitionTo(SessionState.STOPPING));
        assertTrue(stateMachine.transitionTo(SessionState.IDLE));
        assertEquals(SessionState.IDLE, stateMachine.getState());
    }

    @Test
    void loadingToIdleOnFailure() {
        stateMachine.transitionTo(SessionState.LOADING);
        assertTrue(stateMachine.transitionTo(SessionState.IDLE));
        assertEquals(SessionState.IDLE, stateMachine.getState());
    }

    @Test
    void streamingToIdleOnDisconnect() {
        stateMachine.transitionTo(SessionState.LOADING);
        stateMachine.transitionTo(SessionState.STREAMING);
        assertTrue(stateMachine.transitionTo(SessionState.IDLE));
        assertEquals(SessionState.IDLE, stateMachine.getState());
    }

    @Test
    void streamingToLoadingOnReload() {
        stateMachine.transitionTo(SessionState.LOADING);
        stateMachine.transitionTo(SessionState.STREAMING);
        assertTrue(stateMachine.transitionTo(SessionState.LOADING));
        assertEquals(SessionState.LOADING, stateMachine.getState());
    }

    @Test
    void stoppingToStreamingOnRecovery() {
        stateMachine.transitionTo(SessionState.LOADING);
        stateMachine.transitionTo(SessionState.STREAMING);
        stateMachine.transitionTo(SessionState.STOPPING);
        assertTrue(stateMachine.transitionTo(SessionState.STREAMING));
        assertEquals(SessionState.STREAMING, stateMachine.getState());
    }

    @Test
    void invalidTransitionIdleToStreaming() {
        assertFalse(stateMachine.transitionTo(SessionState.STREAMING));
        assertEquals(SessionState.IDLE, stateMachine.getState());
    }

    @Test
    void invalidTransitionIdleToStopping() {
        assertFalse(stateMachine.transitionTo(SessionState.STOPPING));
        assertEquals(SessionState.IDLE, stateMachine.getState());
    }

    @Test
    void invalidTransitionLoadingToStopping() {
        stateMachine.transitionTo(SessionState.LOADING);
        assertFalse(stateMachine.transitionTo(SessionState.STOPPING));
        assertEquals(SessionState.LOADING, stateMachine.getState());
    }

    @Test
    void invalidTransitionStreamingToIdleViaTransitionToIf() {
        stateMachine.transitionTo(SessionState.LOADING);
        stateMachine.transitionTo(SessionState.STREAMING);

        // Try to transition from LOADING (wrong expected state)
        assertFalse(stateMachine.transitionToIf(SessionState.LOADING, SessionState.IDLE));
        assertEquals(SessionState.STREAMING, stateMachine.getState());
    }

    @Test
    void transitionToIfWithCorrectExpectedState() {
        stateMachine.transitionTo(SessionState.LOADING);
        assertTrue(stateMachine.transitionToIf(SessionState.LOADING, SessionState.STREAMING));
        assertEquals(SessionState.STREAMING, stateMachine.getState());
    }

    @Test
    void listenerNotifiedOnTransition() {
        List<SessionState> receivedStates = new ArrayList<>();
        stateMachine.addListener(receivedStates::add);

        stateMachine.transitionTo(SessionState.LOADING);
        stateMachine.transitionTo(SessionState.STREAMING);

        assertEquals(2, receivedStates.size());
        assertEquals(SessionState.LOADING, receivedStates.get(0));
        assertEquals(SessionState.STREAMING, receivedStates.get(1));
    }

    @Test
    void listenerNotCalledOnInvalidTransition() {
        List<SessionState> receivedStates = new ArrayList<>();
        stateMachine.addListener(receivedStates::add);

        stateMachine.transitionTo(SessionState.STREAMING); // Invalid from IDLE

        assertTrue(receivedStates.isEmpty());
    }

    @Test
    void canSendMessageOnlyWhenStreaming() {
        assertFalse(stateMachine.canSendMessage());

        stateMachine.transitionTo(SessionState.LOADING);
        assertFalse(stateMachine.canSendMessage());

        stateMachine.transitionTo(SessionState.STREAMING);
        assertTrue(stateMachine.canSendMessage());

        stateMachine.transitionTo(SessionState.STOPPING);
        assertFalse(stateMachine.canSendMessage());
    }

    @Test
    void canLoadSessionWhenIdleOrStreaming() {
        assertTrue(stateMachine.canLoadSession());

        stateMachine.transitionTo(SessionState.LOADING);
        assertFalse(stateMachine.canLoadSession());

        stateMachine.transitionTo(SessionState.STREAMING);
        assertTrue(stateMachine.canLoadSession());

        stateMachine.transitionTo(SessionState.STOPPING);
        assertFalse(stateMachine.canLoadSession());
    }

    @Test
    void listenerExceptionDoesNotBreakTransition() {
        stateMachine.addListener(state -> {
            throw new RuntimeException("Listener error");
        });

        List<SessionState> secondListenerStates = new ArrayList<>();
        stateMachine.addListener(secondListenerStates::add);

        // Should not throw, and second listener should still be called
        assertTrue(stateMachine.transitionTo(SessionState.LOADING));
        assertEquals(1, secondListenerStates.size());
        assertEquals(SessionState.LOADING, secondListenerStates.get(0));
    }

    @Test
    void concurrentTransitionsAreThreadSafe() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);
        AtomicReference<SessionState> finalState = new AtomicReference<>();

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(5, TimeUnit.SECONDS);
                    stateMachine.transitionTo(SessionState.LOADING);
                    stateMachine.transitionTo(SessionState.STREAMING);
                    stateMachine.transitionTo(SessionState.STOPPING);
                    stateMachine.transitionTo(SessionState.IDLE);
                    finalState.set(stateMachine.getState());
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Final state should be IDLE (all threads completed the cycle)
        assertEquals(SessionState.IDLE, finalState.get());
    }
}
