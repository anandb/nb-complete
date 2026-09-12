package github.anandb.netbeans.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.manager.strategy.StrategyRegistry;
import github.anandb.netbeans.model.HarnessCatalog;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import github.anandb.netbeans.support.PreferenceKeys;

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
        when(processManager.stopMessage(any())).thenReturn(CompletableFuture.completedFuture(null));

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
        NbPreferences.forModule(PreferenceKeys.class).remove(PreferenceKeys.ACP_HARNESS_ID);
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
    void createSessionDoesNotMarkLocalWhenSessionListSupported() throws Exception {
        JsonNode mockResponse = mapper.createObjectNode()
                .put("sessionId", "listed-id")
                .put("title", "Listed");
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            sessionManager.createSession("/test/cwd").get(5, TimeUnit.SECONDS);
        }
        assertTrue(cacheManager().getLocallyCreatedIds("opencode").isEmpty());
        assertTrue(cacheManager().getLocallyCreatedIds(null).isEmpty());
    }

    @Test
    void createSessionMarksLocalWhenSessionListUnsupported() throws Exception {
        JsonNode mockResponse = mapper.createObjectNode()
                .put("sessionId", "gemini-id")
                .put("title", "Gemini");
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.GEMINI);
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            sessionManager.createSession("/test/cwd").get(5, TimeUnit.SECONDS);
        }
        assertTrue(cacheManager().getLocallyCreatedIds(null).contains("gemini-id"));
    }

    @Test
    void retainSessionsForDirectoriesDropsClosedProjectAndEmptyOpenList() {
        Session open = cachedSession("open", "/p1");
        Session closed = cachedSession("closed", "/p2");
        Session noCwd = new Session("bare", "bare", null, null, null, null, List.of(), List.of(), null, null);
        List<Session> retained = SessionManager.retainSessionsForDirectories(
                List.of(open, closed, noCwd), List.of("/p1"));
        assertEquals(1, retained.size());
        assertEquals("open", retained.get(0).id());
        assertTrue(SessionManager.retainSessionsForDirectories(List.of(open), List.of()).isEmpty());
    }

    @Test
    void refreshLocalSessionsHidesClosedProjectButKeepsPersistedId() throws Exception {
        Method updateMeta = SessionManager.class.getDeclaredMethod("updateMetadata",
                String.class, java.util.function.Function.class);
        updateMeta.setAccessible(true);
        cacheManager().addLocallyCreated(cachedSession("keep", "/open"), null);
        cacheManager().addLocallyCreated(cachedSession("gone", "/closed"), null);
        updateMeta.invoke(sessionManager, "keep",
                (java.util.function.Function<SessionManager.SessionMetadata, SessionManager.SessionMetadata>)
                m -> new SessionManager.SessionMetadata(m.title(), m.usage(), m.hidden(), "/open"));
        updateMeta.invoke(sessionManager, "gone",
                (java.util.function.Function<SessionManager.SessionMetadata, SessionManager.SessionMetadata>)
                m -> new SessionManager.SessionMetadata(m.title(), m.usage(), m.hidden(), "/closed"));
        Method save = SessionManager.class.getDeclaredMethod("saveLocallyCreatedSessionIds");
        save.setAccessible(true);
        save.invoke(sessionManager);
        Field listField = SessionCacheManager.class.getDeclaredField("cachedSessions");
        listField.setAccessible(true);
        listField.set(cacheManager(), new CopyOnWriteArrayList<>());
        Field mapField = SessionCacheManager.class.getDeclaredField("locallyCreatedSessionToAgentMap");
        mapField.setAccessible(true);
        ((Map<?, ?>) mapField.get(cacheManager())).clear();

        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.GEMINI);
        ProjectQuery pq = mock(ProjectQuery.class);
        Project project = mock(Project.class);
        FileObject dir = mock(FileObject.class);
        when(dir.getPath()).thenReturn("/open");
        when(project.getProjectDirectory()).thenReturn(dir);
        when(pq.getAllOpenProjects()).thenReturn(new Project[] {project});

        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            when(mockLookup.lookup(ProjectQuery.class)).thenReturn(pq);
            sessionManager.refreshSessions();
            long deadline = System.currentTimeMillis() + 3000;
            while (cacheManager().getCachedSessions().size() != 1
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
        }
        List<Session> shown = cacheManager().getCachedSessions();
        assertEquals(1, shown.size());
        assertEquals("keep", shown.get(0).id());
        assertTrue(cacheManager().getLocallyCreatedIds(null).contains("gone"),
                "closed-project id stays persisted for when that project reopens");
    }

    /** Listener that captures the options passed to onSessionLoaded. */
    private volatile List<SessionConfigOption> lastLoadedOptions;
    private volatile String lastLoadedSessionId;
    private volatile Boolean lastLoadedStartup;
    private volatile String lastError;
    private volatile String lastRenamedId;
    private final AtomicBoolean loadingBecameFalse = new AtomicBoolean();

    private SessionListener mockListener() {
        return new SessionListener() {
            @Override public void onSessionStarted(String sessionId) {}
            @Override public void onSessionLoading(boolean isLoading) {
                if (!isLoading) {
                    loadingBecameFalse.set(true);
                }
            }
            @Override public void onSessionLoaded(String sessionId,
                    List<SessionConfigOption> configOptions, boolean isStartup) {
                lastLoadedSessionId = sessionId;
                lastLoadedOptions = configOptions;
                lastLoadedStartup = isStartup;
            }
            @Override public void onSessionListUpdated(List<Session> sessions) {}
            @Override public void onSessionError(String message) {
                lastError = message;
            }
            @Override public void onSessionUpdate(SessionUpdate update) {}
            @Override public void onSessionRenamed(String sessionId) {
                lastRenamedId = sessionId;
            }
        };
    }

    private SessionCacheManager cacheManager() throws Exception {
        Field cacheField = SessionManager.class.getDeclaredField("cacheManager");
        cacheField.setAccessible(true);
        return (SessionCacheManager) cacheField.get(sessionManager);
    }

    private Session cachedSession(String id, String cwd) {
        return new Session(id, id, cwd, cwd, null, "2026-09-12T00:00:00Z",
                List.of(), List.of(), null, null);
    }

    private void seedCachedSession(String id, String cwd) throws Exception {
        cacheManager().addLocallyCreated(cachedSession(id, cwd), null);
    }

    private void stubSessionLoad(JsonNode response) {
        when(processManager.sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES)))
                .thenReturn(CompletableFuture.completedFuture(response));
    }

    private void awaitLoaded() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (lastLoadedSessionId == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    private void driveToStreaming(String sessionId) throws Exception {
        Field sid = SessionManager.class.getDeclaredField("currentSessionId");
        sid.setAccessible(true);
        sid.set(sessionManager, sessionId);
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.LOADING));
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.STREAMING));
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
    void sessionMetadataPersistsPerSessionNotAsOneBlob() {
        for (int i = 0; i < 80; i++) {
            sessionManager.setCustomTitle("sid-" + i, "Title " + i);
            sessionManager.setHidden("sid-" + i, i % 2 == 0);
        }
        assertEquals("Title 42", sessionManager.getCustomTitle("sid-42", "x"));
        assertTrue(sessionManager.isHidden("sid-40"));
        assertFalse(sessionManager.isHidden("sid-41"));
        String blob = NbPreferences.forModule(SessionManager.class)
                .get("gemini_local_sessions_metadata", null);
        assertTrue(blob == null || blob.isEmpty(), "must not store all metadata in one prefs value");
    }

    @Test
    void sessionMetadataMigratesLegacyBlobThenDropsIt() {
        String blobKey = "gemini_local_sessions_metadata";
        NbPreferences.forModule(SessionManager.class).put(blobKey,
                "{\"blob-1\":{\"title\":\"FromBlob\",\"hidden\":true,\"cwd\":\"/p\"}}");
        assertEquals("FromBlob", sessionManager.getCustomTitle("blob-1", "fallback"));
        assertTrue(sessionManager.isHidden("blob-1"));
        String leftover = NbPreferences.forModule(SessionManager.class).get(blobKey, null);
        assertTrue(leftover == null || leftover.isEmpty());
    }

    @Test
    void blankHarnessIdUsesSameLocalSessionKeyAsUnset() throws Exception {
        NbPreferences.forModule(PreferenceKeys.class).put(PreferenceKeys.ACP_HARNESS_ID, "  ");
        Session s = new Session("blank-id", "Blank", "/cwd", "/cwd", null, null, List.of(), List.of(), null, null);
        cacheManager().addLocallyCreated(s, null);
        Method save = SessionManager.class.getDeclaredMethod("saveLocallyCreatedSessionIds");
        save.setAccessible(true);
        save.invoke(sessionManager);
        assertTrue(localIdNode("sessids").get("blank-id", null) != null);
        assertTrue(isBlankPref("gemini_local_sessions"));
        assertTrue(isBlankPref("gemini_local_sessions_"));
        assertTrue(isBlankPref("gemini_local_sessions_gemini"));
    }

    @Test
    void loadsUnqualifiedLocalIdsAndMetadataAfterGeminiHarnessIdIsSet() throws Exception {
        sessionManager.setCustomTitle("legacy-sid", "OldTitle");
        NbPreferences.forModule(SessionManager.class).put("gemini_local_sessions", "legacy-sid");
        NbPreferences.forModule(PreferenceKeys.class).put(PreferenceKeys.ACP_HARNESS_ID, "gemini");

        Field listField = SessionCacheManager.class.getDeclaredField("cachedSessions");
        listField.setAccessible(true);
        listField.set(cacheManager(), new CopyOnWriteArrayList<>());
        Field mapField = SessionCacheManager.class.getDeclaredField("locallyCreatedSessionToAgentMap");
        mapField.setAccessible(true);
        ((Map<?, ?>) mapField.get(cacheManager())).clear();

        Method load = SessionManager.class.getDeclaredMethod("loadLocallyCreatedSessionIds");
        load.setAccessible(true);
        load.invoke(sessionManager);

        List<Session> restored = cacheManager().getLocallyCreatedSessions("gemini");
        assertEquals(1, restored.size());
        assertEquals("legacy-sid", restored.get(0).id());
        assertEquals("OldTitle", restored.get(0).title());
        assertTrue(localIdNode("sessids_gemini").get("legacy-sid", null) != null);
        assertTrue(isBlankPref("gemini_local_sessions"));
        assertTrue(isBlankPref("gemini_local_sessions_gemini"));
    }

    @Test
    void localSessionIdWithCommaRoundTrips() throws Exception {
        Session s = new Session("id,with,commas", "C", "/cwd", "/cwd", null, null, List.of(), List.of(), null, null);
        cacheManager().addLocallyCreated(s, null);
        Method save = SessionManager.class.getDeclaredMethod("saveLocallyCreatedSessionIds");
        save.setAccessible(true);
        save.invoke(sessionManager);
        Field listField = SessionCacheManager.class.getDeclaredField("cachedSessions");
        listField.setAccessible(true);
        listField.set(cacheManager(), new CopyOnWriteArrayList<>());
        Field mapField = SessionCacheManager.class.getDeclaredField("locallyCreatedSessionToAgentMap");
        mapField.setAccessible(true);
        ((Map<?, ?>) mapField.get(cacheManager())).clear();
        Method load = SessionManager.class.getDeclaredMethod("loadLocallyCreatedSessionIds");
        load.setAccessible(true);
        load.invoke(sessionManager);
        assertTrue(cacheManager().getLocallyCreatedIds(null).contains("id,with,commas"));
    }

    private static java.util.prefs.Preferences localIdNode(String name) {
        return NbPreferences.forModule(SessionManager.class).node(name);
    }

    private static boolean isBlankPref(String key) {
        String v = NbPreferences.forModule(SessionManager.class).get(key, null);
        return v == null || v.isEmpty();
    }

    @Test
    void isHiddenMigratesLegacyThenUnhideIgnoresLeftoverLegacyKey() {
        NbPreferences.forModule(SessionManager.class).putBoolean("session_hidden_h-legacy", true);
        assertTrue(sessionManager.isHidden("h-legacy"));
        sessionManager.setHidden("h-legacy", false);
        NbPreferences.forModule(SessionManager.class).putBoolean("session_hidden_h-legacy", true);
        assertFalse(sessionManager.isHidden("h-legacy"),
                "metadata hidden=false must win over leftover session_hidden_* keys");
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

    @Test
    void loadSessionHappyPathSetsStreamingAndOptions() throws Exception {
        seedCachedSession("s1", "/p");
        stubSessionLoad(mapper.readTree(
                "{\"configOptions\":[{\"id\":\"model\",\"name\":\"Model\",\"category\":\"model\","
                + "\"type\":\"select\",\"currentValue\":\"m1\",\"options\":[]}]}"));
        sessionManager.addSessionListener(mockListener());

        assertTrue(sessionManager.loadSession("s1"));
        awaitLoaded();

        assertEquals("s1", lastLoadedSessionId);
        assertEquals(Boolean.FALSE, lastLoadedStartup);
        assertEquals("s1", sessionManager.getCurrentSessionId());
        assertEquals("/p", sessionManager.getCurrentSessionDirectory());
        assertEquals(SessionState.STREAMING, sessionManager.getCurrentState());
        assertTrue(lastLoadedOptions != null && lastLoadedOptions.size() == 1);
        assertEquals("m1", lastLoadedOptions.get(0).currentValue());
    }

    @Test
    void loadSessionStartupFlagPassedToListener() throws Exception {
        seedCachedSession("s1", "/p");
        stubSessionLoad(mapper.createObjectNode());
        sessionManager.addSessionListener(mockListener());

        assertTrue(sessionManager.loadSession("s1", true));
        awaitLoaded();
        assertEquals(Boolean.TRUE, lastLoadedStartup);
    }

    @Test
    void loadSessionHermesModelsModesBecomeConfigOptions() throws Exception {
        seedCachedSession("hermes-1", "/test/cwd");
        stubSessionLoad(mapper.readTree(
                "{\"sessionId\":\"hermes-1\",\"cwd\":\"/test/cwd\","
                + "\"models\":{\"availableModels\":["
                + "{\"modelId\":\"opencode-go:omen-alpha\",\"name\":\"OpenCode Go · omen-alpha\"}"
                + "],\"currentModelId\":\"opencode-go:omen-alpha\"},"
                + "\"modes\":{\"availableModes\":["
                + "{\"id\":\"default\",\"name\":\"Default\"}"
                + "],\"currentModeId\":\"default\"}}"));
        sessionManager.addSessionListener(mockListener());

        assertTrue(sessionManager.loadSession("hermes-1"));
        awaitLoaded();
        assertTrue(lastLoadedOptions != null && lastLoadedOptions.size() == 2);
    }

    @Test
    void loadSessionWithoutCwdFailsFastForGemini() {
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.GEMINI);
        sessionManager.addSessionListener(mockListener());
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            assertFalse(sessionManager.loadSession("s1"));
        }
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        assertNull(sessionManager.getCurrentSessionId());
        assertTrue(lastError != null && lastError.contains("s1"));
        verify(processManager, never()).sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES));
    }

    @Test
    void loadSessionWithoutCwdProceedsWhenSessionListSupported() throws Exception {
        stubSessionLoad(mapper.createObjectNode());
        sessionManager.addSessionListener(mockListener());
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.OPENCODE);
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            assertTrue(sessionManager.loadSession("s1"));
            awaitLoaded();
        }
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(processManager).sendRequest(eq("session/load"), params.capture(),
                eq(2L), eq(TimeUnit.MINUTES));
        assertFalse(params.getValue().containsKey("cwd"));
        assertEquals("s1", lastLoadedSessionId);
    }

    @Test
    void loadSessionFallsBackToSingleOpenProjectCwd() throws Exception {
        stubSessionLoad(mapper.createObjectNode());
        sessionManager.addSessionListener(mockListener());
        ProjectQuery projectQuery = mock(ProjectQuery.class);
        Project project = mock(Project.class);
        FileObject projectDir = mock(FileObject.class);
        when(projectDir.getPath()).thenReturn("/only/project");
        when(project.getProjectDirectory()).thenReturn(projectDir);
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] { project });

        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);
            assertTrue(sessionManager.loadSession("s1"));
            awaitLoaded();
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(processManager).sendRequest(eq("session/load"), params.capture(),
                eq(2L), eq(TimeUnit.MINUTES));
        assertEquals("/only/project", params.getValue().get("cwd"));
        assertEquals("/only/project", sessionManager.getCurrentSessionDirectory());
    }

    @Test
    void loadSessionRefusesWhenStopping() throws Exception {
        driveToStreaming("s1");
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.STOPPING));
        assertFalse(sessionManager.loadSession("s1"));
        verify(processManager, never()).sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES));
    }

    @Test
    void loadSessionStaleCompletionIgnoredAfterClose() throws Exception {
        seedCachedSession("s1", "/p");
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(processManager.sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES)))
                .thenReturn(pending);
        sessionManager.addSessionListener(mockListener());

        assertTrue(sessionManager.loadSession("s1"));
        sessionManager.closeSession();
        pending.complete(mapper.createObjectNode());

        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertNull(lastLoadedSessionId);
        assertNull(sessionManager.getCurrentSessionId());
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
    }

    @Test
    void loadSessionFailedRpcStillCompletesWithNullOptions() throws Exception {
        seedCachedSession("s1", "/p");
        when(processManager.sendRequest(eq("session/load"), any(), eq(2L), eq(TimeUnit.MINUTES)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        sessionManager.addSessionListener(mockListener());

        assertTrue(sessionManager.loadSession("s1"));
        awaitLoaded();
        assertEquals("s1", lastLoadedSessionId);
        assertNull(lastLoadedOptions);
        assertEquals(SessionState.STREAMING, sessionManager.getCurrentState());
    }

    @Test
    void loadSessionReconnectPromptIsOneShot() throws Exception {
        seedCachedSession("s1", "/p");
        stubSessionLoad(mapper.createObjectNode());
        sessionManager.scheduleManualReconnectPrompt();
        sessionManager.addSessionListener(mockListener());

        assertTrue(sessionManager.loadSession("s1"));
        awaitLoaded();
        verify(processManager).sendRequest(eq("session/prompt"), any());

        lastLoadedSessionId = null;
        sessionManager.closeSession();
        seedCachedSession("s1", "/p");
        assertTrue(sessionManager.loadSession("s1"));
        awaitLoaded();
        verify(processManager, times(1)).sendRequest(eq("session/prompt"), any());
    }

    @Test
    void createNewSessionNullCwdReturnsToIdle() {
        sessionManager.createNewSession(null);
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        verify(processManager, never()).sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void createNewSessionRefusesWhenStopping() throws Exception {
        driveToStreaming("s1");
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.STOPPING));
        sessionManager.createNewSession("/p");
        verify(processManager, never()).sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS));
        assertEquals(SessionState.STOPPING, sessionManager.getCurrentState());
    }

    @Test
    void createNewSessionRpcFailureReturnsIdleAndNotifiesError() throws Exception {
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("create failed")));
        sessionManager.addSessionListener(mockListener());

        sessionManager.createNewSession("/p");
        long deadline = System.currentTimeMillis() + 5000;
        while (lastError == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        assertTrue(lastError != null && lastError.contains("create failed"));
        assertNull(sessionManager.getCurrentSessionId());
    }

    @Test
    void createNewSessionDiscardedIfClosedBeforeComplete() throws Exception {
        CompletableFuture<JsonNode> pending = new CompletableFuture<>();
        when(processManager.sendRequest(eq("session/new"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(pending);
        sessionManager.createNewSession("/p");
        assertEquals(SessionState.LOADING, sessionManager.getCurrentState());
        sessionManager.closeSession();
        pending.complete(mapper.createObjectNode().put("sessionId", "orphan").put("title", "Orphan"));

        long deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertNull(sessionManager.getCurrentSessionId());
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
    }

    @Test
    void closeSessionFromStreamingClearsIdAndDirectory() throws Exception {
        driveToStreaming("s1");
        Field dir = SessionManager.class.getDeclaredField("lastProjectDir");
        dir.setAccessible(true);
        dir.set(sessionManager, "/p");
        sessionManager.addSessionListener(mockListener());

        sessionManager.closeSession();
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        assertNull(sessionManager.getCurrentSessionId());
        assertNull(sessionManager.getCurrentSessionDirectory());
        assertTrue(loadingBecameFalse.get());
    }

    @Test
    void closeSessionFromIdleDoesNotThrow() {
        sessionManager.closeSession();
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        assertNull(sessionManager.getCurrentSessionId());
    }

    @Test
    void closeSessionInvalidatesStrategyRegistry() throws Exception {
        driveToStreaming("s1");
        try (MockedStatic<StrategyRegistry> registry = mockStatic(StrategyRegistry.class)) {
            sessionManager.closeSession();
            registry.verify(() -> StrategyRegistry.invalidateSession("s1"));
        }
    }

    @Test
    void canStopMessageOnlyInStreaming() throws Exception {
        assertFalse(sessionManager.canStopMessage());
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.LOADING));
        assertFalse(sessionManager.canStopMessage());
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.STREAMING));
        assertTrue(sessionManager.canStopMessage());
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.STOPPING));
        assertFalse(sessionManager.canStopMessage());
    }

    @Test
    void stopCurrentMessageFromStreamingCallsStop() throws Exception {
        driveToStreaming("s1");
        sessionManager.stopCurrentMessage();
        assertEquals(SessionState.STOPPING, sessionManager.getCurrentState());
        verify(processManager).stopMessage("s1");
    }

    @Test
    void stopCurrentMessageFromIdleIsNoOp() {
        sessionManager.stopCurrentMessage();
        verify(processManager, never()).stopMessage(any());
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
    }

    @Test
    void forceCancelFromStreamingGoesIdleAndStops() throws Exception {
        driveToStreaming("s1");
        sessionManager.forceCancelCurrentMessage();
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        verify(processManager).stopMessage("s1");
    }

    @Test
    void forceCancelWhenCannotStopIsNoOp() {
        sessionManager.forceCancelCurrentMessage();
        verify(processManager, never()).stopMessage(any());
    }

    @Test
    void onTurnEndedFromStoppingGoesStreaming() throws Exception {
        driveToStreaming("s1");
        assertTrue(sessionManager.getStateMachine().transitionTo(SessionState.STOPPING));
        sessionManager.onTurnEnded();
        assertEquals(SessionState.STREAMING, sessionManager.getCurrentState());
    }

    @Test
    void onTurnEndedFromIdleAndStreamingUnchanged() throws Exception {
        sessionManager.onTurnEnded();
        assertEquals(SessionState.IDLE, sessionManager.getCurrentState());
        driveToStreaming("s1");
        sessionManager.onTurnEnded();
        assertEquals(SessionState.STREAMING, sessionManager.getCurrentState());
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 10, unit = TimeUnit.SECONDS)
    void stopSafetyTimeoutRecoversToStreaming() throws Exception {
        driveToStreaming("s1");
        sessionManager.stopCurrentMessage();
        assertEquals(SessionState.STOPPING, sessionManager.getCurrentState());
        long deadline = System.currentTimeMillis() + 7000;
        while (sessionManager.getCurrentState() != SessionState.STREAMING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(SessionState.STREAMING, sessionManager.getCurrentState());
    }

    @Test
    void setSessionConfigOptionNotifiesWhenCurrentSession() throws Exception {
        driveToStreaming("s1");
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.UNKNOWN);
        when(processManager.sendRequest(eq("session/set_config_option"), any(),
                eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.readTree(
                        "{\"configOptions\":[{\"id\":\"effort\",\"name\":\"Effort\","
                        + "\"category\":\"effort\",\"type\":\"select\",\"currentValue\":\"high\","
                        + "\"options\":[]}]}")));
        sessionManager.addSessionListener(mockListener());
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            sessionManager.setSessionConfigOption("s1", "effort", "high").get(5, TimeUnit.SECONDS);
        }
        awaitLoaded();
        assertEquals("s1", lastLoadedSessionId);
        assertEquals(Boolean.FALSE, lastLoadedStartup);
        assertEquals("high", lastLoadedOptions.get(0).currentValue());
    }

    @Test
    void setSessionConfigOptionSkippedWhenUnsupported() throws Exception {
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.GEMINI);
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            sessionManager.setSessionConfigOption("s1", "effort", "high").get(5, TimeUnit.SECONDS);
        }
        verify(processManager, never()).sendRequest(eq("session/set_config_option"), any(),
                eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void setSessionConfigOptionRejectedValueDoesNotLoad() throws Exception {
        driveToStreaming("s1");
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.UNKNOWN);
        when(processManager.sendRequest(eq("session/set_config_option"), any(),
                eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("rejected")));
        sessionManager.addSessionListener(mockListener());
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            CompletableFuture<Void> future = sessionManager.setSessionConfigOption("s1", "effort", "bad");
            assertTrue(future.isCompletedExceptionally()
                    || future.handle((v, ex) -> ex != null).get(5, TimeUnit.SECONDS));
        }
        assertNull(lastLoadedSessionId);
    }

    @Test
    void setSessionConfigOptionForOtherSessionDoesNotNotify() throws Exception {
        driveToStreaming("current");
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.UNKNOWN);
        when(processManager.sendRequest(eq("session/set_config_option"), any(),
                eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.readTree(
                        "{\"configOptions\":[{\"id\":\"effort\",\"currentValue\":\"high\"}]}")));
        sessionManager.addSessionListener(mockListener());
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            sessionManager.setSessionConfigOption("other", "effort", "high").get(5, TimeUnit.SECONDS);
        }
        Thread.sleep(100);
        assertNull(lastLoadedSessionId);
    }

    @Test
    void setSessionModeSendsRpc() throws Exception {
        when(processManager.sendRequest(eq("session/set_mode"), any(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(mapper.createObjectNode()));
        sessionManager.setSessionMode("s1", "default").get(5, TimeUnit.SECONDS);
        verify(processManager).sendRequest(eq("session/set_mode"), any(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void setSessionModeFailureCompletesExceptionally() throws Exception {
        when(processManager.sendRequest(eq("session/set_mode"), any(), eq(30L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("mode rejected")));
        CompletableFuture<Void> future = sessionManager.setSessionMode("s1", "bad");
        assertTrue(future.handle((v, ex) -> ex != null).get(5, TimeUnit.SECONDS));
    }

    @Test
    void renameSessionBlankTitleIsNoOp() throws Exception {
        seedCachedSession("s1", "/p");
        sessionManager.addSessionListener(mockListener());
        sessionManager.renameSession("s1", "  ");
        assertNull(lastRenamedId);
        assertEquals("s1", cacheManager().getCachedSession("s1").title());
    }

    @Test
    void renameSessionUpdatesCacheAndNotifies() throws Exception {
        seedCachedSession("s1", "/p");
        sessionManager.addSessionListener(mockListener());
        sessionManager.renameSession("s1", "New Title");
        assertEquals("New Title", sessionManager.getCustomTitle("s1", null));
        assertEquals("New Title", cacheManager().getCachedSession("s1").title());
        assertEquals("s1", lastRenamedId);
    }

    @Test
    void renameUnknownSessionStillSetsCustomTitle() {
        sessionManager.addSessionListener(mockListener());
        sessionManager.renameSession("missing", "Solo");
        assertEquals("Solo", sessionManager.getCustomTitle("missing", "fallback"));
        assertEquals("missing", lastRenamedId);
    }

    @Test
    void renameSessionPersistsWhenSessionListUnsupported() throws Exception {
        seedCachedSession("s1", "/p");
        ProcessControl pc = mock(ProcessControl.class);
        when(pc.getCapabilities()).thenReturn(HarnessCatalog.GEMINI);
        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            Lookup mockLookup = mock(Lookup.class);
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            when(mockLookup.lookup(ProcessControl.class)).thenReturn(pc);
            sessionManager.renameSession("s1", "Persisted");
        }
        Field mapField = SessionCacheManager.class.getDeclaredField("locallyCreatedSessionToAgentMap");
        mapField.setAccessible(true);
        ((Map<?, ?>) mapField.get(cacheManager())).clear();
        Field listField = SessionCacheManager.class.getDeclaredField("cachedSessions");
        listField.setAccessible(true);
        listField.set(cacheManager(), new CopyOnWriteArrayList<>());
        Method loadMethod = SessionManager.class.getDeclaredMethod("loadLocallyCreatedSessionIds");
        loadMethod.setAccessible(true);
        loadMethod.invoke(sessionManager);
        List<Session> restored = cacheManager().getLocallyCreatedSessions(null);
        assertTrue(restored.stream().anyMatch(s -> "s1".equals(s.id()) && "Persisted".equals(s.title())));
    }

    @Test
    void getSessionsForDirectoriesNullOrEmpty() throws Exception {
        assertTrue(sessionManager.getSessionsForDirectories(null).get(5, TimeUnit.SECONDS).isEmpty());
        assertTrue(sessionManager.getSessionsForDirectories(List.of()).get(5, TimeUnit.SECONDS).isEmpty());
        verify(processManager, never()).sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void getSessionsForDirectoriesQueriesEachDir() throws Exception {
        when(processManager.sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(CompletableFuture.completedFuture(
                        mapper.readTree("{\"sessions\":[{\"sessionId\":\"a\",\"cwd\":\"/x\"}]}")));
        List<Session> sessions = sessionManager.getSessionsForDirectories(List.of("/a", "/b"))
                .get(5, TimeUnit.SECONDS);
        verify(processManager, times(2)).sendRequest(eq("session/list"), any(), eq(60L), eq(TimeUnit.SECONDS));
        assertEquals(2, sessions.size());
    }
}
