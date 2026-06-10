package github.anandb.netbeans.manager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.swing.text.Document;

import org.apache.commons.exec.CommandLine;

import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.openide.util.RequestProcessor;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.ui.ACPOptionsPanel;
import github.anandb.netbeans.support.LanguageResolver;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.mcp.McpManager;

@ServiceProvider(service = ProcessManager.class)
public class ProcessManager {
    private static final Logger LOG = Logger.from(ProcessManager.class);
    private static volatile ProcessManager INSTANCE;
    private final SlashCommandInterceptor slashCommandInterceptor = new SlashCommandInterceptor();

    public SlashCommandInterceptor getSlashCommandInterceptor() {
        return slashCommandInterceptor;
    }

    // Static shared ObjectMapper for all JSON operations
    private final ObjectMapper objectMapper = MapperSupplier.get();

    // Shared RequestProcessor for reconnection delays
    private RequestProcessor reconnectRP;
    private RequestProcessor.Task reconnectTask;

    private volatile Process serverProcess;
    private final AtomicReference<AcpProtocolClient> rpcClient = new AtomicReference<>();
    private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    private final List<Consumer<SessionUpdate>> sseListeners = new CopyOnWriteArrayList<>();
    private volatile List<SessionUpdate.AvailableCommand> availableCommands = List.of();
    private volatile PermissionHandler permissionHandler;
    private volatile Consumer<String> statusListener;
    private volatile Runnable crashHandler;
    private volatile Runnable readyHandler;
    private volatile boolean isClosing = false;
    private int restartCount = 0;
    private long lastRestartTime = 0;
    private static final int MAX_RESTARTS = 3;
    private static final long RESTART_RESET_INTERVAL = 300000; // 5 minutes


    private final McpManager mcpManager = new McpManager();
    private volatile boolean serverStarted = false;

    public ProcessManager() {
        mcpManager.start();
    }

    public McpManager getMcpManager() {
        return mcpManager;
    }

    public static ProcessManager getInstance() {
        ProcessManager pm = INSTANCE;
        if (pm == null) {
            synchronized (ProcessManager.class) {
                pm = INSTANCE;
                if (pm == null) {
                    pm = Lookup.getDefault().lookup(ProcessManager.class);
                    if (pm == null) {
                        pm = new ProcessManager();
                    }
                    INSTANCE = pm;
                }
            }
        }
        return pm;
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        return client.sendRequest(method, params);
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params, long timeout, TimeUnit unit) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }

        return client.sendRequest(method, params, unit.toSeconds(timeout));
    }

    public void sendNotification(String method, Object params) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            LOG.warn("sendNotification called with null rpcClient");
            return;
        }
        client.sendNotification(method, params);
    }

    public synchronized void ensureStarted() {
        if (!serverStarted) {
            serverStarted = true;
            startServer();
        }
    }

    private synchronized void startServer() {
        if (isClosing || (serverProcess != null && serverProcess.isAlive())) {
            return;
        }

        // Cancel any pending reconnect task
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
        if (reconnectRP == null) {
            reconnectRP = new RequestProcessor("ACP-Reconnect", 1, true);
        }
        isClosing = false;
        readyFuture = new CompletableFuture<>();

        // Ensure MCP server is running (idempotent - start() returns early if already running/disabled)
        mcpManager.start();

        LOG.info("Starting ACP server...");
        try {
            String executable = BinaryResolver.resolveExecutablePath();
            String args = NbPreferences.forModule(ACPOptionsPanel.class).get("processArguments", "acp");

            CommandLine cmd = new CommandLine(executable);
            cmd.addArguments(args, true);

            LOG.info("Executing: {0}", cmd);

            ProcessBuilder pb = new ProcessBuilder(Arrays.asList(cmd.toStrings()));
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Map<String, String> env = pb.environment();
            for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
                env.putIfAbsent(entry.getKey(), entry.getValue());
            }

            this.serverProcess = pb.start();

            AcpProtocolClient client = new AcpProtocolClient(serverProcess);
            this.rpcClient.set(client);
            client.start();
            client.setDisconnectionHandler(this::handleDisconnection);

            // Register handlers
            client.onRequest("fs/readTextFile", this::handleReadTextFile);
            client.onRequest("session/request_permission", this::handleRequestPermission);

            // Listen for session updates
            client.onNotification("session/update", params -> {
                // Extract raw type before parse (needed in catch block too)
                String rawType = null;
                try {
                    LOG.fine("Received session/update notification: {0}", params);
                    // Detect responding_finished/end_turn before Jackson drops them
                    JsonNode updateNode = params != null ? params.get("sessionUpdate") : null;
                    if (updateNode != null) {
                        rawType = updateNode.isTextual() ? updateNode.asText()
                            : (updateNode.has("type") ? updateNode.get("type").asText() : null);
                    }

                    // Construct synthetic SessionUpdate for textual turn-end signals
                    // before Jackson treeToValue drops them (they lack the "update" wrapper object)
                    if ("responding_finished".equals(rawType) || "end_turn".equals(rawType)) {
                        LOG.info("SSE turn-end signal received via textual sessionUpdate: {0}", rawType);
                        MessageType mt = MessageType.valueOf(rawType);
                        String ssId = params != null && params.has("sessionId")
                            ? params.get("sessionId").asText() : null;
                        SessionUpdate.UpdateData syntheticUpdate = new SessionUpdate.UpdateData(
                            mt, null, null, null, null, null, null, null, null, null,
                            null, null, null, null, null, null, null, null);
                        SessionUpdate.Params p = new SessionUpdate.Params(ssId, syntheticUpdate);
                        notifyListeners(new SessionUpdate("2.0", "session/update", p));
                        return;
                    }

                    SessionUpdate.Params sessionParams = objectMapper.treeToValue(params, SessionUpdate.Params.class);
                    SessionUpdate update = new SessionUpdate("2.0", "session/update", sessionParams);

                    // Update available commands if present, then propagate to listeners
                    if (update.update() != null && "available_commands_update".equals(update.type())) {
                        // Single volatile swap so readers never observe a partially populated list.
                        if (update.update().availableCommands() != null) {
                            availableCommands = List.copyOf(update.update().availableCommands());
                        }
                        // Fall through to notifyListeners so UI can treat this as an end_turn signal
                    }

                    notifyListeners(update);
                } catch (Exception e) {
                    LOG.log(rawType != null ? Level.INFO : Level.FINE,
                        "Failed to parse session/update notification: " + e.getMessage(), e);
                }
            });

            // Initialize ACP
            initializeProtocol();

            LOG.fine("ACP server process started successfully");
        } catch (Exception e) {
            LOG.severe("CRITICAL: Failed to start ACP server", e);
            readyFuture.completeExceptionally(e);

            // Clean up partially-started process and client to avoid resource leaks.
            // The process may have been started before the exception was thrown.
            AcpProtocolClient client = rpcClient.getAndSet(null);
            if (client != null) {
                client.close();
            }
            if (serverProcess != null && serverProcess.isAlive()) {
                serverProcess.destroyForcibly();
                serverProcess = null;
            }
        }
    }

    private void initializeProtocol() {
        Map<String, Object> params = Map.of(
                "protocolVersion", 1,
                "clientCapabilities", Map.of(
                        "fs", Map.of("readTextFile", true),
                        "terminal", true
                )
        );
        rpcClient.get().sendRequest("initialize", params)
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .thenAccept(res -> {
                    if (res != null) {
                        mcpManager.checkServerSupport(res);
                    }
                    readyFuture.complete(null);
                    LOG.fine("ACP initialized successfully");
                    if (readyHandler != null) {
                        try {
                            readyHandler.run();
                        } catch (Exception ex) {
                            LOG.warn("Ready handler failed", ex);
                        }
                    }
                })
                .exceptionally(ex -> {
                    LOG.severe("Failed to initialize ACP", ex);
                    readyFuture.completeExceptionally(ex);
                    synchronized (ProcessManager.this) {
                        serverStarted = false;
                    }
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
        // Reset all state so startServer() proceeds cleanly
        isClosing = false;
        serverStarted = true;
        restartCount = 0;
        startServer();
    }

    private synchronized void stopServer() {
        isClosing = true;

        // Cancel any pending reconnect task
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
        if (reconnectRP != null) {
            reconnectRP.stop();
            reconnectRP = null;
        }

        mcpManager.stop();

        AcpProtocolClient client = rpcClient.getAndSet(null);
        if (client != null) {
            client.close();
        }

        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.fine("Stopping ACP server (PID: {0})...", serverProcess.pid());

            // Capture descendants before the parent process potentially disappears
            List<ProcessHandle> descendants = serverProcess.descendants().toList();

            // 1. Try graceful exit via closed stdin (already triggered by rpcClient.close())
            try {
                if (serverProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    LOG.info("ACP server exited gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 2. If still alive, or if there are orphaned descendants, send SIGTERM
            if (serverProcess != null && serverProcess.isAlive()) {
                LOG.warn("Terminating process tree (parent + {0} descendants)...", descendants.size());

                for (ProcessHandle h : descendants) {
                    if (h.isAlive()) {
                        LOG.info("Sending SIGTERM to descendant PID: {0}", h.pid());
                        h.destroy();
                    }
                }
                if (serverProcess.isAlive()) {
                    serverProcess.destroy();
                }

                try {
                    serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (serverProcess.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
                LOG.warn("Some processes still alive, forcing SIGKILL...");
                descendants.forEach(h -> {
                    if (h.isAlive()) {
                        LOG.warn("Killing descendant PID: {0}", h.pid());
                        h.destroyForcibly();
                    }
                });
                if (serverProcess.isAlive()) {
                    serverProcess.destroyForcibly();
                }
            }

            LOG.info("ACP server shutdown complete.");
        }

        serverProcess = null;
    }

    public void addSseListener(Consumer<SessionUpdate> listener) {
        sseListeners.add(listener);
    }

    public void removeSseListener(Consumer<SessionUpdate> listener) {
        sseListeners.remove(listener);
    }

    private void notifyListeners(SessionUpdate update) {
        for (Consumer<SessionUpdate> listener : sseListeners) {
            listener.accept(update);
        }
    }

    public void touchConnection() {
        AcpProtocolClient client = rpcClient.get();
        if (client != null) {
            client.touch();
        }
    }

    public CompletableFuture<JsonNode> sendMessage(String sessionId, String text, Map<String, Object> context) {
        return sendMessage(sessionId, text, context, null);
    }

    public CompletableFuture<JsonNode> sendMessage(String sessionId, String text,  Map<String, Object> context,
                                                   List<Map<String, Object>> additionalBlocks) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
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

                // Metadata XML Block (For the AI)
                StringBuilder xml = new StringBuilder();
                xml.append("<metadata>\n");
                xml.append("  <purpose>reference</purpose>\n");
                xml.append("  <note>The file path, cursor, and selection below are reference-only")
                   .append(" context about the user's editor state. The user's text message")
                   .append(" that follows is the primary instruction.</note>\n");
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

        // 2. Additional blocks (file attachments, etc.)
        if (additionalBlocks != null) {
            promptBlocks.addAll(additionalBlocks);
        }

        // 3. User Message Block (End with this so model's focus is on the instruction)
        Map<String, Object> userTextPart = new HashMap<>();
        userTextPart.put("type", "text");
        userTextPart.put("text", text);
        promptBlocks.add(userTextPart);

        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("prompt", promptBlocks);
        params.put("mcpServers", mcpManager.getServerConfig());

        return client.sendRequest("session/prompt", params);
    }

    public CompletableFuture<Void> stopMessage(String sessionId) {
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            return CompletableFuture.failedFuture(new RuntimeException(operationalError()));
        }
        client.sendNotification("session/cancel", Map.of("sessionId", sessionId));
        return CompletableFuture.completedFuture(null);
    }

    public String getActiveProjectDir() {
        return System.getProperty("user.dir");
    }

    public List<SessionUpdate.AvailableCommand> getAvailableCommands() {
        return new ArrayList<>(availableCommands);
    }

    public CompletableFuture<Void> whenReady() {
        return readyFuture;
    }

    private synchronized void handleDisconnection() {
        if (isClosing) {
            LOG.warn("handleDisconnection called while closing — returning early (PID: {0})",
                    serverProcess != null ? serverProcess.pid() : "unknown");
            return;
        }

        LOG.warn("ACP server disconnected unexpectedly (PID: {0})",
                serverProcess != null ? serverProcess.pid() : "unknown");

        // Kill stale process if alive but broken pipes
        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.warn("Stale process PID {0} is still alive but pipes are broken — killing it",
                    serverProcess.pid());
            List<ProcessHandle> descendants = serverProcess.descendants().toList();
            for (ProcessHandle h : descendants) {
                if (h.isAlive()) {
                    h.destroyForcibly();
                }
            }
            serverProcess.destroyForcibly();
            try {
                serverProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Drain all pending futures so callers (e.g. sendMessage) get their exceptionally() fired
        AcpProtocolClient staleClient = rpcClient.getAndSet(null);
        if (staleClient != null) {
            staleClient.close();
        } else {
            LOG.warn("rpcClient was already null in handleDisconnection — pending futures will never complete");
        }

        // Notify crash handler (e.g. SessionManager) to reset state machine and UI
        if (crashHandler != null) {
            try {
                crashHandler.run();
            } catch (Exception ex) {
                LOG.warn("Crash handler failed", ex);
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastRestartTime > RESTART_RESET_INTERVAL) {
            restartCount = 0;
        }

        if (restartCount < MAX_RESTARTS) {
            restartCount++;
            lastRestartTime = now;
            long delay = restartCount * 2000L; // Exponential backoff: 2s, 4s, 6s...
            LOG.fine("Respawning ACP server in {0}ms (attempt {1}/{2})...",
                    new Object[]{delay, restartCount, MAX_RESTARTS});

            if (reconnectRP != null) {
                reconnectTask = reconnectRP.post(this::startServer, (int) delay);
            }
        } else {
            LOG.severe("ACP server crashed {0} times within {1}ms. Giving up.",
                       new Object[]{MAX_RESTARTS, RESTART_RESET_INTERVAL});
            if (statusListener != null) {
                statusListener.accept(NbBundle.getMessage(ProcessManager.class, "ERR_ServerCrashed", MAX_RESTARTS));
            }
        }
    }

    public void setPermissionHandler(PermissionHandler handler) {
        this.permissionHandler = handler;
    }

    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
    }

    public void setCrashHandler(Runnable handler) {
        this.crashHandler = handler;
    }

    public void setReadyHandler(Runnable handler) {
        this.readyHandler = handler;
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

    private CompletableFuture<JsonNode> handleReadTextFile(JsonNode params) {
        String filePath = params.has("filePath") ? params.get("filePath").asText()
                : params.has("path") ? params.get("path").asText() : null;

        if (filePath == null) {
            return CompletableFuture.failedFuture(new RuntimeException(NbBundle.getMessage(ProcessManager.class, "ERR_MissingFilePath")));
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return CompletableFuture.failedFuture(new RuntimeException(NbBundle.getMessage(ProcessManager.class, "ERR_FileNotFound", filePath)));
        }

        CompletableFuture<JsonNode> resultFuture = new CompletableFuture<>();

        FileObject fo = FileUtil.toFileObject(file);
        if (fo != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    DataObject dobj = DataObject.find(fo);
                    EditorCookie ec = dobj.getLookup().lookup(EditorCookie.class);
                    if (ec != null) {
                        Document doc = ec.getDocument();
                        if (doc != null) {
                            String content = doc.getText(0, doc.getLength());
                            resultFuture.complete(objectMapper.createObjectNode().put("content", content));
                            return;
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Could not read from editor for {0}, falling back to disk", filePath);
                }

                readFromDisk(file, filePath, resultFuture);
            });
        } else {
            readFromDisk(file, filePath, resultFuture);
        }

        return resultFuture;
    }

    private void readFromDisk(File file, String filePath, CompletableFuture<JsonNode> resultFuture) {
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                return objectMapper.createObjectNode().put("content", content);
            } catch (Exception e) {
                LOG.severe("fs/readTextFile failed", e);
                throw new RuntimeException(NbBundle.getMessage(ProcessManager.class, "ERR_ReadFileFailed", e.getMessage()), e);
            }
        }).thenAccept(resultFuture::complete)
          .exceptionally(ex -> {
              resultFuture.completeExceptionally(ex);
              return null;
          });
    }

    private String operationalError() {
        return NbBundle.getMessage(ProcessManager.class, "ERR_NotStarted");
    }
}
