package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;

/** Handles session/request_permission from the ACP server. */
public interface PermissionHandler {

    /** Completes response future with user's decision ("allow" or "deny"). */
    void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response);
}
