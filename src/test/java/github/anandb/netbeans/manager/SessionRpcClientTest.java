package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.model.AgentCapabilities;
import github.anandb.netbeans.support.MapperSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(processManager.getCapabilities()).thenReturn(AgentCapabilities.DEFAULT);
        when(processManager.getToolExecutor()).thenReturn(
                new github.anandb.netbeans.contract.ToolExecutor() {
                    @Override public void start() {}
                    @Override public void stop() {}
                    @Override public CompletableFuture<Void> waitForReady() { return CompletableFuture.completedFuture(null); }
                    @Override public java.util.List<Map<String, Object>> getServerConfig() { return java.util.List.of(); }
                    @Override public void checkServerSupport(JsonNode res) {}
                    @Override public void setMcpAuthRequired(boolean required) {}
                    @Override public void disable() {}
                    @Override public boolean isDisabled() { return false; }
                });
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.createSession("/project");
        verify(processManager).sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void loadSessionFromServerCallsCorrectMethod() {
        when(processManager.getCapabilities()).thenReturn(AgentCapabilities.DEFAULT);
        when(processManager.getToolExecutor()).thenReturn(
                new github.anandb.netbeans.contract.ToolExecutor() {
                    @Override public void start() {}
                    @Override public void stop() {}
                    @Override public CompletableFuture<Void> waitForReady() { return CompletableFuture.completedFuture(null); }
                    @Override public java.util.List<Map<String, Object>> getServerConfig() { return java.util.List.of(); }
                    @Override public void checkServerSupport(JsonNode res) {}
                    @Override public void setMcpAuthRequired(boolean required) {}
                    @Override public void disable() {}
                    @Override public boolean isDisabled() { return false; }
                });
        when(processManager.sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.loadSessionFromServer("sid", "/cwd");
        verify(processManager).sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES));
    }

    @Test
    void renameSessionOnServerReturnsCompletedFuture() {
        CompletableFuture<Void> result = client.renameSessionOnServer("sid", "new title");
        assertNotNull(result);
        assertTrue(result.isDone());
    }

    @Test
    void setSessionConfigOptionCallsCorrectMethod() {
        when(processManager.sendRequest(eq("session/set_config_option"), any(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));

        client.setSessionConfigOption("sid", "cfg1", "val1");
        verify(processManager).sendRequest(eq("session/set_config_option"), any(), eq(30L), eq(TimeUnit.SECONDS));
    }
}
