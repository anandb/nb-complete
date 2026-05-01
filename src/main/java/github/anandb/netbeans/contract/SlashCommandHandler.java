package github.anandb.netbeans.contract;

import java.util.concurrent.CompletableFuture;
import org.openide.util.Lookup;

public interface SlashCommandHandler {
    CompletableFuture<Boolean> handle(String args, Lookup context);
}
