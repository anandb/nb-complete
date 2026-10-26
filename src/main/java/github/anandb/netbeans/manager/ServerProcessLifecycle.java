package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.exception.ExceptionUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.io.File;
import java.io.IOException;

import org.apache.commons.exec.CommandLine;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;

import github.anandb.netbeans.contract.RequestHandler;
import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.FsWriteSettings;
import github.anandb.netbeans.support.ProcessTerminator;

/**
 * Extracted server lifecycle methods from ProcessManager.
 * Manages start, stop, restart, and protocol initialization of the ACP server process.
 */
class ServerProcessLifecycle {
    private static final Logger LOG = Logger.from(ServerProcessLifecycle.class);

    private final AtomicReference<AcpProtocolClient> rpcClient;
    private final ToolExecutor toolExecutor;
    private final Runnable onReady;
    private final Consumer<SessionUpdate> onNotify;
    private final Runnable onDisconnection;
    private final Consumer<String> onConnectionError;
    private final RequestHandler onReadTextFile;
    private final RequestHandler onWriteTextFile;
    private final RequestHandler onRequestPermission;

    private volatile Process serverProcess;
    /** Working directory the server process is bound to ({@code null} = inherited IDE directory). */
    private volatile File spawnWorkDir;
    private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    private volatile boolean isClosing = false;
    private volatile boolean serverStarted = false;
    /** Resolved harness with capability flags. */
    private volatile HarnessCatalog.Harness capabilities = HarnessCatalog.UNKNOWN;
    /**
     * Whether this agent advertised {@code sessionCapabilities.close} in the
     * {@code initialize} response. Per connection, not part of the static
     * catalog: ACP forbids calling {@code session/close} on an agent that did
     * not advertise it, so every close is gated on this.
     */
    private volatile boolean sessionCloseSupported;
    /** Listener fired once the harness is resolved after the initialize handshake. */
    private volatile Consumer<HarnessCatalog.Harness> harnessListener;
    private RequestProcessor reconnectRP;
    private RequestProcessor.Task reconnectTask;

    ServerProcessLifecycle(AtomicReference<AcpProtocolClient> rpcClient,
                           ToolExecutor toolExecutor,
                           Runnable onReady,
                           Consumer<SessionUpdate> onNotify,
                           Runnable onDisconnection,
                            Consumer<String> onConnectionError,
                            RequestHandler onReadTextFile,
                            RequestHandler onWriteTextFile,
                            RequestHandler onRequestPermission) {
        this.rpcClient = rpcClient;
        this.toolExecutor = toolExecutor;
        this.onReady = onReady;
        this.onNotify = onNotify;
        this.onDisconnection = onDisconnection;
        this.onConnectionError = onConnectionError;
        this.onReadTextFile = onReadTextFile;
        this.onWriteTextFile = onWriteTextFile;
        this.onRequestPermission = onRequestPermission;
    }

    /** Returns the resolved harness with capability flags. */
    HarnessCatalog.Harness getCapabilities() {
        return capabilities;
    }

    /** Whether the agent advertised {@code sessionCapabilities.close}. */
    boolean isSessionCloseSupported() {
        return sessionCloseSupported;
    }

    /** Registers a listener fired (on the initialize completion thread) when the
     *  harness is resolved from stored preferences. */
    void setHarnessListener(Consumer<HarnessCatalog.Harness> listener) {
        this.harnessListener = listener;
    }

    synchronized void ensureStarted() {
        if (!serverStarted) {
            serverStarted = true;
            startServer();
        }
    }

    /**
     * Ensures the server serves sessions from {@code workDir}: starts it bound
     * to the directory, restarts it when bound to a different one (one ACP
     * process serves one working directory for its whole life), and does
     * nothing when already bound to it.
     *
     * <p>A {@code null} or missing directory leaves a live server's binding
     * untouched — there is nothing to rebind to. When no server is running it
     * clears any previous binding, so the next process starts unbound instead
     * of inheriting a directory that no longer exists on disk.</p>
     *
     * <p>Rebinding restarts the process, which terminates every session the
     * current process serves: switching a session to another project ends the
     * previous project's in-flight turns by construction.</p>
     *
     * <p>The whole binding is gated by the experimental
     * {@code projectSwitchRestart.enabled} setting. While disabled the server is
     * started without a working directory and a live server keeps whatever
     * binding it has — exactly the behaviour before cwd binding was added.</p>
     */
    synchronized void ensureStartedIn(File workDir) {
        boolean bindCwd = PluginSettings.isProjectSwitchRestartEnabled();
        File target = bindCwd && workDir != null && workDir.isDirectory()
                ? workDir.getAbsoluteFile() : null;
        if (serverProcess != null && serverProcess.isAlive()) {
            if (shouldRebind(target, spawnWorkDir)) {
                LOG.warn("ACP server bound to {0}; restarting to serve {1}", spawnWorkDir, target);
                spawnWorkDir = target;
                restartServer();
            } else if (bindCwd && target != null && !sameDirectory(target, spawnWorkDir)) {
                LOG.fine("Project-switch rebind disabled; keeping ACP server bound to {0} (requested {1})",
                        spawnWorkDir, target);
            }
            return;
        }
        // Always assign: keeping a deleted directory here makes every later
        // ProcessBuilder.start() fail with "No such file or directory". When the
        // feature is off this also clears any binding left from an earlier run.
        spawnWorkDir = target;
        ensureStarted();
    }

    /**
     * Working directory to spawn the server in, or {@code null} to inherit the
     * IDE's. Gated by the experimental setting so a binding left over from when
     * the feature was enabled is not reused after the user turns it off
     * (v1.27.0 behaviour: the server starts unbound).
     */
    File spawnDirectory() {
        return PluginSettings.isProjectSwitchRestartEnabled() ? spawnWorkDir : null;
    }

    /**
     * True when a live server must be restarted to serve {@code target}: the
     * directories differ and the experimental project-switch rebind setting is
     * enabled. Kept as a pure decision so the gate is testable without a process.
     */
    static boolean shouldRebind(File target, File current) {
        return target != null && !sameDirectory(target, current)
                && PluginSettings.isProjectSwitchRestartEnabled();
    }

    /**
     * Absolute-path equality that also resolves symlinks and {@code ..}, so a
     * project reachable through two paths does not trigger a pointless restart.
     * The stored spawn directory stays as given; only the comparison is canonical.
     */
    static boolean sameDirectory(File a, File b) {
        if (a == null || b == null) {
            return a == b;
        }
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException ex) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
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
        if (readyFuture == null || readyFuture.isDone()) {
            readyFuture = new CompletableFuture<>();
        }

        // Token auth for the embedded MCP server is skipped only for the PI
        // harness binaries (pi, pi-acp, pi-agent) — they cannot carry tokens
        // on MCP URLs. Must be set BEFORE the MCP server starts so there is
        // no unauthenticated window.
        toolExecutor.setMcpAuthRequired(!BinaryResolver.isPiHarness());
        toolExecutor.start();

        LOG.info("Starting ACP server...");
        try {
            String args = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get("processArguments", "acp");

            // Wrap with WSL on Windows when available; otherwise launch natively.
            List<String> cmdStrings;
            if (BinaryResolver.isWslAvailable()) {
                // Tokenize once (no shell interpolation); buildWslArgs passes
                // the tokens as separate argv to a fixed 'exec "$0" "$@"' script
                // so user arguments cannot inject shell commands.
                String[] tokens = BinaryResolver.tokenizeArgs(args);
                cmdStrings = List.of(BinaryResolver.buildWslArgs(tokens));
                LOG.info("Executing via WSL: {0}", String.join(" ", cmdStrings));
            } else {
                String executable = BinaryResolver.resolveExecutablePath();
                CommandLine cmd = new CommandLine(executable);
                cmd.addArguments(args, true);
                cmdStrings = Arrays.asList(cmd.toStrings());
                LOG.info("Executing: {0}", cmd);
            }

            ProcessBuilder pb = new ProcessBuilder(cmdStrings);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Map<String, String> env = pb.environment();
            System.getenv().forEach(env::putIfAbsent);

            // The process is bound to one working directory for its whole life:
            // harnesses that resolve paths relative to their cwd (e.g. a
            // container workspace derived from it) need it fixed before the
            // process starts. The directory is the project the user picked for
            // a session (set by ensureStartedIn); there is no pre-session
            // guess. Before the first session the server inherits the IDE dir.
            File bindDir = spawnDirectory();
            if (bindDir != null) {
                pb.directory(bindDir);
                LOG.info("ACP server working directory: {0}", bindDir);
            } else {
                LOG.fine("ACP server inherits IDE working directory (cwd binding disabled or unbound)");
            }

            this.serverProcess = pb.start();

            // Seed the harness name from preferences so the wire log is named
            // correctly from the first write, before the initialize response
            // carries the full agentInfo.
            AcpSessionInfo.getInstance().setHarnessInfo(
                    BinaryResolver.resolveBinaryName(), null);
            // A restart may land on a different harness, which must not inherit
            // the previous one's advertised capabilities.
            sessionCloseSupported = false;

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
                    LOG.warn("Connection error detected, triggering reconnection: {0}", ExceptionUtils.getMessage(t));
                    if (onConnectionError != null) {
                        onConnectionError.accept(ExceptionUtils.getMessage(t));
                    }
                    onDisconnection.run();
                }
            });

            // Register handlers for both camelCase (legacy) and snake_case (spec) names.
            client.onRequest("fs/readTextFile", onReadTextFile);
            client.onRequest("fs/read_text_file", onReadTextFile);
            client.onRequest("fs/writeTextFile", onWriteTextFile);
            client.onRequest("fs/write_text_file", onWriteTextFile);
            client.onRequest("session/request_permission", onRequestPermission);

            // Listen for session updates
            client.onNotification("session/update", params -> {
                SessionUpdate update = SessionUpdateDecoder.decode(params);
                if (update != null) {
                    onNotify.accept(update);
                }
            });

            // Initialize ACP
            initializeProtocol();

            LOG.fine("ACP server process started successfully");
        } catch (Exception e) {
            if (e instanceof IllegalStateException && ExceptionUtils.getMessage(e) != null && ExceptionUtils.getMessage(e).contains("not found")) {
                LOG.warn("Failed to start ACP server: {0}", ExceptionUtils.getMessage(e));
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
        Map<String, Object> fsCaps = new HashMap<>();
        fsCaps.put("readTextFile", true);
        fsCaps.put("writeTextFile", FsWriteSettings.isEnabled());
        Map<String, Object> params = Map.of(
                "protocolVersion", 1,
                "clientCapabilities", Map.of(
                        "fs", fsCaps,
                        "terminal", false
                )
        );
        AcpProtocolClient client = rpcClient.get();
        if (client == null) {
            LOG.severe("Cannot initialize protocol — rpcClient is null");
            readyFuture.completeExceptionally(new IllegalStateException("rpcClient not set"));
            return;
        }
        // Bind this handshake to the generation it belongs to. A restart swaps
        // both fields; this callback must then leave the new process alone.
        CompletableFuture<Void> generation = readyFuture;
        client.sendRequest("initialize", params)
                // Exception to the no-orTimeout rule: initialize must complete fast
                // and does not stream long-running data.
                .orTimeout(30, TimeUnit.SECONDS)
                .thenAccept(res -> {
                    if (!isCurrentGeneration(client, generation)) {
                        LOG.fine("Ignoring initialize response from a superseded server generation");
                        generation.completeExceptionally(
                                new IllegalStateException("ACP server generation superseded during initialize"));
                        return;
                    }
                    if (res != null) {
                        // Store harness info from initialize response
                        JsonNode agentInfo = res.get("agentInfo");
                        // Reported agent version, kept for the version-dependent
                        // capability delta applied after the harness is resolved.
                        String agentVersion = null;
                        if (agentInfo != null) {
                            String name = agentInfo.has("name") ? agentInfo.get("name").asText() : null;
                            agentVersion = agentInfo.has("version") ? agentInfo.get("version").asText() : null;
                            if (name != null && !name.isBlank()) {
                                // Only a named agent may replace the name seeded from
                                // preferences above: that seed is what names the wire
                                // log and stamps harness attribution, and an unnamed
                                // agentInfo must not wipe it back to "unknown".
                                AcpSessionInfo.getInstance().setHarnessInfo(name, agentVersion);
                            }
                        }
                        // session/close is advertised per agent by the handshake,
                        // not by the static catalog, so it is captured here.
                        // ACP forbids calling session/close on an agent that did
                        // not advertise it.
                        JsonNode sessionCaps = res.get("agentCapabilities") == null
                                ? null : res.get("agentCapabilities").get("sessionCapabilities");
                        sessionCloseSupported = sessionCaps != null && sessionCaps.has("close");
                        // Resolve harness from stored preferences, ignoring the
                        // agent name from the initialize response.
                        String harnessId = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                                .get(PreferenceKeys.ACP_HARNESS_ID, null);
                        if (harnessId != null && !harnessId.isEmpty()) {
                            capabilities = HarnessCatalog.byId(harnessId);
                        }
                        if (capabilities == HarnessCatalog.UNKNOWN) {
                            String binName = BinaryResolver.resolveBinaryName();
                            if (binName != null) {
                                capabilities = HarnessCatalog.byBinaryName(binName);
                            }
                        }
                        // Version-dependent deltas win over the static catalog entry:
                        // OpenCode v2 dropped mid-turn steering, so its messages
                        // must be queued until turn end (used live by MessageSender).
                        capabilities = HarnessCatalog.forAgentVersion(capabilities, agentVersion);
                        toolExecutor.checkServerSupport(capabilities);
                        LOG.info("Harness resolved: {0} ({1})", capabilities.id(), capabilities.displayName());
                        Consumer<HarnessCatalog.Harness> listener = harnessListener;
                        if (listener != null) {
                            listener.accept(capabilities);
                        }
                    }
                    generation.complete(null);
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
                    // Unblock this generation's waiters first, even when superseded.
                    generation.completeExceptionally(ex);
                    if (!isCurrentGeneration(client, generation)) {
                        // A restart or shutdown replaced the process: never reset
                        // the new generation's state or stop its server from here.
                        LOG.fine("Ignoring failed initialize from a superseded server generation: {0}",
                                ExceptionUtils.getMessage(ex));
                        return null;
                    }
                    LOG.severe("Failed to initialize ACP", ex);
                    synchronized (ServerProcessLifecycle.this) {
                        serverStarted = false;
                    }
                    stopServer();
                    return null;
                });
    }

    /** True when {@code client}/{@code generation} still own the lifecycle state. */
    private boolean isCurrentGeneration(AcpProtocolClient client, CompletableFuture<Void> generation) {
        return rpcClient.get() == client && readyFuture == generation;
    }

    synchronized void restartServer() {
        LOG.fine("Manual restart of ACP server requested...");
        // A restart starts a new generation. Install its ready future BEFORE the
        // old client is closed: initialize callbacks capture the future they
        // started with, so the old generation can only complete its own future
        // and can never poison the new one or stop the new process.
        CompletableFuture<Void> previous = readyFuture;
        readyFuture = new CompletableFuture<>();
        stopServer();
        // Reset all state so startServer() proceeds cleanly
        isClosing = false;
        serverStarted = true;
        startServer();
        // Unblock waiters of the previous generation. That future is normally
        // already failed by stopServer() closing the client; a waiter that
        // grabbed it before any server existed would otherwise wait forever.
        if (previous != null && !previous.isDone()) {
            previous.completeExceptionally(new IOException("ACP server restarting"));
        }
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

        // Terminate the process FIRST, then close the client. The previous order
        // (close client, then kill process) could hang the entire restart: if the
        // server stopped draining its stdin (wedged thread/pipe buffer full),
        // writer.flush() blocks forever inside the writer monitor and close()
        // (which synchronizes on the same monitor) never returns — so the
        // process is never killed and startServer() never runs again.
        // Killing the process first breaks the pipes, unblocking the flush.
        if (serverProcess != null && serverProcess.isAlive()) {
            LOG.fine("Stopping ACP server (PID: {0})...", serverProcess.pid());
            ProcessTerminator.terminate(serverProcess);
        }

        AcpProtocolClient client = rpcClient.getAndSet(null);
        if (client != null) {
            client.close();
        }

        serverProcess = null;
        capabilities = HarnessCatalog.UNKNOWN;
    }

    // Getters for fields ProcessManager still needs
    Process serverProcess() { return serverProcess; }

    /** Working directory the current (or next) server process is bound to. */
    File boundWorkDir() { return spawnWorkDir; }

    boolean isClosing() { return isClosing; }

    boolean serverStarted() { return serverStarted; }

    CompletableFuture<Void> readyFuture() { return readyFuture; }

    RequestProcessor reconnectRP() { return reconnectRP; }

    void setReconnectTask(RequestProcessor.Task t) { this.reconnectTask = t; }
}
