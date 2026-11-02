package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.MapperSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionRpcClientTest {

    @Mock
    private ProcessManager processManager;

    private SessionRpcClient client;
    private final ObjectMapper mapper = MapperSupplier.get();

    @BeforeEach
    void setUp() {
        client = new SessionRpcClient(processManager);
    }

    @Test
    void getSessionsCallsCorrectMethod() {
        when(processManager.sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.getSessions("/test/dir");
        verify(processManager).sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void getSessionsWithNullDirectory() {
        when(processManager.sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.getSessions(null);
        verify(processManager).sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void createSessionIncludesMcpServersWhenCapabilityEnabled() {
        when(processManager.getCapabilities()).thenReturn(HarnessCatalog.UNKNOWN);
        when(processManager.getToolExecutor()).thenReturn(createMockToolExecutor());
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.createSession("/project");
        verify(processManager).sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void loadSessionFromServerCallsCorrectMethod() {
        when(processManager.getCapabilities()).thenReturn(HarnessCatalog.UNKNOWN);
        when(processManager.getToolExecutor()).thenReturn(createMockToolExecutor());
        when(processManager.sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.loadSessionFromServer("sid", "/cwd");
        verify(processManager).sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES));
    }

    private ToolExecutor createMockToolExecutor() {
        return new ToolExecutor() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public CompletableFuture<Void> waitForReady() { return CompletableFuture.completedFuture(null); }
            @Override public java.util.List<Map<String, Object>> getServerConfig() { return java.util.List.of(); }
            @Override public void checkServerSupport(HarnessCatalog.Harness caps) {}
            @Override public void setMcpAuthRequired(boolean required) {}
            @Override public void disable() {}
            @Override public boolean isDisabled() { return false; }
        };
    }


    @Test
    void setSessionConfigOptionCallsCorrectMethod() {
        when(processManager.sendRequest(eq("session/set_config_option"), any(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.setSessionConfigOption("sid", "cfg1", "val1");
        verify(processManager).sendRequest(eq("session/set_config_option"), any(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void closeBeforeLoadSkipsWhenAgentDoesNotAdvertiseClose() {
        CompletableFuture<Void> future = client.closeBeforeLoad("sid");

        assertNull(future.join(), "the no-op close must complete");
        verify(processManager, never()).stopMessage(any());
        verify(processManager, never())
                .sendRequest(eq("session/close"), any(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void renameSessionUsesGoosesRenameExtension() {
        when(processManager.sendRequest(eq("_goose/unstable/session/rename"), any(), eq(30L),
                eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.renameSession("sid", "New name").join();

        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(processManager).sendRequest(eq("_goose/unstable/session/rename"), params.capture(),
                eq(30L), eq(TimeUnit.SECONDS));
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) params.getValue();
        assertEquals("sid", sent.get("sessionId"));
        assertEquals("New name", sent.get("title"));
    }

    @Test
    void deleteSessionUsesTheStandardMethod() {
        when(processManager.sendRequest(eq("session/delete"), any(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.deleteSession("sid").join();

        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(processManager).sendRequest(eq("session/delete"), params.capture(), eq(30L),
                eq(TimeUnit.SECONDS));
        assertEquals("sid", ((Map<?, ?>) params.getValue()).get("sessionId"));
    }

    @Test
    void closeBeforeLoadCancelsThenClosesWhenAdvertised() {
        when(processManager.isSessionCloseSupported()).thenReturn(true);
        when(processManager.sendRequest(eq("session/close"), any(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.closeBeforeLoad("sid").join();

        // stopMessage() rather than a bare session/cancel notification: it also forgets
        // the session's run id and pending steer echoes, which the cancelled turn owns.
        InOrder order = inOrder(processManager);
        order.verify(processManager).stopMessage("sid");
        order.verify(processManager).sendRequest(eq("session/close"), any(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void closeBeforeLoadNeverSkipsTheLoadThatFollows() {
        when(processManager.isSessionCloseSupported()).thenReturn(true);
        when(processManager.sendRequest(eq("session/close"), any(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("no such session")));
        AtomicBoolean loadRan = new AtomicBoolean(false);

        client.closeBeforeLoad("sid")
                .thenCompose(v -> {
                    loadRan.set(true);
                    return CompletableFuture.completedFuture(null);
                })
                .join();

        assertTrue(loadRan.get(), "a failed session/close must not skip the load that follows");
    }
}
