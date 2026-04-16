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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the state and lifecycle of chat sessions.
 * Decouples session logic from the AssistantTopComponent UI.
 */
public class SessionManager {
    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());
    private static SessionManager instance;

    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private volatile String currentSessionId;
    private volatile String lastProjectDir;
    private volatile boolean isSessionLoading = false;
    private final List<Session> cachedSessions = new CopyOnWriteArrayList<>();

    public interface SessionListener {
        void onSessionListUpdated(List<Session> sessions);
        void onSessionStarted(String sessionId);
        void onSessionLoaded(String sessionId, List<SessionConfigOption> configOptions, boolean isStartup);
        void onSessionLoading(boolean isLoading);
        void onSessionError(String message);
    }

    private SessionManager() {
        // Register for project changes to keep sessions in sync
        ACPManager.getInstance().addProjectChangeListener(this::handleProjectChanged);
        ACPProjectManager.getInstance().setProjectOpenListener(this::handleProjectOpened);
        ACPProjectManager.getInstance().setProjectCloseListener(this::handleProjectClosed);
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

    public boolean isSessionLoading() {
        return isSessionLoading;
    }

    public List<Session> getCachedSessions() {
        return new ArrayList<>(cachedSessions);
    }

    public void refreshSessions() {
        ACPManager manager = ACPManager.getInstance();
        manager.whenReady()
                .thenCompose(v -> {
                    Project[] openProjects = ACPProjectManager.getInstance().getAllOpenProjects();
                    List<String> openProjectDirs = new ArrayList<>();
                    for (Project p : openProjects) {
                        if (p != null) {
                            openProjectDirs.add(p.getProjectDirectory().getPath());
                        }
                    }
                    LOG.log(Level.INFO, "refreshSessions: starting refresh for {0} projects", openProjectDirs.size());
                    if (openProjectDirs.isEmpty()) {
                        return CompletableFuture.completedFuture(new ArrayList<Session>());
                    }
                    return manager.getSessionsForDirectories(openProjectDirs);
                })
                .thenAccept(sessions -> {
                    List<Session> filteredSessions = new ArrayList<>(sessions);
                    filteredSessions.sort((s1, s2) -> {
                        String p1 = s1.projectName() != null ? s1.projectName() : "";
                        String p2 = s2.projectName() != null ? s2.projectName() : "";
                        int projectComp = p1.compareTo(p2);
                        if (projectComp != 0) {
                            return projectComp;
                        }
                        return Long.compare(parseTimestamp(s2.updatedAt()), parseTimestamp(s1.updatedAt()));
                    });

                    cachedSessions.clear();
                    cachedSessions.addAll(filteredSessions);
                    notifySessionListUpdated(filteredSessions);
                });
    }

    public void createNewSession(String explicitCwd) {
        setLoading(true);
        notifySessionStarted(null); // Signal that we are starting a new session

        ACPManager.getInstance().createSession(explicitCwd)
                .thenAccept(session -> {
                    this.currentSessionId = session.id();
                    this.lastProjectDir = session.effectiveDirectory();
                    
                    SwingUtilities.invokeLater(() -> {
                        notifySessionLoaded(session.id(), session.configOptions(), true);
                        refreshSessions();
                        sendPreamble(session.id());
                    });
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Failed to create session", ex);
                    notifyError("Failed to create session: " + ex.getMessage());
                    setLoading(false);
                    return null;
                });
    }

    public void loadSession(String sessionId) {
        loadSession(sessionId, false);
    }

    public void loadSession(String sessionId, boolean isStartup) {
        this.currentSessionId = sessionId;
        setLoading(true);
        notifySessionStarted(sessionId);

        String projectCwd = ACPManager.getInstance().getActiveProjectDir();
        ACPManager.getInstance().getSessions(projectCwd).thenAccept(sessions -> {
            String sessionCwd = sessions.stream()
                    .filter(s -> s.id().equals(sessionId))
                    .findFirst()
                    .map(s -> s.cwd() != null ? s.cwd() : s.directory())
                    .orElse(null);

            String workingCwd = projectCwd != null ? projectCwd : sessionCwd;
            if (workingCwd == null) {
                workingCwd = System.getProperty("user.dir");
            }

            this.lastProjectDir = workingCwd;
            ACPManager.getInstance().loadSession(sessionId, workingCwd)
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
        SessionTitleManager.setTitle(sessionId, newTitle.trim());
        refreshSessions();
    }

    private void handleProjectChanged(String path) {
        if (path != null && !path.equals(lastProjectDir)) {
            if (lastProjectDir != null) {
                LOG.log(Level.INFO, "Project changed from {0} to {1}, creating new session",
                        new Object[]{lastProjectDir, path});
                createNewSession(path);
            }
            lastProjectDir = path;
        }
    }

    private void handleProjectOpened(String openedDir) {
        refreshSessions();
    }

    private void handleProjectClosed(String closedDir) {
        ACPManager.getInstance().getSessions(closedDir)
                .thenAccept(closedDirSessions -> {
                    for (Session s : closedDirSessions) {
                        ACPManager.getInstance().deleteSession(s.id());
                    }
                    refreshSessions();
                });
    }

    private void sendPreamble(String sessionId) {
        String preamble = ACPSettings.getPreamble().trim();
        if (!preamble.isEmpty()) {
            ACPManager.getInstance().sendMessage(sessionId, preamble, null)
                    .exceptionally(ex -> {
                        LOG.log(Level.WARNING, "Failed to send preamble", ex);
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
