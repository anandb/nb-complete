package github.anandb.netbeans.contract;

import java.util.concurrent.CompletableFuture;
import org.openide.util.Lookup;

/**
 * Handles a slash command entered in the chat input.
 * Implementations process commands like /models, /agents, /level, /sessions, /new.
 */
public interface SlashCommandHandler {

    /**
     * Executes the slash command.
     *
     * @param args    the command arguments (may be empty)
     * @param context the NetBeans lookup context (editor, project, etc.)
     * @return future resolving to {@code true} if command was handled or executed
     */
    CompletableFuture<Boolean> handle(String args, Lookup context);
}
