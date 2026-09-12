package github.anandb.netbeans.manager;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.HarnessCatalog;


/**
 * Handles all session-related RPC calls to the ACP server.
 * Extracted from SessionManager to isolate transport-layer logic.
 */
final class SessionRpcClient {

    private final ProcessManager processManager;

    SessionRpcClient(ProcessManager processManager) {
        this.processManager = processManager;
    }


    CompletableFuture<JsonNode> getSessions(String directory) {
        Map<String, Object> params = new HashMap<>();
        if (directory != null && !directory.isEmpty()) {
            params.put("cwd", directory);
        }
        return processManager.sendRequest("session/list", params, 60, TimeUnit.SECONDS);
    }

    CompletableFuture<JsonNode> createSession(String cwd) {
        Map<String, Object> params = new HashMap<>();
        params.put("cwd", cwd);
        params.put("mcpServers", caps().sendsMcpServerConfig()
                ? processManager.getToolExecutor().getServerConfig() : List.of());
        return processManager.sendRequest("session/new", params, 60, TimeUnit.SECONDS);
    }

    CompletableFuture<JsonNode> loadSessionFromServer(String sessionId, String cwd) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        if (cwd != null) {
            params.put("cwd", cwd);
        }
        if (caps().sendsMcpServerConfig()) {
            params.put("mcpServers", processManager.getToolExecutor().getServerConfig());
        } else {
            params.put("mcpServers", List.of());
        }
        return processManager.sendRequest("session/load", params, 2, TimeUnit.MINUTES);
    }

    CompletableFuture<JsonNode> setSessionConfigOption(String sessionId, String configId, String value) {
        return processManager.sendRequest("session/set_config_option", Map.of(
                "sessionId", sessionId,
                "configId", configId,
                "value", value
        ), 30, TimeUnit.SECONDS);
    }

    CompletableFuture<JsonNode> setSessionMode(String sessionId, String modeId) {
        return processManager.sendRequest("session/set_mode", Map.of(
                "sessionId", sessionId,
                "modeId", modeId
        ), 30, TimeUnit.SECONDS);
    }

    private HarnessCatalog.Harness caps() {
        return processManager.getCapabilities();
    }
}
