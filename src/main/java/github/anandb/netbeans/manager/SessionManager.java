package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.ConfigOptionConverter;
import github.anandb.netbeans.model.ModelsInfo;
import github.anandb.netbeans.model.ModesInfo;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.ProjectQuery;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.project.Project;

import javax.swing.SwingUtilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import java.util.function.Supplier;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.prefs.Preferences;
import java.util.logging.Level;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.manager.strategy.StrategyRegistry;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PreferenceKeys;
import org.apache.commons.lang3.exception.ExceptionUtils;

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

    private static final String TITLE_PREFIX = "session_title_";
    private static final String HIDDEN_PREFIX = "session_hidden_";
    private static final String USAGE_PREFIX = "session_usage_";
    private static final String LOCAL_SESSIONS_KEY = "gemini_local_sessions";

    /**
     * Returns the current harness ID from preferences, or {@code null} if none
     * is configured. Used to build agent-qualified preference keys.
     */
    private static String agentName() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .get(PreferenceKeys.ACP_HARNESS_ID, null);
    }

    /** True when the harness has no session/list and the plugin tracks IDs locally. */
    private static boolean tracksSessionsLocally() {
        ProcessControl pc = Lookup.getDefault().lookup(ProcessControl.class);
        return pc != null && !pc.getCapabilities().supportsSessionList();
    }

    private static List<String> openProjectDirectories() {
        ProjectQuery projectQuery = Lookup.getDefault().lookup(ProjectQuery.class);
        Project[] openProjects = projectQuery == null
                ? new Project[0] : projectQuery.getAllOpenProjects();
        List<String> dirs = new ArrayList<>();
        for (Project p : openProjects) {
            if (p != null) {
                String path = p.getProjectDirectory().getPath();
                if (!dirs.contains(path)) {
                    dirs.add(path);
                }
            }
        }
        return dirs;
    }

    /**
     * Keeps sessions whose cwd (or directory) is one of the open project roots.
     * Same visibility rule as session/list: a closed project must not keep
     * client-tracked Gemini rows in the dropdown. IDs stay in prefs so the
     * session returns when that project is opened again.
     */
    static List<Session> retainSessionsForDirectories(List<Session> sessions, List<String> directories) {
        if (sessions == null || sessions.isEmpty()
                || directories == null || directories.isEmpty()) {
            return new ArrayList<>();
        }
        List<Session> retained = new ArrayList<>();
        for (Session s : sessions) {
            String cwd = s.cwd() != null ? s.cwd() : s.directory();
            if (cwd != null && directories.contains(cwd)) {
                retained.add(s);
            }
        }
        return retained;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record SessionMetadata(
        @JsonProperty("title") String title,
        @JsonProperty("usage") String usage,
        @JsonProperty("hidden") boolean hidden,
        @JsonProperty("cwd") String cwd
    ) {}

    private final Map<String, SessionMetadata> metadataCache = new ConcurrentHashMap<>();
    private String cachedAgent = null;

    private synchronized Map<String, SessionMetadata> getMetadataCache() {
        String agent = agentName();
        if (cachedAgent == null || !cachedAgent.equals(agent)) {
            metadataCache.clear();
            metadataCache.putAll(loadMetadataMap());
            cachedAgent = agent;
        }
        return metadataCache;
    }

    private Map<String, SessionMetadata> loadMetadataMap() {
        Map<String, SessionMetadata> result = new ConcurrentHashMap<>();
        Preferences node = metadataNode();
        migrateBlobMetadata(node);
        try {
            for (String id : node.keys()) {
                String json = node.get(id, null);
                if (json == null || json.isEmpty()) {
                    continue;
                }
                try {
                    result.put(id, MAPPER.readValue(json, SessionMetadata.class));
                } catch (Exception e) {
                    LOG.warn("Failed to deserialize session metadata for {0}: {1}",
                            id, ExceptionUtils.getMessage(e), e);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to list session metadata keys: {0}", ExceptionUtils.getMessage(e), e);
        }
        return result;
    }

    /** One JSON object per session. NbPreferences values are capped at 8KB. */
    private Preferences metadataNode() {
        String agent = agentName();
        String name = agent != null ? "sessmeta_" + agent.replaceAll("[^A-Za-z0-9._-]", "_") : "sessmeta";
        if (name.length() > Preferences.MAX_NAME_LENGTH) {
            name = name.substring(0, Preferences.MAX_NAME_LENGTH);
        }
        return NbPreferences.forModule(SessionManager.class).node(name);
    }

    private void migrateBlobMetadata(Preferences node) {
        String agent = agentName();
        String blobKey = agent != null
                ? LOCAL_SESSIONS_KEY + "_" + agent + "_metadata" : LOCAL_SESSIONS_KEY + "_metadata";
        Preferences prefs = NbPreferences.forModule(SessionManager.class);
        String json = prefs.get(blobKey, null);
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            Map<String, SessionMetadata> blob = MAPPER.readValue(json,
                    new TypeReference<Map<String, SessionMetadata>>() {});
            for (Map.Entry<String, SessionMetadata> entry : blob.entrySet()) {
                persistOne(node, entry.getKey(), entry.getValue());
            }
            prefs.remove(blobKey);
        } catch (Exception e) {
            LOG.warn("Failed to migrate session metadata blob: {0}", ExceptionUtils.getMessage(e), e);
        }
    }

    private void persistOne(String sessionId, SessionMetadata meta) {
        persistOne(metadataNode(), sessionId, meta);
    }

    private void persistOne(Preferences node, String sessionId, SessionMetadata meta) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String key = sessionId.length() > Preferences.MAX_KEY_LENGTH
                ? sessionId.substring(0, Preferences.MAX_KEY_LENGTH) : sessionId;
        try {
            String json = MAPPER.writeValueAsString(meta);
            if (json.length() > Preferences.MAX_VALUE_LENGTH) {
                String title = meta.title();
                int overflow = json.length() - Preferences.MAX_VALUE_LENGTH + 16;
                if (title != null && title.length() > overflow) {
                    title = title.substring(0, Math.max(0, title.length() - overflow));
                    json = MAPPER.writeValueAsString(
                            new SessionMetadata(title, meta.usage(), meta.hidden(), meta.cwd()));
                }
            }
            if (json.length() > Preferences.MAX_VALUE_LENGTH) {
                LOG.warn("Session metadata for {0} exceeds preferences value limit; not saved", sessionId);
                return;
            }
            node.put(key, json);
        } catch (Exception e) {
            LOG.warn("Failed to persist session metadata for {0}: {1}",
                    sessionId, ExceptionUtils.getMessage(e), e);
        }
    }

    private synchronized void updateMetadata(String sessionId,
            Function<SessionMetadata, SessionMetadata> updater) {
        Map<String, SessionMetadata> cache = getMetadataCache();
        SessionMetadata current = cache.get(sessionId);
        if (current == null) {
            current = new SessionMetadata(null, null, false, null);
        }
        SessionMetadata updated = updater.apply(current);
        cache.put(sessionId, updated);
        persistOne(sessionId, updated);
    }

    /**
     * One-shot copy of pre-metadata preference keys into the JSON map.
     * Skipped when a row already exists so {@code setHidden(false)} is not
     * overwritten by leftover {@code session_hidden_*} keys.
     */
    private synchronized void migrateLegacyMetadataIfAbsent(String sessionId) {
        Map<String, SessionMetadata> cache = getMetadataCache();
        if (cache.containsKey(sessionId)) {
            return;
        }
        String title = readLegacyString(TITLE_PREFIX, sessionId);
        String usage = readLegacyString(USAGE_PREFIX, sessionId);
        boolean hidden = readLegacyBoolean(HIDDEN_PREFIX, sessionId);
        if (title == null && usage == null && !hidden) {
            return;
        }
        cache.put(sessionId, new SessionMetadata(title, usage, hidden, null));
        persistOne(sessionId, cache.get(sessionId));
        removeLegacyKeys(TITLE_PREFIX, sessionId);
        removeLegacyKeys(USAGE_PREFIX, sessionId);
        removeLegacyKeys(HIDDEN_PREFIX, sessionId);
    }

    private String readLegacyString(String prefix, String sessionId) {
        Preferences prefs = NbPreferences.forModule(SessionManager.class);
        String agent = agentName();
        String qualified = agent != null ? prefix + agent + "_" + sessionId : prefix + sessionId;
        String val = prefs.get(qualified, null);
        if (val == null && agent != null) {
            val = prefs.get(prefix + sessionId, null);
        }
        return val;
    }

    private boolean readLegacyBoolean(String prefix, String sessionId) {
        Preferences prefs = NbPreferences.forModule(SessionManager.class);
        String agent = agentName();
        String qualified = agent != null ? prefix + agent + "_" + sessionId : prefix + sessionId;
        boolean val = prefs.getBoolean(qualified, false);
        if (!val && agent != null) {
            val = prefs.getBoolean(prefix + sessionId, false);
        }
        return val;
    }

    private void removeLegacyKeys(String prefix, String sessionId) {
        Preferences prefs = NbPreferences.forModule(SessionManager.class);
        String agent = agentName();
        prefs.remove(prefix + sessionId);
        if (agent != null) {
            prefs.remove(prefix + agent + "_" + sessionId);
        }
    }

    /** @see SessionQuery#getCustomTitle(String, String) */
    @Override
    public String getCustomTitle(String sessionId, String defaultTitle) {
        if (sessionId == null) return defaultTitle;
        migrateLegacyMetadataIfAbsent(sessionId);
        SessionMetadata meta = getMetadataCache().get(sessionId);
        String val = (meta != null) ? meta.title() : null;
        if (val == null) {
            String legacy = readLegacyString(TITLE_PREFIX, sessionId);
            if (legacy != null) {
                updateMetadata(sessionId, m -> new SessionMetadata(legacy, m.usage(), m.hidden(), m.cwd()));
                removeLegacyKeys(TITLE_PREFIX, sessionId);
                val = legacy;
            }
        }
        return decodeHtmlEntities(val != null ? val : defaultTitle);
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
        SessionManager instance = getInstance();
        instance.updateMetadata(sessionId, m -> new SessionMetadata(title, m.usage(), m.hidden(), m.cwd()));
        instance.removeLegacyKeys(TITLE_PREFIX, sessionId);
    }

    // --- hidden session flag (stored locally) -------------------------------

    @Override
    public boolean isHidden(String sessionId) {
        if (sessionId == null) return false;
        migrateLegacyMetadataIfAbsent(sessionId);
        SessionMetadata meta = getMetadataCache().get(sessionId);
        return meta != null && meta.hidden();
    }

    @Override
    public void setHidden(String sessionId, boolean hidden) {
        updateMetadata(sessionId, m -> new SessionMetadata(m.title(), m.usage(), hidden, m.cwd()));
        removeLegacyKeys(HIDDEN_PREFIX, sessionId);
    }
    // -------------------------------------------------------------------------

    // --- persisted context usage (used/size) ---------------------------------

    @Override
    public String getContextUsage(String sessionId) {
        if (sessionId == null) return null;
        migrateLegacyMetadataIfAbsent(sessionId);
        SessionMetadata meta = getMetadataCache().get(sessionId);
        String val = (meta != null) ? meta.usage() : null;
        if (val == null) {
            String legacy = readLegacyString(USAGE_PREFIX, sessionId);
            if (legacy != null) {
                updateMetadata(sessionId, m -> new SessionMetadata(m.title(), legacy, m.hidden(), m.cwd()));
                removeLegacyKeys(USAGE_PREFIX, sessionId);
                val = legacy;
            }
        }
        return val;
    }

    @Override
    public Session getSession(String sessionId) {
        return cacheManager.getCachedSession(sessionId);
    }

    @Override
    public void setContextUsage(String sessionId, long used, long size) {
        updateMetadata(sessionId, m -> new SessionMetadata(m.title(), used + "," + size, m.hidden(), m.cwd()));
        removeLegacyKeys(USAGE_PREFIX, sessionId);
    }

    // --- locally-created sessions persistence (for agents without session/list) -

    /** Persists the set of locally-created sessions to NbPreferences. */
    private void saveLocallyCreatedSessionIds() {
        String agent = agentName();
        String key = agent != null ? LOCAL_SESSIONS_KEY + "_" + agent : LOCAL_SESSIONS_KEY;
        String value = String.join(",", cacheManager.getLocallyCreatedIds(agent));
        NbPreferences.forModule(SessionManager.class).put(key, value);
    }

    /** Loads locally-created session IDs from NbPreferences into the cache manager. */
    private void loadLocallyCreatedSessionIds() {
        String agent = agentName();
        String key = agent != null ? LOCAL_SESSIONS_KEY + "_" + agent : LOCAL_SESSIONS_KEY;
        String value = NbPreferences.forModule(SessionManager.class).get(key, null);
        if (value != null && !value.isEmpty()) {
            String[] ids = value.split(",");
            Map<String, SessionMetadata> metadata = getMetadataCache();
            // Data repair: sessions saved before the cwd field existed carry no
            // project directory. With exactly one project open they can be
            // attributed safely — backfill and persist the repair once. With
            // several projects open the ambiguity is left to the load-time
            // fallback instead of guessing.
            ProjectQuery projectQuery = Lookup.getDefault().lookup(ProjectQuery.class);
            Project[] openProjects = projectQuery == null
                    ? new Project[0] : projectQuery.getAllOpenProjects();
            String repairCwd = openProjects.length == 1 && openProjects[0] != null
                    ? openProjects[0].getProjectDirectory().getPath() : null;
            // Load in reverse order since cacheManager.addLocallyCreated prepends
            for (int i = ids.length - 1; i >= 0; i--) {
                String sessionId = ids[i];
                if (sessionId != null && !sessionId.isEmpty()) {
                    String title = getCustomTitle(sessionId, sessionId);
                    SessionMetadata meta = metadata.get(sessionId);
                    String cwd = meta != null ? meta.cwd() : null;
                    if (cwd == null && repairCwd != null) {
                        LOG.info("Repairing missing cwd for session {0}: {1}",
                                sessionId, repairCwd);
                        updateMetadata(sessionId, m -> new SessionMetadata(
                                m.title(), m.usage(), m.hidden(), m.cwd() != null ? m.cwd() : repairCwd));
                        cwd = repairCwd;
                    }
                    Session s = new Session(sessionId, title, cwd, cwd, null, null, null, null, null, null);
                    cacheManager.addLocallyCreated(s, agent);
                }
            }
        }
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
    private volatile BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> beforePreambleHandler;
    /** True if a manual reconnect was initiated by the user. */
    private volatile boolean manualReconnectPending;

    public SessionManager() {
        ProjectQuery projectQuery = Lookup.getDefault().lookup(ProjectQuery.class);
        if (projectQuery != null) {
            projectQuery.setProjectOpenListener(this::handleProjectOpened);
            projectQuery.setProjectCloseListener(this::handleProjectClosed);
        }

        // Register for SSE updates to route them to the active session
        ProcessManager.getInstance().addSseListener(sseListener);
        this.rpcClient = new SessionRpcClient(ProcessManager.getInstance());

        // Reset state machine and notify UI when server crashes
        ProcessManager.getInstance().setCrashHandler(() -> {
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError(NbBundle.getMessage(SessionManager.class, "ERR_ServerDisconnected"));
        });

        // Reset sticky session state on EVERY manual server restart (Restart
        // Server button, global-config flow, executable-path preference change).
        // The crash handler above covers the disconnect path; without this the
        // manual restart path leaves a LOADING/STOPPING state machine in place,
        // which makes the post-restart loadSession() refuse to run and freezes
        // the panel until the whole IDE is restarted.
        ProcessManager.getInstance().setPreRestartHandler(this::resetForServerRestart);

        // Auto-reload last session after successful reconnect
        ProcessManager.getInstance().setReadyHandler(() -> {
            String sid = currentSessionId;
            if (sid != null) {
                SwingUtilities.invokeLater(() -> loadSession(sid));
            }
        });

        // Fire onSessionLoading for UI backward compatibility — copy avoids CME if listener mutates list
        stateMachine.addListener(newState -> {
            boolean loading = newState == SessionState.LOADING;
            new ArrayList<>(listeners).forEach(l -> l.onSessionLoading(loading));
        });
    }

    private void handleSseUpdate(SessionUpdate update) {
        if (update.update() != null && update.update().sessions() != null) {
            update.update().sessions().forEach(cacheManager::cacheSession);
        }

        // Capture once: currentSessionId is volatile and can change between the
        // equality check and the descendant query, routing updates to the wrong
        // session (TOCTOU).
        String currentSid = this.currentSessionId;
        String updateSessionId = update.params() != null ? update.params().sessionId() : null;
        if (updateSessionId != null
                && (updateSessionId.equals(currentSid)
                    || cacheManager.isDescendantOfCurrent(updateSessionId, currentSid))) {
            new ArrayList<>(listeners).forEach(l -> l.onSessionUpdate(update));
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
                    } else {
                        // No registered instance (e.g. headless test env) — fall back
                        // to direct construction.
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
    public void setBeforePreambleHandler(BiFunction<String, List<SessionConfigOption>, CompletableFuture<Void>> handler) {
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
                                    // Preserve models/modes — session/load has no
                                    // configOptions for Hermes-style agents and falls
                                    // back to this cached session for the dropdowns.
                                    resolved = new Session(s.id(), s.title(), directory, directory,
                                                           s.parentID(), s.updatedAt(), s.mcpServers(),
                                                           s.configOptions(), s.models(), s.modes());
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
                        LOG.info("session/new extracted sessionId: {0}, title: {1}", s.id(), s.title());

                        // Use session ID as title if server didn't provide one.
                        // Preserve models/modes — Hermes reports them in session/new
                        // and sends no title, so this branch always ran for it.
                        if (StringUtils.isBlank(s.title())) {
                            s = new Session(s.id(), s.id(), s.cwd(), s.directory(),
                                    s.parentID(), s.updatedAt(), s.mcpServers(),
                                    s.configOptions(), s.models(), s.modes());
                            LOG.info("session/new: using sessionId as title");
                        }

                        if (s.effectiveDirectory() == null) {
                            LOG.fine("session/new: effectiveDirectory is null, " +
                                    "reconstructing with finalCwd: {0}", finalCwd);
                            s = new Session(s.id(), s.title(), finalCwd, finalCwd,
                                    s.parentID(), s.updatedAt(), s.mcpServers(),
                                    s.configOptions(), s.models(), s.modes());
                            LOG.info("session/new reconstructed session, id is now: {0}", s.id());
                        }

                        // Log models and modes if present
                        if (s.models() != null) {
                            LOG.info("session/new: models available={0}, current={1}",
                                    s.models().availableModels() != null ? s.models().availableModels().size() : 0,
                                    s.models().currentModelId());
                        }
                        if (s.modes() != null) {
                            LOG.info("session/new: modes available={0}, current={1}",
                                    s.modes().availableModes() != null ? s.modes().availableModes().size() : 0,
                                    s.modes().currentModeId());
                        }
                        cacheManager.cacheSession(s);
                        updateMetadata(s.id(), m -> new SessionMetadata(m.title(), m.usage(), m.hidden(), finalCwd));
                        if (tracksSessionsLocally()) {
                            cacheManager.addLocallyCreated(s, agentName());
                            saveLocallyCreatedSessionIds();
                        }
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
                    // Agents that report session state as structured fields rather than a
                    // configOptions array (Hermes) — mirror the session/new fallback.
                    if (res != null && (res.has("models") || res.has("modes"))) {
                        try {
                            ModelsInfo models = res.has("models")
                                    ? MAPPER.convertValue(res.get("models"), ModelsInfo.class) : null;
                            ModesInfo modes = res.has("modes")
                                    ? MAPPER.convertValue(res.get("modes"), ModesInfo.class) : null;
                            List<SessionConfigOption> resolved = ConfigOptionConverter.fromModelsAndModes(models, modes);
                            if (resolved != null) {
                                return resolved;
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to parse models/modes from session/load: {0}",
                                    ExceptionUtils.getMessage(e), e);
                        }
                    }
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        ProcessControl pc = Lookup.getDefault().lookup(ProcessControl.class);
        if (pc != null && !pc.getCapabilities().supportsSessionSetConfigOption()) {
            LOG.fine("Harness does not support session/set_config_option, skipping config {0}", configId);
            return CompletableFuture.completedFuture(null);
        }
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
                        // A rejected config value is an expected, recoverable outcome
                        // (e.g. the server does not accept the selected effort level).
                        // Log the message WITHOUT the Throwable — passing it routes the
                        // record to NetBeans' Exceptions/Notifications panel.
                        LOG.warn("Failed to set config {0}: {1}", configId, ExceptionUtils.getMessage(ex));
                    }
                });
    }

    @Override
    public CompletableFuture<Void> setSessionMode(String sessionId, String modeId) {
        return rpcClient.setSessionMode(sessionId, modeId)
                .thenApply(res -> (Void) null)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        LOG.warn("Failed to set mode {0}: {1}", modeId, ExceptionUtils.getMessage(ex));
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
                    // Agents that don't support session/list (e.g. Gemini) rely on
                    // locally-created sessions tracked in the cache. Skip the RPC.
                    ProcessControl pc = Lookup.getDefault().lookup(ProcessControl.class);
                    if (pc != null && !pc.getCapabilities().supportsSessionList()) {
                        loadLocallyCreatedSessionIds();
                        List<String> openDirs = openProjectDirectories();
                        List<Session> local = retainSessionsForDirectories(
                                cacheManager.getLocallyCreatedSessions(agentName()), openDirs);
                        // Do not preserve: loadLocallyCreatedSessionIds already put
                        // closed-project rows in the cache; merge would undo the filter.
                        cacheManager.setCachedSessions(local, agentName(), false);
                        return CompletableFuture.completedFuture(local);
                    }
                    List<String> openProjectDirs = openProjectDirectories();
                    LOG.fine("refreshSessions: starting refresh for {0} unique projects", openProjectDirs.size());
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

                    cacheManager.setCachedSessions(filteredSessions, agentName(), false);
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
                        LOG.info("createNewSession: setting currentSessionId to {0}", session.id());
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
                        // Derive config options from models/modes when the server
                        // reports them as structured fields (Hermes) instead of a
                        // configOptions array (Claude). Without this, agents that
                        // use models/modes get empty model/agent dropdowns.
                        List<SessionConfigOption> resolvedOptions = session.configOptions();
                        if (resolvedOptions == null) {
                            resolvedOptions = ConfigOptionConverter.fromModelsAndModes(
                                    session.models(), session.modes());
                        }
                        notifySessionLoaded(session.id(), resolvedOptions, true);
                        // Do NOT call refreshSessions() here — the server may not have
                        // added the new session to its list yet, causing onSessionListUpdated
                        // to fall back to loading an old session instead of the new one.
                        // Before preamble, let the UI handler (if set) show a config
                        // dialog so the user can pick agent/model/level.
                        if (beforePreambleHandler != null) {
                            beforePreambleHandler.apply(session.id(), resolvedOptions)
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

        // Prefer the cached cwd; fall back to the last known project dir, then
        // to the single open project. Gemini's session/load REQUIRES a cwd
        // string — omitting it makes the server reject the request with
        // invalid_type (JSON-RPC "Internal error"). Harnesses that implement
        // session/list accept load without cwd (SessionRpcClient omits a null).
        String workingCwd = sessionCwd != null ? sessionCwd : lastProjectDir;
        if (workingCwd == null) {
            List<String> openDirs = openProjectDirectories();
            if (openDirs.size() == 1) {
                workingCwd = openDirs.get(0);
            }
        }
        if (workingCwd == null && tracksSessionsLocally()) {
            this.currentSessionId = null;
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError(NbBundle.getMessage(SessionManager.class,
                    "ERR_LoadSessionNoCwd", sessionId));
            return false;
        }

        if (workingCwd != null) {
            this.lastProjectDir = workingCwd;
        }
        notifySessionProgress(30);
        try {
            loadSessionFromServer(sessionId, workingCwd)
                    .thenAccept(configOptions -> {
                        if (sessionId.equals(this.currentSessionId)) {
                            notifySessionProgress(60);
                            stateMachine.transitionTo(SessionState.STREAMING);
                            notifySessionLoaded(sessionId, configOptions, isStartup);

                            if (manualReconnectPending) {
                                manualReconnectPending = false;
                                sendAssistantPrompt(sessionId, NbBundle.getMessage(SessionManager.class,
                                        "MSG_ReconnectPrompt"), "reconnect prompt");
                            }
                        }
                    })
                    .exceptionally(ex -> {
                        LOG.severe("Failed to load session async: {0}", ExceptionUtils.getMessage(ex), ex);
                        if (sessionId.equals(this.currentSessionId)) {
                            // The session never became active — clear the pointer
                            // so SSE routing and the UI don't treat it as current.
                            this.currentSessionId = null;
                            stateMachine.transitionTo(SessionState.IDLE);
                            notifyError(NbBundle.getMessage(SessionManager.class, "ERR_LoadSessionFailed", rootMessage(ex)));
                            // Re-sync session list from server so the dropdown reflects
                            // the actual server state and loads the most recent session.
                            refreshSessions();
                        }
                        return null;
                    });
        } catch (Exception ex) {
            LOG.severe("Failed to load session", ex);
            this.currentSessionId = null;
            stateMachine.transitionTo(SessionState.IDLE);
            notifyError(NbBundle.getMessage(SessionManager.class, "ERR_LoadSessionFailed", rootMessage(ex)));
            refreshSessions();
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
                    s.parentID(), s.updatedAt(), s.mcpServers(), s.configOptions(),
                    s.models(), s.modes());
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
            cacheManager.setCachedSessions(updatedList, agentName(), tracksSessionsLocally());
            if (tracksSessionsLocally()) {
                saveLocallyCreatedSessionIds();
            }
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
        ProjectQuery projectQuery = Lookup.getDefault().lookup(ProjectQuery.class);
        Project[] remaining = projectQuery == null ? new Project[0] : projectQuery.getAllOpenProjects();
        if (remaining == null || remaining.length == 0
                || (remaining.length == 1 && remaining[0] != null
                && remaining[0].getProjectDirectory().getPath().equals(closedDir))) {
            lastProjectDir = "";
            if (remaining == null || remaining.length == 0) {
                new ArrayList<>(listeners).forEach(SessionListener::onAllProjectsClosed);
            }
        }
    }

    /** Sends the critical rules followed by the preamble prompt for a new session.
     *  Splits preamble by "---" separators and sends each chunk separately.
     *  @return true if a preamble was sent, false if empty/skipped */
    private boolean sendPreamble(String sessionId) {
        String rules = PluginSettings.getCriticalRules();
        String preamble = PluginSettings.getPreamble();
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        boolean cavemanMode = prefs.getBoolean(PreferenceKeys.CAVEMAN_MODE, false);

        // Combine critical rules, caveman instruction, and preamble
        StringBuilder combined = new StringBuilder();
        if (!isBlank(rules)) {
            combined.append(rules);
        }
        if (cavemanMode) {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append("Respond in minimal, terse prose. Short sentences. No filler. ")
                .append("Code, commands, and file paths stay exact. ")
                .append("When explaining, use the fewest words that convey the meaning.");
        }
        if (!isBlank(preamble)) {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append(preamble);
        }

        String text = combined.toString().trim();
        if (text.isEmpty()) {
            return false;
        }

        // Split by "---" separators and send each chunk
        String[] chunks = text.split("\n---\n");
        if (chunks.length == 0) {
            return false;
        }

        // Notify UI that preamble is being posted
        new ArrayList<>(listeners).forEach(SessionListener::onPreambleStarted);

        // Send chunks sequentially, waiting for each to complete
        sendPreambleChunks(sessionId, chunks, 0);
        return true;
    }

    /** Sends preamble chunks sequentially, one at a time */
    private void sendPreambleChunks(String sessionId, String[] chunks, int index) {
        if (index >= chunks.length) {
            // All chunks sent, notify done
            notifyPreambleDone();
            return;
        }

        String chunk = chunks[index].trim();
        if (chunk.isEmpty()) {
            // Skip empty chunks
            sendPreambleChunks(sessionId, chunks, index + 1);
            return;
        }

        String label = "preamble chunk " + (index + 1) + "/" + chunks.length;
        sendAssistantPrompt(sessionId, chunk, label)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        LOG.warn("Failed to send {0}: {1}", label, ExceptionUtils.getRootCauseMessage(ex));
                        // Continue with next chunk even if this one failed
                    }
                    // Send next chunk
                    sendPreambleChunks(sessionId, chunks, index + 1);
                });
    }

    private CompletableFuture<JsonNode> sendAssistantPrompt(String sessionId, String text, String label) {
        new ArrayList<>(listeners).forEach(SessionListener::onInternalMessageSent);

        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("type", "text");
        textBlock.put("text", text);
        textBlock.put("annotations", Map.of("audience", List.of("assistant")));

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", List.of(textBlock));

        return ProcessManager.getInstance().sendRequest("session/prompt", params)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        LOG.warn("Failed to send {0}: {1}", label, ExceptionUtils.getRootCauseMessage(ex));
                        notifyError(NbBundle.getMessage(SessionManager.class, "ERR_SendFailed",
                                label, ExceptionUtils.getRootCauseMessage(ex)));
                    }
                    new ArrayList<>(listeners).forEach(SessionListener::onInternalMessageDone);
                });
    }

    @Override
    public void closeSession() {
        String sessionId = this.currentSessionId;
        if (stateMachine.transitionTo(SessionState.IDLE)) {
            this.currentSessionId = null;
            // No active session — drop the stale project-dir fallback so
            // getCurrentSessionDirectory() no longer reports the closed
            // session's directory (e.g. to auto-backup or context capture).
            this.lastProjectDir = null;
            new ArrayList<>(listeners).forEach(l -> l.onSessionLoading(false));
        }
        if (sessionId != null) {
            StrategyRegistry.invalidateSession(sessionId);
        }
    }

    /**
     * Resets sticky session state before a MANUAL server restart (not a crash).
     * The crash path already transitions to IDLE via {@code setCrashHandler};
     * the manual restart path (Restart Server button, executable-path
     * preference change, OnboardingBubble) never touched the state machine.
     * A state stuck in LOADING/STOPPING makes the post-restart loadSession()
     * (which requires IDLE or STREAMING) refuse to run, so the panel stays
     * frozen until the whole IDE is restarted. Called synchronously by
     * {@link ProcessManager#restartServer()} before the process is torn down.
     */
    public void resetForServerRestart() {
        if (stateMachine.getState() != SessionState.IDLE
                && stateMachine.transitionTo(SessionState.IDLE)) {
            LOG.info("resetForServerRestart: transitioning to IDLE");
            new ArrayList<>(listeners).forEach(l -> l.onSessionLoading(false));
        }
        // The current session id is kept: ComponentLifecycleHandler.doRestart()
        // reloads it once the restarted server is ready. In-flight prompts
        // belong to the dying connection and are failed by AcpProtocolClient.close().
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
        new ArrayList<>(listeners).forEach(SessionListener::onPreambleDone);
    }

    private void notifySessionListUpdated(List<Session> sessions) {
        new ArrayList<>(listeners).forEach(l -> l.onSessionListUpdated(sessions));
    }

    private void notifySessionRenamed(String sessionId) {
        new ArrayList<>(listeners).forEach(l -> l.onSessionRenamed(sessionId));
    }

    private void notifySessionStarted(String sessionId) {
        new ArrayList<>(listeners).forEach(l -> l.onSessionStarted(sessionId));
    }

    private void notifySessionLoaded(String sessionId, List<SessionConfigOption> options, boolean isStartup) {
        new ArrayList<>(listeners).forEach(l -> l.onSessionLoaded(sessionId, options, isStartup));
    }

    private void notifySessionProgress(int percent) {
        new ArrayList<>(listeners).forEach(l -> l.onSessionProgress(percent));
    }

    private void notifyError(String message) {
        new ArrayList<>(listeners).forEach(l -> l.onSessionError(message));
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
            // Use full nanosecond resolution rather than truncating to millis: two
            // sessions updated within the same millisecond (e.g. a sub-agent spawned
            // by a tool call) would otherwise compare equal, yielding an unstable
            // "most recent" sort order. Preserving fractional-second precision keeps
            // the comparison deterministic even for near-simultaneous updates.
            Instant inst = OffsetDateTime.parse(ts).toInstant();
            return inst.getEpochSecond() * 1_000_000_000L + inst.getNano();
        } catch (Exception e) {
            LOG.warn("Failed to parse timestamp: {0}", ts, e);
            return 0;
        }
    }

    private boolean isInvalidParamsError(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = ExceptionUtils.getMessage(t);
        return msg != null && msg.contains("Invalid params");
    }
}