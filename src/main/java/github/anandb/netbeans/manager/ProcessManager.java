package github.anandb.netbeans.manager;

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
import java.util.regex.Pattern;

import javax.swing.text.Document;

import org.apache.commons.exec.CommandLine;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbPreferences;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.ui.ACPOptionsPanel;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.LanguageResolver;
import github.anandb.netbeans.support.MapperSupplier;

public class ProcessManager {

    private static final Logger LOG = new Logger(ProcessManager.class);
    private static final Pattern PATH_SEPARATOR_SPLIT = Pattern.compile(Pattern.quote(File.pathSeparator));
    private static ProcessManager instance;

    private final SlashCommandInterceptor slashCommandInterceptor = new SlashCommandInterceptor();

    public SlashCommandInterceptor getSlashCommandInterceptor() {
        return slashCommandInterceptor;
    }

    // Static shared ObjectMapper for all JSON operations
    private final ObjectMapper objectMapper = MapperSupplier.get();

    // Shared ScheduledExecutor for reconnection delays
    private ScheduledFuture<?> reconnectFuture;
    private ScheduledExecutorService reconnectExecutor;

    private Process serverProcess;
    private AcpProtocolClient rpcClient;
    private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    private final List<Consumer<SessionUpdate>> sseListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> projectChangeListeners = new CopyOnWriteArrayList<>();
    private final List<SessionUpdate.AvailableCommand> availableCommands = new CopyOnWriteArrayList<>();
    private PermissionHandler permissionHandler;
    private volatile boolean isClosing = false;
    private int restartCount = 0;
    private long lastRestartTime = 0;
    private static final int MAX_RESTARTS = 3;
    private static final long RESTART_RESET_INTERVAL = 300000; // 5 minutes
    private boolean shutdownHookAdded = false;

    private McpServer mcpServer;
    private volatile boolean mcpDisabled = true;

    private volatile boolean serverStarted = false;

    private ProcessManager() {
    }

    public synchronized void ensureStarted() {
        if (!serverStarted) {
            serverStarted = true;
            startServer();
        }
    }

    public static synchronized ProcessManager getInstance() {
        if (instance == null) {
            instance = new ProcessManager();
        }
        return instance;
    }

    private synchronized void startServer() {
        if (isClosing || (serverProcess != null && serverProcess.isAlive())) {
            return;
        }

        // Cancel any pending reconnect future
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ACP-Reconnect");
                t.setDaemon(true);
                return t;
            });
        }
        isClosing = false;
        if (readyFuture.isDone()) {
            readyFuture = new CompletableFuture<>();
        }

        // Start MCP weather server
        if (!mcpDisabled) {
            if (mcpServer == null) {
                mcpServer = new McpServer();
            }
            try {
                mcpServer.start();
            } catch (IOException e) {
                LOG.warn("Failed to start MCP weather server: {0}", e.getMessage());
            }
        }

        LOG.info("Starting ACP server...");
        try {
            String executable = resolveExecutablePath();
            String args = NbPreferences.forModule(ACPOptionsPanel.class).get("processArguments", "acp");

            CommandLine cmd = new CommandLine(executable);
            cmd.addArguments(args, true);

            LOG.info("Executing: {0}", cmd);

            ProcessBuilder pb = new ProcessBuilder(Arrays.asList(cmd.toStrings()));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Map<String, String> env = pb.environment();
            env.putAll(System.getenv()); // Ensure all system environment variables are propagated

            this.serverProcess = pb.start();

            this.rpcClient = new AcpProtocolClient(serverProcess);
            rpcClient.start();
            rpcClient.setDisconnectionHandler(this::handleDisconnection);

            // Register handlers
            rpcClient.onRequest("fs/readTextFile", this::handleReadTextFile);
            rpcClient.onRequest("fs/writeTextFile", this::handleWriteTextFile);
            rpcClient.onRequest("session/request_permission", this::handleRequestPermission);

            // Listen for session updates
            rpcClient.onNotification("session/update", params -> {
                try {
                    LOG.fine("Received session/update notification: {0}", params);
                    SessionUpdate.Params sessionParams = objectMapper.treeToValue(params, SessionUpdate.Params.class);
                    SessionUpdate update = new SessionUpdate("2.0", "session/update", sessionParams);

                    // Update available commands if present
                    if (update.update() != null && "available_commands_update".equals(update.type())) {
                        if (update.update().availableCommands() != null) {
                            availableCommands.clear();
                            availableCommands.addAll(update.update().availableCommands());
                            return;
                        }
                    }

                    notifyListeners(update);
                } catch (Exception e) {
                    LOG.warn("Failed to parse session/update notification: " + e.getMessage(), e);
                }
            });

            // Initialize ACP
            initializeProtocol();

            if (!shutdownHookAdded) {
                Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
                shutdownHookAdded = true;
            }

            LOG.fine("ACP server process started successfully");
        } catch (Exception e) {
            LOG.severe("CRITICAL: Failed to start ACP server", e);
            readyFuture.completeExceptionally(e);
        }
    }

    private String resolveExecutablePath() {
        // We use NbPreferences to match the rest of the plugin
        java.util.prefs.Preferences nbPrefs = NbPreferences.forModule(ACPOptionsPanel.class);
        String configuredPath = nbPrefs.get("acpExecutablePath", null);
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String exeName = isWindows ? "opencode.exe" : "opencode";

        // 1. Configured absolute path
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            File f = new File(configuredPath);
            if (f.isAbsolute() && f.exists()) {
                LOG.fine("Using configured absolute path: {0}", configuredPath);
                return configuredPath;
            } else {
                LOG.warn("Configured path not found: {0}", configuredPath);
            }
        }

        // 2. Search System PATH
        if (isInPath(exeName)) {
            LOG.log(Level.FINE, "Using 'opencode' found in system PATH");
            return exeName;
        }

        LOG.log(Level.WARNING, "Binary not found: no configured path and not on system PATH");
        throw new IllegalStateException("opencode binary not found: not configured and not on system PATH");
    }

    private boolean isInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        String sep = File.pathSeparator;
        for (String p : PATH_SEPARATOR_SPLIT.split(pathEnv)) {
            File f = new File(p, command);
            if (f.exists() && f.canExecute()) {
                return true;
            }
        }
        return false;
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
                    if (res != null) {
                        checkServerMcpSupport(res);
                    }
                    readyFuture.complete(null);
                    LOG.log(Level.FINE, "ACP initialized successfully");
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Failed to initialize ACP", ex);
                    readyFuture.completeExceptionally(ex);
                    serverStarted = false;
                    stopServer();
                    return null;
                });
    }

    private void checkServerMcpSupport(JsonNode res) {
        JsonNode caps = res.has("agentCapabilities") ? res.get("agentCapabilities") : null;
        if (caps == null || !caps.has("mcpCapabilities")) {
            if (!mcpDisabled) {
                LOG.info("Server does not advertise MCP support, disabling MCP for this process");
                disableMcp();
            }
            return;
        }
        JsonNode mcpCaps = caps.get("mcpCapabilities");
        boolean supportsMcp = mcpCaps.has("http") && mcpCaps.get("http").asBoolean(false)
                || mcpCaps.has("sse") && mcpCaps.get("sse").asBoolean(false);
        if (!supportsMcp && !mcpDisabled) {
            LOG.info("Server does not advertise MCP support, disabling MCP for this process");
            disableMcp();
        } else {
            LOG.fine("Server advertises MCP support");
        }
    }

    public void shutdown() {
        if (isClosing) {
            return;
        }
        stopServer();
    }

    public synchronized void restartServer() {
        LOG.fine("Manual restart of ACP server requested...");
        stopServer();
        // Reset state so startServer() actually proceeds
        isClosing = false;
        serverStarted = true;
        startServer();
    }

    private synchronized void stopServer() {
        isClosing = true;

        // Cancel any pending reconnect future
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
        reconnectExecutor.shutdownNow().forEach(task ->
                LOG.fine("Discarded pending reconnect task on shutdown: {0}", task));

        if (mcpServer != null) {
            mcpServer.stop();
            mcpServer = null;
        }

        if (rpcClient != null) {
            rpcClient.close();
            rpcClient = null;
        }

        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.log(Level.FINE, "Stopping ACP server (PID: {0})...", serverProcess.pid());

            // Capture descendants before the parent process potentially disappears
            List<ProcessHandle> descendants = serverProcess.descendants().toList();

            // 1. Try graceful exit via closed stdin (already triggered by rpcClient.close())
            try {
                if (serverProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    LOG.log(Level.FINE, "ACP server exited gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 2. If still alive, or if there are orphaned descendants, send SIGTERM
            if (serverProcess.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                LOG.log(Level.FINE, "Terminating process tree (parent + {0} descendants)...", descendants.size());

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
            LOG.log(Level.FINE, "ACP server shutdown complete.");
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
        LOG.log(Level.FINE, "getSessions: called with directory={0}", directory);
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        Map<String, Object> params = new java.util.HashMap<>();
        if (directory != null && !directory.isEmpty()) {
            params.put("cwd", directory);
        }
        return rpcClient.sendRequest("session/list", params)
                .thenApply(res -> {
                    try {
                        LOG.log(Level.FINE, "getSessions: got response");
                        if (res == null) {
                            LOG.warn("getSessions: null response");
                            return new ArrayList<Session>();
                        }
                        JsonNode root = res;
                        JsonNode sessionsNode = root.has("sessions") ? root.get("sessions") : root.has("data") ? root.get("data") : root;
                        if (sessionsNode.isArray()) {
                            List<Session> rawSessions = objectMapper.readValue(sessionsNode.traverse(), new TypeReference<List<Session>>() {
                            });
                            List<Session> sessions = new ArrayList<>();
                            for (Session s : rawSessions) {
                                // If the server returns a session for this specific directory, but it's missing the directory field, fill it in.
                                if (s.effectiveDirectory() != null) {
                                    sessions.add(s);
                                    continue;
                                }

                                sessions.add(new Session(s.id(), s.title(), directory, directory, s.parentID(), s.updatedAt(), s.mcpServers(), s.configOptions()));
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

    private List<Map<String, String>> getMcpServerConfig() {
        if (mcpDisabled || mcpServer == null || !mcpServer.isRunning()) {
            return List.of();
        }
        return List.of(Map.of(
                "type", "sse",
                "name", "weather",
                "url", mcpServer.getUrl()
        ));
    }

    private boolean isInvalidParamsError(Throwable t) {
        if (t == null) {
            return false;
        }
        String msg = t.getMessage();
        return msg != null && msg.contains("Invalid params");
    }

    private void disableMcp() {
        if (!mcpDisabled) {
            mcpDisabled = true;
            LOG.warn("Disabling MCP servers for the remainder of this process due to Invalid Params error from server");
            if (mcpServer != null) {
                mcpServer.stop();
            }
        }
    }

    public CompletableFuture<Session> createSession(String cwd) {
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
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

        LOG.log(Level.FINE, "Creating new session with CWD: {0}", effectiveCwd);
        final String finalCwd = effectiveCwd;

        return sendCreateSessionRequest(finalCwd).handle((res, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(res);
            }
            Throwable cause = (ex instanceof java.util.concurrent.CompletionException) ? ex.getCause() : ex;
            if (isInvalidParamsError(cause) && !mcpDisabled) {
                LOG.warn("session/new failed with Invalid Params, retrying without MCP servers");
                disableMcp();
                return sendCreateSessionRequest(finalCwd);
            }
            return CompletableFuture.<Session>failedFuture(ex);
        }).thenCompose(f -> f);
    }

    private CompletableFuture<Session> sendCreateSessionRequest(String finalCwd) {
        Map<String, Object> params = new HashMap<>();
        params.put("cwd", finalCwd);
        params.put("mcpServers", getMcpServerConfig());
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
        LOG.fine("loadSession: called with {0}, cwd={1}", sessionId, cwd);
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }

        return sendLoadSessionRequest(sessionId, cwd).handle((res, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(res);
            }
            Throwable cause = (ex instanceof java.util.concurrent.CompletionException) ? ex.getCause() : ex;
            if (isInvalidParamsError(cause) && !mcpDisabled) {
                LOG.warn("session/load failed with Invalid Params, retrying without MCP servers");
                disableMcp();
                return sendLoadSessionRequest(sessionId, cwd);
            }
            return CompletableFuture.<List<SessionConfigOption>>failedFuture(ex);
        }).thenCompose(f -> f)
        .exceptionally(ex -> {
            LOG.warn("loadSession: error: {0}", ex.getMessage());
            return null;
        });
    }

    private CompletableFuture<List<SessionConfigOption>> sendLoadSessionRequest(String sessionId, String cwd) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("sessionId", sessionId);
        if (cwd != null) {
            params.put("cwd", cwd);
        }
        params.put("mcpServers", getMcpServerConfig());

        return rpcClient.sendRequest("session/load", params)
                .thenApply(res -> {
                    LOG.fine("loadSession: got response {0}", res);
                    if (res != null && res.has("configOptions")) {
                        try {
                            return objectMapper.convertValue(res.get("configOptions"), new TypeReference<List<SessionConfigOption>>() {
                            });
                        } catch (Exception e) {
                            LOG.warn("Failed to parse configOptions: {0}", e.getMessage());
                        }
                    }
                    return null;
                });
    }

    public CompletableFuture<JsonNode> sendMessage(String sessionId, String text, Map<String, Object> context) {
        return sendMessage(sessionId, text, context, null);
    }

    public CompletableFuture<JsonNode> sendMessage(String sessionId, String text, Map<String, Object> context, List<Map<String, Object>> additionalBlocks) {
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }

        List<Map<String, Object>> promptBlocks = new ArrayList<>();

        // 1. Context & Metadata (Start with this so model has environment context first)
        if (context != null) {
            String filePath = (String) context.get("filePath");
            if (filePath != null) {
                File file = new File(filePath);
                String lang = LanguageResolver.fromPath(filePath);
                String fileName = file.getName();

                // Resource Link Block (Visual Breadcrumb)
                Map<String, Object> resourceLinkPart = new HashMap<>();
                resourceLinkPart.put("type", "resource_link");
                resourceLinkPart.put("uri", "file://" + filePath);
                resourceLinkPart.put("name", fileName);
                promptBlocks.add(resourceLinkPart);

                // Metadata XML Block (For the AI)
                StringBuilder xml = new StringBuilder();
                xml.append("<metadata>\n");
                xml.append("  <language>").append(lang).append("</language>\n");
                xml.append("  <file_path>").append(filePath).append("</file_path>\n");

                Object cursorObj = context.get("cursor");
                if (cursorObj != null) {
                    xml.append("  <cursor>").append(cursorObj.toString()).append("</cursor>\n");
                }

                Object selObj = context.get("selection");
                if (selObj != null) {
                    xml.append("  <selection>").append(selObj.toString()).append("</selection>\n");
                }
                xml.append("</metadata>");

                Map<String, Object> metadataPart = new HashMap<>();
                metadataPart.put("type", "text");
                metadataPart.put("text", xml.toString());

                // Add annotations for assistant audience (OpenCode internal)
                Map<String, Object> annotations = new HashMap<>();
                annotations.put("audience", List.of("assistant"));
                metadataPart.put("annotations", annotations);

                promptBlocks.add(metadataPart);

                // Selection Content Block (if any)
                String selectionContent = (String) context.get("selectionContent");
                if (selectionContent != null && !selectionContent.isEmpty()) {
                    Map<String, Object> selectionPart = new HashMap<>();
                    selectionPart.put("type", "text");
                    selectionPart.put("text", "\nSelection from `" + fileName + "`:\n```" + lang + "\n" + selectionContent + "\n```\n");
                    promptBlocks.add(selectionPart);
                }
            }
        }

        // 1b. Additional blocks (file attachments, etc.)
        if (additionalBlocks != null) {
            promptBlocks.addAll(additionalBlocks);
        }

        // 2. User Message Block (End with this so model's focus is on the instruction)
        Map<String, Object> userTextPart = new HashMap<>();
        userTextPart.put("type", "text");
        userTextPart.put("text", text);
        promptBlocks.add(userTextPart);

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", promptBlocks);
        params.put("mcpServers", getMcpServerConfig());

        return rpcClient.sendRequest("session/prompt", params)
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> stopMessage(String sessionId) {
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        rpcClient.sendNotification("session/cancel", Map.of("sessionId", sessionId));
        return CompletableFuture.completedFuture(null);
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
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        // Using session/close as the standard termination method
        return rpcClient.sendRequest("session/delete", Map.of("sessionId", sessionId))
                .thenApply(v -> null);
    }

    public CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        if (!rpcClientReady()) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
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

    public CompletableFuture<Void> whenReady() {
        return readyFuture;
    }

    private synchronized void handleDisconnection() {
        if (isClosing) {
            return;
        }

        LOG.log(Level.WARNING, "ACP server disconnected unexpectedly (PID: {0})",
                serverProcess != null ? serverProcess.pid() : "unknown");

        // Kill stale process if alive but broken pipes
        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.log(Level.WARNING, "Stale process PID {0} is still alive but pipes are broken — killing it",
                    serverProcess.pid());
            List<ProcessHandle> descendants = serverProcess.descendants().toList();
            descendants.forEach(h -> { if (h.isAlive()) h.destroyForcibly(); });
            serverProcess.destroyForcibly();
            try {
                serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Drain all pending futures so callers (e.g. sendMessage) get their exceptionally() fired
        AcpProtocolClient staleClient = rpcClient;
        if (staleClient != null) {
            rpcClient = null;
            staleClient.close();
        }


        long now = System.currentTimeMillis();
        if (now - lastRestartTime > RESTART_RESET_INTERVAL) {
            restartCount = 0;
        }

        if (restartCount < MAX_RESTARTS) {
            restartCount++;
            lastRestartTime = now;
            long delay = restartCount * 2000L; // Exponential backoff: 2s, 4s, 6s...
            LOG.log(Level.FINE, "Respawning ACP server in {0}ms (attempt {1}/{2})...",
                    new Object[]{delay, restartCount, MAX_RESTARTS});

            this.reconnectFuture = reconnectExecutor.schedule(this::startServer, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
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
        String toolCallId = ToolDataExtractor.extractToolCallId(params);

        final String extractedId = toolCallId;
        CompletableFuture<String> response = new CompletableFuture<>();

        if (permissionHandler != null) {
            SwingUtilities.invokeLater(() -> {
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

    private String operationalError() {
        return "Server not started, Please check if Opencode is installed and available";
    }
}
