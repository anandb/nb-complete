package github.anandb.netbeans.contract;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import github.anandb.netbeans.model.ModelRecords.CommandInfo;
import org.openide.util.Lookup;

/**
 * Intercepts slash commands typed in the chat input. Commands starting
 * with "/" are matched against registered handlers before being sent to the AI.
 */
public interface SlashCommandInterceptor {

    /** Registers the callback for triggering UI actions from commands. */
    void setCallback(SlashCommandCallback callback);

    /** Returns the current callback, or null if none is set. */
    SlashCommandCallback getCallback();

    /**
     * Intercepts and handles a slash command.
     * @return future resolving to true if the command was handled locally.
     */
    CompletableFuture<Boolean> intercept(String text, Lookup context);

    /** Registers a custom slash command with its handler and description. */
    void registerCommand(String command, SlashCommandHandler handler, String description);

    /** Returns the map of registered commands. */
    Map<String, CommandInfo> getCommands();
}
