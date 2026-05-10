package github.anandb.netbeans.manager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.Set;

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
    private Consumer<String> statusListener;
    private volatile boolean isClosing = false;
    private int restartCount = 0;
    private long lastRestartTime = 0;
    private static final int MAX_RESTARTS = 3;
    private static final long RESTART_RESET_INTERVAL = 300000; // 5 minutes
    private boolean shutdownHookAdded = false;

    private final McpManager mcpManager = new McpManager();

    private volatile boolean serverStarted = false;

    private final Set<String> pluginWritingFiles = ConcurrentHashMap.newKeySet();

    private ProcessManager() {
    }

    public McpManager getMcpManager() {
        return mcpManager;
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params) {
        return rpcClient.sendRequest(method, params);
    }

    public void sendNotification(String method, Object params) {
        rpcClient.sendNotification(method, params);
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
        mcpManager.start();

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

                    // Record local history for completed file-modifying tool calls
                    recordLocalHistory(params);

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
                        mcpManager.checkServerSupport(res);
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

        mcpManager.stop();

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
        params.put("mcpServers", mcpManager.getServerConfig());

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


    public void setActiveProject(String path) {
        for (Consumer<String> listener : projectChangeListeners) {
            listener.accept(path);
        }
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

    public boolean isPluginWriting(String path) {
        return path != null && pluginWritingFiles.contains(path);
    }

    private void markPluginWriting(String path) {
        if (path != null) {
            pluginWritingFiles.add(path);
        }
    }

    private void unmarkPluginWriting(String path) {
        if (path != null) {
            pluginWritingFiles.remove(path);
        }
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

    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
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

    private boolean isWithinProject(File file) {
        String projectDir = getActiveProjectDir();
        if (projectDir == null) return false;
        return file.toPath().normalize().startsWith(Paths.get(projectDir).normalize());
    }

    private void recordLocalHistory(JsonNode updateParams) {
        // Only act on completed tool_call_update messages
        JsonNode update = updateParams.get("update");
        if (update == null) return;
        String type = update.has("sessionUpdate") ? update.get("sessionUpdate").asText() : null;
        if (!"tool_call_update".equals(type)) return;
        String status = update.has("status") ? update.get("status").asText() : null;
        if (!"completed".equals(status)) return;
        String kind = update.has("kind") ? update.get("kind").asText() : null;
        if (!"edit".equals(kind) && !"write".equals(kind) && !"delete".equals(kind)) return;

        // Extract file path from rawInput
        JsonNode rawInput = update.get("rawInput");
        if (rawInput == null) {
            LOG.fine("Local history skipped: rawInput is null for kind={0}", kind);
            return;
        }
        String filePath = rawInput.has("filePath") ? rawInput.get("filePath").asText()
                : rawInput.has("path") ? rawInput.get("path").asText() : null;
        if (filePath == null || filePath.isEmpty()) return;

        // Resolve and verify path is within current project directory
        File file = new File(filePath);
        if (!file.isAbsolute()) {
            String projectDir = getActiveProjectDir();
            file = new File(projectDir, filePath);
        }
        if (!isWithinProject(file)) {
            LOG.fine("Skipping local history: file outside project: {0}", filePath);
            Consumer<String> listener = statusListener;
            if (listener != null) {
                listener.accept("Local history skipped: file outside project");
            }
            return;
        }

        // Skip timestamp check for deletes: file may already be gone
        if (!"delete".equals(kind)) {
            long twoMinAgo = System.currentTimeMillis() - 120_000;
            if (file.exists() && file.lastModified() < twoMinAgo) {
                LOG.fine("Skipping local history: file not recently modified: {0}", filePath);
                return;
            }
        }

        if ("delete".equals(kind)) {
            triggerDeleteHistory(file);
        } else {
            writeThroughVFS(file);
        }
    }

    private void writeThroughVFS(File file) {
        String filePath = file.getAbsolutePath();
        markPluginWriting(filePath);
        try {
            if (!file.exists()) return;
            FileObject fo = FileUtil.toFileObject(file);
            if (fo == null) {
                fo = FileUtil.createData(file);
            }
            if (fo != null) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);

                // Sync the open editor Document to match disk
                try {
                    DataObject dobj = DataObject.find(fo);
                    EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                    if (ec != null) {
                        Document doc = ec.openDocument();
                        if (doc != null) {
                            doc.remove(0, doc.getLength());
                            doc.insertString(0, content, null);
                            ec.saveDocument();
                            markUnmodifiedAfterWrite(fo);
                            LOG.fine("Local history triggered via editor Document: {0}", file.getAbsolutePath());
                            return;
                        }
                    }
                } catch (Exception e) {
                    LOG.fine("Could not sync editor for local history: {0}", e.getMessage());
                }

                // Fallback: refresh VFS
                fo.refresh();
                LOG.fine("Notified NetBeans of file change (refresh only): {0}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            LOG.info("Failed to trigger local history: {0}", e.getMessage());
        } finally {
            unmarkPluginWriting(filePath);
        }
    }

    private void triggerDeleteHistory(File file) {
        try {
            File parentDir = file.getParentFile();
            if (parentDir == null) return;
            FileObject parentFo = FileUtil.toFileObject(parentDir);
            if (parentFo != null) {
                parentFo.refresh();
                LOG.fine("Local history recorded for deletion: {0}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            LOG.info("Failed to trigger local history for deletion: {0}", e.getMessage());
        }
    }

    private CompletableFuture<JsonNode> handleWriteTextFile(JsonNode params) {
        return CompletableFuture.supplyAsync(() -> {
            String filePath = null;
            try {
                filePath = params.has("path") ? params.get("path").asText()
                        : params.has("filePath") ? params.get("filePath").asText() : null;

                LOG.info("Write {0}", filePath);
                if (filePath == null) {
                    throw new RuntimeException("Missing path parameter");
                }

                String content = params.has("content") ? params.get("content").asText() : "";
                File file = new File(filePath);

                // Verify path is within project directory
                if (!isWithinProject(file)) {
                    LOG.warn("fs/writeTextFile rejected: path outside project: {0}", filePath);
                    throw new RuntimeException("Refused to write outside project directory: " + filePath);
                }

                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                boolean written = false;

                // Suppress VFS "externally modified" notifications for this file
                markPluginWriting(filePath);

                // Write to disk first so the FileObject is up-to-date
                byte[] data = content.getBytes(StandardCharsets.UTF_8);
                Files.write(file.toPath(), data);

                // Then sync the open editor Document to match disk
                try {
                    FileObject fo = FileUtil.toFileObject(file);
                    if (fo != null) {
                        DataObject dobj = DataObject.find(fo);
                        EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                        if (ec != null) {
                            Document doc = ec.openDocument();
                            if (doc != null) {
                                doc.remove(0, doc.getLength());
                                doc.insertString(0, content, null);
                                ec.saveDocument();
                                markUnmodifiedAfterWrite(fo);
                                written = true;
                                LOG.fine("Write through editor Document: {0}", filePath);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.fine("Could not sync editor after write: {0}", e.getMessage());
                    written = true; // disk write succeeded
                }

                return objectMapper.createObjectNode().put("success", true);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "fs/writeTextFile failed", e);
                throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
            } finally {
                if (filePath != null) {
                    unmarkPluginWriting(filePath);
                }
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

                File file = new File(filePath);
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
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
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

    /**
     * Registers a temporary FileChangeListener on the given FileObject that
     * marks the file as unmodified after a plugin-initiated save, preventing
     * NetBeans' "externally modified" dialog from appearing.
     */
    private void markUnmodifiedAfterWrite(FileObject fo) {
        try {
            fo.setAttribute("unmodified", true);
        } catch (IOException e) {
            LOG.fine("Could not set unmodified attribute: {0}", e.getMessage());
        }
    }
}
