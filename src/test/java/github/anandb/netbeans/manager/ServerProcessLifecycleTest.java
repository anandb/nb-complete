package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.model.AgentCapabilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServerProcessLifecycleTest {

    private AtomicReference<AcpProtocolClient> rpcClient;
    private ServerProcessLifecycle lifecycle;
    private final AtomicBoolean readyCalled = new AtomicBoolean(false);
    private final AtomicBoolean disconnectionCalled = new AtomicBoolean(false);

    @BeforeEach
    void setUp() {
        rpcClient = new AtomicReference<>();
        lifecycle = new ServerProcessLifecycle(
                rpcClient,
                new NoOpToolExecutor(),
                () -> readyCalled.set(true),
                u -> {},
                () -> disconnectionCalled.set(true),
                reason -> {},
                params -> CompletableFuture.completedFuture(null),
                params -> CompletableFuture.completedFuture(null),
                params -> CompletableFuture.completedFuture(null)
        );
    }

    @Test
    void initialState() {
        assertFalse(lifecycle.isClosing());
        assertFalse(lifecycle.serverStarted());
        assertNull(lifecycle.serverProcess());
        assertNull(lifecycle.getAgentName());
        assertNotNull(lifecycle.getCapabilities());
        assertEquals(AgentCapabilities.DEFAULT, lifecycle.getCapabilities());
    }

    @Test
    void readyFutureIsNonNull() {
        assertNotNull(lifecycle.readyFuture());
    }

    @Test
    void stopServerSetsClosing() {
        lifecycle.stopServer();
        assertTrue(lifecycle.isClosing());
    }

    @Test
    void stopServerTwiceDoesNotThrow() {
        lifecycle.stopServer();
        lifecycle.stopServer();
        assertTrue(lifecycle.isClosing());
    }

    @Test
    void getAgentNameReturnsNullBeforeInit() {
        assertNull(lifecycle.getAgentName());
    }

    @Test
    void setAgentNameListenerDoesNotThrow() {
        lifecycle.setAgentNameListener(cap -> {});
    }

    @Test
    void restartServerAfterStopWorks() {
        lifecycle.stopServer();
        assertTrue(lifecycle.isClosing());
        // restartServer should reset isClosing and attempt start
        // (will fail because no real process, but state should be updated)
        lifecycle.restartServer();
        // After restart, isClosing should be false (reset before startServer)
        assertFalse(lifecycle.isClosing());
    }

    @Test
    void ensureStartedIsIdempotent() {
        // First call sets serverStarted = true and calls startServer
        // (which will fail in test env, but serverStarted flag is set before the try)
        lifecycle.ensureStarted();
        // Second call should be a no-op
        lifecycle.ensureStarted();
    }

    @Test
    void reconnectRPAndTaskManagement() {
        assertNull(lifecycle.reconnectRP());
        lifecycle.setReconnectTask(null);
        // No exception
    }

    /** Minimal ToolExecutor that does nothing. */
    private static class NoOpToolExecutor implements github.anandb.netbeans.contract.ToolExecutor {
        @Override public void start() {}
        @Override public void setMcpAuthRequired(boolean required) {}
        @Override public void stop() {}
        @Override public CompletableFuture<Void> waitForReady() { return CompletableFuture.completedFuture(null); }
        @Override public void disable() {}
        @Override public boolean isDisabled() { return false; }
        @Override public void checkServerSupport(JsonNode r) {}
        @Override public java.util.List<java.util.Map<String, Object>> getServerConfig() { return java.util.List.of(); }
    }
}
