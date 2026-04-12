package ai.opencode.netbeans.manager;

import ai.opencode.netbeans.model.Session;
import ai.opencode.netbeans.model.SessionConfigOption;
import ai.opencode.netbeans.model.SessionUpdate;
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
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbPreferences;
import javax.swing.text.Document;

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
            
            Map<String, String> env = pb.environment();
            String defaultModel = NbPreferences.forModule(ai.opencode.netbeans.ui.OpenCodeOptionsPanel.class)
                    .get("defaultModel", "opencode/big-pickle");
            if (!defaultModel.isEmpty()) {
                env.put("OPENCODE_DEFAULT_MODEL", defaultModel);
            }
            
            this.serverProcess = pb.start();

            this.rpcClient = new JsonRpcClient(serverProcess);
            rpcClient.start();

            // Register handlers
            rpcClient.onRequest("fs/readTextFile", this::handleReadTextFile);

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

            LOG.log(Level.INFO, "OpenCode ACP server process started successfully");
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
                    LOG.log(Level.INFO, "OpenCode ACP initialized successfully");
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
            LOG.log(Level.INFO, "OpenCode server stopped");
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
        LOG.log(Level.INFO, "getSessions: called");
        if (rpcClient == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }
        return rpcClient.sendRequest("session/list", Map.of())
                .thenApply(res -> {
                    try {
                        LOG.log(Level.INFO, "getSessions: got response");
                        JsonNode root = objectMapper.readTree(res.traverse());
                        JsonNode result = root.has("result") ? root.get("result") : root;
                        JsonNode sessionsNode = result.has("sessions") ? result.get("sessions") : result.has("data") ? result.get("data") : result;
                        if (sessionsNode.isArray()) {
                            List<Session> sessions = objectMapper.readValue(sessionsNode.traverse(), new TypeReference<List<Session>>() {});
                            LOG.log(Level.INFO, "getSessions: deserialized {0} sessions", sessions.size());
                            for (Session s : sessions) {
                                LOG.log(Level.INFO, "getSessions: id={0}, title=''{1}''", new Object[]{s.id(), s.title()});
                            }
                            return sessions;
                        } else {
                            LOG.log(Level.WARNING, "getSessions: sessionsNode is not an array: {0}", sessionsNode);
                            return new ArrayList<Session>();
                        }
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "getSessions: failed to deserialize: {0} {1}", new Object[]{e.getMessage(), e.toString()});
                        return new ArrayList<Session>();
                    }
                })
                .exceptionally(ex -> {
                    LOG.log(Level.WARNING, "getSessions: rpc error: {0} {1}", new Object[]{ex.getMessage(), ex.toString()});
                    return new ArrayList<>();
                });
    }

    private static String getProjectPath() {
        Project main = OpenProjects.getDefault().getMainProject();
        if (main != null && main.getProjectDirectory() != null) {
            return main.getProjectDirectory().getPath();
        }
        Project[] open = OpenProjects.getDefault().getOpenProjects();
        if (open != null && open.length > 0 && open[0].getProjectDirectory() != null) {
            return open[0].getProjectDirectory().getPath();
        }
        return null;
    }

    public CompletableFuture<Session> createSession(String cwd) {
        if (rpcClient == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }

        String effectiveCwd = cwd;

        // 1. Use provided CWD if given
        // 2. Query NetBeans APIs directly
        if (effectiveCwd == null) {
            effectiveCwd = getProjectPath();
        }

        // 3. Default to system user dir if all else fails
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

    public CompletableFuture<List<SessionConfigOption>> loadSession(String sessionId, String cwd) {
        LOG.log(Level.INFO, "loadSession: called with {0}, cwd={1}", new Object[]{sessionId, cwd});
        if (rpcClient == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("sessionId", sessionId);
        if (cwd != null) {
            params.put("cwd", cwd);
        }
        params.put("mcpServers", List.of());

        return rpcClient.sendRequest("session/load", params)
                .thenApply(res -> {
                    LOG.log(Level.INFO, "loadSession: got response {0}", res);
                    if (res != null && res.has("configOptions")) {
                        try {
                            return objectMapper.convertValue(res.get("configOptions"), new TypeReference<List<SessionConfigOption>>() {});
                        } catch (Exception e) {
                            LOG.log(Level.WARNING, "Failed to parse configOptions: {0}", e.getMessage());
                        }
                    }
                    return null;
                })
                .exceptionally(ex -> {
                    LOG.log(Level.WARNING, "loadSession: error: {0}", ex.getMessage());
                    return null;
                });
    }

    public CompletableFuture<Void> sendMessage(String sessionId, String text, Map<String, Object> context) {
        if (rpcClient == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }

        List<Map<String, Object>> promptBlocks = new ArrayList<>();
        
        String displayText = text;
        
        if (context != null) {
            String filePath = (String) context.get("filePath");
            String selectionContent = (String) context.get("selectionContent");
            if (filePath != null) {
                java.io.File file = new java.io.File(filePath);
                String lang = getLanguageFromPath(filePath);
                
                String fileName = file.getName();
                Object cursorObj = context.get("cursor");
                String cursorPos = cursorObj != null ? objectMapper.valueToTree(cursorObj).toString() : null;
                Object selObj = context.get("selection");
                String selection = selObj != null ? objectMapper.valueToTree(selObj).toString() : null;
                
                StringBuilder metadata = new StringBuilder();
                metadata.append("<!-- ");
                metadata.append("{");
                metadata.append("\"file\":\"").append(fileName.replace("\"", "\\\"")).append("\",");
                metadata.append("\"path\":\"").append(filePath.replace("\"", "\\\"")).append("\",");
                metadata.append("\"language\":\"").append(lang).append("\"");
                if (cursorPos != null && !cursorPos.isEmpty()) {
                    metadata.append(",\"cursor\":\"").append(cursorPos.replace("\"", "\\\"")).append("\"");
                }
                if (selection != null && !selection.isEmpty()) {
                    metadata.append(",\"selection\":\"").append(selection.replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
                }
                metadata.append("} -->");
                
                if (selectionContent != null && !selectionContent.isEmpty()) {
                    displayText = metadata.toString() + "\n\nSelection from `" + fileName + "`:\n```" + lang + "\n" + selectionContent + "\n```\n" + text;
                } else {
                    displayText = metadata.toString() + "\n" + text;
                }
            }
        }
        
        promptBlocks.add(Map.of("type", "text", "text", displayText));

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", promptBlocks);
        params.put("mcpServers", List.of());
        
        return rpcClient.sendRequest("session/prompt", params)
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> stopMessage(String sessionId) {
        if (rpcClient == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }
        return rpcClient.sendRequest("session/cancel", Map.of("sessionId", sessionId))
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> deleteSession(String sessionId) {
        if (rpcClient == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }
        // Using session/close as the standard termination method
        return rpcClient.sendRequest("session/delete", Map.of("sessionId", sessionId))
                .thenApply(v -> null);
    }

    public CompletableFuture<JsonNode> getCompletions(String sessionId, String text, int line, int column, String prefix, String suffix) {
        return getCompletionsInline(text, line, column, prefix, suffix);
    }

    public CompletableFuture<JsonNode> getCompletions(String sessionId, String text, int line, int column) {
        return getCompletions(sessionId, text, line, column, null, null);
    }

    public CompletableFuture<JsonNode> getCompletionsInline(String text, int line, int column, String prefix, String suffix) {
        if (rpcClient == null) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("text", text);
        params.put("line", line);
        params.put("column", column);
        if (prefix != null && !prefix.isEmpty()) {
            params.put("prefix", prefix);
        }
        if (suffix != null && !suffix.isEmpty()) {
            params.put("suffix", suffix);
        }
        return rpcClient.sendRequest("completion/inline", params);
    }

    public CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        if (rpcClient == null) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }
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
        String path = getProjectPath();
        if (path == null) {
            path = System.getProperty("user.dir");
        }
        return path;
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

    private CompletableFuture<JsonNode> handleReadTextFile(JsonNode params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filePath = params.has("filePath") ? params.get("filePath").asText()
                        : params.has("path") ? params.get("path").asText() : null;

                if (filePath == null) {
                    throw new RuntimeException("Missing filePath parameter");
                }

                java.io.File file = new java.io.File(filePath);
                if (!file.exists()) {
                    throw new RuntimeException("File not found: " + filePath);
                }

                FileObject fo = FileUtil.toFileObject(file);
                if (fo != null) {
                    try {
                        DataObject dobj = DataObject.find(fo);
                        EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                        if (ec != null) {
                            Document doc = ec.getDocument();
                            if (doc != null) {
                                String content = doc.getText(0, doc.getLength());
                                return objectMapper.createObjectNode().put("content", content);
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.FINE, "Could not read from editor for {0}, falling back to disk", filePath);
                    }
                }

                // Fallback to disk
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                return objectMapper.createObjectNode().put("content", content);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "fs/readTextFile failed", e);
                throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
            }
        });
    }

    private String getLanguageFromPath(String path) {
        if (path == null) return "";
        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) return "";
        String ext = path.substring(lastDot + 1).toLowerCase();
        return switch (ext) {
            case "java" -> "java";
            case "py" -> "python";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "html" -> "html";
            case "css" -> "css";
            case "xml" -> "xml";
            case "md" -> "markdown";
            case "json" -> "json";
            case "sh" -> "bash";
            default -> ext;
        };
    }
}
