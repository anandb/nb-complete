package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.Harness;

/**
 * Control interface for the ACP process and message sending.
 * UI layer should depend on this interface rather than the concrete ProcessManager.
 */
public interface ProcessControl {

    /** Ensures the server process is started. */
    void ensureStarted();

    /** Restarts the server process. */
    void restartServer();

    /** Sends a chat message and returns the response. */
    CompletableFuture<JsonNode> sendMessage(String sessionId, String text, Map<String, Object> context);

    /** Sends a chat message with additional blocks. */
    CompletableFuture<JsonNode> sendMessage(String sessionId, String text, Map<String, Object> context, List<Map<String, Object>> additionalBlocks);

    /** Returns the harness's active run id for a session, or null when no run is active. */
    String getActiveRunId(String sessionId);

    /**
     * Adds user input to the running turn through the harness's steering extension
     * ({@code _goose/unstable/session/steer}) instead of starting a new turn.
     * The returned future fails when no run is active.
     */
    CompletableFuture<JsonNode> steerMessage(String sessionId, String text, List<Map<String, Object>> additionalBlocks);

    /**
     * Whether the agent advertised the cross-adapter {@code _session/steering} extension in the
     * {@code initialize} handshake ({@code _meta.steering.supported}). Per connection: false
     * before the handshake completes and after a restart until the new process answers.
     */
    boolean supportsSessionSteering();

    /**
     * Adds user input to the running turn through {@code _session/steering}, the extension shared
     * by claude-agent-acp and several pi ACP adapters. The reply's {@code outcome} says whether
     * the message joined the turn ({@code injected}) or the turn had already ended
     * ({@code promptRequired}, {@code startedNewTurn}).
     */
    CompletableFuture<JsonNode> steerSession(String sessionId, String text, List<Map<String, Object>> additionalBlocks);

    /**
     * Adds user input to the running turn through Grok Build's {@code x.ai/interject}
     * extension. The reply is {@code {status:"queued"}}; the session actor drains the
     * interjection at the next safe point. Needs no run id.
     */
    CompletableFuture<JsonNode> interjectMessage(String sessionId, String text, List<Map<String, Object>> additionalBlocks);

    /** Stops the current message being processed. */
    CompletableFuture<Void> stopMessage(String sessionId);

    /** Returns a future that completes when the server is ready. */
    CompletableFuture<Void> whenReady();

    /** Touches the connection to prevent idle timeout. */
    void touchConnection();

    /** Returns the tool executor for MCP operations. */
    ToolExecutor getToolExecutor();

    /** Returns the slash command interceptor. */
    SlashCommandInterceptor getSlashCommandInterceptor();

    /** Sets the permission handler for file access permissions. */
    void setPermissionHandler(PermissionHandler handler);

    /** Sets the status listener for status messages. */
    void setStatusListener(Consumer<String> listener);

    /** Sets the crash handler for server crash notifications. */
    void setCrashHandler(Runnable handler);

    /** Returns the list of available slash commands. */
    List<SessionUpdate.AvailableCommand> getAvailableCommands();

    /** Shuts down the server process and releases resources. */
    void shutdown();

    /** Registers a listener invoked when the harness is resolved after the
     *  {@code initialize} handshake. Fires once per server start. */
    void setHarnessListener(Consumer<Harness> listener);

    /**
     * Returns the resolved harness with capability flags. Returns
     * {@link HarnessCatalog#UNKNOWN} until the handshake completes.
     */
    Harness getCapabilities();

    /**
     * Whether the current agent advertised {@code promptCapabilities.embeddedContext}
     * in the {@code initialize} handshake. False before the handshake completes and
     * after a restart until the new process answers.
     */
    boolean supportsEmbeddedContext();
}
