package github.anandb.netbeans.contract;

import java.util.concurrent.CompletableFuture;
import org.openide.util.Lookup;

/** Handles a slash command entered in the chat input. */
public interface SlashCommandHandler {

    /** @return future resolving to true if handled. */
    CompletableFuture<Boolean> handle(String args, Lookup context);
}
