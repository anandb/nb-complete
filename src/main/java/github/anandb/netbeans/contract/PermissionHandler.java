package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;

/**
 * Handles permission request notifications ({@code session/request_permission})
 * from the ACP server. The server sends permission requests when it needs
 * user approval for an action.
 *
 * <p>Implementations must complete the {@code response} future with the
 * user's decision ("allow" or "deny").
 */
public interface PermissionHandler {

    /**
     * Processes a permission request from the server.
     *
     * @param sessionId the session requesting permission
     * @param params    the request payload (tool name, arguments, etc.)
     * @param response  future to complete with the user's decision
     */
    void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response);
}
