package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.AgentCapabilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Port for MCP tool execution capabilities.
 * Replaces the upward dependency from manager/ to mcp/McpManager.
 */
public interface ToolExecutor {

    /** Starts the MCP tool server. */
    void start();

    /**
     * Configures whether the embedded MCP server requires a token from clients.
     * Must be called before {@link #start()}. Auth is skipped only for harness
     * binaries that cannot carry tokens on MCP URLs (the PI family: pi,
     * pi-acp, pi-agent).
     *
     * @param required true to hand out token-protected MCP URLs and enforce them
     */
    void setMcpAuthRequired(boolean required);

    /** Stops the MCP tool server. */
    void stop();

    /**
     * Returns a CompletableFuture that completes when the server is ready.
     */
    CompletableFuture<Void> waitForReady();

    /**
     * Permanently disables MCP for the process lifetime.
     */
    void disable();

    /**
     * Returns true if MCP has been disabled.
     */
    boolean isDisabled();

    /**
     * Ensures the embedded MCP server is started when the agent supports it.
     * @param capabilities agent capabilities derived from the handshake
     */
    void checkServerSupport(AgentCapabilities capabilities);

    /**
     * Returns the MCP server configuration to include in session prompts.
     * @return server configuration list
     */
    List<Map<String, Object>> getServerConfig();
}
