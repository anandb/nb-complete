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
import org.netbeans.api.project.Project;
import org.openide.util.NbBundle;
import github.anandb.netbeans.contract.SlashCommandCallback;
import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.mcp.McpManager;
import github.anandb.netbeans.manager.ProcessManager;
import github.anandb.netbeans.manager.SessionManager;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.support.Logger;

/**
 * Manages the component lifecycle: open, activate, deactivate, close, and remove notify.
 * Owns the page key event dispatcher and handles server startup, session refresh,
 * permissions, status listeners, slash command callback, and ESC key handler.
 */
public class ComponentLifecycleHandler {

    private static final Logger LOG = new Logger(ComponentLifecycleHandler.class);

    private final ChatThreadPanel chatPanel;
    private final StatusController statusController;
    private final SessionLifecycleHandler sessionLifecycleHandler;
    private final ConfigPanelController configPanelController;
    private final PlaceholderTextArea inputArea;
    private final JComboBox<SessionItem> sessionDropdown;
    private final JButton toggleOptionsBtn;
    private final AssistantTopComponent topComponent;
    private final KeyEventDispatcher pageKeyDispatcher;

    private Set<String> closedProjectDirs = Set.of();

    public ComponentLifecycleHandler(
            ChatThreadPanel chatPanel,
            StatusController statusController,
            SessionLifecycleHandler sessionLifecycleHandler,
            ConfigPanelController configPanelController,
            PlaceholderTextArea inputArea,
            JComboBox<SessionItem> sessionDropdown,
            JButton toggleOptionsBtn,
            AssistantTopComponent topComponent) {
        this.chatPanel = chatPanel;
        this.statusController = statusController;
        this.sessionLifecycleHandler = sessionLifecycleHandler;
        this.configPanelController = configPanelController;
        this.inputArea = inputArea;
        this.sessionDropdown = sessionDropdown;
        this.toggleOptionsBtn = toggleOptionsBtn;
        this.topComponent = topComponent;

        this.pageKeyDispatcher = createPageKeyDispatcher();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(pageKeyDispatcher);
    }

    // -- Lifecycle callbacks --

    public void componentOpened() {
        SessionManager.getInstance().addSessionListener(sessionLifecycleHandler);
        Set<String> currentDirs = new HashSet<>();
        for (var p : ACPProjectManager.getInstance().getAllOpenProjects()) {
            if (p != null) {
                currentDirs.add(p.getProjectDirectory().getPath());
            }
        }
        if (!currentDirs.equals(closedProjectDirs)) {
            SessionManager.getInstance().refreshSessions();
        }
        closedProjectDirs = Set.of();
        try {
            ProcessManager.getInstance().ensureStarted();
        } catch (Exception ex) {
            LOG.severe("Failed to ensure server is started", ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            SwingUtilities.invokeLater(() -> {
                chatPanel.stopStreaming();
                chatPanel.addMessage(ProcessedMessage.createError(
                        MessageType.error_response, NbBundle.getMessage(AssistantTopComponent.class, "STATUS_FailedToStart", msg), null, null
                ));
            });
        }

        // Update status label when MCP server is starting/ready
        McpManager mcp = ProcessManager.getInstance().getMcpManager();
        if (!mcp.isDisabled() && !mcp.waitForReady().isDone()) {
            SwingUtilities.invokeLater(() -> statusController.setStatus("STATUS_McpInitializing"));
            mcp.waitForReady().thenRun(() ->
                SwingUtilities.invokeLater(() -> statusController.setStatus("STATUS_Ready"))
            );
        }

        ProcessManager.getInstance().setPermissionHandler(topComponent);
        ProcessManager.getInstance().setStatusListener(msg -> {
            SwingUtilities.invokeLater(() -> {
                statusController.setStatusText(msg);
                statusController.scheduleReset();
            });
        });
        ProcessManager.getInstance().getSlashCommandInterceptor().setCallback(new SlashCommandCallback() {
            {
                Runnable returnFocus = () -> inputArea.requestFocusInWindow();
                configPanelController.setOnModelSelectedCallback(returnFocus);
                configPanelController.setOnModeSelectedCallback(returnFocus);
                configPanelController.setOnThinkingSelectedCallback(returnFocus);
            }

            @Override
            public void expandOptionsPanel() {
                if (sessionLifecycleHandler.isOptionsPanelCollapsed()) {
                    sessionLifecycleHandler.setOptionsPanelCollapsed(false);
                    configPanelController.getComponent().setVisible(true);
                    toggleOptionsBtn.setIcon(ThemeManager.getIcon("arrow-down.svg", 25));
                    topComponent.revalidate();
                    topComponent.repaint();
                }
            }

            @Override
            public void popupModelCombo() {
                configPanelController.popupModelCombo();
            }

            @Override
            public void popupAgentCombo() {
                configPanelController.popupModeCombo();
            }

            @Override
            public void popupThinkingCombo() {
                configPanelController.popupThinkingCombo();
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
                    Project[] projects = ACPProjectManager.getInstance().getAllOpenProjects();
                    if (projects == null || projects.length == 0) {
                        return;
                    }
                    if (projects.length == 1) {
                        SessionManager.getInstance().createNewSession(projects[0].getProjectDirectory().getPath());
                    } else {
                        showProjectPickerPopup(inputArea);
                    }
                });
            }
        });

        // ESC key handler to close options panel and return focus to input
        KeyAdapter escHandler = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    if (!sessionLifecycleHandler.isOptionsPanelCollapsed()) {
                        sessionLifecycleHandler.setOptionsPanelCollapsed(true);
                        configPanelController.getComponent().setVisible(false);
                        toggleOptionsBtn.setIcon(ThemeManager.getIcon("settings.svg", 25));
                        topComponent.revalidate();
                        topComponent.repaint();
                    }
                    inputArea.requestFocusInWindow();
                }
            }
        };
        configPanelController.addKeyListenerToInputs(escHandler);
        configPanelController.getComponent().addKeyListener(escHandler);
        SwingUtilities.invokeLater(() -> {
            if (inputArea != null) {
                inputArea.requestFocusInWindow();
            }
            String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
            if (currentSessionId != null) {
                SessionManager.getInstance().loadSession(currentSessionId);
            } else {
                SessionManager.getInstance().refreshSessions();
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
        if (pageKeyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pageKeyDispatcher);
        }
        statusController.stopAllTimers();
        if (chatPanel != null) {
            chatPanel.clearMessages();
        }
        closedProjectDirs = new HashSet<>();
        for (var p : ACPProjectManager.getInstance().getAllOpenProjects()) {
            if (p != null) {
                closedProjectDirs.add(p.getProjectDirectory().getPath());
            }
        }
        SessionManager.getInstance().removeSessionListener(sessionLifecycleHandler);

        // Clear handler references to prevent memory leak (ProcessManager holds these)
        ProcessManager.getInstance().setPermissionHandler(null);
        ProcessManager.getInstance().setStatusListener(null);
        ProcessManager.getInstance().setCrashHandler(null);
        ProcessManager.getInstance().setReadyHandler(null);
    }

    public void removeNotify() {
        if (pageKeyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pageKeyDispatcher);
        }
        statusController.stopAllTimers();
    }

    // -- Public helpers --

    /** Shows a popup listing all open projects to pick which one to create a session for. */
    public void showProjectPickerPopup(JComponent parent) {
        Project[] projects = ACPProjectManager.getInstance().getAllOpenProjects();
        if (projects == null || projects.length <= 1) {
            return;
        }
        JPopupMenu popup = new JPopupMenu();
        for (Project project : projects) {
            String projectDir = project.getProjectDirectory().getPath();
            JMenuItem item = new JMenuItem(project.getProjectDirectory().getName());
            item.addActionListener(ev -> SessionManager.getInstance().createNewSession(projectDir));
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
                        if (keyCode == KeyEvent.VK_PAGE_UP) {
                            chatPanel.scrollByBlock(true);
                        } else if (keyCode == KeyEvent.VK_PAGE_DOWN) {
                            chatPanel.scrollByBlock(false);
                        } else if (keyCode == KeyEvent.VK_HOME) {
                            chatPanel.scrollToTop();
                        } else if (keyCode == KeyEvent.VK_END) {
                            chatPanel.scrollToBottom(true);
                        }
                        return true;
                    }
                }
            }
            return false;
        };
    }
}
