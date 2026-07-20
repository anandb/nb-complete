package github.anandb.netbeans.manager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.support.MapperSupplier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionManagerTest {

    @Mock
    private ProcessManager processManager;

    @Mock
    private ToolExecutor toolExecutor;

    private SessionManager sessionManager;
    private final ObjectMapper mapper = MapperSupplier.get();
    private MockedStatic<ProcessManager> pmMock;

    @BeforeEach
    void setUp() throws Exception {
        // Mock ProcessManager.getInstance() to return our mock
        pmMock = mockStatic(ProcessManager.class);
        pmMock.when(ProcessManager::getInstance).thenReturn(processManager);

        // Configure mock delegates
        when(processManager.whenReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(processManager.getToolExecutor()).thenReturn(toolExecutor);
        when(toolExecutor.waitForReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(toolExecutor.getServerConfig()).thenReturn(java.util.List.of());
        // Default stub for 2-param sendRequest (e.g. session/prompt from sendPreamble)
        when(processManager.sendRequest(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        sessionManager = SessionManager.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (pmMock != null) {
            pmMock.close();
        }
    }

    @Test
    void testCreateSession() {
        JsonNode mockResponse = mapper.createObjectNode()
                .put("id", "new-id")
                .put("title", "New");
        when(processManager.sendRequest(eq("session/new"), any()))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        sessionManager.createSession("/test/cwd");
        verify(processManager).sendRequest(eq("session/new"), any());
    }
}
