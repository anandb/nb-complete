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
        return getSessions(directory, null);
    }

    /**
     * One page of {@code session/list}: {@code cursor} is the previous page's
     * {@code nextCursor}, or null for the first page. Harnesses that do not paginate
     * ignore the cursor and answer a single page.
     */
    CompletableFuture<JsonNode> getSessions(String directory, String cursor) {
        Map<String, Object> params = new HashMap<>();
        if (directory != null && !directory.isEmpty()) {
            params.put("cwd", directory);
        }
        if (cursor != null && !cursor.isBlank()) {
            params.put("cursor", cursor);
        }
        return processManager.sendRequest("session/list", params, 60, TimeUnit.SECONDS);
    }

    /** goose's retitle extension: ACP has no rename, and the name must survive a reload. */
    CompletableFuture<JsonNode> renameSession(String sessionId, String title) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("title", title);
        return processManager.sendRequest("_goose/unstable/session/rename", params, 30, TimeUnit.SECONDS);
    }

    /** goose's context-window breakdown; makes no model call. */
    CompletableFuture<JsonNode> reportContext(String sessionId) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        return processManager.sendRequest("_goose/unstable/context/report", params, 30, TimeUnit.SECONDS);
    }

    /** goose's live working-directory move: no process restart, no terminated sessions. */
    CompletableFuture<JsonNode> updateWorkingDirectory(String sessionId, String directory) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("workingDir", directory);
        return processManager.sendRequest("_goose/unstable/session/working-dir/update", params,
                60, TimeUnit.SECONDS);
    }

    /** Standard {@code session/delete}; the caller gates on the advertised capability. */
    CompletableFuture<JsonNode> deleteSession(String sessionId) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        return processManager.sendRequest("session/delete", params, 30, TimeUnit.SECONDS);
    }

    /** Standard {@code session/fork}; the caller gates on the advertised capability. */
    CompletableFuture<JsonNode> forkSession(String sessionId, String cwd) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        if (cwd != null) {
            params.put("cwd", cwd);
        }
        params.put("mcpServers", caps().sendsMcpServerConfig()
                ? processManager.getToolExecutor().getServerConfig() : List.of());
        return processManager.sendRequest("session/fork", params, 2, TimeUnit.MINUTES);
    }

    /** Standard {@code session/resume}; the caller gates on the advertised capability. */
    CompletableFuture<JsonNode> resumeSession(String sessionId, String cwd) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionId", sessionId);
        params.put("cwd", cwd);
        params.put("mcpServers", caps().sendsMcpServerConfig()
                ? processManager.getToolExecutor().getServerConfig() : List.of());
        return processManager.sendRequest("session/resume", params, 2, TimeUnit.MINUTES);
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
        // Through stopMessage() (not sendNotification) so the session's run id and
        // pending steer echoes go with the cancelled turn: a later steer must not
        // carry an expectedRunId the new process/run never issued.
        processManager.stopMessage(sessionId);
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
