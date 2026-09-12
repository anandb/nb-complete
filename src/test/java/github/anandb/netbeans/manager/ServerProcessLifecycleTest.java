package github.anandb.netbeans.manager;

import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.model.HarnessCatalog;
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
        assertNotNull(lifecycle.getCapabilities());
        assertEquals(HarnessCatalog.UNKNOWN, lifecycle.getCapabilities());
    }

    @Test
    void readyFutureIsNonNull() {
        assertNotNull(lifecycle.readyFuture());
    }

    @Test
    void stopServerResetsCapabilities() throws Exception {
        java.lang.reflect.Field field = ServerProcessLifecycle.class.getDeclaredField("capabilities");
        field.setAccessible(true);
        field.set(lifecycle, HarnessCatalog.GEMINI);
        assertEquals(HarnessCatalog.GEMINI, lifecycle.getCapabilities());

        lifecycle.stopServer();

        assertEquals(HarnessCatalog.UNKNOWN, lifecycle.getCapabilities());
    }

    @Test
    void stopServerTwiceDoesNotThrow() {
        lifecycle.stopServer();
        lifecycle.stopServer();
        assertTrue(lifecycle.isClosing());
    }

    @Test
    void setHarnessListenerDoesNotThrow() {
        lifecycle.setHarnessListener(cap -> {});
    }

    @Test
    void restartServerAfterStopWorks() {
        lifecycle.stopServer();
        assertTrue(lifecycle.isClosing());
        lifecycle.restartServer();
        assertFalse(lifecycle.isClosing());
    }

    @Test
    void ensureStartedIsIdempotent() {
        lifecycle.ensureStarted();
        lifecycle.ensureStarted();
    }

    @Test
    void reconnectRPAndTaskManagement() {
        assertNull(lifecycle.reconnectRP());
        lifecycle.setReconnectTask(null);
    }

    /** Minimal ToolExecutor that does nothing. */
    private static class NoOpToolExecutor implements ToolExecutor {
        @Override public void start() {}
        @Override public void setMcpAuthRequired(boolean required) {}
        @Override public void stop() {}
        @Override public CompletableFuture<Void> waitForReady() { return CompletableFuture.completedFuture(null); }
        @Override public void disable() {}
        @Override public boolean isDisabled() { return false; }
        @Override public void checkServerSupport(HarnessCatalog.Harness c) {}
        @Override public java.util.List<java.util.Map<String, Object>> getServerConfig() { return java.util.List.of(); }
    }
}
