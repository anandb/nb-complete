package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;

/** Handles session/request_permission from the ACP server. */
public interface PermissionHandler {

    /** Completes response future with user's decision ("allow" or "deny"). */
    void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response);

    /**
     * Returns a monotonically increasing "message epoch" that advances whenever a
     * new user message is sent to the server. The ACP request router captures this
     * when a permission request arrives and compares it against the current value
     * before displaying the panel, so a permission request that belongs to a turn
     * that was superseded by a newer user message is rejected instead of shown.
     */
    default long currentPermissionEpoch() {
        return 0L;
    }
}
