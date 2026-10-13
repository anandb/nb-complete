package github.anandb.netbeans.manager;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.support.Logger;


/**
 * Handles all session-related RPC calls to the ACP server.
 * Extracted from SessionManager to isolate transport-layer logic.
 */
final class SessionRpcClient {

    private static final Logger LOG = Logger.from(SessionRpcClient.class);

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

    /**
     * Cancels, then closes a session ahead of {@code session/load}, as ACP requires
     * for an agent that advertises {@code sessionCapabilities.close}. A no-op when
     * the agent does not advertise it — calling {@code session/close} unadvertised
     * is forbidden — and it never fails the load that follows.
     */
    CompletableFuture<Void> closeBeforeLoad(String sessionId) {
        if (!processManager.isSessionCloseSupported()) {
            LOG.fine("closeBeforeLoad: agent does not advertise session/close; skipping {0}", sessionId);
            return CompletableFuture.completedFuture(null);
        }
        // Cancel first so close cannot race a turn still in flight; the
        // notification is written ahead of the close request on the same pipe.
        processManager.sendNotification("session/cancel", Map.of("sessionId", sessionId));
        return processManager.sendRequest("session/close", Map.of("sessionId", sessionId), 30, TimeUnit.SECONDS)
                .thenAccept(res -> LOG.fine("closeBeforeLoad: session/close completed for {0}", sessionId))
                .exceptionally(ex -> {
                    LOG.fine("closeBeforeLoad: session/close failed for {0}: {1}",
                            sessionId, ExceptionUtils.getRootCauseMessage(ex));
                    return null;
                });
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

    CompletableFuture<JsonNode> setSessionModel(String sessionId, String modelId) {
        return processManager.sendRequest("session/set_model", Map.of(
                "sessionId", sessionId,
                "modelId", modelId
        ), 30, TimeUnit.SECONDS);
    }

    private HarnessCatalog.Harness caps() {
        return processManager.getCapabilities();
    }
}
