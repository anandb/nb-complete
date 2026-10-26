package github.anandb.netbeans.manager;

import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.BinaryResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    private MockedStatic<BinaryResolver> binaryMock;

    @BeforeEach
    void setUp() {
        // Never spawn the binary configured in the shared test user directory:
        // these tests exercise lifecycle state, and a real process would make
        // ensureStartedIn() non-deterministic and leak children.
        binaryMock = Mockito.mockStatic(BinaryResolver.class);
        binaryMock.when(BinaryResolver::resolveExecutablePath)
                .thenThrow(new IllegalStateException("no agent binary in unit tests"));
        binaryMock.when(BinaryResolver::isWslAvailable).thenReturn(false);
        binaryMock.when(BinaryResolver::isPiHarness).thenReturn(false);
        binaryMock.when(BinaryResolver::resolveBinaryName).thenReturn("test-agent");
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

    @AfterEach
    void tearDown() {
        if (binaryMock != null) {
            binaryMock.close();
        }
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
    void sameDirectoryResolvesDotSegmentsAndSymlinks() throws Exception {
        File dir = Files.createTempDirectory("acp-cwd").toFile();
        assertTrue(ServerProcessLifecycle.sameDirectory(dir, new File(dir, ".")),
                "a redundant dot segment must not trigger a restart");
        assertFalse(ServerProcessLifecycle.sameDirectory(dir, new File(dir, "other")));
        assertTrue(ServerProcessLifecycle.sameDirectory(null, null));
        assertFalse(ServerProcessLifecycle.sameDirectory(dir, null));
    }

    @Test
    void ensureStartedInBindsAndClearsAVanishedDirectory() throws Exception {
        File dir = Files.createTempDirectory("acp-cwd").toFile();
        lifecycle.ensureStartedIn(dir);
        assertEquals(dir.getAbsoluteFile(), lifecycle.boundWorkDir());

        // The project directory no longer exists: the binding must be cleared,
        // or every later start retries a path ProcessBuilder cannot use.
        lifecycle.ensureStartedIn(new File(dir, "gone"));
        assertNull(lifecycle.boundWorkDir());
    }

    @Test
    void restartInstallsAFreshReadyFutureAndFailsThePrevious() {
        CompletableFuture<Void> previous = lifecycle.readyFuture();
        lifecycle.restartServer();
        assertNotSame(previous, lifecycle.readyFuture(),
                "a restart must not let the old initialize complete the new generation's future");
        assertTrue(previous.isCompletedExceptionally(),
                "waiters of the replaced generation must be unblocked, not left hanging");
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
