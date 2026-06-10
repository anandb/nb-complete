package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
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
     * Checks if the ACP server supports MCP tool integration.
     * @param initializeResponse the response from the ACP initialize call
     */
    void checkServerSupport(JsonNode initializeResponse);

    /**
     * Returns the MCP server configuration to include in session prompts.
     * @return server configuration list
     */
    List<Map<String, Object>> getServerConfig();
}
