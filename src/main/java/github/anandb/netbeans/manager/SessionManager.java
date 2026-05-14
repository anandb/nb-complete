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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

/**
 * Manages the state and lifecycle of chat sessions.
 * Decouples session logic from the AssistantTopComponent UI.
 */
public class SessionManager {
    private static final Logger LOG = new Logger(SessionManager.class);
    private static SessionManager instance;

    private final ObjectMapper objectMapper = MapperSupplier.get();
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private final SessionStateMachine stateMachine = new SessionStateMachine();
    private volatile String currentSessionId;
    private volatile String lastProjectDir;
    private final List<Session> cachedSessions = new CopyOnWriteArrayList<>();

    private SessionManager() {
        ACPProjectManager.getInstance().setProjectOpenListener(this::handleProjectOpened);
        ACPProjectManager.getInstance().setProjectCloseListener(this::handleProjectClosed);

        // Register for SSE updates to route them to the active session
        ProcessManager.getInstance().addSseListener(this::handleSseUpdate);

        // Reset state machine and notify UI when server crashes
        ProcessManager.getInstance().setCrashHandler(() -> {
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError("Server disconnected. Reconnecting...");
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
        String updateSessionId = update.params() != null ? update.params().sessionId() : null;
        if (updateSessionId != null && updateSessionId.equals(currentSessionId)) {
            for (SessionListener l : listeners) {
                l.onSessionUpdate(update);
            }
        } else {
            LOG.fine("Ignoring update for background session: {0}", updateSessionId);
        }
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
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

    public SessionStateMachine getStateMachine() {
        return stateMachine;
    }

    // --- Session CRUD (moved from ProcessManager) ---

    public CompletableFuture<List<Session>> getSessions(String directory) {
        LOG.log(Level.FINE, "getSessions: called with directory={0}", directory);
        return ProcessManager.getInstance().getMcpManager().waitForReady()
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
                                if (s.effectiveDirectory() != null) {
                                    sessions.add(s);
                                    continue;
                                }

                                sessions.add(new Session(s.id(), s.title(), directory, directory,
                                                         s.parentID(), s.updatedAt(), s.mcpServers(),
                                                         s.configOptions()));
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

        return sendCreateSessionRequest(finalCwd, start).handle((res, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(res);
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (ex instanceof java.util.concurrent.TimeoutException) {
                LOG.warn("session/new timed out after {0}ms", durationMs);
            } else {
                LOG.warn("session/new failed after {0}ms with error: {1}", durationMs, ex.getMessage());
            }
            Throwable cause = (ex instanceof java.util.concurrent.CompletionException) ? ex.getCause() : ex;
            if (isInvalidParamsError(cause) && !ProcessManager.getInstance().getMcpManager().isDisabled()) {
                LOG.warn("session/new failed with Invalid Params, retrying without MCP servers");
                ProcessManager.getInstance().getMcpManager().disable();
                return sendCreateSessionRequest(finalCwd, start);
            }
            return CompletableFuture.<Session>failedFuture(ex);
        }).thenCompose(f -> f);
    }

    private CompletableFuture<Session> sendCreateSessionRequest(String finalCwd, long start) {
        return ProcessManager.getInstance().getMcpManager().waitForReady()
                .orTimeout(15, TimeUnit.SECONDS)
                .thenCompose(v -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("cwd", finalCwd);
                    params.put("mcpServers", ProcessManager.getInstance().getMcpManager().getServerConfig());
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
                        return s;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public CompletableFuture<List<SessionConfigOption>> loadSessionFromServer(String sessionId, String cwd) {
        LOG.fine("loadSessionFromServer: called with {0}, cwd={1}", sessionId, cwd);
        final long start = System.nanoTime();
        return sendLoadSessionRequest(sessionId, cwd, start).handle((res, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(res);
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (ex instanceof java.util.concurrent.TimeoutException) {
                LOG.warn("session/load timed out after {0}ms", durationMs);
            } else {
                LOG.warn("session/load failed after {0}ms with error: {1}", durationMs, ex.getMessage());
            }
            Throwable cause = (ex instanceof java.util.concurrent.CompletionException) ? ex.getCause() : ex;
            if (isInvalidParamsError(cause) && !ProcessManager.getInstance().getMcpManager().isDisabled()) {
                LOG.warn("session/load failed with Invalid Params, retrying without MCP servers");
                ProcessManager.getInstance().getMcpManager().disable();
                return sendLoadSessionRequest(sessionId, cwd, start);
            }
            return CompletableFuture.<List<SessionConfigOption>>failedFuture(ex);
        }).thenCompose(f -> f)
        .exceptionally(ex -> {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            LOG.warn("loadSessionFromServer: error after {0}ms: {1}", durationMs, ex.getMessage());
            return null;
        });
    }

    private CompletableFuture<List<SessionConfigOption>> sendLoadSessionRequest(String sessionId, String cwd, long start) {
        return ProcessManager.getInstance().getMcpManager().waitForReady()
                .orTimeout(15, TimeUnit.SECONDS)
                .thenCompose(v -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("sessionId", sessionId);
                    if (cwd != null) {
                        params.put("cwd", cwd);
                    }
                    params.put("mcpServers", ProcessManager.getInstance().getMcpManager().getServerConfig());
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

                    SwingUtilities.invokeLater(() -> {
                        stateMachine.transitionTo(SessionState.STREAMING);
                        notifySessionLoaded(session.id(), session.configOptions(), true);
                        refreshSessions();
                        sendPreamble(session.id());
                    });
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
                        notifyError("Failed to load session: " + ex.getMessage());
                    }
                    return null;
                });
    }

    public void renameSession(String sessionId, String newTitle) {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }
        SessionTitleMapper.setTitle(sessionId, newTitle.trim());
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
        if (stateMachine.transitionTo(SessionState.IDLE)) {
            this.currentSessionId = null;
            for (SessionListener l : listeners) {
                l.onSessionLoading(false);
            }
        }
    }

    public void stopCurrentMessage() {
        if (!stateMachine.canStopMessage()) {
            return;
        }
        stateMachine.transitionTo(SessionState.STOPPING);
        String sid = this.currentSessionId;
        if (sid != null) {
            ProcessManager.getInstance().stopMessage(sid);
        }
    }

    public void onTurnEnded() {
        if (stateMachine.getState() == SessionState.STOPPING) {
            stateMachine.transitionTo(SessionState.IDLE);
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

    private long parseTimestamp(String ts) {
        if (ts == null) return 0;
        try {
            return java.time.OffsetDateTime.parse(ts).toInstant().toEpochMilli();
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
