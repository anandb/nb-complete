package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.project.ACPProjectManager;
import org.netbeans.api.project.Project;

import javax.swing.SwingUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

/**
 * Manages the state and lifecycle of chat sessions.
 * Decouples session logic from the AssistantTopComponent UI.
 */
public class SessionManager {
    private static final Logger LOG = new Logger(SessionManager.class);
    private static SessionManager instance;

    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private volatile String currentSessionId;
    private volatile String lastProjectDir;
    private volatile boolean isSessionLoading = false;
    private final List<Session> cachedSessions = new CopyOnWriteArrayList<>();

    private SessionManager() {
        // Register for project changes to keep sessions in sync
        ProcessManager.getInstance().addProjectChangeListener(this::handleProjectChanged);
        ACPProjectManager.getInstance().setProjectOpenListener(this::handleProjectOpened);
        ACPProjectManager.getInstance().setProjectCloseListener(this::handleProjectClosed);

        // Register for SSE updates to route them to the active session
        ProcessManager.getInstance().addSseListener(this::handleSseUpdate);
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

    public void refreshSessions() {
        ProcessManager manager = ProcessManager.getInstance();
        manager.whenReady()
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
                    return manager.getSessionsForDirectories(openProjectDirs);
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
        setLoading(true);
        notifySessionStarted(null); // Signal that we are starting a new session

        ProcessManager.getInstance().createSession(explicitCwd)
                .thenAccept(session -> {
                    this.currentSessionId = session.id();
                    this.lastProjectDir = session.effectiveDirectory();
                    Logger.setSession(session.id(), session.title());

                    SwingUtilities.invokeLater(() -> {
                        notifySessionLoaded(session.id(), session.configOptions(), true);
                        refreshSessions();
                        sendPreamble(session.id());
                    });
                })
                .exceptionally(ex -> {
                    LOG.severe("Failed to create session", ex);
                    notifyError(ex.getMessage());
                    setLoading(false);
                    return null;
                });
    }

    public void loadSession(String sessionId) {
        loadSession(sessionId, false);
    }

    public void loadSession(String sessionId, boolean isStartup) {
        // Always set the new sessionId and notify UI that we are starting a load
        this.currentSessionId = sessionId;
        setLoading(true);
        notifySessionStarted(sessionId);

        String projectCwd = ProcessManager.getInstance().getActiveProjectDir();
        ProcessManager.getInstance().getSessions(projectCwd).thenAccept(sessions -> {
            String sessionCwd = sessions.stream()
                    .filter(s -> s.id().equals(sessionId))
                    .findFirst()
                    .map(s -> {
                        Logger.setSession(s.id(), s.title());
                        return s.cwd() != null ? s.cwd() : s.directory();
                    })
                    .orElse(null);

            String workingCwd = projectCwd != null ? projectCwd : sessionCwd;
            if (workingCwd == null) {
                workingCwd = System.getProperty("user.dir");
            }

            this.lastProjectDir = workingCwd;
            ProcessManager.getInstance().loadSession(sessionId, workingCwd)
                    .thenAccept(configOptions -> {
                        if (sessionId.equals(this.currentSessionId)) {
                            notifySessionLoaded(sessionId, configOptions, isStartup);
                            setLoading(false);
                        }
                    })
                    .exceptionally(ex -> {
                        if (sessionId.equals(this.currentSessionId)) {
                            notifyError("Failed to load session: " + ex.getMessage());
                            setLoading(false);
                        }
                        return null;
                    });
        });
    }

    public void renameSession(String sessionId, String newTitle) {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }
        SessionTitleMapper.setTitle(sessionId, newTitle.trim());
        refreshSessions();
    }

    private void handleProjectChanged(String path) {
        if (path != null && !path.equals(lastProjectDir)) {
            if (lastProjectDir != null) {
                LOG.fine("Project changed from {0} to {1}, creating new session",
                        new Object[]{lastProjectDir, path});
                createNewSession(path);
            }
            lastProjectDir = path;
        }
    }

    private void handleProjectOpened(String openedDir) {
        refreshSessions();
        ProcessManager.getInstance().getSessions(openedDir)
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
        ProcessManager.getInstance().getSessions(closedDir)
                .thenAccept(closedDirSessions -> {
                    for (Session s : closedDirSessions) {
                        ProcessManager.getInstance().deleteSession(s.id());
                    }
                    refreshSessions();
                });
    }

    private void sendPreamble(String sessionId) {
        String preamble = PluginSettings.getPreamble().trim();
        if (!preamble.isEmpty()) {
            ProcessManager.getInstance().sendMessage(sessionId, preamble, null)
                    .exceptionally(ex -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        LOG.warn("Failed to send preamble: {0}", cause.getMessage());
                        notifyError("Connection lost while sending preamble: " + cause.getMessage());
                        return null;
                    });
        }
    }

    private void setLoading(boolean loading) {
        this.isSessionLoading = loading;
        for (SessionListener l : listeners) {
            l.onSessionLoading(loading);
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
}
