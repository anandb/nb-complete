package ai.opencode.netbeans.manager;

import ai.opencode.netbeans.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.util.NbPreferences;

public class OpenCodeManager {
    private static final Logger LOG = Logger.getLogger(OpenCodeManager.class.getName());
    private static OpenCodeManager instance;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Process serverProcess;
    private JsonRpcClient rpcClient;
    private boolean initialized = false;
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    
    private final List<Consumer<SessionUpdate>> sseListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> projectChangeListeners = new CopyOnWriteArrayList<>();
    private String activeProjectDir;
    private final List<SessionUpdate.AvailableCommand> availableCommands = new CopyOnWriteArrayList<>();

    private OpenCodeManager() {
        startServer();
    }

    public static synchronized OpenCodeManager getInstance() {
        if (instance == null) {
            instance = new OpenCodeManager();
        }
        return instance;
    }

    private void startServer() {
        LOG.info("Starting OpenCode ACP server...");
        try {
            String defaultPath = System.getProperty("user.home") + "/.opencode/bin/opencode";
            String binaryPath = NbPreferences.forModule(ai.opencode.netbeans.ui.OpenCodeOptionsPanel.class)
                    .get("opencodeExecutablePath", defaultPath);
            LOG.log(Level.INFO, "Binary path: {0}", binaryPath);
            
            java.io.File binaryFile = new java.io.File(binaryPath);
            if (!binaryFile.exists()) {
                LOG.log(Level.SEVERE, "OpenCode binary NOT found at {0}", binaryPath);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(binaryPath, "acp");
            pb.redirectError(ProcessBuilder.Redirect.INHERIT); 
            this.serverProcess = pb.start();
            
            this.rpcClient = new JsonRpcClient(serverProcess);
            
            // Listen for session updates
            rpcClient.onNotification("session/update", params -> {
                try {
                    SessionUpdate.Params sessionParams = objectMapper.treeToValue(params, SessionUpdate.Params.class);
                    SessionUpdate update = new SessionUpdate("2.0", "session/update", sessionParams);
                    
                    // Update available commands if present
                    if (update.update() != null && "available_commands_update".equals(update.update().type())) {
                        if (update.update().availableCommands() != null) {
                            availableCommands.clear();
                            availableCommands.addAll(update.update().availableCommands());
                        }
                    }
                    
                    // Update session configurations if present
                    if (update.update() != null && "config_options_update".equals(update.update().type())) {
                        if (update.update().configOptions() != null) {
                            // Forward this update to UI via the SSE listener
                        }
                    }
                    
                    notifyListeners(update);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to parse session/update notification: " + e.getMessage(), e);
                }
            });

            // Initialize ACP
            initializeProtocol();
            
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
            
            LOG.info("OpenCode ACP server process started successfully");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "CRITICAL: Failed to start OpenCode ACP server", e);
        }
    }

    private void initializeProtocol() {
        Map<String, Object> params = Map.of(
            "protocolVersion", 1,
            "clientCapabilities", Map.of(
                "fs", Map.of("readTextFile", true, "writeTextFile", true),
                "terminal", true
            )
        );
        rpcClient.sendRequest("initialize", params)
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .thenAccept(res -> {
                    this.initialized = true;
                    readyFuture.complete(null);
                    LOG.info("OpenCode ACP initialized successfully");
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Failed to initialize OpenCode ACP", ex);
                    readyFuture.completeExceptionally(ex);
                    stopServer();
                    return null;
                });
    }

    private void stopServer() {
        if (rpcClient != null) {
            rpcClient.close();
            rpcClient = null;
        }
        if (serverProcess != null && serverProcess.isAlive()) {
            serverProcess.destroy();
            LOG.info("OpenCode server stopped");
        }
    }

    public void addSseListener(Consumer<SessionUpdate> listener) {
        sseListeners.add(listener);
    }

    private void notifyListeners(SessionUpdate update) {
        for (Consumer<SessionUpdate> listener : sseListeners) {
            listener.accept(update);
        }
    }


    public CompletableFuture<List<Session>> getSessions() {
        if (rpcClient == null) return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        return rpcClient.sendRequest("session/list", Map.of())
                .thenApply(res -> {
                    try {
                        List<Session> sessions = objectMapper.readValue(res.traverse(), new TypeReference<List<Session>>() {});
                        return sessions;
                    } catch (IOException e) {
                        return new ArrayList<Session>();
                    }
                })
                .exceptionally(ex -> new ArrayList<Session>());
    }

    public CompletableFuture<Session> createSession(String cwd) {
        if (rpcClient == null) return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        
        String effectiveCwd = cwd;

        // 1. Try provided CWD
        // 2. Fallback to activeProjectDir (synchronized by OpenCodeProjectManager)
        if (effectiveCwd == null) {
            effectiveCwd = activeProjectDir;
        }
        
        // 3. Last ditch attempt to get it directly from NetBeans APIs
        if (effectiveCwd == null) {
            Project main = OpenProjects.getDefault().getMainProject();
            if (main != null && main.getProjectDirectory() != null) {
                effectiveCwd = main.getProjectDirectory().getPath();
            } else {
                Project[] open = OpenProjects.getDefault().getOpenProjects();
                if (open != null && open.length > 0 && open[0].getProjectDirectory() != null) {
                    effectiveCwd = open[0].getProjectDirectory().getPath();
                }
            }
        }

        // 4. Default to system user dir if all else fails
        if (effectiveCwd == null) {
            effectiveCwd = System.getProperty("user.dir");
        }

        LOG.log(Level.INFO, "Creating new session with CWD: {0}", effectiveCwd);

        Map<String, Object> params = Map.of(
            "cwd", effectiveCwd,
            "mcpServers", List.of()
        );
        return rpcClient.sendRequest("session/new", params)
                .thenApply(res -> {
                    try {
                        return objectMapper.treeToValue(res, Session.class);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public CompletableFuture<List<Message>> getMessages(String sessionId) {
        if (rpcClient == null) return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        return rpcClient.sendRequest("session/messages", Map.of("sessionId", sessionId))
                .thenApply(res -> {
                    try {
                        List<Message> messages = objectMapper.readValue(res.traverse(), new TypeReference<List<Message>>() {});
                        return messages;
                    } catch (IOException e) {
                        return new ArrayList<Message>();
                    }
                })
                .exceptionally(ex -> new ArrayList<Message>());
    }

    public CompletableFuture<Void> sendMessage(String sessionId, String text) {
        if (rpcClient == null) return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        Map<String, Object> params = Map.of(
            "sessionId", sessionId,
            "prompt", List.of(Map.of("type", "text", "text", text))
        );
        
        return rpcClient.sendRequest("session/prompt", params)
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> stopMessage(String sessionId) {
        if (rpcClient == null) return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        return rpcClient.sendRequest("session/cancel", Map.of("sessionId", sessionId))
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> deleteSession(String sessionId) {
        if (rpcClient == null) return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        // Using session/close as the standard termination method
        return rpcClient.sendRequest("session/close", Map.of("sessionId", sessionId))
                .thenApply(v -> null);
    }
    
    // Placeholder for completions - need to verify method name
    public CompletableFuture<JsonNode> getCompletions(String sessionId, String text, int line, int column) {
        if (rpcClient == null) return CompletableFuture.completedFuture(null);
        Map<String, Object> params = Map.of(
            "sessionId", sessionId,
            "text", text,
            "line", line,
            "column", column
        );
        // Trying completion/inline as a best guess for ACP
        return rpcClient.sendRequest("completion/inline", params);
    }

    public CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        if (rpcClient == null) return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        Map<String, Object> params = Map.of(
            "sessionId", sessionId,
            "configId", configId,
            "value", value
        );
        return rpcClient.sendRequest("session/set_config_option", params)
                .thenApply(v -> null);
    }

    public void setActiveProject(String path) {
        this.activeProjectDir = path;
        for (Consumer<String> listener : projectChangeListeners) {
            listener.accept(path);
        }
    }

    public String getActiveProjectDir() {
        return activeProjectDir;
    }

    public void addProjectChangeListener(Consumer<String> listener) {
        projectChangeListeners.add(listener);
    }

    public List<SessionUpdate.AvailableCommand> getAvailableCommands() {
        return new ArrayList<>(availableCommands);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public CompletableFuture<Void> whenReady() {
        return readyFuture;
    }
}
