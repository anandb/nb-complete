package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import github.anandb.netbeans.contract.SlashCommandCallback;
import github.anandb.netbeans.contract.ToolExecutor;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.support.BinaryResolver;
import github.anandb.netbeans.support.GlobalOpencodeConfig;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProcessService;
import github.anandb.netbeans.ui.platform.ProjectContext;
import github.anandb.netbeans.ui.platform.SessionService;

/**
 * Manages the component lifecycle: open, activate, deactivate, close, and remove notify.
 * Owns the page key event dispatcher and handles server startup, session refresh,
 * permissions, status listeners, slash command callback, and ESC key handler.
 */
// DSL-CONTROLLER: not a view — focus/window/visibility bridge + project picker
// popup state. Stays imperative; the DSL declares the AssistantTopComponent
// shell it drives. The restart-needed flag + tooltip update logic stays here.
public class ComponentLifecycleHandler {

    private final SessionService sessionService;
    private final ProcessService processService;
    private final ProjectContext projectContext;

    private static final Logger LOG = Logger.from(ComponentLifecycleHandler.class);

    private final ChatThreadPanel chatPanel;
    private final StatusController statusController;
    private final SessionLifecycleHandler sessionLifecycleHandler;
    private final ConfigPanelController configPanelController;
    private final PlaceholderTextArea inputArea;
    private final JComboBox<SessionItem> sessionDropdown;
    private final JButton toggleOptionsBtn;
    private final JButton restartServerBtn;
    private final AssistantTopComponent topComponent;
    private final KeyEventDispatcher pageKeyDispatcher;
    private KeyAdapter escKeyListener;

    private Set<String> closedProjectDirs = Set.of();

    /** True once the user picks "Not now" on the global config prompt; suppresses
     *  further prompts for the rest of this IDE process (until restart). */
    private boolean configPromptDeferredThisProcess;

    public ComponentLifecycleHandler(
            ChatThreadPanel chatPanel,
            StatusController statusController,
            SessionLifecycleHandler sessionLifecycleHandler,
            ConfigPanelController configPanelController,
            PlaceholderTextArea inputArea,
            JComboBox<SessionItem> sessionDropdown,
            JButton toggleOptionsBtn,
            JButton restartServerBtn,
            AssistantTopComponent topComponent) {
        this.chatPanel = chatPanel;
        this.statusController = statusController;
        this.sessionLifecycleHandler = sessionLifecycleHandler;
        this.configPanelController = configPanelController;
        this.inputArea = inputArea;
        this.sessionDropdown = sessionDropdown;
        this.toggleOptionsBtn = toggleOptionsBtn;
        this.restartServerBtn = restartServerBtn;
        this.topComponent = topComponent;

        var bridge = Lookup.getDefault().lookup(PlatformBridge.class);
        if (bridge == null) {
            LOG.severe("PlatformBridge not found in Lookup — UI services unavailable");
        }
        this.sessionService = bridge != null ? bridge.sessionService() : null;
        this.processService = bridge != null ? bridge.processService() : null;
        this.projectContext = bridge != null ? bridge.projectContext() : null;

        this.pageKeyDispatcher = createPageKeyDispatcher();
    }

    // -- Lifecycle callbacks --

    public void componentOpened() {
        if (sessionService == null || processService == null || projectContext == null) {
            LOG.severe("PlatformBridge unavailable, skipping componentOpened");
            return;
        }
        // Reset turn-ended flag from any prior RPC completion that fired while panel was closed.
        // Without this, new SSE updates after reopen would be suppressed.
        sessionLifecycleHandler.onNewMessageSent();
        sessionService.get().addSessionListener(sessionLifecycleHandler);

        // Defer session refresh and server start so the component opens immediately.
        // During plugin installation the @OnStart handler opens this component while
        // the module installer wizard is still active; deferring prevents the installation
        // dialog from being blocked by server/session initialization.
        SwingUtilities.invokeLater(() -> {
            Set<String> currentDirs = new HashSet<>();
            for (var p : projectContext.getAllOpenProjects()) {
                if (p != null) {
                    currentDirs.add(p.getProjectDirectory().getPath());
                }
            }
            if (!currentDirs.equals(closedProjectDirs)) {
                sessionService.get().refreshSessions();
            }
            closedProjectDirs = Set.of();

            // Proactive binary check: if opencode is not installed, enter
            // the "binary not found" state immediately and skip starting the server.
            if (!BinaryResolver.isAvailable()) {
                topComponent.setBinaryNotFoundState(true);
                return;
            }

            // Offer the global opencode configuration prompt BEFORE launching the
            // server: a starter config written here is picked up on the first
            // start, so the user does not need to restart a second time.
            maybeShowGlobalConfigPrompt(promptShown -> {
                Runnable start = () -> {
                    RequestProcessor.getDefault().post(() -> processService.get().ensureStarted());
                    // Register the not-found handler only once the start is actually
                    // proceeding (after any config prompt is answered), matching the
                    // restart path below.
                    processService.get().whenReady().exceptionally(ex -> {
                        Throwable cause = ex;
                        while (cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                        if (cause instanceof IllegalStateException && ExceptionUtils.getMessage(cause) != null
                                && ExceptionUtils.getMessage(cause).contains("not found")) {
                            topComponent.setBinaryNotFoundState(true);
                        }
                        return null;
                    });
                };
                // Mirror the restart flow: when a config prompt was shown, present
                // the start-a-session hint (with OK) before starting the server.
                if (promptShown) {
                    chatPanel.showStartSessionHint(start);
                } else {
                    start.run();
                }
            });
        });

        // Update status label when MCP server is starting/ready
        ToolExecutor mcp = processService.get().getToolExecutor();
        if (!mcp.isDisabled() && !mcp.waitForReady().isDone()) {
            SwingUtilities.invokeLater(() -> statusController.setStatus("STATUS_McpInitializing"));
            mcp.waitForReady().thenRun(() ->
                SwingUtilities.invokeLater(() -> statusController.setStatus("STATUS_Ready"))
            );
        }

        processService.get().setPermissionHandler(topComponent);
        processService.get().setStatusListener(msg -> {
            SwingUtilities.invokeLater(() -> {
                statusController.setStatusText(msg);
                statusController.scheduleReset();
            });
        });
        processService.get().getSlashCommandInterceptor().setCallback(new SlashCommandCallback() {
            {
                Runnable returnFocus = () -> inputArea.requestFocusInWindow();
                configPanelController.setOnModelSelectedCallback(returnFocus);
                configPanelController.setOnModeSelectedCallback(returnFocus);
                configPanelController.setOnThinkingSelectedCallback(returnFocus);
            }

            @Override
            public void expandOptionsPanel() {
                if (sessionLifecycleHandler.isOptionsPanelCollapsed()) {
                    topComponent.setOptionsPanelVisible(true);
                }
            }

            private void preparePopupAndAutoClose(Runnable popupAction) {
                boolean wasCollapsed = sessionLifecycleHandler.isOptionsPanelCollapsed();
                if (wasCollapsed) {
                    configPanelController.setAutoHideOnClose(true, () -> {
                        topComponent.setOptionsPanelVisible(false);
                    });
                    topComponent.setOptionsPanelVisible(true);
                }
                popupAction.run();
            }

            @Override
            public void popupModelCombo() {
                preparePopupAndAutoClose(() -> configPanelController.popupCombo(configPanelController.getModelCombo()));
            }

            @Override
            public void popupAgentCombo() {
                preparePopupAndAutoClose(() -> configPanelController.popupCombo(configPanelController.getModeCombo()));
            }

            @Override
            public void popupThinkingCombo() {
                preparePopupAndAutoClose(() -> configPanelController.popupCombo(configPanelController.getThinkingCombo()));
            }

            @Override
            public void popupSessionCombo() {
                SwingUtilities.invokeLater(() -> {
                    sessionDropdown.requestFocusInWindow();
                    SwingUtilities.invokeLater(() -> sessionDropdown.setPopupVisible(true));
                });
            }

            @Override
            public void popupNewSession() {
                SwingUtilities.invokeLater(() -> {
                    Project[] projects = projectContext.getAllOpenProjects();
                    if (projects == null || projects.length == 0) {
                        return;
                    }
                    if (projects.length == 1) {
                        sessionService.get().createNewSession(projects[0].getProjectDirectory().getPath());
                    } else {
                        showProjectPickerPopup(inputArea);
                    }
                });
            }

            @Override
            public void popupArchiveSession() {
                var sc = Lookup.getDefault().lookup(github.anandb.netbeans.contract.SessionControl.class);
                if (sc == null) return;
                String sid = sc.getCurrentSessionId();
                if (sid != null) {
                    boolean currentlyHidden = sc.isHidden(sid);
                    boolean newHidden = !currentlyHidden;
                    sc.setHidden(sid, newHidden);
                    sc.refreshSessions();
                    if (newHidden && !ChatLayoutBuilder.isShowingHidden()) {
                        MiniAssistantDialog.closeIfVisible();
                    }
                }
            }

            @Override
            public void displayToolMessage(String title, String text) {
                SwingUtilities.invokeLater(() -> {
                    chatPanel.addMessage(new ProcessedMessage(
                        MessageType.tool_call_update,
                        text, null, null, title, text, false, "completed"));
                });
            }

            @Override
            public void onAsyncSendStarted() {
                SwingUtilities.invokeLater(() -> {
                    statusController.setStatus("STATUS_Sending");
                    statusController.startThinking();
                    statusController.updateButtonState(true);
                });
            }

            @Override
            public void onAsyncSendCompleted() {
                SwingUtilities.invokeLater(() -> {
                    statusController.updateButtonState(false);
                    statusController.stopThinking();
                    sessionLifecycleHandler.onMessageDone();
                    statusController.setStatus("STATUS_Ready");
                });
            }
        });

        // ESC key handler to close options panel and return focus to input
        if (escKeyListener == null) {
            escKeyListener = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        e.consume();
                        if (!sessionLifecycleHandler.isOptionsPanelCollapsed()) {
                            topComponent.setOptionsPanelVisible(false);
                        }
                        inputArea.requestFocusInWindow();
                    }
                }
            };
            configPanelController.addKeyListenerToInputs(escKeyListener);
            configPanelController.getComponent().addKeyListener(escKeyListener);
        }
        SwingUtilities.invokeLater(() -> {
            if (inputArea != null) {
                inputArea.requestFocusInWindow();
            }
            String currentSessionId = sessionService.get().getCurrentSessionId();
            if (currentSessionId != null) {
                sessionService.get().loadSession(currentSessionId);
            } else {
                sessionService.get().refreshSessions();
            }
        });
    }

    public void componentActivated() {
        SwingUtilities.invokeLater(() -> {
            if (inputArea != null) {
                inputArea.requestFocusInWindow();
            }
        });
    }

    public void componentDeactivated() {
        statusController.stopAllTimers();
    }

    public void componentClosed() {
        // UNREACHABLE (by design): AssistantTopComponent.componentClosed() is a no-op.
        // The assistant lives for the entire IDE session and must not tear down the
        // server connection, session state, or registered handlers on tab close.
        // If this method is ever wired in, the crash handler removal below (line 285)
        // will break the crash→reconnect→resume flow after close/reopen.
        if (sessionService == null || processService == null || projectContext == null) {
            LOG.severe("PlatformBridge unavailable, skipping componentClosed");
            return;
        }
        // Cancel any active message before detaching the listener, so the server
        // stops processing and doesn't flood stale SSE content on reopen.
        // We bypass stopCurrentMessage() and go directly to IDLE to avoid the
        // STOPPING state — loadSession() on reopen needs IDLE→LOADING to work.
        sessionService.get().forceCancelCurrentMessage();

        if (pageKeyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pageKeyDispatcher);
        }
        statusController.stopAllTimers();
        if (chatPanel != null) {
            chatPanel.clearMessages();
        }
        closedProjectDirs = new HashSet<>();
        for (var p : projectContext.getAllOpenProjects()) {
            if (p != null) {
                closedProjectDirs.add(p.getProjectDirectory().getPath());
            }
        }
        sessionService.get().removeSessionListener(sessionLifecycleHandler);

        // Clear handler references to prevent memory leak (ProcessManager holds these)
        processService.get().setPermissionHandler(null);
        processService.get().setStatusListener(null);
        processService.get().setCrashHandler(null);
        // Note: readyHandler is intentionally NOT cleared — SessionManager sets it
        // to reload sessions after reconnect and its lambda captures only singleton references.
        processService.get().getSlashCommandInterceptor().setCallback(null);

        if (escKeyListener != null) {
            configPanelController.removeKeyListenerFromInputs(escKeyListener);
            configPanelController.getComponent().removeKeyListener(escKeyListener);
            escKeyListener = null;
        }
    }

    public void registerKeyEventDispatchers() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(pageKeyDispatcher);
    }

    public void removeNotify() {
        if (pageKeyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pageKeyDispatcher);
        }
        statusController.stopAllTimers();
    }

    // -- Public helpers --

    /** Shows a popup listing all open projects to pick which one to create a session for. */
    // -- Server restart --

    /** Shows a confirmation dialog (or skips it when no process is running)
     *  and restarts the server. {@code onRestarted} is called when the restart
     *  actually begins, so callers can disable buttons / update UI. */
    public void promptRestartServer(Runnable onRestarted) {
        // When no server process is running (binary-not-found install flow),
        // re-check whether the binary is now available. If it is, restart; if
        // not, stay on the current screen so the user can try again.
        if (topComponent.isBinaryNotFound()) {
            if (BinaryResolver.isAvailable()) {
                if (onRestarted != null) onRestarted.run();
                restartServer();
            }
            return;
        }
        NotifyDescriptor.Confirmation confirm = new NotifyDescriptor.Confirmation(
            NbBundle.getMessage(AssistantTopComponent.class, "MSG_ConfirmRestart"),
            NbBundle.getMessage(AssistantTopComponent.class, "TITLE_RestartServer"),
            NotifyDescriptor.YES_NO_OPTION,
            NotifyDescriptor.WARNING_MESSAGE
        );
        Object result = DialogDisplayer.getDefault().notify(confirm);
        if (result == NotifyDescriptor.YES_OPTION) {
            if (onRestarted != null) onRestarted.run();
            restartServer();
        }
    }

    public void restartServer() {
        if (sessionService == null || processService == null) {
            LOG.severe("PlatformBridge unavailable, cannot restart server");
            return;
        }
        String currentSessionId = sessionService.get().getCurrentSessionId();
        statusController.setStatus("STATUS_RestartingServer");
        statusController.setInputEnabled(false);
        restartServerBtn.setEnabled(false);

        // Ask about the global opencode configuration before the server
        // restarts, so a starter config is written before the process reads it.
        maybeShowGlobalConfigPrompt(promptShown -> {
            // When a config prompt was shown, ask the user to confirm the restart
            // with an OK button (the server restarts only once OK is clicked). When
            // no prompt was needed (config already present, prompt disabled), start
            // directly without the confirmation hint.
            if (promptShown) {
                chatPanel.showStartSessionHint(() -> doRestart(currentSessionId));
            } else {
                doRestart(currentSessionId);
            }
        });
    }

    /** Performs the actual server restart and re-enables the UI on completion. */
    private void doRestart(String currentSessionId) {
        // Safety timeout: re-enable after 10 seconds regardless. Started only
        // once the restart actually begins, so it cannot fire while any prompt
        // or hint above is still awaiting confirmation.
        Timer safetyTimeout = new Timer(10_000, e -> restartServerBtn.setEnabled(true));
        safetyTimeout.setRepeats(false);
        safetyTimeout.start();

        processService.get().restartServer();
        // Arm the manual-reconnect prompt only after the restart has begun,
        // so it cannot fire while any prompt is still open.
        sessionService.get().scheduleManualReconnectPrompt();

        processService.get().whenReady().thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
                safetyTimeout.stop();
                // After server ready, wait 5 more seconds before re-enabling
                Timer cooldown = new Timer(5_000, e -> restartServerBtn.setEnabled(true));
                cooldown.setRepeats(false);
                cooldown.start();
                statusController.setStatus("STATUS_ServerRestarted");
                // Clear binary-not-found state on successful restart
                topComponent.setBinaryNotFoundState(false);
                if (currentSessionId != null) {
                    sessionService.get().loadSession(currentSessionId);
                } else {
                    sessionService.get().refreshSessions();
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                safetyTimeout.stop();
                restartServerBtn.setEnabled(true);
                Throwable cause = ex;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof IllegalStateException && ExceptionUtils.getMessage(cause) != null
                        && ExceptionUtils.getMessage(cause).contains("not found")) {
                    topComponent.setBinaryNotFoundState(true);
                } else {
                    String msg = ExceptionUtils.getMessage(ex) != null ? ExceptionUtils.getMessage(ex) : ex.getClass().getSimpleName();
                    statusController.setStatus("STATUS_RestartFailed", msg);
                    chatPanel.stopStreaming();
                    chatPanel.addMessage(ProcessedMessage.createError(
                        MessageType.error_response, NbBundle.getMessage(AssistantTopComponent.class, "STATUS_RestartFailed", msg), null, null
                    ));
                    statusController.setInputEnabled(true);
                }
            });
            return null;
        });
    }

    /**
     * Offers to set up the global opencode configuration before the server
     * starts. The file check runs on a background thread and the prompt bubble
     * is shown in the chat panel. {@code afterAnswer} is invoked with {@code true}
     * when a config prompt was shown to the user (and answered), or {@code false}
     * when no prompt was needed (real configuration present, the prompt was
     * disabled in Options / via "Don't ask again", or the user already chose
     * "Not now" earlier this session).
     */
    private void maybeShowGlobalConfigPrompt(Consumer<Boolean> afterAnswer) {
        if (configPromptDeferredThisProcess) {
            afterAnswer.accept(false);
            return;
        }
        RequestProcessor.getDefault().post(() -> {
            GlobalOpencodeConfig.CheckResult result = GlobalOpencodeConfig.evaluate();
            if (result.state == GlobalOpencodeConfig.State.REAL_CONTENT) {
                SwingUtilities.invokeLater(() -> afterAnswer.accept(false));
                return;
            }
            SwingUtilities.invokeLater(() -> chatPanel.addGlobalConfigBubble(
                    result,
                    () -> RequestProcessor.getDefault().post(() -> {
                        GlobalOpencodeConfig.writeDefaultConfig();
                        SwingUtilities.invokeLater(() -> afterAnswer.accept(true));
                    }),
                    () -> {
                        configPromptDeferredThisProcess = true;
                        afterAnswer.accept(true);
                    }
            ));
        });
    }

    public void showProjectPickerPopup(JComponent parent) {
        if (projectContext == null) return;
        Project[] projects = projectContext.getAllOpenProjects();
        if (projects == null || projects.length <= 1) {
            return;
        }
        JPopupMenu popup = new JPopupMenu();
        for (Project project : projects) {
            String projectDir = project.getProjectDirectory().getPath();
            JMenuItem item = new JMenuItem(project.getProjectDirectory().getName());
            item.addActionListener(ev -> sessionService.get().createNewSession(projectDir));
            popup.add(item);
        }
        popup.show(parent, 0, parent.getHeight());
    }

    // -- Internals --

    private KeyEventDispatcher createPageKeyDispatcher() {
        return e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_PAGE_UP
                        || keyCode == KeyEvent.VK_PAGE_DOWN
                        || ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0
                            && (keyCode == KeyEvent.VK_HOME
                                || keyCode == KeyEvent.VK_END))) {
                    Component src = e.getComponent();
                    if (src != null && SwingUtilities.isDescendingFrom(src, topComponent)
                            && !SwingUtilities.isDescendingFrom(src, chatPanel)) {
                        if (keyCode == KeyEvent.VK_PAGE_UP
                                || keyCode == KeyEvent.VK_PAGE_DOWN) {
                            Component c = src;
                            while (c != null) {
                                if (c instanceof JComboBox) {
                                    return false;
                                }
                                c = c.getParent();
                            }
                        }
                        switch (keyCode) {
                            case KeyEvent.VK_PAGE_UP -> chatPanel.scrollByBlock(true);
                            case KeyEvent.VK_PAGE_DOWN -> chatPanel.scrollByBlock(false);
                            case KeyEvent.VK_HOME -> chatPanel.scrollToTop();
                            case KeyEvent.VK_END -> chatPanel.scrollToBottom(true);
                            default -> {
                            }
                        }
                        return true;
                    }
                }
            }
            return false;
        };
    }
}
