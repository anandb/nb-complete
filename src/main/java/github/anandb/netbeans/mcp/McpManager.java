package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class McpManager {
    private static final Logger LOG = Logger.from(McpManager.class);

    private final ExecutorService mcpStartExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "McpStart");
        t.setDaemon(true);
        return t;
    });

    private McpServer mcpServer;
    private final AtomicBoolean mcpDisabled = new AtomicBoolean(false);
    private CompletableFuture<Void> serverStartFuture;
    private volatile CompletableFuture<Void> readyFuture = new CompletableFuture<>();
    /** Thread running the server start task — used for explicit interruption
     *  on stop(), since CompletableFuture.cancel(true) only completes the
     *  future exceptionally without interrupting the running task. */
    private volatile Thread startThread;

    public void start() {
        LOG.info("McpManager.start() called, disabled={0}", mcpDisabled.get());
        if (mcpDisabled.get()) {
            LOG.info("MCP disabled, completing ready future immediately");
            readyFuture.complete(null);
            return;
        }
        synchronized (this) {
            if (mcpServer != null || serverStartFuture != null) {
                LOG.info("MCP server already starting or started");
                return;
            }
            readyFuture = new CompletableFuture<>();
            LOG.info("Starting MCP server asynchronously...");
            serverStartFuture = CompletableFuture.runAsync(() -> {
                startThread = Thread.currentThread();
                try {
                    McpServer server = null;
                    try {
                        LOG.info("Creating new McpServer instance...");
                        server = new McpServer();
                        LOG.info("Starting MCP server...");
                        server.start();
                        synchronized (McpManager.this) {
                            mcpServer = server;
                            serverStartFuture = null;
                            LOG.info("MCP Server running at {0}", server.getUrl());
                            // Complete inside the synchronized block to prevent a
                            // race where a concurrent start() replaces readyFuture
                            // between our null-out and the complete, which would
                            // prematurely complete the NEW future.
                            readyFuture.complete(null);
                        }
                    } catch (IOException e) {
                        LOG.warn("Failed to start MCP server: {0}", e.getMessage());
                        if (server != null) {
                            server.stop();
                        }
                        synchronized (McpManager.this) {
                            serverStartFuture = null;
                            // Complete inside the synchronized block to prevent a
                            // race where a concurrent start() replaces readyFuture
                            // between our null-out and the complete, which would
                            // prematurely complete the NEW future.
                            readyFuture.complete(null);
                        }
                    }
                } finally {
                    startThread = null;
                }
            }, mcpStartExecutor);
            LOG.info("MCP server start task submitted to async executor");
        }
    }

    public CompletableFuture<Void> waitForReady() {
        return readyFuture;
    }

    public void stop() {
        synchronized (this) {
            if (serverStartFuture != null) {
                // Interrupt the running start thread — CompletableFuture.cancel(true)
                // only completes the future exceptionally without interrupting.
                Thread t = startThread;
                if (t != null) {
                    t.interrupt();
                }
                serverStartFuture.cancel(true);
                serverStartFuture = null;
            }
            if (mcpServer != null) {
                mcpServer.stop();
                mcpServer = null;
            }
        }
        // Do NOT shut down mcpStartExecutor — it uses daemon threads and
        // must remain alive for restartServer() to submit new tasks.
    }

    public void disable() {
        if (mcpDisabled.compareAndSet(false, true)) {
            LOG.warn("Disabling MCP servers for remainder of this process");
            stop();
            readyFuture.complete(null);
        }
    }

    public boolean isDisabled() {
        return mcpDisabled.get();
    }

    public List<Map<String, Object>> getServerConfig() {
        List<Map<String, Object>> mcpServerList = new ArrayList<>();
        if (mcpDisabled.get()) return mcpServerList;

        synchronized (this) {
            if (mcpServer == null || !mcpServer.isRunning()) {
                return mcpServerList;
            }
            mcpServerList.add(Map.of(
                    "headers", List.of(),
                    "type", "http",
                    "name", "nb",
                    "url", mcpServer.getUrl()
            ));
        }
        return mcpServerList;
    }

    public synchronized McpTools getMcpTools() {
        if (mcpServer == null) {
            return null;
        }
        return mcpServer.getMcpTools();
    }

    public void checkServerSupport(JsonNode res) {
        JsonNode caps = res.has("agentCapabilities") ? res.get("agentCapabilities") : null;
        if (caps == null || !caps.has("mcpCapabilities")) {
            if (!mcpDisabled.get()) {
                LOG.info("Server does not advertise MCP support, disabling");
                disable();
            }
            return;
        }
        JsonNode mcpCaps = caps.get("mcpCapabilities");
        boolean supportsMcp = mcpCaps.has("http") && mcpCaps.get("http").asBoolean(false) ||
                              mcpCaps.has("sse") && mcpCaps.get("sse").asBoolean(false);

        if (!supportsMcp && !mcpDisabled.get()) {
            LOG.info("Server does not advertise MCP support, disabling {0}", mcpCaps.asText());
            disable();
        } else {
            LOG.info("Server advertises MCP support {0}", mcpCaps.asText());
        }
    }
}
