package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class McpManager {
    private static final Logger LOG = new Logger(McpManager.class);

    private McpServer mcpServer;
    private volatile boolean mcpDisabled = true;
    private CompletableFuture<Void> serverStartFuture;
    private final CompletableFuture<Void> readyFuture = new CompletableFuture<>();

    public void start() {
        LOG.info("McpManager.start() called, disabled={0}", mcpDisabled);
        if (mcpDisabled) {
            LOG.info("MCP disabled, completing ready future immediately");
            readyFuture.complete(null);
            return;
        }
        synchronized (this) {
            if (mcpServer != null || serverStartFuture != null) {
                LOG.info("MCP server already starting or started");
                return;
            }
            LOG.info("Starting MCP server asynchronously...");
            serverStartFuture = CompletableFuture.runAsync(() -> {
                try {
                    LOG.info("Creating new McpServer instance...");
                    McpServer server = new McpServer();
                    LOG.info("Starting MCP server...");
                    server.start();
                    synchronized (McpManager.this) {
                        mcpServer = server;
                        serverStartFuture = null;
                    }
                    LOG.info("MCP Server running at {0}", server.getUrl());
                    LOG.info("Completing MCP ready future");
                    readyFuture.complete(null);
                } catch (IOException e) {
                    LOG.warn("Failed to start MCP server: {0}", e.getMessage());
                    synchronized (McpManager.this) {
                        serverStartFuture = null;
                    }
                    readyFuture.complete(null);
                }
            });
            LOG.info("MCP server start task submitted to async executor");
        }
    }

    public CompletableFuture<Void> waitForReady() {
        return readyFuture;
    }

    public void stop() {
        synchronized (this) {
            if (serverStartFuture != null) {
                serverStartFuture.cancel(true);
                serverStartFuture = null;
            }
            if (mcpServer != null) {
                mcpServer.stop();
                mcpServer = null;
            }
        }
    }

    public void disable() {
        if (!mcpDisabled) {
            mcpDisabled = true;
            LOG.warn("Disabling MCP servers for remainder of this process");
            stop();
            readyFuture.complete(null);
        }
    }

    public boolean isDisabled() {
        return mcpDisabled;
    }

    public List<Map<String, Object>> getServerConfig() {
        List<Map<String, Object>> mcpServerList = new ArrayList<>();
        if (mcpDisabled) return mcpServerList;

        synchronized (this) {
            if (mcpServer == null || !mcpServer.isRunning()) {
                return mcpServerList;
            }
            mcpServerList.add(Map.of(
                    "headers", List.of(),
                    "type", "http",
                    "name", "netbeans",
                    "url", mcpServer.getUrl()
            ));
        }
        return mcpServerList;
    }

    public McpTools getMcpTools() {
        if (mcpServer == null) {
            return null;
        }
        return mcpServer.getMcpTools();
    }

    public void checkServerSupport(JsonNode res) {
        JsonNode caps = res.has("agentCapabilities") ? res.get("agentCapabilities") : null;
        if (caps == null || !caps.has("mcpCapabilities")) {
            if (!mcpDisabled) {
                LOG.info("Server does not advertise MCP support, disabling");
                disable();
            }
            return;
        }
        JsonNode mcpCaps = caps.get("mcpCapabilities");
        boolean supportsMcp = mcpCaps.has("http") && mcpCaps.get("http").asBoolean(false) ||
                              mcpCaps.has("sse") && mcpCaps.get("sse").asBoolean(false);

        if (!supportsMcp && !mcpDisabled) {
            LOG.info("Server does not advertise MCP support, disabling {0}", mcpCaps.asText());
            disable();
        } else {
            LOG.info("Server advertises MCP support {0}", mcpCaps.asText());
        }
    }
}
