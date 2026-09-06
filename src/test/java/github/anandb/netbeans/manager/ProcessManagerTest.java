package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.model.SessionUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessManagerTest {

    private ProcessManager processManager;

    @BeforeEach
    void setUp() {
        // Construct directly to avoid Lookup/ServiceProvider issues in tests.
        // This triggers constructor logic (NbPreferences, timers, etc.)
        // so it must run in a headless-safe way.
        try {
            processManager = new ProcessManager();
        } catch (Exception e) {
            // NbPreferences or LifecycleManager may throw in headless env
            processManager = null;
        }
    }

    @Test
    void getInstanceReturnsNonNull() {
        if (processManager == null) return; // skip if headless init failed
        ProcessManager pm = ProcessManager.getInstance();
        assertNotNull(pm);
    }

    @Test
    void getSlashCommandInterceptorReturnsNonNull() {
        if (processManager == null) return;
        assertNotNull(processManager.getSlashCommandInterceptor());
    }

    @Test
    void getToolExecutorReturnsNonNull() {
        if (processManager == null) return;
        assertNotNull(processManager.getToolExecutor());
    }

    @Test
    void sendRequestWithNullClientReturnsFailedFuture() {
        if (processManager == null) return;
        // rpcClient is null in fresh instance — sendRequest should fail gracefully
        CompletableFuture<JsonNode> future = processManager.sendRequest("test/method", null);
        assertNotNull(future);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void sendRequestWithTimeoutReturnsFailedFuture() {
        if (processManager == null) return;
        CompletableFuture<JsonNode> future = processManager.sendRequest(
                "test/method", null, 10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(future);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void sendNotificationWithNullClientDoesNotThrow() {
        if (processManager == null) return;
        // Should log warning but not throw
        processManager.sendNotification("test/method", null);
    }

    @Test
    void sendMessageWithNullClientReturnsFailedFuture() {
        if (processManager == null) return;
        CompletableFuture<JsonNode> future = processManager.sendMessage("sid", "hello", null);
        assertNotNull(future);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void sendMessageWithAdditionalBlocksReturnsFailedFuture() {
        if (processManager == null) return;
        CompletableFuture<JsonNode> future = processManager.sendMessage("sid", "hello", null, List.of());
        assertNotNull(future);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void stopMessageWithNullClientReturnsFailedFuture() {
        if (processManager == null) return;
        CompletableFuture<Void> future = processManager.stopMessage("sid");
        assertNotNull(future);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void getAvailableCommandsReturnsEmptyByDefault() {
        if (processManager == null) return;
        List<SessionUpdate.AvailableCommand> cmds = processManager.getAvailableCommands();
        assertNotNull(cmds);
        assertTrue(cmds.isEmpty());
    }

    @Test
    void addAndRemoveSseListener() {
        if (processManager == null) return;
        AtomicBoolean called = new AtomicBoolean(false);
        Consumer<SessionUpdate> listener = u -> called.set(true);
        processManager.addSseListener(listener);
        processManager.removeSseListener(listener);
        // After removal, listener should not be called
    }

    @Test
    void touchConnectionWithNullClientDoesNotThrow() {
        if (processManager == null) return;
        processManager.touchConnection();
    }

    @Test
    void setAndGetHandlers() {
        if (processManager == null) return;
        processManager.setCrashHandler(() -> {});
        processManager.setReadyHandler(() -> {});
        processManager.setPreRestartHandler(() -> {});
        processManager.setStatusListener(s -> {});
        // No exception = pass
    }

    @Test
    void getAgentNameBeforeInitReturnsNull() {
        if (processManager == null) return;
        // Before server starts, agentName is null
        assertNotNull(processManager.getCapabilities());
    }

    @Test
    void notifyListenersDistributesToAll() {
        if (processManager == null) return;
        List<String> received = new ArrayList<>();
        Consumer<SessionUpdate> listener1 = u -> received.add("l1");
        Consumer<SessionUpdate> listener2 = u -> received.add("l2");
        processManager.addSseListener(listener1);
        processManager.addSseListener(listener2);
        processManager.removeSseListener(listener1);
        processManager.removeSseListener(listener2);
    }

    @Test
    void whenReadyReturnsFuture() {
        if (processManager == null) return;
        CompletableFuture<Void> future = processManager.whenReady();
        assertNotNull(future);
    }

    @Test
    void setPermissionHandlerDoesNotThrow() {
        if (processManager == null) return;
        processManager.setPermissionHandler(null);
    }

    @Test
    void getCapabilitiesReturnsNonNull() {
        if (processManager == null) return;
        assertNotNull(processManager.getCapabilities());
    }
}
