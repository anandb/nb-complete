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
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import github.anandb.netbeans.support.PreferenceKeys;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.text.StringEscapeUtils.unescapeHtml4;

/**
 * Manages the state and lifecycle of chat sessions.
 * Decouples session logic from the AssistantTopComponent UI.
 *
 * <h3>Lifecycle</h3>
 * Sessions transition through states managed by {@link SessionStateMachine}:
 * {@code IDLE → LOADING → STREAMING → STOPPING → IDLE}. The state machine
 * enforces valid transitions and fires listeners on change.
 *
 * <h3>SSE Message Routing</h3>
 * Incoming {@code session/update} messages from the ACP server are received
 * via {@code SessionLifecycleHandler.displayMessage()} and dispatched to
 * {@code StrategyRegistry.handle()} for type-based routing. See
 * {@code strategy/StrategyRegistry.java} for the dispatch chain.
 *
 * <h3>Preamble</h3>
 * On new session creation, this class sends a combined prompt of
 * critical rules ({@link PluginSettings#getCriticalRules()}) followed by
 * the preamble ({@link PluginSettings#getPreamble()}), both as an
 * assistant-only message with {@code audience: ["assistant"]}.
 *
 * <h3>Reconnection</h3>
 * When the server disconnects, {@code AcpReconnectManager} handles up to 3
 * reconnection attempts with linear backoff (3s, 6s, 9s). On successful
 * reconnection, the current session is automatically reloaded.
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

    /** @see SessionQuery#getSessionTitle(String) */
    @Override
    public String getSessionTitle(String sessionId) {
        if (sessionId == null) return null;
        Session s = cacheManager.getCachedSession(sessionId);
        if (s == null) return null;
        String serverTitle = s.title();
        if (serverTitle == null || serverTitle.isBlank()) {
            serverTitle = sessionId;
        }
        return getCustomTitle(sessionId, serverTitle);
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

    // --- persisted context usage (used/size) ---------------------------------
    private static final String USAGE_PREFIX = "session_usage_";

    @Override
    public String getContextUsage(String sessionId) {
        return NbPreferences.forModule(SessionManager.class).get(USAGE_PREFIX + sessionId, null);
    }

    @Override
    public void setContextUsage(String sessionId, long used, long size) {
        NbPreferences.forModule(SessionManager.class).put(USAGE_PREFIX + sessionId, used + "," + size);
    }
    // -------------------------------------------------------------------------

    private static volatile SessionManager INSTANCE;

    private static final Logger LOG = Logger.from(SessionManager.class);

    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private final SessionStateMachine stateMachine = new SessionStateMachine();
    private volatile String currentSessionId;
    private volatile String lastProjectDir;
    private final SessionCacheManager cacheManager = new SessionCacheManager();
    private final Consumer<SessionUpdate> sseListener = this::handleSseUpdate;
    private final SessionRpcClient rpcClient;
    private volatile java.util.function.BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> beforePreambleHandler;
    /** True if a manual reconnect was initiated by the user. */
    private volatile boolean manualReconnectPending;

    public SessionManager() {
        ACPProjectManager.getInstance().setProjectOpenListener(this::handleProjectOpened);
        ACPProjectManager.getInstance().setProjectCloseListener(this::handleProjectClosed);

        // Register for SSE updates to route them to the active session
        ProcessManager.getInstance().addSseListener(sseListener);
        this.rpcClient = new SessionRpcClient(ProcessManager.getInstance());

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

    @Override
    public void setBeforePreambleHandler(java.util.function.BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> handler) {
        this.beforePreambleHandler = handler;
    }

    @Override
    public void scheduleManualReconnectPrompt() {
        this.manualReconnectPending = true;
    }

    // --- Session CRUD (moved from ProcessManager) ---

    public CompletableFuture<List<Session>> getSessions(String directory) {
        LOG.log(Level.FINE, "getSessions: called with directory={0}", directory);
        return ProcessManager.getInstance().getToolExecutor().waitForReady()
                .orTimeout(60, TimeUnit.SECONDS)
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
                            List<Session> rawSessions = MAPPER.readValue(sessionsNode.traverse(), new TypeReference<List<Session>>() {});
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
                        LOG.warn("getSessions: failed to deserialize: {0}", ExceptionUtils.getMessage(e), e);
                        return new ArrayList<Session>();
                    }
                });
    }

    public CompletableFuture<List<Session>> getSessionsForDirectories(List<String> directories) {
        if (directories == null || directories.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        LOG.fine("getSessionsForDirectories: querying {0} directories: {1}", directories.size(), directories);
        return getSessionsBatched(directories, 2, 0)
                .orTimeout(3, TimeUnit.MINUTES);
    }

    private CompletableFuture<List<Session>> getSessionsBatched(List<String> dirs, int batchSize, int startIndex) {
        if (startIndex >= dirs.size()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        int endIndex = Math.min(startIndex + batchSize, dirs.size());
        List<String> batch = dirs.subList(startIndex, endIndex);

        List<CompletableFuture<List<Session>>> futures = batch.stream()
                .map(dir -> getSessions(dir))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenCompose(v -> {
                    List<Session> currentBatch = new ArrayList<>();
                    for (CompletableFuture<List<Session>> f : futures) {
                        currentBatch.addAll(f.join());
                    }
                    return getSessionsBatched(dirs, batchSize, endIndex)
                            .thenApply(nextBatch -> {
                                currentBatch.addAll(nextBatch);
                                return currentBatch;
                            });
                });
    }

    @Override
    public CompletableFuture<Session> createSession(String cwd) {
        // Do NOT fall back to System.getProperty("user.dir") which points at the
        // IDE launcher directory and would cause the server to sandbox the session
        // in the wrong project. Fail fast instead.
        if (cwd == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("cwd must not be null — provide a valid project directory"));
        }
        LOG.log(Level.FINE, "Creating new session with CWD: {0}", cwd);
        final String finalCwd = cwd;
        final long start = System.nanoTime();
        return withMcpFallback("session/new",
                () -> sendCreateSessionRequest(finalCwd, start), start);
    }

    private CompletableFuture<Session> sendCreateSessionRequest(String finalCwd, long start) {
        return ProcessManager.getInstance().getToolExecutor().waitForReady()
                .orTimeout(60, TimeUnit.SECONDS)
                .thenCompose(v -> {
                    return rpcClient.createSession(finalCwd);
                })
                .thenApply(res -> {
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    LOG.info("session/new completed in {0}ms", durationMs);
                    try {
                        Session s = MAPPER.treeToValue(res, Session.class);
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
                    LOG.warn("loadSessionFromServer: error: {0}", ExceptionUtils.getMessage(ex), ex);
                    return null;
                });
    }

    private CompletableFuture<List<SessionConfigOption>> sendLoadSessionRequest(String sessionId, String cwd, long start) {
        return ProcessManager.getInstance().getToolExecutor().waitForReady()
                .orTimeout(2, TimeUnit.MINUTES)
                .thenCompose(v -> {
                    return rpcClient.loadSessionFromServer(sessionId, cwd);
                })
                .thenApply(res -> {
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    LOG.info("session/load completed in {0}ms", durationMs);
                    LOG.fine("loadSessionFromServer: got response {0}", res);
                    if (res != null && res.has("configOptions")) {
                        try {
                            return MAPPER.convertValue(res.get("configOptions"), new TypeReference<List<SessionConfigOption>>() {});
                        } catch (Exception e) {
                            LOG.warn("Failed to parse configOptions: {0}", ExceptionUtils.getMessage(e), e);
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
                            List<SessionConfigOption> configOptions = MAPPER.convertValue(
                                    res.get("configOptions"), new TypeReference<List<SessionConfigOption>>() {});
                            if (sessionId.equals(currentSessionId)) {
                                notifySessionLoaded(sessionId, configOptions, false);
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to parse configOptions from set_config_option: {0}", ExceptionUtils.getMessage(e), e);
                        }
                    }
                    return (Void) null;
                })
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        LOG.warn("Failed to set config {0}: {1}", configId, ExceptionUtils.getMessage(ex), ex);
                    }
                });
    }

    public CompletableFuture<JsonNode> renameSessionOnServer(String sessionId, String newTitle) {
        return rpcClient.renameSessionOnServer(sessionId, newTitle)
                .thenApply(v -> MAPPER.createObjectNode());
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
                })
                .exceptionally(ex -> {
                    LOG.warn("Failed to refresh sessions, keeping cache: {0}", ExceptionUtils.getRootCauseMessage(ex), ex);
                    return null;
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
        try {
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
                        if (!stateMachine.transitionTo(SessionState.STREAMING)) {
                            LOG.fine("createNewSession: transitionTo(STREAMING) failed, state is {0}",
                                    stateMachine.getState());
                            return;
                        }
                        notifySessionLoaded(session.id(), session.configOptions(), true);
                        checkAndPromptOpencodeInit(session.effectiveDirectory());
                        refreshSessions();
                        // Before preamble, let the UI handler (if set) show a config
                        // dialog so the user can pick agent/model/level.
                        if (beforePreambleHandler != null) {
                            beforePreambleHandler.apply(session.id(), session.configOptions())
                                .whenComplete((v, ex) -> {
                                    if (!sendPreamble(session.id())) {
                                        notifyPreambleDone();
                                    }
                                });
                        } else {
                            if (!sendPreamble(session.id())) {
                                notifyPreambleDone();
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        LOG.severe("Failed to create session", ex);
                        stateMachine.transitionTo(SessionState.IDLE);
                        notifyError(rootMessage(ex));
                        return null;
                    });
        } catch (Exception ex) {
            LOG.severe("Failed to create session", ex);
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError(rootMessage(ex));
        }
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
        // Post clearMessages() to EDT first, then set currentSessionId,
        // so SSE messages for the new session always arrive after the
        // old session's bubbles have been cleared.
        notifySessionStarted(sessionId);
        this.currentSessionId = sessionId;
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

        // Prefer the cached cwd; fall back to the last known project dir rather
        // than System.getProperty("user.dir") which may point at an unrelated project.
        String workingCwd = sessionCwd != null ? sessionCwd : lastProjectDir;

        this.lastProjectDir = workingCwd;
        notifySessionProgress(30);
        try {
            loadSessionFromServer(sessionId, workingCwd)
                    .thenAccept(configOptions -> {
                        if (sessionId.equals(this.currentSessionId)) {
                            notifySessionProgress(60);
                            stateMachine.transitionTo(SessionState.STREAMING);
                            notifySessionLoaded(sessionId, configOptions, isStartup);
                            checkAndPromptOpencodeInit(workingCwd);

                            if (manualReconnectPending) {
                                manualReconnectPending = false;
                                sendAssistantPrompt(sessionId, "Ask the user if you should continue ?", "reconnect prompt");
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        LOG.severe("Failed to load session async: {0}", ExceptionUtils.getMessage(ex), ex);
                        if (sessionId.equals(this.currentSessionId)) {
                            stateMachine.transitionTo(SessionState.IDLE);
                            notifyError(NbBundle.getMessage(SessionManager.class, "ERR_LoadSessionFailed", rootMessage(ex)));
                        }
                        return null;
                    });
        } catch (Exception ex) {
            LOG.severe("Failed to load session", ex);
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError(NbBundle.getMessage(SessionManager.class, "ERR_LoadSessionFailed", rootMessage(ex)));
        }
        return true;
    }

    @Override
    public void renameSession(String sessionId, String newTitle) {
        if (isBlank(newTitle)) {
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
                        LOG.warn("Failed to rename session on server: {0}", ExceptionUtils.getMessage(ex), ex);
                    }
                });
    }

    @SuppressWarnings("unused") // parameter required by the Consumer<String> listener
    private void handleProjectOpened(String openedDir) {
        refreshSessions();
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
        // Reset lastProjectDir when no more open projects remain, preventing
        // stale path matches from a prior session that was never set via a project.
        Project[] remaining = ACPProjectManager.getInstance().getAllOpenProjects();
        if (remaining == null || remaining.length == 0
                || (remaining.length == 1 && remaining[0] != null
                && remaining[0].getProjectDirectory().getPath().equals(closedDir))) {
            lastProjectDir = "";
            if (remaining == null || remaining.length == 0) {
                for (SessionListener l : listeners) {
                    l.onAllProjectsClosed();
                }
            }
        }
    }

    /** Sends the critical rules followed by the preamble prompt for a new session.
     *  @return true if a preamble was sent, false if empty/skipped */
    private boolean sendPreamble(String sessionId) {
        String rules = PluginSettings.getCriticalRules();
        String preamble = PluginSettings.getPreamble();

        StringBuilder combined = new StringBuilder();
        if (!isBlank(rules)) {
            combined.append(rules);
        }
        if (!isBlank(preamble)) {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append(preamble);
        }

        String text = combined.toString().trim();
        if (!text.isEmpty()) {
            sendAssistantPrompt(sessionId, text, "critical rules + preamble");
            return true;
        }

        return false;
    }

    private void sendAssistantPrompt(String sessionId, String text, String label) {
        for (SessionListener l : listeners) {
            l.onInternalMessageSent();
        }

        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", text);
        textBlock.put("annotations", Map.of("audience", List.of("assistant")));

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", List.of(textBlock));
        params.put("mcpServers", ProcessManager.getInstance().getToolExecutor().getServerConfig());

        ProcessManager.getInstance().sendRequest("session/prompt", params)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        LOG.warn("Failed to send {0}: {1}", label, ExceptionUtils.getRootCauseMessage(ex));
                        notifyError("Connection lost while sending " + label + ": " + ExceptionUtils.getRootCauseMessage(ex));
                    }
                    for (SessionListener l : listeners) {
                        l.onInternalMessageDone();
                    }
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
                LOG.warn("{0} failed after {1}ms: {2}", operationName, durationMs, ExceptionUtils.getMessage(ex));
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

    /** Unwrap the cause chain and return the root error message. */
    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = ExceptionUtils.getMessage(cause);
        return msg != null ? msg : ExceptionUtils.getMessage(ex);
    }

    private long parseTimestamp(String ts) {
        if (ts == null) return 0;
        try {
            return OffsetDateTime.parse(ts).toInstant().toEpochMilli();
        } catch (Exception e) {
            LOG.warn("Failed to parse timestamp: {0}", ts, e);
            return 0;
        }
    }

    private void checkAndPromptOpencodeInit(String cwd) {
        if (cwd == null) {
            return;
        }
        boolean promptEnabled = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .getBoolean(PreferenceKeys.PROMPT_OPENCODE_INIT, false);
        if (!promptEnabled) {
            return;
        }

        String safeKey = Base64.getEncoder().encodeToString(cwd.getBytes(StandardCharsets.UTF_8));
        String skipPrefKey = "opencode_skip_" + safeKey;
        boolean skipPrompt = NbPreferences.forModule(SessionManager.class).getBoolean(skipPrefKey, false);
        if (skipPrompt) {
            return;
        }

        File opencodeDir = new File(cwd, ".opencode");
        File opencodeJson = new File(opencodeDir, "opencode.json");
        if (opencodeJson.exists()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            // Recheck existance in EDT just in case
            if (opencodeJson.exists()) {
                return;
            }

            // Using github.anandb.netbeans.ui.Bundle constants, loaded via NbBundle
            // Unfortunately, SessionManager is in manager package, so we must load from ui package bundle.
            String title = NbBundle.getMessage(github.anandb.netbeans.ui.AssistantTopComponent.class, "TITLE_InitOpencode");
            String msg = NbBundle.getMessage(github.anandb.netbeans.ui.AssistantTopComponent.class, "MSG_InitOpencode");
            String btnYes = NbBundle.getMessage(github.anandb.netbeans.ui.AssistantTopComponent.class, "BTN_InitYes");
            String btnNo = NbBundle.getMessage(github.anandb.netbeans.ui.AssistantTopComponent.class, "BTN_InitNo");
            String btnLater = NbBundle.getMessage(github.anandb.netbeans.ui.AssistantTopComponent.class, "BTN_InitLater");
            String btnOff = NbBundle.getMessage(github.anandb.netbeans.ui.AssistantTopComponent.class, "BTN_InitOff");

            NotifyDescriptor nd = new NotifyDescriptor(
                    msg,
                    title,
                    NotifyDescriptor.DEFAULT_OPTION,
                    NotifyDescriptor.QUESTION_MESSAGE,
                    new Object[]{btnYes, btnNo, btnLater, btnOff},
                    btnYes
            );

            Object result = DialogDisplayer.getDefault().notify(nd);

            if (btnYes.equals(result)) {
                try {
                    if (!opencodeDir.exists() && !opencodeDir.mkdirs()) {
                        LOG.warn("Failed to create .opencode directory");
                        return;
                    }
                    try (InputStream is = SessionManager.class.getResourceAsStream("/github/anandb/netbeans/support/opencode.json.template")) {
                        if (is != null) {
                            Files.copy(is, opencodeJson.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            LOG.warn("opencode.json.template not found in resources");
                        }
                    }
                } catch (IOException ex) {
                    LOG.warn("Error creating opencode.json", ex);
                }
            } else if (btnNo.equals(result)) {
                NbPreferences.forModule(SessionManager.class).putBoolean(skipPrefKey, true);
            } else if (btnOff.equals(result)) {
                NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                        .putBoolean(PreferenceKeys.PROMPT_OPENCODE_INIT, false);
            }
            // If btnLater, do nothing, so it will prompt again
        });
    }

    private boolean isInvalidParamsError(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = ExceptionUtils.getMessage(t);
        return msg != null && msg.contains("Invalid params");
    }
}
