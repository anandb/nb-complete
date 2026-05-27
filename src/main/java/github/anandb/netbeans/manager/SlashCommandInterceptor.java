package github.anandb.netbeans.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import github.anandb.netbeans.contract.SlashCommandHandler;
import github.anandb.netbeans.contract.SlashCommandCallback;
import github.anandb.netbeans.contract.SlashCommandProvider;
import github.anandb.netbeans.model.ModelRecords.CommandInfo;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;

public class SlashCommandInterceptor {

    private static final Logger LOG = Logger.from(SlashCommandInterceptor.class);

    private final Map<String, CommandInfo> commands = new HashMap<>();

    private volatile SlashCommandCallback callback;

    public SlashCommandInterceptor() {
        registerDefaultCommands();
        // Discover additional commands registered via @ServiceProvider
        for (SlashCommandProvider provider : Lookup.getDefault().lookupAll(SlashCommandProvider.class)) {
            if (provider.getCommand() != null && provider.getHandler() != null) {
                commands.put(provider.getCommand(), new CommandInfo(provider.getHandler(), provider.getDescription()));
                LOG.fine("Registered slash command from provider: {0}", provider.getCommand());
            }
        }
    }

    private void registerDefaultCommands() {
        commands.put("/models", new CommandInfo(this::handleModels, "Select model"));
        commands.put("/agents", new CommandInfo(this::handleAgents, "Select agent or mode"));
        commands.put("/level", new CommandInfo(this::handleLevel, "Select thinking level"));
        commands.put("/sessions", new CommandInfo(this::handleSession, "Select session"));
        commands.put("/new", new CommandInfo(this::handleNew, "Create new session"));
    }

    public void setCallback(SlashCommandCallback callback) {
        this.callback = callback;
    }

    public SlashCommandCallback getCallback() {
        return callback;
    }

    public CompletableFuture<Boolean> intercept(String text, Lookup context) {
        if (text == null || !text.trim().startsWith("/")) {
            return CompletableFuture.completedFuture(false);
        }

        String trimmed = text.trim();
        int spaceIdx = trimmed.indexOf(' ');
        String command = spaceIdx > 0 ? trimmed.substring(0, spaceIdx) : trimmed;
        String args = spaceIdx > 0 ? trimmed.substring(spaceIdx + 1) : "";

        CommandInfo info = commands.get(command);
        if (info != null) {
            LOG.info("Intercepted local command: {0}", command);
            return info.handler().handle(args, context);
        }

        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> handleModels(String args, Lookup context) {
        SlashCommandCallback cb = callback;
        if (cb != null) {
            cb.expandOptionsPanel();
            cb.popupModelCombo();
        }
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> handleAgents(String args, Lookup context) {
        SlashCommandCallback cb = callback;
        if (cb != null) {
            cb.expandOptionsPanel();
            cb.popupAgentCombo();
        }
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> handleLevel(String args, Lookup context) {
        SlashCommandCallback cb = callback;
        if (cb != null) {
            cb.expandOptionsPanel();
            cb.popupThinkingCombo();
        }
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> handleSession(String args, Lookup context) {
        SlashCommandCallback cb = callback;
        if (cb != null) {
            cb.popupSessionCombo();
        }
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> handleNew(String args, Lookup context) {
        if (callback != null) {
            callback.popupNewSession();
        }
        return CompletableFuture.completedFuture(true);
    }

    public void registerCommand(String command, SlashCommandHandler handler, String description) {
        commands.put(command, new CommandInfo(handler, description));
    }

    public Map<String, CommandInfo> getCommands() {
        return commands;
    }
}
