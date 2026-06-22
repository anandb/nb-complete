package github.anandb.netbeans.manager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        Map<String, Object> params = new java.util.HashMap<>();
        if (directory != null && !directory.isEmpty()) {
            params.put("cwd", directory);
        }
        return processManager.sendRequest("session/list", params);
    }

    CompletableFuture<JsonNode> createSession(String cwd) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("cwd", cwd);
        params.put("mcpServers", processManager.getToolExecutor().getServerConfig());
        return processManager.sendRequest("session/new", params, 60, java.util.concurrent.TimeUnit.SECONDS);
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

    CompletableFuture<Void> renameSessionOnServer(String sessionId, String title) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("sessionId", sessionId);
        ObjectNode update = MAPPER.createObjectNode();
        update.put("sessionUpdate", "session_info_update");
        update.put("title", title);
        params.set("update", update);
        return processManager.sendRequest("session/update", params).thenApply(r -> null);
    }

    CompletableFuture<Void> setSessionConfigOption(String sessionId, String configId, String value) {
        return processManager.sendRequest("session/set_config_option", Map.of(
                "sessionId", sessionId,
                "configId", configId,
                "value", value
        )).thenApply(r -> null);
    }
}
