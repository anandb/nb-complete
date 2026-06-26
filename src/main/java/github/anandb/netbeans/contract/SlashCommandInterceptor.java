package github.anandb.netbeans.contract;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import github.anandb.netbeans.model.ModelRecords.CommandInfo;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class SlashCommandInterceptor {

    private static final Logger LOG = Logger.from(SlashCommandInterceptor.class);

    private final Map<String, CommandInfo> commands = new HashMap<>();

    private volatile SlashCommandCallback callback;

    public SlashCommandInterceptor() {
        registerDefaultCommands();
        // Discover additional commands registered via @ServiceProvider
        for (SlashCommandProvider provider : Lookup.getDefault().lookupAll(SlashCommandProvider.class)) {
            if (provider.getCommand() != null && provider.getHandler() != null) {
                // Wrap SlashCommandHandler → BiFunction to match CommandInfo type
                SlashCommandHandler h = provider.getHandler();
                commands.put(provider.getCommand(), new CommandInfo(h::handle, provider.getDescription()));
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
        commands.put("/title", new CommandInfo(this::handleTitle, "Generate session title"));
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
            return info.handler().apply(args, context);
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
        SlashCommandCallback cb = callback;
        if (cb != null) {
            cb.popupNewSession();
        }
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> handleTitle(String args, Lookup context) {
        SessionControl sc = context.lookup(SessionControl.class);
        if (sc == null) {
            return CompletableFuture.completedFuture(false);
        }
        String sessionId = sc.getCurrentSessionId();
        if (isBlank(sessionId)) {
            return CompletableFuture.completedFuture(false);
        }

        // If user provided a title directly (e.g. /title My Title), rename immediately
        // without relying on AI tool calling — works even when MCP tools are not available.
        if (!args.isBlank()) {
            String newTitle = args.trim();
            LOG.info("Direct rename via /title: sessionId={0}, title=\"{1}\"", sessionId, newTitle);
            sc.renameSession(sessionId, newTitle);
            SlashCommandCallback cb = callback;
            if (cb != null) {
                cb.displayToolMessage("title", "Renamed to: " + newTitle);
            }

            return CompletableFuture.completedFuture(true);
        }

        // Show simulated tool_call_update in chat
        SlashCommandCallback cb = callback;
        if (cb != null) {
            cb.displayToolMessage("title", "Updating title...");
        }

        // Send as regular message — AI needs conversation context to suggest a title
        String prompt = "Suggest a title for this session and call the nb_rename_session tool to rename the session.";
        ProcessControl pc = context.lookup(ProcessControl.class);
        if (pc != null) {
            pc.sendMessage(sessionId, prompt, null);
        }
        return CompletableFuture.completedFuture(true);
    }

    public void registerCommand(String command, SlashCommandHandler handler, String description) {
        commands.put(command, new CommandInfo(handler::handle, description));
    }

    public Map<String, CommandInfo> getCommands() {
        return commands;
    }
}
