package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;

public interface PermissionHandler {
    void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response);
}
