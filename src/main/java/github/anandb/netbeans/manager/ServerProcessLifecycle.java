package github.anandb.netbeans.manager;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.apache.commons.exec.CommandLine;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.RequestHandler;
import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.ProcessTerminator;

/**
 * Extracted server lifecycle methods from ProcessManager.
 * Manages start, stop, restart, and protocol initialization of the ACP server process.
 */
class ServerProcessLifecycle {
    private static final Logger LOG = Logger.from(ServerProcessLifecycle.class);

    private final AtomicReference<AcpProtocolClient> rpcClient;
    private final ToolExecutor toolExecutor;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final Runnable onReady;
    private final Consumer<SessionUpdate> onNotify;
    private final Runnable onDisconnection;
    private final Consumer<String> onConnectionError;
    private final RequestHandler onReadTextFile;
    private final RequestHandler onRequestPermission;

    private volatile Process serverProcess;
    private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    private volatile boolean isClosing = false;
    private volatile boolean serverStarted = false;
    private RequestProcessor reconnectRP;
    private RequestProcessor.Task reconnectTask;

    ServerProcessLifecycle(AtomicReference<AcpProtocolClient> rpcClient,
                           ToolExecutor toolExecutor,
                           com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                           Runnable onReady,
                           Consumer<SessionUpdate> onNotify,
                           Runnable onDisconnection,
                           Consumer<String> onConnectionError,
                           RequestHandler onReadTextFile,
                           RequestHandler onRequestPermission) {
        this.rpcClient = rpcClient;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.onReady = onReady;
        this.onNotify = onNotify;
        this.onDisconnection = onDisconnection;
        this.onConnectionError = onConnectionError;
        this.onReadTextFile = onReadTextFile;
        this.onRequestPermission = onRequestPermission;
    }

    synchronized void ensureStarted() {
        if (!serverStarted) {
            serverStarted = true;
            startServer();
        }
    }

    synchronized void startServer() {
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
        toolExecutor.start();

        LOG.info("Starting ACP server...");
        try {
            String executable = BinaryResolver.resolveExecutablePath();
            String args = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get("processArguments", "acp");

            // Strip shell metacharacters — args are passed directly to the binary
            // (no shell interpretation), but users can accidentally paste dangerous
            // patterns into the Options panel.
            if (args != null && (args.contains(";") || args.contains("|") || args.contains("$")
                    || args.contains("`") || args.contains("\\n"))) {
                LOG.warn("processArguments contains suspicious characters, stripping: {0}", args);
                args = args.replaceAll("[;|$`]", "").replace("\\n", " ");
            }

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
            client.setDisconnectionHandler(onDisconnection);
            client.setConnectionErrorHandler(t -> {
                // Write failures (broken pipe) should also trigger reconnection.
                // The reader thread may be stuck on blocking I/O and not detect
                // the server death promptly. Guard with isClosing to avoid
                // reconnection during intentional shutdown.
                if (!isClosing) {
                    LOG.warn("Connection error detected, triggering reconnection: {0}", t.getMessage());
                    if (onConnectionError != null) {
                        onConnectionError.accept(t.getMessage());
                    }
                    onDisconnection.run();
                }
            });

            // Register handlers
            client.onRequest("fs/readTextFile", onReadTextFile);
            client.onRequest("session/request_permission", onRequestPermission);

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
                        LOG.fine("SSE turn-end signal received via textual sessionUpdate: {0}", rawType);
                        MessageType mt = MessageType.valueOf(rawType);
                        String ssId = params != null && params.has("sessionId")
                            ? params.get("sessionId").asText() : null;
                        SessionUpdate.UpdateData syntheticUpdate = new SessionUpdate.UpdateData(
                            mt, null, null, null, null, null, null, null, null, null,
                            null, null, null, null, null, null, null, null);
                        SessionUpdate.Params p = new SessionUpdate.Params(ssId, syntheticUpdate);
                        onNotify.accept(new SessionUpdate("2.0", "session/update", p));
                        return;
                    }

                    SessionUpdate.Params sessionParams = objectMapper.treeToValue(params, SessionUpdate.Params.class);
                    SessionUpdate update = new SessionUpdate("2.0", "session/update", sessionParams);

                    onNotify.accept(update);
                } catch (Exception e) {
                    LOG.log(rawType != null ? Level.INFO : Level.FINE,
                        "Failed to parse session/update notification: " + e.getMessage(), e);
                }
            });

            // Initialize ACP
            initializeProtocol();

            LOG.fine("ACP server process started successfully");
        } catch (Exception e) {
            if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().contains("not found")) {
                LOG.warn("Failed to start ACP server: {0}", e.getMessage());
            } else {
                LOG.severe("CRITICAL: Failed to start ACP server", e);
            }
            readyFuture.completeExceptionally(e);
            // Reset so ensureStarted() can retry.
            serverStarted = false;

            // Clean up partially-started process and client to avoid resource leaks.
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
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            LOG.severe("Cannot initialize protocol — rpcClient is null");
            readyFuture.completeExceptionally(new IllegalStateException("rpcClient not set"));
            return;
        }
        client.sendRequest("initialize", params)
                .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .thenAccept(res -> {
                    if (res != null) {
                        toolExecutor.checkServerSupport(res);
                    }
                    readyFuture.complete(null);
                    LOG.fine("ACP initialized successfully");
                    // Guard: stopServer() may have been called during the async
                    // initialize window. Do not fire the ready callback if the
                    // server is shutting down — it would operate on dead resources.
                    if (onReady != null && !isClosing) {
                        try {
                            onReady.run();
                        } catch (Exception ex) {
                            LOG.warn("Ready handler failed", ex);
                        }
                    }
                })
                .exceptionally(ex -> {
                    LOG.severe("Failed to initialize ACP", ex);
                    readyFuture.completeExceptionally(ex);
                    synchronized (ServerProcessLifecycle.this) {
                        serverStarted = false;
                    }
                    stopServer();
                    return null;
                });
    }

    synchronized void restartServer() {
        LOG.fine("Manual restart of ACP server requested...");
        stopServer();
        // Reset all state so startServer() proceeds cleanly
        isClosing = false;
        serverStarted = true;
        startServer();
    }

    synchronized void stopServer() {
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

        toolExecutor.stop();

        AcpProtocolClient client = rpcClient.getAndSet(null);
        if (client != null) {
            client.close();
        }

        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.fine("Stopping ACP server (PID: {0})...", serverProcess.pid());
            ProcessTerminator.terminate(serverProcess);
        }

        serverProcess = null;
    }

    // Getters for fields ProcessManager still needs
    Process serverProcess() { return serverProcess; }

    boolean isClosing() { return isClosing; }

    boolean serverStarted() { return serverStarted; }

    CompletableFuture<Void> readyFuture() { return readyFuture; }

    RequestProcessor reconnectRP() { return reconnectRP; }

    RequestProcessor.Task reconnectTask() { return reconnectTask; }

    void setReconnectRP(RequestProcessor rp) { this.reconnectRP = rp; }

    void setReconnectTask(RequestProcessor.Task t) { this.reconnectTask = t; }
}
