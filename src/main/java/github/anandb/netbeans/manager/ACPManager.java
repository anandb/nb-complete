package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.ui.ACPOptionsPanel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

import org.apache.commons.exec.CommandLine;

public class ACPManager {
    private static final Logger LOG = Logger.getLogger(ACPManager.class.getName());
    private static ACPManager instance;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Process serverProcess;
    private JsonRpcClient rpcClient;
    private boolean initialized = false;
    private CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    private final List<Consumer<SessionUpdate>> sseListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> projectChangeListeners = new CopyOnWriteArrayList<>();
    private String activeProjectDir;
    private final List<SessionUpdate.AvailableCommand> availableCommands = new CopyOnWriteArrayList<>();
    private PermissionHandler permissionHandler;
    private volatile boolean isClosing = false;
    private int restartCount = 0;
    private long lastRestartTime = 0;
    private static final int MAX_RESTARTS = 3;
    private static final long RESTART_RESET_INTERVAL = 300000; // 5 minutes
    private boolean shutdownHookAdded = false;

    public interface PermissionHandler {
        void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response);
    }

    private volatile boolean serverStarted = false;

    private ACPManager() {
    }

    public synchronized void ensureStarted() {
        if (!serverStarted) {
            serverStarted = true;
            startServer();
        }
    }

    public static synchronized ACPManager getInstance() {
        if (instance == null) {
            instance = new ACPManager();
        }
        return instance;
    }

    private synchronized void startServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            return;
        }
        isClosing = false;
        if (readyFuture.isDone()) {
            readyFuture = new CompletableFuture<>();
        }
        LOG.info("Starting ACP server...");
        try {
            CommandLine cmd = parseCommand();
            LOG.log(Level.INFO, "Command Line: {0}", cmd);

            File binaryFile = new File(cmd.getExecutable());
            if (!binaryFile.exists()) {
                String errorMsg = "ACP binary NOT found at " + cmd.getExecutable();
                LOG.log(Level.SEVERE, errorMsg);
                readyFuture.completeExceptionally(new IOException(errorMsg));
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(Arrays.asList(cmd.toStrings()));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Map<String, String> env = pb.environment();
            env.putAll(System.getenv()); // Ensure all system environment variables are propagated

            this.serverProcess = pb.start();

            this.rpcClient = new JsonRpcClient(serverProcess);
            rpcClient.start();
            rpcClient.setDisconnectionHandler(this::handleDisconnection);

            // Register handlers
            rpcClient.onRequest("fs/readTextFile", this::handleReadTextFile);
            rpcClient.onRequest("fs/writeTextFile", this::handleWriteTextFile);
            rpcClient.onRequest("session/request_permission", this::handleRequestPermission);

            // Listen for session updates
            rpcClient.onNotification("session/update", params -> {
                try {
                    LOG.info("Received session/update notification: " + params.toString());
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

            if (!shutdownHookAdded) {
                Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
                shutdownHookAdded = true;
            }

            LOG.log(Level.INFO, "ACP server process started successfully");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "CRITICAL: Failed to start ACP server", e);
            readyFuture.completeExceptionally(e);
        }
    }

    public CommandLine parseCommand() {
        String ocPath = System.getProperty("user.home") + "/.opencode/bin/opencode";
        ACPCommandBuilder commandBuilder = new ACPCommandBuilder(NbPreferences.forModule(ACPOptionsPanel.class));
        return commandBuilder.build(ocPath);
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
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .thenAccept(res -> {
                    this.initialized = true;
                    readyFuture.complete(null);
                    LOG.log(Level.INFO, "ACP initialized successfully");
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Failed to initialize ACP", ex);
                    readyFuture.completeExceptionally(ex);
                    serverStarted = false;
                    stopServer();
                    return null;
                });
    }

    public void shutdown() {
        if (isClosing) {
            return;
        }
        stopServer();
    }

    public synchronized void restartServer() {
        LOG.info("Manual restart of ACP server requested...");
        stopServer();
        // Reset state so startServer() actually proceeds
        serverStarted = true; // Still marked as started since we want it to run
        initialized = false;
        startServer();
    }

    private void stopServer() {
        isClosing = true;
        if (rpcClient != null) {
            rpcClient.close();
            rpcClient = null;
        }

        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.log(Level.INFO, "Stopping ACP server (PID: {0})...", serverProcess.pid());

            // Capture descendants before the parent process potentially disappears
            List<ProcessHandle> descendants = serverProcess.descendants().toList();

            // 1. Try graceful exit via closed stdin (already triggered by rpcClient.close())
            try {
                if (serverProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    LOG.log(Level.INFO, "ACP server exited gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 2. If still alive, or if there are orphaned descendants, send SIGTERM
            if (serverProcess.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                LOG.log(Level.INFO, "Terminating process tree (parent + {0} descendants)...", descendants.size());

                descendants.forEach(h -> {
                    if (h.isAlive()) {
                        LOG.log(Level.FINE, "Sending SIGTERM to descendant PID: {0}", h.pid());
                        h.destroy();
                    }
                });
                if (serverProcess.isAlive()) {
                    serverProcess.destroy();
                }

                try {
                    serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 3. Final cleanup: Force kill anything remaining in the tree
            if (serverProcess.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                LOG.log(Level.WARNING, "Some processes still alive, forcing SIGKILL...");
                descendants.forEach(h -> {
                    if (h.isAlive()) {
                        LOG.log(Level.WARNING, "Killing descendant PID: {0}", h.pid());
                        h.destroyForcibly();
                    }
                });
                if (serverProcess.isAlive()) {
                    serverProcess.destroyForcibly();
                }
            }

            serverProcess = null;
            LOG.log(Level.INFO, "ACP server shutdown complete.");
        }
        serverProcess = null;
    }

    public void addSseListener(Consumer<SessionUpdate> listener) {
        sseListeners.add(listener);
    }

    private void notifyListeners(SessionUpdate update) {
        for (Consumer<SessionUpdate> listener : sseListeners) {
            listener.accept(update);
        }
    }

    /**
     * Check if RPC client is initialized. Returns false if not ready.
     */
    private boolean rpcClientReady() {
        return rpcClient != null;
    }

    public CompletableFuture<List<Session>> getSessions(String directory) {
        LOG.log(Level.INFO, "getSessions: called with directory={0}", directory);
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }
        Map<String, Object> params = new java.util.HashMap<>();
        if (directory != null && !directory.isEmpty()) {
            params.put("cwd", directory);
        }
        return rpcClient.sendRequest("session/list", params)
                .thenApply(res -> {
                    try {
                        LOG.log(Level.INFO, "getSessions: got response");
                        JsonNode root = objectMapper.readTree(res.traverse());
                        JsonNode result = root.has("result") ? root.get("result") : root;
                        JsonNode sessionsNode = result.has("sessions") ? result.get("sessions") : result.has("data") ? result.get("data") : result;
                        if (sessionsNode.isArray()) {
                            List<Session> rawSessions = objectMapper.readValue(sessionsNode.traverse(), new TypeReference<List<Session>>() {});
                            List<Session> sessions = new ArrayList<>();
                            for (Session s : rawSessions) {
                                // If the server returns a session for this specific directory, but it's missing the directory field, fill it in.
                                if (s.effectiveDirectory() == null) {
                                    s = new Session(s.id(), s.title(), directory, directory, s.parentID(), s.updatedAt(), s.mcpServers(), s.configOptions());
                                }
                                sessions.add(s);
                            }
                            LOG.log(Level.INFO, "getSessions: deserialized {0} sessions", sessions.size());
                            for (Session s : sessions) {
                                LOG.log(Level.INFO, "getSessions: id={0}, title=''{1}'', directory={2}", new Object[]{s.id(), s.title(), s.effectiveDirectory()});
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

    public CompletableFuture<List<Session>> getSessionsForDirectories(List<String> directories) {
        if (directories == null || directories.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        LOG.log(Level.INFO, "getSessionsForDirectories: querying {0} directories: {1}", new Object[]{directories.size(), directories});
        List<CompletableFuture<List<Session>>> futures = directories.stream()
                .map(dir -> getSessions(dir))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .flatMap(f -> {
                            try {
                                return f.get().stream();
                            } catch (Exception e) {
                                LOG.log(Level.WARNING, "Failed to get sessions for directory: {0}", e.getMessage());
                                return java.util.stream.Stream.empty();
                            }
                        })
                        .toList());
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
        if (!rpcClientReady()) {
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
        final String finalCwd = effectiveCwd;

        Map<String, Object> params = Map.of(
            "cwd", finalCwd,
            "mcpServers", List.of()
        );
        return rpcClient.sendRequest("session/new", params)
                .thenApply(res -> {
                    try {
                        Session s = objectMapper.treeToValue(res, Session.class);
                        // If the server didn't explicitly return the CWD we sent, associate it ourselves
                        if (s.effectiveDirectory() == null) {
                            s = new Session(s.id(), s.title(), finalCwd, finalCwd, s.parentID(), s.updatedAt(), s.mcpServers(), s.configOptions());
                        }
                        return s;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public CompletableFuture<List<SessionConfigOption>> loadSession(String sessionId, String cwd) {
        LOG.log(Level.INFO, "loadSession: called with {0}, cwd={1}", new Object[]{sessionId, cwd});
        if (!rpcClientReady()) {
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
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }

        List<Map<String, Object>> promptBlocks = new ArrayList<>();

        // 1. User Message Block
        Map<String, Object> userTextPart = new HashMap<>();
        userTextPart.put("type", "text");
        userTextPart.put("text", text);
        promptBlocks.add(userTextPart);

        if (context != null) {
            String filePath = (String) context.get("filePath");
            if (filePath != null) {
                File file = new File(filePath);
                String lang = getLanguageFromPath(filePath);
                String fileName = file.getName();

                // 2. Resource Link Block (Visual Breadcrumb)
                Map<String, Object> resourceLinkPart = new HashMap<>();
                resourceLinkPart.put("type", "resource_link");
                resourceLinkPart.put("uri", "file://" + filePath);
                resourceLinkPart.put("name", fileName);
                promptBlocks.add(resourceLinkPart);

                // 3. Metadata Comment Block (For the AI)
                Object cursorObj = context.get("cursor");
                Object selObj = context.get("selection");

                // Construct the JSON metadata object
                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("language", lang);
                metadataMap.put("filePath", filePath);
                if (cursorObj != null) {
                    metadataMap.put("cursor", cursorObj);
                }
                if (selObj != null) {
                    metadataMap.put("selection", selObj);
                }

                try {
                    String json = objectMapper.writeValueAsString(metadataMap);
                    Map<String, Object> metadataPart = new HashMap<>();
                    metadataPart.put("type", "text");
                    metadataPart.put("text", "<!-- " + json + " -->");

                    // Add annotations for assistant audience (OpenCode internal)
                    Map<String, Object> annotations = new HashMap<>();
                    annotations.put("audience", List.of("assistant"));
                    metadataPart.put("annotations", annotations);

                    promptBlocks.add(metadataPart);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to serialize metadata", e);
                }

                // 4. Selection Content Block (if any)
                String selectionContent = (String) context.get("selectionContent");
                if (selectionContent != null && !selectionContent.isEmpty()) {
                    Map<String, Object> selectionPart = new HashMap<>();
                    selectionPart.put("type", "text");
                    selectionPart.put("text", "\nSelection from `" + fileName + "`:\n```" + lang + "\n" + selectionContent + "\n```");
                    promptBlocks.add(selectionPart);
                }
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", promptBlocks);
        params.put("mcpServers", List.of());

        return rpcClient.sendRequest("session/prompt", params)
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> stopMessage(String sessionId) {
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException("Server not started"));
        }
        return rpcClient.sendRequest("session/cancel", Map.of("sessionId", sessionId))
                .thenApply(v -> null);
    }

    public CompletableFuture<JsonNode> renameSession(String sessionId, String newTitle) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);

        ObjectNode update = objectMapper.createObjectNode();
        update.put("sessionUpdate", "session_info_update");
        update.put("title", newTitle);

        params.set("update", update);

        return rpcClient.sendRequest("session/update", params);
    }

    public CompletableFuture<Void> deleteSession(String sessionId) {
        if (!rpcClientReady()) {
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
        return getCompletionsInline(text, line, column, prefix, suffix, 0, java.util.concurrent.TimeUnit.SECONDS);
    }

    public CompletableFuture<JsonNode> getCompletionsInline(String text, int line, int column, String prefix, String suffix, long timeout, java.util.concurrent.TimeUnit unit) {
        if (!rpcClientReady()) {
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
        return rpcClient.sendRequest("completion/inline", params, timeout, unit);
    }

    public CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        if (!rpcClientReady()) {
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

    public void removeSseListener(Consumer<SessionUpdate> listener) {
        sseListeners.remove(listener);
    }

    private void handleDisconnection() {
        if (isClosing) {
            return;
        }

        LOG.log(Level.WARNING, "ACP server disconnected unexpectedly");
        this.initialized = false;

        long now = System.currentTimeMillis();
        if (now - lastRestartTime > RESTART_RESET_INTERVAL) {
            restartCount = 0;
        }

        if (restartCount < MAX_RESTARTS) {
            restartCount++;
            lastRestartTime = now;
            long delay = restartCount * 2000L; // Exponential backoff: 2s, 4s, 6s...
            LOG.log(Level.INFO, "Respawning ACP server in {0}ms (attempt {1}/{2})...",
                    new Object[]{delay, restartCount, MAX_RESTARTS});

            java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                    .schedule(this::startServer, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            LOG.log(Level.SEVERE, "ACP server crashed {0} times within {1}ms. Giving up.",
                    new Object[]{MAX_RESTARTS, RESTART_RESET_INTERVAL});
        }
    }

    public void setPermissionHandler(PermissionHandler handler) {
        this.permissionHandler = handler;
    }

    private CompletableFuture<JsonNode> handleRequestPermission(JsonNode params) {
        String sessionId = params.has("sessionId") ? params.get("sessionId").asText() : null;
        String toolCallId = extractToolCallId(params);

        final String extractedId = toolCallId;
        CompletableFuture<String> response = new CompletableFuture<>();

        if (permissionHandler != null) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                permissionHandler.handlePermissionRequest(sessionId, params, response);
            });
        } else {
            response.complete("reject");
        }

        return response.thenApply(optionId -> {
            ObjectNode res = objectMapper.createObjectNode();

            // Map common internal IDs to standard ACP ones if needed
            String mappedId = optionId;
            if ("allow".equals(optionId) || "true".equals(optionId)) {
                mappedId = "once";
            } else if ("deny".equals(optionId) || "false".equals(optionId)) {
                mappedId = "reject";
            }
            if (mappedId == null) {
                mappedId = "once";
            }

            // Match ACP outcome structure
            ObjectNode outcome = objectMapper.createObjectNode();
            outcome.put("outcome", "selected");
            outcome.put("optionId", mappedId);
            res.set("outcome", outcome);

            if (sessionId != null) {
                res.put("sessionId", sessionId);
            }
            if (extractedId != null) {
                res.put("toolCallId", extractedId);
                res.put("tool_call_id", extractedId);
            }

            // Compatibility fields
            if ("reject".equals(optionId)) {
                res.put("allow", false);
            } else {
                res.put("allow", true);
            }

            return res;
        });
    }

    /**
     * Extract toolCallId from params, supporting both camelCase and snake_case variants.
     */
    private String extractToolCallId(JsonNode params) {
        if (params.has("toolCallId")) {
            return params.get("toolCallId").asText();
        } else if (params.has("tool_call_id")) {
            return params.get("tool_call_id").asText();
        } else if (params.has("toolCall")) {
            JsonNode tc = params.get("toolCall");
            if (tc.has("toolCallId")) {
                return tc.get("toolCallId").asText();
            } else if (tc.has("id")) {
                return tc.get("id").asText();
            }
        } else if (params.has("tool_call")) {
            JsonNode tc = params.get("tool_call");
            if (tc.has("id")) {
                return tc.get("id").asText();
            }
        }
        return null;
    }

    private CompletableFuture<JsonNode> handleWriteTextFile(JsonNode params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filePath = params.has("path") ? params.get("path").asText()
                        : params.has("filePath") ? params.get("filePath").asText() : null;

                if (filePath == null) {
                    throw new RuntimeException("Missing path parameter");
                }

                String content = params.has("content") ? params.get("content").asText() : "";
                java.io.File file = new java.io.File(filePath);

                // Ensure parent directories exist
                java.io.File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                java.nio.file.Files.writeString(file.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);

                // Refresh NetBeans filesystem to see the change
                FileObject fo = FileUtil.toFileObject(file);
                if (fo != null) {
                    fo.refresh();
                }

                return objectMapper.createObjectNode().put("success", true);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "fs/writeTextFile failed", e);
                throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
            }
        });
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

    private static String getLanguageFromPath(String path) {
        if (path == null || path.isEmpty()) return "";

        int lastDot = path.lastIndexOf('.');
        if (lastDot == -1) return "";

        try {
            String ext = path.substring(lastDot + 1).toLowerCase();

            // Common language keywords
            if (ext.equals("java")) return "java";
            if (ext.equals("py")) return "python";
            if (ext.equals("pl")) return "perl";
            if (ext.equals("awk")) return "awk";

            // Alias mappings - extract to map for cleaner handling
            if (ext.equals("javascript") || ext.equals("js")) return "javascript";
            if (ext.equals("typescript") || ext.equals("ts")) return "typescript";
            if (ext.equals("html")) return "html";
            if (ext.equals("css")) return "css";
            if (ext.equals("xml")) return "xml";
            if (ext.equals("markdown") || ext.equals("md")) return "markdown";
            if (ext.equals("json")) return "json";

            // Shell family aliases
            if (ext.equals("bash") || ext.equals("sh") || ext.equals("ksh") || ext.equals("zsh")) return "bash";

            // Return raw extension for unrecognized files
            return ext;
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }
}
