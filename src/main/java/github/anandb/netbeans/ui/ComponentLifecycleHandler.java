package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
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

            // Start server on a background thread to avoid blocking EDT
            // (BinaryResolver PATH scan + ProcessBuilder.start() can be slow).
            RequestProcessor.getDefault().post(() -> processService.get().ensureStarted());
            processService.get().whenReady().exceptionally(ex -> {
                Throwable cause = ex;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof IllegalStateException && cause.getMessage() != null && cause.getMessage().contains("not found")) {
                    topComponent.setBinaryNotFoundState(true);
                }
                return null;
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

            @Override
            public void popupModelCombo() {
                configPanelController.popupCombo(configPanelController.getModelCombo());
            }

            @Override
            public void popupAgentCombo() {
                configPanelController.popupCombo(configPanelController.getModeCombo());
            }

            @Override
            public void popupThinkingCombo() {
                configPanelController.popupCombo(configPanelController.getThinkingCombo());
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
            public void displayToolMessage(String title, String text) {
                SwingUtilities.invokeLater(() -> {
                    chatPanel.addMessage(new github.anandb.netbeans.model.ProcessedMessage(
                        github.anandb.netbeans.model.MessageType.tool_call_update,
                        text, null, null, title, text, false, "completed"));
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

    public void promptRestartServer() {
        NotifyDescriptor.Confirmation confirm = new NotifyDescriptor.Confirmation(
            NbBundle.getMessage(AssistantTopComponent.class, "MSG_ConfirmRestart"),
            NbBundle.getMessage(AssistantTopComponent.class, "TITLE_RestartServer"),
            NotifyDescriptor.YES_NO_OPTION,
            NotifyDescriptor.WARNING_MESSAGE
        );
        Object result = DialogDisplayer.getDefault().notify(confirm);
        if (result == NotifyDescriptor.YES_OPTION) {
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

        // Safety timeout: re-enable after 10 seconds regardless
        Timer safetyTimeout = new Timer(10_000, e -> restartServerBtn.setEnabled(true));
        safetyTimeout.setRepeats(false);
        safetyTimeout.start();

        processService.get().restartServer();

        processService.get().whenReady().thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
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
                if (cause instanceof IllegalStateException && cause.getMessage() != null && cause.getMessage().contains("not found")) {
                    topComponent.setBinaryNotFoundState(true);
                } else {
                    String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
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
