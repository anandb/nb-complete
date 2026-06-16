package github.anandb.netbeans.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import github.anandb.netbeans.model.SessionUpdate;

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
}
