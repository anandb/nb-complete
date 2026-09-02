package github.anandb.netbeans.manager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;


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
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.support.MapperSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        when(processManager.getCapabilities()).thenReturn(github.anandb.netbeans.model.AgentCapabilities.DEFAULT);
        when(toolExecutor.waitForReady()).thenReturn(CompletableFuture.completedFuture(null));
        when(toolExecutor.getServerConfig()).thenReturn(List.of());
        // Default stub for 2-param sendRequest (e.g. session/prompt from sendPreamble)
        when(processManager.sendRequest(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // Construct directly rather than via getInstance(): SessionManager is
        // registered in META-INF/services (SessionControl), and
        // MetaInfServicesLookup CACHES the instance it instantiates. A later
        // test would get the cached instance whose SessionRpcClient is bound to
        // an EARLIER test's ProcessManager mock, so sendRequest verifies
        // against the wrong mock.
        sessionManager = new SessionManager();
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
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        sessionManager.createSession("/test/cwd");
        verify(processManager).sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void constructorRegistersPreRestartHandler() {
        // Every manual server restart must reset sticky session state; otherwise
        // a LOADING/STOPPING state machine blocks the post-restart loadSession()
        // until the IDE itself is restarted.
        verify(processManager).setPreRestartHandler(any());
    }

    @Test
    void resetForServerRestartRecoversFromStuckLoadingState() {
        // Simulate a session/load that never completed (server died mid-load):
        // the state machine is wedged in LOADING, which would make the
        // post-restart loadSession() refuse to run.
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.LOADING));

        sessionManager.resetForServerRestart();

        assertEquals(SessionState.IDLE, sessionManager.getStateMachine().getState());
        assertTrue(sessionManager.getStateMachine().canSendMessage()
                || sessionManager.getStateMachine().getState() == SessionState.IDLE);
        assertTrue(sessionManager.getStateMachine().canLoadSession());
    }

    @Test
    void resetForServerRestartIsIdempotentFromIdle() {
        sessionManager.resetForServerRestart();
        assertEquals(SessionState.IDLE, sessionManager.getStateMachine().getState());
        // A second reset must not throw or log an invalid-transition warning.
        sessionManager.resetForServerRestart();
        assertEquals(SessionState.IDLE, sessionManager.getStateMachine().getState());
    }
}
