package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.support.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class McpManager {
    private static final Logger LOG = new Logger(McpManager.class);

    private McpServer mcpServer;
    private volatile boolean mcpDisabled = true;

    public void start() {
        if (mcpDisabled) return;
        if (mcpServer == null) {
            mcpServer = new McpServer();
        }
        try {
            mcpServer.start();
        } catch (IOException e) {
            LOG.warn("Failed to start MCP server: {0}", e.getMessage());
        }
    }

    public void stop() {
        if (mcpServer != null) {
            mcpServer.stop();
            mcpServer = null;
        }
    }

    public void disable() {
        if (!mcpDisabled) {
            mcpDisabled = true;
            LOG.warn("Disabling MCP servers for remainder of this process");
            stop();
        }
    }

    public boolean isDisabled() {
        return mcpDisabled;
    }

    public List<Map<String, String>> getServerConfig() {
        if (mcpDisabled || mcpServer == null || !mcpServer.isRunning()) {
            return List.of();
        }
        return List.of(Map.of(
                "type", "sse",
                "name", "weather",
                "url", mcpServer.getUrl()
        ));
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
