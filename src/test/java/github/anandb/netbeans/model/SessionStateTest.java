package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStateTest {

    @Test
    void idleCanOnlyTransitionToLoading() {
        assertTrue(SessionState.IDLE.canTransitionTo(SessionState.LOADING));
        assertFalse(SessionState.IDLE.canTransitionTo(SessionState.STREAMING));
        assertFalse(SessionState.IDLE.canTransitionTo(SessionState.STOPPING));
        assertFalse(SessionState.IDLE.canTransitionTo(SessionState.IDLE));
    }

    @Test
    void loadingCanTransitionToStreamingOrIdle() {
        assertTrue(SessionState.LOADING.canTransitionTo(SessionState.STREAMING));
        assertTrue(SessionState.LOADING.canTransitionTo(SessionState.IDLE));
        assertFalse(SessionState.LOADING.canTransitionTo(SessionState.LOADING));
        assertFalse(SessionState.LOADING.canTransitionTo(SessionState.STOPPING));
    }

    @Test
    void streamingCanTransitionToLoadingStoppingOrIdle() {
        assertTrue(SessionState.STREAMING.canTransitionTo(SessionState.LOADING));
        assertTrue(SessionState.STREAMING.canTransitionTo(SessionState.STOPPING));
        assertTrue(SessionState.STREAMING.canTransitionTo(SessionState.IDLE));
        assertFalse(SessionState.STREAMING.canTransitionTo(SessionState.STREAMING));
    }

    @Test
    void stoppingCanOnlyTransitionToStreamingOrIdle() {
        assertTrue(SessionState.STOPPING.canTransitionTo(SessionState.STREAMING));
        assertTrue(SessionState.STOPPING.canTransitionTo(SessionState.IDLE));
        assertFalse(SessionState.STOPPING.canTransitionTo(SessionState.LOADING));
        assertFalse(SessionState.STOPPING.canTransitionTo(SessionState.STOPPING));
    }

    @Test
    void canSendMessageOnlyInStreaming() {
        assertFalse(SessionState.IDLE.canSendMessage());
        assertFalse(SessionState.LOADING.canSendMessage());
        assertTrue(SessionState.STREAMING.canSendMessage());
        assertFalse(SessionState.STOPPING.canSendMessage());
    }

    @Test
    void canLoadSessionInIdleOrStreaming() {
        assertTrue(SessionState.IDLE.canLoadSession());
        assertFalse(SessionState.LOADING.canLoadSession());
        assertTrue(SessionState.STREAMING.canLoadSession());
        assertFalse(SessionState.STOPPING.canLoadSession());
    }

    @Test
    void canStopMessageOnlyInStreaming() {
        assertFalse(SessionState.IDLE.canStopMessage());
        assertFalse(SessionState.LOADING.canStopMessage());
        assertTrue(SessionState.STREAMING.canStopMessage());
        assertFalse(SessionState.STOPPING.canStopMessage());
    }

    @Test
    void isActiveExcludesIdle() {
        assertFalse(SessionState.IDLE.isActive());
        assertTrue(SessionState.LOADING.isActive());
        assertTrue(SessionState.STREAMING.isActive());
        assertTrue(SessionState.STOPPING.isActive());
    }
}
