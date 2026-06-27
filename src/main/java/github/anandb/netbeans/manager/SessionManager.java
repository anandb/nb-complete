package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.project.ACPProjectManager;
import org.netbeans.api.project.Project;

import javax.swing.SwingUtilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.logging.Level;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.manager.strategy.StrategyRegistry;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static org.apache.commons.text.StringEscapeUtils.unescapeHtml4;

/**
 * Manages the state and lifecycle of chat sessions.
 * Decouples session logic from the AssistantTopComponent UI.
 */
@ServiceProvider(service = SessionControl.class)
public class SessionManager implements SessionQuery, SessionControl {

    // --- custom session titles (merged from SessionTitleMapper) --------------
    private static final String TITLE_PREFIX = "session_title_";

    /** @see SessionQuery#getCustomTitle(String, String) */
    @Override
    public String getCustomTitle(String sessionId, String defaultTitle) {
        return decodeHtmlEntities(resolveCustomTitle(sessionId, defaultTitle));
    }

    private static String resolveCustomTitle(String sessionId, String defaultTitle) {
        return NbPreferences.forModule(SessionManager.class).get(TITLE_PREFIX + sessionId, defaultTitle);
    }

    private static String decodeHtmlEntities(String input) {
        if (input == null) return null;
        return unescapeHtml4(input);
    }

    static void setCustomTitle(String sessionId, String title) {
        NbPreferences.forModule(SessionManager.class).put(TITLE_PREFIX + sessionId, title);
    }

    // --- hidden session flag (stored locally) -------------------------------
    private static final String HIDDEN_PREFIX = "session_hidden_";

    @Override
    public boolean isHidden(String sessionId) {
        return NbPreferences.forModule(SessionManager.class).getBoolean(HIDDEN_PREFIX + sessionId, false);
    }

    @Override
    public void setHidden(String sessionId, boolean hidden) {
        NbPreferences.forModule(SessionManager.class).putBoolean(HIDDEN_PREFIX + sessionId, hidden);
    }
    // -------------------------------------------------------------------------

    private static volatile SessionManager INSTANCE;

    private static final Logger LOG = Logger.from(SessionManager.class);

    private final ObjectMapper objectMapper = MapperSupplier.get();
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private final SessionStateMachine stateMachine = new SessionStateMachine();
    private volatile String currentSessionId;
    private volatile String lastProjectDir;
    private final SessionCacheManager cacheManager = new SessionCacheManager();
    private final Consumer<SessionUpdate> sseListener = this::handleSseUpdate;
    private final SessionRpcClient rpcClient;
    private volatile boolean sendResumeOnLoad;
    /** True when the server crashed and we're waiting for reconnect to resume. */
    private volatile boolean crashedBeforeReconnect;

    public SessionManager() {
        ACPProjectManager.getInstance().setProjectOpenListener(this::handleProjectOpened);
        ACPProjectManager.getInstance().setProjectCloseListener(this::handleProjectClosed);

        // Register for SSE updates to route them to the active session
        ProcessManager.getInstance().addSseListener(sseListener);
        this.rpcClient = new SessionRpcClient(ProcessManager.getInstance());

        // Reset state machine and notify UI when server crashes
        ProcessManager.getInstance().setCrashHandler(() -> {
            crashedBeforeReconnect = true;
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError(NbBundle.getMessage(SessionManager.class, "ERR_ServerDisconnected"));
        });

        // Auto-reload last session after successful reconnect
        ProcessManager.getInstance().setReadyHandler(() -> {
            String sid = currentSessionId;
            if (sid != null) {
                // Only send "Proceed" on reconnect after a crash — not on manual restart.
                if (crashedBeforeReconnect) {
                    crashedBeforeReconnect = false;
                    sendResumeOnLoad = true;
                }
                SwingUtilities.invokeLater(() -> loadSession(sid));
            }
        });

        // Fire onSessionLoading for UI backward compatibility
        stateMachine.addListener(newState -> {
            boolean loading = newState == SessionState.LOADING;
            for (SessionListener l : listeners) {
                l.onSessionLoading(loading);
            }
        });
    }

    private void handleSseUpdate(SessionUpdate update) {
        if (update.update() != null && update.update().sessions() != null) {
            for (Session s : update.update().sessions()) {
                cacheManager.cacheSession(s);
            }
        }

        String updateSessionId = update.params() != null ? update.params().sessionId() : null;
        if (updateSessionId != null
                && (updateSessionId.equals(currentSessionId)
                    || cacheManager.isDescendantOfCurrent(updateSessionId, currentSessionId))) {
            for (SessionListener l : listeners) {
                l.onSessionUpdate(update);
            }
        } else {
            LOG.fine("Ignoring update for background session: {0}", updateSessionId);
        }
    }

    @Override
    public boolean isDescendantOfCurrent(String sessionId) {
        return cacheManager.isDescendantOfCurrent(sessionId, currentSessionId);
    }

    public static SessionManager getInstance() {
        SessionManager sm = INSTANCE;
        if (sm == null) {
            synchronized (SessionManager.class) {
                sm = INSTANCE;
                if (sm == null) {
                    // @ServiceProvider registers under SessionControl.class,
                    // not SessionManager.class — look up the interface.
                    SessionControl sc = Lookup.getDefault().lookup(SessionControl.class);
                    if (sc instanceof SessionManager mgr) {
                        sm = mgr;
                    } else if (sc != null) {
                        // Interface found but different implementation — use it
                        // wrapped, or fall back to direct construction.
                        sm = new SessionManager();
                    } else {
                        sm = new SessionManager();
                    }
                    INSTANCE = sm;
                }
            }
        }
        return sm;
    }

    @Override
    public void addSessionListener(SessionListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeSessionListener(SessionListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    @Override
    public String getCurrentSessionDirectory() {
        return lastProjectDir;
    }

    @Override
    public SessionState getCurrentState() {
        return stateMachine.getState();
    }

    public SessionStateMachine getStateMachine() {
        return stateMachine;
    }

    @Override
    public boolean canSendMessage() {
        return stateMachine.canSendMessage();
    }

    @Override
    public boolean canStopMessage() {
        return stateMachine.canStopMessage();
    }

    @Override
    public void forceCancelCurrentMessage() {
        if (!stateMachine.canStopMessage()) {
            return;
        }
        stateMachine.transitionTo(SessionState.IDLE);
        String sid = this.currentSessionId;
        if (sid != null) {
            ProcessManager.getInstance().stopMessage(sid);
        }
    }

    // --- Session CRUD (moved from ProcessManager) ---

    public CompletableFuture<List<Session>> getSessions(String directory) {
        LOG.log(Level.FINE, "getSessions: called with directory={0}", directory);
        return ProcessManager.getInstance().getToolExecutor().waitForReady()
                .orTimeout(15, TimeUnit.SECONDS)
                .thenCompose(v -> {
                    return rpcClient.getSessions(directory);
                })
                .thenApply(res -> {
                    try {
                        LOG.log(Level.FINE, "getSessions: got response");
                        if (res == null) {
                            LOG.warn("getSessions: null response");
                            return new ArrayList<Session>();
                        }
                        JsonNode sessionsNode = res.has("sessions") ? res.get("sessions") : res.has("data") ? res.get("data") : res;
                        if (sessionsNode.isArray()) {
                            List<Session> rawSessions = objectMapper.readValue(sessionsNode.traverse(), new TypeReference<List<Session>>() {});
                            List<Session> sessions = new ArrayList<>();
                            for (Session s : rawSessions) {
                                Session resolved = s;
                                if (s.effectiveDirectory() == null) {
                                    resolved = new Session(s.id(), s.title(), directory, directory,
                                                           s.parentID(), s.updatedAt(), s.mcpServers(),
                                                           s.configOptions());
                                }
                                sessions.add(resolved);
                                cacheManager.cacheSession(resolved);
                            }
                            LOG.fine("getSessions: deserialized {0} sessions", sessions.size());
                            for (Session s : sessions) {
                                LOG.fine("getSessions: id={0}, title=''{1}'', directory={2}", s.id(), s.title(), s.effectiveDirectory());
                            }
                            return sessions;
                        } else {
                            LOG.warn("getSessions: sessionsNode is not an array: {0}", sessionsNode);
                            return new ArrayList<Session>();
                        }
                    } catch (IOException e) {
                        LOG.warn("getSessions: failed to deserialize: {0} {1}", e.getMessage(), e.toString());
                        return new ArrayList<Session>();
                    }
                })
                .exceptionally(ex -> {
                    LOG.warn("getSessions: rpc error: {0} {1}", ex.getMessage(), ex.toString());
                    return new ArrayList<>();
                });
    }

    public CompletableFuture<List<Session>> getSessionsForDirectories(List<String> directories) {
        if (directories == null || directories.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        LOG.fine("getSessionsForDirectories: querying {0} directories: {1}", directories.size(), directories);
        List<CompletableFuture<List<Session>>> futures = directories.stream()
                .map(dir -> getSessions(dir))
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(v -> futures.stream()
                .flatMap(f -> f.join().stream())
                .toList())
                .exceptionally(ex -> {
                    LOG.log(Level.WARNING, "Failed to get sessions within timeout: {0}", ex.getMessage());
                    return new ArrayList<>();
                });
    }

    @Override
    public CompletableFuture<Session> createSession(String cwd) {
        if (cwd == null) {
            cwd = System.getProperty("user.dir");
        }

        LOG.log(Level.FINE, "Creating new session with CWD: {0}", cwd);
        final String finalCwd = cwd;
        final long start = System.nanoTime();
        return withMcpFallback("session/new",
                () -> sendCreateSessionRequest(finalCwd, start), start);
    }

    private CompletableFuture<Session> sendCreateSessionRequest(String finalCwd, long start) {
        return ProcessManager.getInstance().getToolExecutor().waitForReady()
                .orTimeout(15, TimeUnit.SECONDS)
                .thenCompose(v -> {
                    return rpcClient.createSession(finalCwd);
                })
                .thenApply(res -> {
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    LOG.info("session/new completed in {0}ms", durationMs);
                    try {
                        Session s = objectMapper.treeToValue(res, Session.class);
                        if (s.effectiveDirectory() == null) {
                            s = new Session(s.id(), s.title(), finalCwd, finalCwd, s.parentID(), s.updatedAt(), s.mcpServers(), s.configOptions());
                        }
                        cacheManager.cacheSession(s);
                        return s;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public CompletableFuture<List<SessionConfigOption>> loadSessionFromServer(String sessionId, String cwd) {
        LOG.fine("loadSessionFromServer: called with {0}, cwd={1}", sessionId, cwd);
        final long start = System.nanoTime();
        return withMcpFallback("session/load",
                () -> sendLoadSessionRequest(sessionId, cwd, start), start)
                .exceptionally(ex -> {
                    LOG.warn("loadSessionFromServer: error: {0}", ex.getMessage());
                    return null;
                });
    }

    private CompletableFuture<List<SessionConfigOption>> sendLoadSessionRequest(String sessionId, String cwd, long start) {
        return ProcessManager.getInstance().getToolExecutor().waitForReady()
                .orTimeout(15, TimeUnit.SECONDS)
                .thenCompose(v -> {
                    return rpcClient.loadSessionFromServer(sessionId, cwd);
                })
                .thenApply(res -> {
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    LOG.info("session/load completed in {0}ms", durationMs);
                    LOG.fine("loadSessionFromServer: got response {0}", res);
                    if (res != null && res.has("configOptions")) {
                        try {
                            return objectMapper.convertValue(res.get("configOptions"), new TypeReference<List<SessionConfigOption>>() {});
                        } catch (Exception e) {
                            LOG.warn("Failed to parse configOptions: {0}", e.getMessage());
                        }
                    }
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        return rpcClient.setSessionConfigOption(sessionId, configId, value)
                .thenApply(res -> {
                    if (res != null && res.has("configOptions")) {
                        try {
                            List<SessionConfigOption> configOptions = objectMapper.convertValue(
                                    res.get("configOptions"), new TypeReference<List<SessionConfigOption>>() {});
                            if (sessionId.equals(currentSessionId)) {
                                notifySessionLoaded(sessionId, configOptions, false);
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to parse configOptions from set_config_option: {0}", e.getMessage());
                        }
                    }
                    return null;
                });
    }

    public CompletableFuture<JsonNode> renameSessionOnServer(String sessionId, String newTitle) {
        return rpcClient.renameSessionOnServer(sessionId, newTitle)
                .thenApply(v -> objectMapper.createObjectNode());
    }

    // --- High-level session operations ---

    @Override
    public void refreshSessions() {
        ProcessManager.getInstance().whenReady()
                .thenCompose(v -> {
                    Project[] openProjects = ACPProjectManager.getInstance().getAllOpenProjects();
                    List<String> openProjectDirs = new ArrayList<>();
                    for (Project p : openProjects) {
                        if (p != null) {
                            openProjectDirs.add(p.getProjectDirectory().getPath());
                        }
                    }
                    LOG.fine("refreshSessions: starting refresh for {0} projects", openProjectDirs.size());
                    if (openProjectDirs.isEmpty()) {
                        return CompletableFuture.completedFuture(new ArrayList<Session>());
                    }
                    return getSessionsForDirectories(openProjectDirs);
                })
                .thenAccept(sessions -> {
                    List<Session> filteredSessions = new ArrayList<>(sessions);
                    filteredSessions.sort((s1, s2) ->
                        Long.compare(parseTimestamp(s2.updatedAt()), parseTimestamp(s1.updatedAt()))
                    );

                    cacheManager.setCachedSessions(filteredSessions);
                    notifySessionListUpdated(filteredSessions);
                });
    }

    @Override
    public void refreshSessionList() {
        List<Session> cached = cacheManager.getCachedSessions();
        if (!cached.isEmpty()) {
            notifySessionListUpdated(cached);
        }
    }

    @Override
    public void createNewSession(String explicitCwd) {
        if (!stateMachine.transitionTo(SessionState.LOADING)) {
            LOG.warn("Cannot create session in state {0}", stateMachine.getState());
            return;
        }

        if (explicitCwd == null) {
            stateMachine.transitionTo(SessionState.IDLE);
            return;
        }

        notifySessionStarted(null);
        notifySessionProgress(10);

        notifySessionProgress(30);
        createSession(explicitCwd)
                .thenAccept(session -> {
                    // Guard: if closeSession() ran during the async window, the
                    // state machine is no longer LOADING. Discard the orphaned
                    // session to avoid setting currentSessionId while IDLE.
                    if (stateMachine.getState() != SessionState.LOADING) {
                        LOG.fine("createNewSession: discarding session {0}, state is {1}",
                                session.id(), stateMachine.getState());
                        return;
                    }
                    this.currentSessionId = session.id();
                    this.lastProjectDir = session.effectiveDirectory();
                    Logger.setSession(session.id(), session.title());

                    notifySessionProgress(60);

                    // Run on the CompletableFuture thread for consistency with loadSession
                    // The state machine is thread-safe and listeners marshall their own
                    // UI work onto the EDT, so an explicit invokeLater hop here is unnecessary.
                    stateMachine.transitionTo(SessionState.STREAMING);
                    notifySessionLoaded(session.id(), session.configOptions(), true);
                    refreshSessions();
                    if (!sendPreamble(session.id())) {
                        notifyPreambleDone();
                    }
                })
                .exceptionally(ex -> {
                    LOG.severe("Failed to create session", ex);
                    stateMachine.transitionTo(SessionState.IDLE);
                    notifyError(ex.getMessage());
                    return null;
                });
    }

    @Override
    public boolean loadSession(String sessionId) {
        return loadSession(sessionId, false);
    }

    @Override
    public boolean loadSession(String sessionId, boolean isStartup) {
        StrategyRegistry.invalidateSession(sessionId);
        if (!stateMachine.transitionTo(SessionState.LOADING)) {
            LOG.warn("Cannot load session in state {0}", stateMachine.getState());
            return false;
        }
        this.currentSessionId = sessionId;
        notifySessionStarted(sessionId);
        notifySessionProgress(10);

        // Look up session directory from cache
        String sessionCwd = cacheManager.getCachedSessions().stream()
                .filter(s -> s.id().equals(sessionId))
                .findFirst()
                .map(s -> {
                    Logger.setSession(s.id(), s.title());
                    return s.cwd() != null ? s.cwd() : s.directory();
                })
                .orElse(null);

        String workingCwd = sessionCwd != null ? sessionCwd : System.getProperty("user.dir");

        this.lastProjectDir = workingCwd;
        notifySessionProgress(30);
        loadSessionFromServer(sessionId, workingCwd)
                .thenAccept(configOptions -> {
                    if (sessionId.equals(this.currentSessionId)) {
                        notifySessionProgress(60);
                        stateMachine.transitionTo(SessionState.STREAMING);
                        notifySessionLoaded(sessionId, configOptions, isStartup);

                        // After reconnect, send an invisible "Proceed" prompt so the
                        // agent resumes execution from where it left off.
                        if (sendResumeOnLoad) {
                            sendResumeOnLoad = false;
                            sendResumePrompt(sessionId);
                        }
                    }
                })
                .exceptionally(ex -> {
                    if (sessionId.equals(this.currentSessionId)) {
                        stateMachine.transitionTo(SessionState.IDLE);
                        notifyError(NbBundle.getMessage(SessionManager.class, "ERR_LoadSessionFailed", ex.getMessage()));
                    }
                    return null;
                });
        return true;
    }

    @Override
    public void renameSession(String sessionId, String newTitle) {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }
        setCustomTitle(sessionId, newTitle.trim());
        // Update the cached session title so in-memory state is consistent
        Session s = cacheManager.getCachedSession(sessionId);
        if (s != null) {
            Session updated = new Session(s.id(), newTitle, s.cwd(), s.directory(),
                    s.parentID(), s.updatedAt(), s.mcpServers(), s.configOptions());
            cacheManager.cacheSession(updated);
            // Refresh the cached list to include the updated session
            List<Session> cached = cacheManager.getCachedSessions();
            List<Session> updatedList = new ArrayList<>(cached);
            for (int i = 0; i < updatedList.size(); i++) {
                if (sessionId.equals(updatedList.get(i).id())) {
                    updatedList.set(i, updated);
                    break;
                }
            }
            cacheManager.setCachedSessions(updatedList);
        }
        notifySessionRenamed(sessionId);
        // Sync the rename to the server asynchronously (fire-and-forget)
        renameSessionOnServer(sessionId, newTitle)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        LOG.log(Level.WARNING, "Failed to rename session on server: {0}", ex.getMessage());
                    }
                });
    }

    private void handleProjectOpened(String openedDir) {
        refreshSessions();
        getSessions(openedDir)
            .thenAccept(sessions -> {
                Session newest = sessions.stream()
                    .sorted((s1, s2) -> Long.compare(
                        parseTimestamp(s2.updatedAt()),
                        parseTimestamp(s1.updatedAt())))
                    .findFirst()
                    .orElse(null);
                if (newest != null) {
                    loadSession(newest.id());
                }
            });
    }

    private void handleProjectClosed(String closedDir) {
        // If the active session belongs to the closed project, close it first
        // so the UI and state machine are reset.
        if (closedDir.equals(lastProjectDir)) {
            closeSession();
        }
        // Refresh the session list — it only shows sessions for open projects,
        // so sessions for the closed project will disappear from the dropdown.
        refreshSessions();
    }

    /** Sends the initial preamble prompt for a new session.
     *  @return true if a preamble was sent, false if empty/skipped */
    private boolean sendPreamble(String sessionId) {
        String preamble = PluginSettings.getPreamble().trim();
        if (!preamble.isEmpty()) {
            sendAssistantPrompt(sessionId, preamble, "preamble");
            return true;
        }
        return false;
    }

    /**
     * Sends an invisible "Proceed" prompt after reconnect so the agent resumes
     * execution. The audience annotation marks it as assistant-directed, so the
     * UI does not render it as a user message.
     */
    private void sendResumePrompt(String sessionId) {
        sendAssistantPrompt(sessionId, "Proceed", "resume prompt");
    }

    private void sendAssistantPrompt(String sessionId, String text, String label) {
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", text);
        textBlock.put("annotations", Map.of("audience", List.of("assistant")));

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", List.of(textBlock));
        params.put("mcpServers", ProcessManager.getInstance().getToolExecutor().getServerConfig());

        ProcessManager.getInstance().sendRequest("session/prompt", params)
                .exceptionally(ex -> {
                    LOG.warn("Failed to send {0}: {1}", label, ExceptionUtils.getRootCauseMessage(ex));
                    notifyError("Connection lost while sending " + label + ": " + ExceptionUtils.getRootCauseMessage(ex));
                    return null;
                });
    }

    @Override
    public void closeSession() {
        String sessionId = this.currentSessionId;
        if (stateMachine.transitionTo(SessionState.IDLE)) {
            this.currentSessionId = null;
            for (SessionListener l : listeners) {
                l.onSessionLoading(false);
            }
        }
        if (sessionId != null) {
            StrategyRegistry.invalidateSession(sessionId);
        }
    }

    /** Release resources and unregister from ProcessManager SSE stream. */
    public void dispose() {
        ProcessManager.getInstance().removeSseListener(sseListener);
    }

    @Override
    public void stopCurrentMessage() {
        if (!stateMachine.canStopMessage()) {
            return;
        }
        LOG.info("stopCurrentMessage: transitioning STOPPING, scheduling 5s safety timeout");
        stateMachine.transitionTo(SessionState.STOPPING);
        String sid = this.currentSessionId;
        // Schedule safety timeout BEFORE potentially-blocking I/O, so the state
        // always recovers even if ProcessManager.stopMessage() blocks on pipe write.
        scheduleStopRecovery();
        if (sid != null) {
            ProcessManager.getInstance().stopMessage(sid);
        }
    }

    private void scheduleStopRecovery() {
        RequestProcessor.getDefault().post(() -> {
            if (stateMachine.transitionToIf(SessionState.STOPPING, SessionState.STREAMING)) {
                LOG.info("Safety timeout fired: transitioning STOPPING -> STREAMING");
            }
        }, 5000);
    }

    @Override
    public void onTurnEnded() {
        if (stateMachine.transitionToIf(SessionState.STOPPING, SessionState.STREAMING)) {
            LOG.info("onTurnEnded: transitioning STOPPING -> STREAMING");
        } else {
            LOG.fine("onTurnEnded: current state={0} (no transition needed)", stateMachine.getState());
        }
    }

    private void notifyPreambleDone() {
        for (SessionListener l : listeners) {
            l.onPreambleDone();
        }
    }

    private void notifySessionListUpdated(List<Session> sessions) {
        for (SessionListener l : listeners) {
            l.onSessionListUpdated(sessions);
        }
    }

    private void notifySessionRenamed(String sessionId) {
        for (SessionListener l : listeners) {
            l.onSessionRenamed(sessionId);
        }
    }

    private void notifySessionStarted(String sessionId) {
        for (SessionListener l : listeners) {
            l.onSessionStarted(sessionId);
        }
    }

    private void notifySessionLoaded(String sessionId, List<SessionConfigOption> options, boolean isStartup) {
        for (SessionListener l : listeners) {
            l.onSessionLoaded(sessionId, options, isStartup);
        }
    }

    private void notifySessionProgress(int percent) {
        for (SessionListener l : listeners) {
            l.onSessionProgress(percent);
        }
    }

    private void notifyError(String message) {
        for (SessionListener l : listeners) {
            l.onSessionError(message);
        }
    }

    /**
     * Executes a request function with automatic retry on InvalidParams errors
     * by disabling MCP server support and retrying once.
     */
    private <T> CompletableFuture<T> withMcpFallback(
            String operationName,
            Supplier<CompletableFuture<T>> requestFn,
            long startNanos) {
        return requestFn.get().handle((res, ex) -> {
            if (ex == null) return CompletableFuture.completedFuture(res);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            if (ex instanceof TimeoutException) {
                LOG.warn("{0} timed out after {1}ms", operationName, durationMs);
            } else {
                LOG.warn("{0} failed after {1}ms: {2}", operationName, durationMs, ex.getMessage());
            }
            Throwable cause = (ex instanceof CompletionException) ? ex.getCause() : ex;
            if (isInvalidParamsError(cause) && !ProcessManager.getInstance().getToolExecutor().isDisabled()) {
                LOG.warn("{0} failed with Invalid Params, retrying without MCP", operationName);
                ProcessManager.getInstance().getToolExecutor().disable();
                return requestFn.get();
            }
            return CompletableFuture.<T>failedFuture(ex);
        }).thenCompose(f -> f);
    }

    private long parseTimestamp(String ts) {
        if (ts == null) return 0;
        try {
            return OffsetDateTime.parse(ts).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isInvalidParamsError(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = t.getMessage();
        return msg != null && msg.contains("Invalid params");
    }
}
