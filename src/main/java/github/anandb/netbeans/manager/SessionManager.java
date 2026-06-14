package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
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
        return resolveCustomTitle(sessionId, defaultTitle);
    }

    private static String resolveCustomTitle(String sessionId, String defaultTitle) {
        return NbPreferences.forModule(SessionManager.class).get(TITLE_PREFIX + sessionId, defaultTitle);
    }

    static void setCustomTitle(String sessionId, String title) {
        NbPreferences.forModule(SessionManager.class).put(TITLE_PREFIX + sessionId, title);
    }
    // -------------------------------------------------------------------------

    private static volatile SessionManager INSTANCE;

    private static final Logger LOG = Logger.from(SessionManager.class);

    private final ObjectMapper objectMapper = MapperSupplier.get();
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private final SessionStateMachine stateMachine = new SessionStateMachine();
    private volatile String currentSessionId;
    private volatile String lastProjectDir;
    private final List<Session> cachedSessions = new CopyOnWriteArrayList<>();
    private final Map<String, Session> sessionCacheMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final Consumer<SessionUpdate> sseListener = this::handleSseUpdate;

    public SessionManager() {
        ACPProjectManager.getInstance().setProjectOpenListener(this::handleProjectOpened);
        ACPProjectManager.getInstance().setProjectCloseListener(this::handleProjectClosed);

        // Register for SSE updates to route them to the active session
        ProcessManager.getInstance().addSseListener(sseListener);

        // Reset state machine and notify UI when server crashes
        ProcessManager.getInstance().setCrashHandler(() -> {
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError(NbBundle.getMessage(SessionManager.class, "ERR_ServerDisconnected"));
        });

        // Auto-reload last session after successful reconnect
        ProcessManager.getInstance().setReadyHandler(() -> {
            String sid = currentSessionId;
            if (sid != null) {
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
                if (s.id() != null) {
                    sessionCacheMap.put(s.id(), s);
                }
            }
        }

        String updateSessionId = update.params() != null ? update.params().sessionId() : null;
        if (updateSessionId != null && (updateSessionId.equals(currentSessionId) || isDescendantOfCurrent(updateSessionId))) {
            for (SessionListener l : listeners) {
                l.onSessionUpdate(update);
            }
        } else {
            LOG.fine("Ignoring update for background session: {0}", updateSessionId);
        }
    }

    @Override
    public boolean isDescendantOfCurrent(String sessionId) {
        if (sessionId == null || currentSessionId == null) {
            return false;
        }
        Session s = sessionCacheMap.get(sessionId);
        int depth = 0;
        while (s != null && depth < 20) {
            String parentId = s.parentID();
            if (parentId == null) {
                break;
            }
            if (parentId.equals(currentSessionId)) {
                return true;
            }
            s = sessionCacheMap.get(parentId);
            depth++;
        }
        return false;
    }

    public static SessionManager getInstance() {
        SessionManager sm = INSTANCE;
        if (sm == null) {
            synchronized (SessionManager.class) {
                sm = INSTANCE;
                if (sm == null) {
                    sm = Lookup.getDefault().lookup(SessionManager.class);
                    if (sm == null) {
                        sm = new SessionManager();
                    }
                    INSTANCE = sm;
                }
            }
        }
        return sm;
    }

    public void addSessionListener(SessionListener listener) {
        listeners.add(listener);
    }

    public void removeSessionListener(SessionListener listener) {
        listeners.remove(listener);
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

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
                    Map<String, Object> params = new HashMap<>();
                    if (directory != null && !directory.isEmpty()) {
                        params.put("cwd", directory);
                    }
                    return ProcessManager.getInstance().sendRequest("session/list", params);
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
                                if (resolved.id() != null) {
                                    sessionCacheMap.put(resolved.id(), resolved);
                                }
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
                    Map<String, Object> params = new HashMap<>();
                    params.put("cwd", finalCwd);
                    params.put("mcpServers", ProcessManager.getInstance().getToolExecutor().getServerConfig());
                    return ProcessManager.getInstance().sendRequest("session/new", params, 60, TimeUnit.SECONDS);
                })
                .thenApply(res -> {
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    LOG.info("session/new completed in {0}ms", durationMs);
                    try {
                        Session s = objectMapper.treeToValue(res, Session.class);
                        if (s.effectiveDirectory() == null) {
                            s = new Session(s.id(), s.title(), finalCwd, finalCwd, s.parentID(), s.updatedAt(), s.mcpServers(), s.configOptions());
                        }
                        if (s.id() != null) {
                            sessionCacheMap.put(s.id(), s);
                        }
                        return s;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

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
                    Map<String, Object> params = new HashMap<>();
                    params.put("sessionId", sessionId);
                    if (cwd != null) {
                        params.put("cwd", cwd);
                    }
                    params.put("mcpServers", ProcessManager.getInstance().getToolExecutor().getServerConfig());
                    return ProcessManager.getInstance().sendRequest("session/load", params, 60, TimeUnit.SECONDS);
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

    public CompletableFuture<Void> deleteSession(String sessionId) {
        return ProcessManager.getInstance().sendRequest("session/delete", Map.of("sessionId", sessionId))
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        Map<String, Object> params = Map.of(
                "sessionId", sessionId,
                "configId", configId,
                "value", value
        );
        return ProcessManager.getInstance().sendRequest("session/set_config_option", params)
                .thenApply(v -> null);
    }

    public CompletableFuture<JsonNode> renameSessionOnServer(String sessionId, String newTitle) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("sessionUpdate", "session_info_update");
        update.put("title", newTitle);
        params.set("update", update);

        return ProcessManager.getInstance().sendRequest("session/update", params);
    }

    // --- High-level session operations ---

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

                    cachedSessions.clear();
                    cachedSessions.addAll(filteredSessions);
                    notifySessionListUpdated(filteredSessions);
                });
    }

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

        createSession(explicitCwd)
                .thenAccept(session -> {
                    this.currentSessionId = session.id();
                    this.lastProjectDir = session.effectiveDirectory();
                    Logger.setSession(session.id(), session.title());

                    // Run on the CompletableFuture thread for consistency with loadSession
                    // The state machine is thread-safe and listeners marshall their own
                    // UI work onto the EDT, so an explicit invokeLater hop here is unnecessary.
                    stateMachine.transitionTo(SessionState.STREAMING);
                    notifySessionLoaded(session.id(), session.configOptions(), true);
                    refreshSessions();
                    sendPreamble(session.id());
                })
                .exceptionally(ex -> {
                    LOG.severe("Failed to create session", ex);
                    stateMachine.transitionTo(SessionState.IDLE);
                    notifyError(ex.getMessage());
                    return null;
                });
    }

    public void loadSession(String sessionId) {
        loadSession(sessionId, false);
    }

    public void loadSession(String sessionId, boolean isStartup) {
        StrategyRegistry.invalidateSession(sessionId);
        if (!stateMachine.transitionTo(SessionState.LOADING)) {
            LOG.warn("Cannot load session in state {0}", stateMachine.getState());
            return;
        }
        this.currentSessionId = sessionId;
        notifySessionStarted(sessionId);

        // Look up session directory from cache
        String sessionCwd = cachedSessions.stream()
                .filter(s -> s.id().equals(sessionId))
                .findFirst()
                .map(s -> {
                    Logger.setSession(s.id(), s.title());
                    return s.cwd() != null ? s.cwd() : s.directory();
                })
                .orElse(null);

        String workingCwd = sessionCwd != null ? sessionCwd : System.getProperty("user.dir");

        this.lastProjectDir = workingCwd;
        loadSessionFromServer(sessionId, workingCwd)
                .thenAccept(configOptions -> {
                    if (sessionId.equals(this.currentSessionId)) {
                        stateMachine.transitionTo(SessionState.STREAMING);
                        notifySessionLoaded(sessionId, configOptions, isStartup);
                    }
                })
                .exceptionally(ex -> {
                    if (sessionId.equals(this.currentSessionId)) {
                        stateMachine.transitionTo(SessionState.IDLE);
                        notifyError(NbBundle.getMessage(SessionManager.class, "ERR_LoadSessionFailed", ex.getMessage()));
                    }
                    return null;
                });
    }

    public void renameSession(String sessionId, String newTitle) {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }
        setCustomTitle(sessionId, newTitle.trim());
        refreshSessions();
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
        getSessions(closedDir)
                .thenAccept(closedDirSessions -> {
                    for (Session s : closedDirSessions) {
                        deleteSession(s.id());
                    }
                    refreshSessions();
                });
    }

    private void sendPreamble(String sessionId) {
        String preamble = PluginSettings.getPreamble().trim();
        if (!preamble.isEmpty()) {
            CompletableFuture<JsonNode> future = ProcessManager.getInstance().sendMessage(sessionId, preamble, null);
            if (future != null) {
                future.exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    LOG.warn("Failed to send preamble: {0}", cause.getMessage());
                    notifyError("Connection lost while sending preamble: " + cause.getMessage());
                    return null;
                });
            }
        }
    }

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

    public void onTurnEnded() {
        if (stateMachine.transitionToIf(SessionState.STOPPING, SessionState.STREAMING)) {
            LOG.info("onTurnEnded: transitioning STOPPING -> STREAMING");
        } else {
            LOG.fine("onTurnEnded: current state={0} (no transition needed)", stateMachine.getState());
        }
    }

    private void notifySessionListUpdated(List<Session> sessions) {
        for (SessionListener l : listeners) {
            l.onSessionListUpdated(sessions);
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
