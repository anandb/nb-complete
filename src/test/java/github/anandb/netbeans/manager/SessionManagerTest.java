package github.anandb.netbeans.manager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.mcp.McpManager;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    @Mock
    private ProcessManager processManager;

    @Mock
    private McpManager mcpManager;

    private SessionManager sessionManager;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        // Reset Singletons
        Field instanceField = SessionManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        Field acpInstanceField = ProcessManager.class.getDeclaredField("instance");
        acpInstanceField.setAccessible(true);
        acpInstanceField.set(null, processManager);

        // Mock ProcessManager delegates
        when(processManager.whenReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(processManager.getMcpManager()).thenReturn(mcpManager);
        when(mcpManager.waitForReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(mcpManager.getServerConfig()).thenReturn(java.util.List.of());

        sessionManager = SessionManager.getInstance();
    }

    @Test
    void testCreateSession() {
        JsonNode mockResponse = objectMapper.createObjectNode()
                .put("id", "new-id")
                .put("title", "New");
        when(processManager.sendRequest(eq("session/new"), any(), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        sessionManager.createNewSession("/dir");
        verify(processManager).sendRequest(eq("session/new"), any(), anyLong(), any());
    }
}
