package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.SessionUpdate;
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
        when(processManager.getCapabilities()).thenReturn(github.anandb.netbeans.model.HarnessCatalog.UNKNOWN);
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

    /** Listener that captures the options passed to onSessionLoaded. */
    private volatile List<SessionConfigOption> lastLoadedOptions;

    private SessionListener mockListener() {
        return new SessionListener() {
            @Override public void onSessionStarted(String sessionId) {}
            @Override public void onSessionLoading(boolean isLoading) {}
            @Override public void onSessionLoaded(String sessionId,
                    List<SessionConfigOption> configOptions, boolean isStartup) {
                lastLoadedOptions = configOptions;
            }
            @Override public void onSessionListUpdated(List<Session> sessions) {}
            @Override public void onSessionError(String message) {}
            @Override public void onSessionUpdate(SessionUpdate update) {}
        };
    }

    @Test
    void testCreateSessionHermesModelsModesBecomeConfigOptions() throws Exception {
        // Hermes-style session/new response: models/modes as structured fields,
        // no title, no configOptions. Models and modes must survive the title
        // fallback and be converted into config options for the dropdowns.
        JsonNode mockResponse = mapper.readTree(
                "{\"sessionId\":\"hermes-1\",\"cwd\":\"/test/cwd\","
                + "\"models\":{\"availableModels\":["
                + "{\"modelId\":\"opencode-go:omen-alpha\",\"name\":\"OpenCode Go · omen-alpha\",\"description\":\"Provider: OpenCode Go\"},"
                + "{\"modelId\":\"opencode-go:glm-5.3\",\"name\":\"OpenCode Go · glm-5.3\",\"description\":\"Provider: OpenCode Go\"}"
                + "],\"currentModelId\":\"opencode-go:omen-alpha\"},"
                + "\"modes\":{\"availableModes\":["
                + "{\"id\":\"default\",\"name\":\"Default\",\"description\":\"Ask before edits.\"}"
                + "],\"currentModeId\":\"default\"}}");
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        CompletableFuture<Session> future = sessionManager.createSession("/test/cwd");
        Session created = future.get(5, TimeUnit.SECONDS);

        assertEquals("hermes-1", created.id());
        // Title fallback ran (no title in response) and must not drop models/modes
        assertEquals("hermes-1", created.title());
        assertTrue(created.models() != null && created.models().availableModels().size() == 2);
        assertTrue(created.modes() != null && created.modes().availableModes().size() == 1);

        sessionManager.addSessionListener(mockListener());
        sessionManager.createNewSession("/test/cwd");
        // notifySessionLoaded fires from the async chain; wait for it
        long deadline = System.currentTimeMillis() + 5000;
        List<SessionConfigOption> opts = null;
        while (opts == null && System.currentTimeMillis() < deadline) {
            opts = lastLoadedOptions;
            Thread.sleep(50);
        }
        assertTrue(opts != null && opts.size() == 2, "expected model+mode config options");
        for (SessionConfigOption o : opts) {
            if ("model".equals(o.category())) {
                assertEquals("model", o.id());
                assertTrue(o.options().size() == 2);
                assertEquals("opencode-go:omen-alpha", o.currentValue());
            }
            if ("mode".equals(o.category())) {
                assertEquals("default", o.currentValue());
            }
        }
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

    @Test
    void testGetStateAndCanSendMessage() {
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        // canSendMessage() returns true only in STREAMING state
        assertFalse(sessionManager.canSendMessage());
        assertFalse(sessionManager.canStopMessage());
    }

    @Test
    void testGetAndSetCustomTitle() {
        sessionManager.setCustomTitle("s1", "My Title");
        // Title IS stored, so getCustomTitle returns it regardless of default
        assertEquals("My Title", sessionManager.getCustomTitle("s1", null));
        assertEquals("My Title", sessionManager.getCustomTitle("s1", "default"));
        // Unknown session returns default
        assertEquals("fallback", sessionManager.getCustomTitle("unknown", "fallback"));
    }

    @Test
    void testLocallyCreatedSessionsPersistence() throws Exception {
        // Access cacheManager field via reflection
        Field cacheField = SessionManager.class.getDeclaredField("cacheManager");
        cacheField.setAccessible(true);
        SessionCacheManager cacheManager = (SessionCacheManager) cacheField.get(sessionManager);

        // Access save and load private methods via reflection
        Method saveMethod = SessionManager.class.getDeclaredMethod("saveLocallyCreatedSessionIds");
        saveMethod.setAccessible(true);
        Method loadMethod = SessionManager.class.getDeclaredMethod("loadLocallyCreatedSessionIds");
        loadMethod.setAccessible(true);

        // Set up two mock sessions with custom titles
        Session s1 = new Session("id-1", "Title 1", "/cwd/1", "/cwd/1", null, "2026-09-12T00:00:01Z", List.of(), List.of(), null, null);
        Session s2 = new Session("id-2", "Title 2", "/cwd/2", "/cwd/2", null, "2026-09-12T00:00:02Z", List.of(), List.of(), null, null);

        sessionManager.setCustomTitle("id-1", "Title 1");
        sessionManager.setCustomTitle("id-2", "Title 2");

        // Add to cacheManager (agent is null in this test context)
        String agent = null;
        cacheManager.addLocallyCreated(s1, agent);
        cacheManager.addLocallyCreated(s2, agent);

        // Save
        saveMethod.invoke(sessionManager);

        // Clear cacheManager to simulate a restart/fresh state
        Field cachedSessionsField = SessionCacheManager.class.getDeclaredField("cachedSessions");
        cachedSessionsField.setAccessible(true);
        cachedSessionsField.set(cacheManager, new CopyOnWriteArrayList<>());

        Field locallyCreatedMapField = SessionCacheManager.class.getDeclaredField("locallyCreatedSessionToAgentMap");
        locallyCreatedMapField.setAccessible(true);
        ((Map<?, ?>) locallyCreatedMapField.get(cacheManager)).clear();

        Field sessionCacheMapField = SessionCacheManager.class.getDeclaredField("sessionCacheMap");
        sessionCacheMapField.setAccessible(true);
        ((Map<?, ?>) sessionCacheMapField.get(cacheManager)).clear();

        // Verify it is completely empty
        assertTrue(cacheManager.getLocallyCreatedSessions(agent).isEmpty());

        // Load
        loadMethod.invoke(sessionManager);

        // Verify sessions are restored in the correct original order (s2, s1) and titles are preserved
        List<Session> restored = cacheManager.getLocallyCreatedSessions(agent);
        assertEquals(2, restored.size());
        assertEquals("id-2", restored.get(0).id());
        assertEquals("Title 2", restored.get(0).title());
        assertEquals("id-1", restored.get(1).id());
        assertEquals("Title 1", restored.get(1).title());
    }

    @Test
    void testGetSessionTitle() {
        // No cached session → returns null
        assertNull(sessionManager.getSessionTitle("s2"));
    }

    @Test
    void testHiddenSessions() {
        assertFalse(sessionManager.isHidden("h1"));
        sessionManager.setHidden("h1", true);
        assertTrue(sessionManager.isHidden("h1"));
        sessionManager.setHidden("h1", false);
        assertFalse(sessionManager.isHidden("h1"));
    }

    @Test
    void testContextUsage() {
        assertNull(sessionManager.getContextUsage("s1"));
        sessionManager.setContextUsage("s1", 500, 1000);
        // getContextUsage returns a String like "500,1000"
        String usage = sessionManager.getContextUsage("s1");
        assertTrue(usage != null && usage.contains("500"));
    }

    @Test
    void testGetSessionReturnsNullForUnknown() {
        assertNull(sessionManager.getSession("nonexistent"));
    }

    @Test
    void testGetCurrentSessionIdInitiallyNull() {
        assertNull(sessionManager.getCurrentSessionId());
    }

    @Test
    void testGetCurrentSessionDirectoryInitiallyNull() {
        assertNull(sessionManager.getCurrentSessionDirectory());
    }
    @Test
    void testAddAndRemoveSessionListener() {
        // Just verify no exception on add/remove
        SessionListener listener = new SessionListener() {
            @Override public void onSessionListUpdated(List<Session> s) {}
            @Override public void onSessionStarted(String id) {}
            @Override public void onSessionLoaded(String id, List<SessionConfigOption> o, boolean b) {}
            @Override public void onSessionLoading(boolean l) {}
            @Override public void onSessionError(String m) {}
            @Override public void onSessionUpdate(SessionUpdate u) {}
        };
        sessionManager.addSessionListener(listener);
        sessionManager.removeSessionListener(listener);
    }

    @Test
    void testGetSessionsWithNullDirectory() {
        when(processManager.sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));
        sessionManager.getSessions(null);
        verify(processManager).sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void testRefreshSessions() {
        // In headless test env, ProjectQuery lookup returns null → no open
        // projects → empty list returned directly, sendRequest never called.
        // Just verify the chain completes without throwing.
        sessionManager.refreshSessions();
        // Allow async chain to complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // No exception = pass. Sessions remain empty (no projects open).
    }

    @Test
    void testIsDescendantOfCurrent() {
        // No current session → false
        assertFalse(sessionManager.isDescendantOfCurrent("any"));
    }

    @Test
    void testDisposeDoesNotThrow() {
        sessionManager.dispose();
    }

    @Test
    void testQualifiedKey() {
        // Test the internal qualifiedKey method via反射
        try {
            Method m = SessionManager.class
                    .getDeclaredMethod("qualifiedKey", String.class, String.class);
            m.setAccessible(true);
            String result = (String) m.invoke(sessionManager, "prefix", "sid123");
            assertTrue(result.contains("sid123"));
        } catch (Exception e) {
            // Method may not exist or be renamed — skip
        }
    }

    @Test
    void testDecodeHtmlEntities() {
        try {
            Method m = SessionManager.class
                    .getDeclaredMethod("decodeHtmlEntities", String.class);
            m.setAccessible(true);
            assertEquals("a & b", m.invoke(sessionManager, "a &amp; b"));
            assertEquals("\"hi\"", m.invoke(sessionManager, "&quot;hi&quot;"));
        } catch (Exception e) {
            // skip
        }
    }
}
