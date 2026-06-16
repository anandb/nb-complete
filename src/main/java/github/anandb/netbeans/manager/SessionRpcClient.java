package github.anandb.netbeans.manager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles all session-related RPC calls to the ACP server.
 * Extracted from SessionManager to isolate transport-layer logic.
 */
final class SessionRpcClient {

    private static final Logger LOG = Logger.from(SessionRpcClient.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private final ProcessManager processManager;

    SessionRpcClient(ProcessManager processManager) {
        this.processManager = processManager;
    }

    CompletableFuture<JsonNode> getSessions(String directory) {
        return processManager.sendRequest("session/list", Map.of("directory", directory));
    }

    CompletableFuture<JsonNode> createSession(String cwd) {
        return processManager.sendRequest("session/new", Map.of("directory", cwd));
    }

    CompletableFuture<JsonNode> loadSessionFromServer(String sessionId, String cwd) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("sessionId", sessionId);
        if (cwd != null) {
            params.put("cwd", cwd);
        }
        params.put("mcpServers", processManager.getToolExecutor().getServerConfig());
        return processManager.sendRequest("session/load", params);
    }

    CompletableFuture<Void> deleteSession(String sessionId) {
        return processManager.sendRequest("session/delete", Map.of("sessionId", sessionId))
                .thenApply(r -> null);
    }

    CompletableFuture<Void> renameSessionOnServer(String sessionId, String title) {
        return processManager.sendRequest("session/update", Map.of(
                "sessionId", sessionId,
                "title", title
        )).thenApply(r -> null);
    }

    CompletableFuture<Void> setSessionConfigOption(String sessionId, String optionId, String value) {
        return processManager.sendRequest("session/set_config_option", Map.of(
                "sessionId", sessionId,
                "optionId", optionId,
                "value", value
        )).thenApply(r -> null);
    }
}
