package github.anandb.netbeans.manager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.SlashCommandCallback;
import github.anandb.netbeans.contract.SlashCommandHandler;
import github.anandb.netbeans.contract.SlashCommandInterceptor;
import github.anandb.netbeans.contract.SlashCommandProvider;
import github.anandb.netbeans.model.ModelRecords.CommandInfo;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.LookupProvider;
import org.openide.util.Lookup;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class DefaultSlashCommandInterceptor implements SlashCommandInterceptor {

    private static final Logger LOG = Logger.from(DefaultSlashCommandInterceptor.class);

    private final Map<String, CommandInfo> commands = new ConcurrentHashMap<>();

    private volatile SlashCommandCallback callback;

    public DefaultSlashCommandInterceptor() {
        registerDefaultCommands();
        // Discover additional command registered via @ServiceProvider
        SlashCommandProvider provider = LookupProvider.getDefault().lookup(SlashCommandProvider.class);
        if (provider != null && provider.getCommand() != null && provider.getHandler() != null) {
            SlashCommandHandler h = provider.getHandler();
            commands.put(provider.getCommand(), new CommandInfo(h::handle, provider.getDescription()));
            LOG.fine("Registered slash command from provider: {0}", provider.getCommand());
        }
    }

    private void registerDefaultCommands() {
        commands.put("/agent", new CommandInfo(popupHandler(SlashCommandCallback::popupAgentCombo),
                "Select agent or mode"));
        commands.put("/agents", new CommandInfo(popupHandler(SlashCommandCallback::popupAgentCombo),
                "Select agent or mode"));
        commands.put("/archive", new CommandInfo(popupHandler(SlashCommandCallback::popupArchiveSession),
                "Archive/unarchive session"));
        commands.put("/level", new CommandInfo(popupHandler(SlashCommandCallback::popupThinkingCombo),
                "Select thinking level"));
        commands.put("/levels", new CommandInfo(popupHandler(SlashCommandCallback::popupThinkingCombo),
                "Select thinking level"));
        commands.put("/model", new CommandInfo(popupHandler(SlashCommandCallback::popupModelCombo),
                "Select model"));
        commands.put("/models", new CommandInfo(popupHandler(SlashCommandCallback::popupModelCombo),
                "Select model"));
        commands.put("/new", new CommandInfo(popupHandler(SlashCommandCallback::popupNewSession),
                "Create new session"));
        commands.put("/session", new CommandInfo(popupHandler(SlashCommandCallback::popupSessionCombo),
                "Select session"));
        commands.put("/sessions", new CommandInfo(popupHandler(SlashCommandCallback::popupSessionCombo),
                "Select session"));
        commands.put("/title", new CommandInfo(this::handleTitle, "Generate session title"));

        // /compact is passed verbatim to the server (no local interception)
        commands.put("/compact", new CommandInfo(this::handlePassthrough, "Summarise Conversation"));
    }

    private CompletableFuture<Boolean> handlePassthrough(String args, Lookup context) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public void setCallback(SlashCommandCallback callback) {
        this.callback = callback;
    }

    @Override
    public SlashCommandCallback getCallback() {
        return callback;
    }

    @Override
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

    /** Handler that opens one callback-owned popup and consumes the command. */
    private BiFunction<String, Lookup, CompletableFuture<Boolean>> popupHandler(
            Consumer<SlashCommandCallback> action) {
        return (args, context) -> popup(action);
    }

    private CompletableFuture<Boolean> popup(Consumer<SlashCommandCallback> action) {
        SlashCommandCallback cb = callback;
        if (cb != null) {
            action.accept(cb);
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
        if (isNotBlank(args)) {
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
            SwingUtilities.invokeLater(cb::onAsyncSendStarted);
        }

        // Send as regular message — AI needs conversation context to suggest a title
        String prompt = "Suggest a title for this session and call the nb_rename_session tool to rename the session.";
        ProcessControl pc = context.lookup(ProcessControl.class);
        if (pc != null) {
            try {
                pc.sendMessage(sessionId, prompt, Map.of("annotations", Map.of("audience", List.of("assistant"))))
                        .whenComplete((result, error) -> {
                            // The model names the session with its own rename tool, which
                            // never reaches this plugin as a session/update — a rename is
                            // only observable through session/list. The name is final when
                            // the turn that produced it ends, so re-read the list once
                            // here; otherwise the dropdown keeps the old title until the
                            // IDE restarts.
                            sc.refreshSessions();
                            if (cb != null) {
                                SwingUtilities.invokeLater(cb::onAsyncSendCompleted);
                            }
                        });
            } catch (Exception e) {
                LOG.severe("Failed to send title generation message", e);
                if (cb != null) {
                    SwingUtilities.invokeLater(cb::onAsyncSendCompleted);
                }
            }
        } else if (cb != null) {
            SwingUtilities.invokeLater(cb::onAsyncSendCompleted);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void registerCommand(String command, SlashCommandHandler handler, String description) {
        commands.put(command, new CommandInfo(handler::handle, description));
    }

    @Override
    public Map<String, CommandInfo> getCommands() {
        return Collections.unmodifiableMap(commands);
    }
}
