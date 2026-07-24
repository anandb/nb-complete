package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.left;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Desktop;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

import github.anandb.netbeans.ui.platform.PlatformBridge;
import github.anandb.netbeans.ui.platform.ProjectContext;
import github.anandb.netbeans.ui.platform.SessionService;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import org.netbeans.api.project.Project;
import github.anandb.netbeans.support.BrowserUtils;




@ConvertAsProperties(
    dtd = "-//github.anandb.netbeans.ui//Assistant//EN",
    autostore = false
)
@TopComponent.Description(
    preferredID = "AssistantTopComponent",
    iconBase = "github/anandb/netbeans/ui/icons/logo_window.svg",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "properties", openAtStartup = true, position = 1001)
@ActionID(category = "Window", id = "github.anandb.netbeans.ui.AssistantTopComponent")
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_AssistantAction",
    preferredID = "AssistantTopComponent"
)
// DSL-LEAF: not a controller — TopComponent is the root view shell. The DSL
// migration ports the BorderLayout.add(...) + JSplitPane assembly to a
// declarative tree; the lifecycle handlers (componentOpened/componentClosed)
// stay imperative and are wired via PlatformBridge.
public final class AssistantTopComponent extends TopComponent implements PermissionHandler {

    private static final Logger LOG = Logger.from(AssistantTopComponent.class);

    private static final long serialVersionUID = 1L;
    private final SessionService sessionService = PlatformBridge.sessionServiceSafe();
    private final ProjectContext projectContext = PlatformBridge.projectContextSafe();
    private final ChatThreadPanel chatPanel;
    private final PlaceholderTextArea inputArea;
    private final JButton sendBtn;
    private final JButton stopBtn;
    private final JComboBox<SessionItem> sessionDropdown;
    private final JButton hideBtn;
    private final JButton newSessionBtn;
    private final JButton renameSessionBtn;
    private final JButton toggleBlocksBtn;
    private final JButton keepBtn;
    private final JButton filterBtn;
    private final JButton helpBtn;
    private final JButton toggleOptionsBtn;
    private final JButton restartServerBtn;
    private final JButton refreshBtn;
    private final JButton exportBtn;
    private final JButton rocketBtn;
    private final JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel cwdLabel;

    private final JScrollPane inputScrollPane;
    private final JPanel header;
    private final transient MessageHistory messageHistory = new MessageHistory();
    private final transient StatusController statusController;
    private volatile String pendingExportFormat;
    private final transient AttachmentUiHandler attachmentUiHandler;
    private final transient SessionDropdownHandler sessionDropdownHandler;
    private final transient ComponentLifecycleHandler componentLifecycleHandler;
    private final transient InputHandler inputHandler;
    private final transient SessionLifecycleHandler sessionLifecycleHandler;
    private final transient MessageSender messageSender;

    private final transient AttachmentManager attachmentManager = new AttachmentManager();
    private final transient ConfigPanelController configPanelController;
    private final transient AutocompleteManager autocompleteManager;

    private final transient PermissionDialogManager permissionDialogManager;
    private final transient JSplitPane mainSplitPane;
    private final transient ChatLayoutBuilder layoutBuilder;

    private boolean sessionActive = false;
    private transient Timer attentionPeriodicTimer;
    private transient Timer attentionShakeTimer;

    public AssistantTopComponent() {
        setName(NbBundle.getMessage(AssistantTopComponent.class, "CTL_AssistantTopComponent"));

        setLayout(new BorderLayout());
        chatPanel = new ChatThreadPanel();

        configPanelController = new ConfigPanelController(this::updateTabName);
        layoutBuilder = new ChatLayoutBuilder(this, chatPanel, configPanelController);

        header = layoutBuilder.buildHeader();
        JPanel bottomPanel = layoutBuilder.buildBottomPanel();

        sessionDropdown = layoutBuilder.getSessionDropdown();
        hideBtn = layoutBuilder.getHideBtn();
        newSessionBtn = layoutBuilder.getNewSessionBtn();
        renameSessionBtn = layoutBuilder.getRenameSessionBtn();
        toggleBlocksBtn = layoutBuilder.getToggleBlocksBtn();
        keepBtn = layoutBuilder.getKeepBtn();
        filterBtn = layoutBuilder.getFilterBtn();
        helpBtn = layoutBuilder.getHelpBtn();
        toggleOptionsBtn = layoutBuilder.getToggleOptionsBtn();
        restartServerBtn = layoutBuilder.getRestartServerBtn();
        refreshBtn = layoutBuilder.getRefreshBtn();
        exportBtn = layoutBuilder.getExportBtn();
        statusLabel = layoutBuilder.getStatusLabel();
        versionLabel = layoutBuilder.getVersionLabel();
        cwdLabel = layoutBuilder.getCwdLabel();
        inputArea = layoutBuilder.getInputArea();
        inputScrollPane = layoutBuilder.getInputScrollPane();
        sendBtn = layoutBuilder.getSendBtn();
        stopBtn = layoutBuilder.getStopBtn();

        add(header, BorderLayout.NORTH);

        mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatPanel, bottomPanel);
        mainSplitPane.setResizeWeight(1.0);
        mainSplitPane.setOneTouchExpandable(false);
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setBorder(null);
        // Divider position is not persisted across restarts.
        add(mainSplitPane, BorderLayout.CENTER);

        statusController = new StatusController(statusLabel, sendBtn, stopBtn, inputArea, toggleOptionsBtn);
        statusController.setProcessingListener(
                processing -> {
                    configPanelController.setCombosEnabled(!processing);
                    MiniAssistantDialog.getInstance().onProcessingChanged(processing);
                });
        attachmentUiHandler = new AttachmentUiHandler(attachmentManager, statusController, inputArea, AssistantTopComponent.this);

        // Add attachment button to the right status panel (before settings button)
        layoutBuilder.getRightStatusPanel().add(attachmentUiHandler.getButton(), 0);

        // Add rocket (OpenCode Go) button to the left of the attachment button
        int iconSize = PluginSettings.getToolbarIconSize();
        rocketBtn = UIUtils.createToolbarButton("rocket-ship.svg", iconSize,
                "Sign up for OpenCode Go, Referral Link", e -> {
            BrowserUtils.openOrCopyUrl(
                    "https://opencode.ai/go?ref=DWTNHGN9KX", null, null);
        });
        rocketBtn.setVisible(false);
        layoutBuilder.getRightStatusPanel().add(rocketBtn, 0);

        // Add token usage button after the rocket button
        JButton tokenUsageBtn = UIUtils.createToolbarButton("currency.svg", iconSize,
                "Token Stats", e -> {
            Window win = SwingUtilities.getWindowAncestor(AssistantTopComponent.this);
            if (win instanceof Frame frame) {
                TokenUsageDialog.show(frame);
            } else {
                TokenUsageDialog.show(null);
            }
        });
        layoutBuilder.getRightStatusPanel().add(tokenUsageBtn, 1);

        sessionDropdownHandler = new SessionDropdownHandler(sessionDropdown, inputArea);
        sessionLifecycleHandler = new SessionLifecycleHandler(
            chatPanel, sessionDropdown, hideBtn, newSessionBtn, renameSessionBtn,
            toggleOptionsBtn, configPanelController, inputArea, statusController,
            this::showProjectPickerPopup, this::updateTabName, this::updateCwdLabel,
            sessionActive -> {
                this.sessionActive = sessionActive;
                sessionDropdown.setEnabled(sessionActive);
                hideBtn.setEnabled(sessionActive);
                renameSessionBtn.setEnabled(sessionActive);
                toggleBlocksBtn.setEnabled(sessionActive);
                keepBtn.setEnabled(sessionActive);
                filterBtn.setEnabled(sessionActive);
                attachmentUiHandler.getButton().setEnabled(sessionActive);
                refreshBtn.setEnabled(sessionActive);
                exportBtn.setEnabled(sessionActive);
                helpBtn.setEnabled(true);
                restartServerBtn.setEnabled(true);
                updateNewSessionBtnState();
            },
            this::setOptionsPanelVisible
        );
        // Track project open/close to disable new-session button when no projects are open
        projectContext.addProjectChangeListener(this::updateNewSessionBtnState);
        updateNewSessionBtnState();

        messageSender = new MessageSender(
            inputArea, chatPanel, attachmentManager, messageHistory,
            statusController, attachmentUiHandler::updateTooltip, inputArea::requestFocusInWindow
        );
        messageSender.setOnNewMessageCallback(sessionLifecycleHandler::onNewMessageSent);
        messageSender.setOnMessageDoneCallback(sessionLifecycleHandler::onMessageDone);
        messageSender.setOnUserMessageSentCallback(chatPanel::recordUserMessageSent);

        sendBtn.addActionListener(e -> messageSender.sendMessage());
        stopBtn.addActionListener(e -> messageSender.stopMessage());

        toggleOptionsBtn.addActionListener(e -> {
            boolean collapsed = !sessionLifecycleHandler.isOptionsPanelCollapsed();
            setOptionsPanelVisible(!collapsed);
        });

        autocompleteManager = new AutocompleteManager(inputArea, messageSender::sendMessage);
        chatPanel.setScrollBlocker(autocompleteManager::isPopupVisible);

        componentLifecycleHandler = new ComponentLifecycleHandler(
            chatPanel, statusController, sessionLifecycleHandler,
            configPanelController, inputArea, sessionDropdown, toggleOptionsBtn,
            restartServerBtn, AssistantTopComponent.this
        );
        inputHandler = new InputHandler(inputArea, autocompleteManager, messageSender, messageHistory);

        permissionDialogManager = new PermissionDialogManager(chatPanel);

        initChat();
        applyInitialTheme();
    }

    private void initChat() {
        sessionService.get().refreshSessions();
    }

    public void setInputText(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setInputText(text));
            return;
        }
        if (inputArea != null) {
            inputArea.setText(text);
            inputArea.requestFocusInWindow();
        } else {
            LOG.warn("setInputText: inputArea is null");
        }
    }

    public static void copyToInput(String text) {
        AssistantTopComponent tc = findInstance();
        if (tc != null) {
            tc.setInputText(text);
        } else {
            LOG.warn("copyToInput: no instance, cannot copy text to input area");
        }
    }

    void renameCurrentSession() {
        String currentId = sessionService.get().getCurrentSessionId();
        if (currentId == null) {
            return;
        }

        SessionItem selectedItem = (SessionItem) sessionDropdown.getSelectedItem();
        if (selectedItem == null) {
            return;
        }

        String currentTitle = selectedIdToTitle(selectedItem.getSession());
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(
            NbBundle.getMessage(AssistantTopComponent.class, "MSG_EnterTitle"),
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_RenameSession")
        );
        input.setInputText(currentTitle);
        Object result = DialogDisplayer.getDefault().notify(input);
        if (result == NotifyDescriptor.OK_OPTION) {
            String newTitle = input.getInputText();
            if (isNotBlank(newTitle)) {
                sessionService.get().renameSession(currentId, newTitle.trim());
            }
        }
    }

    private String selectedIdToTitle(Session session) {
        String title = defaultIfBlank(session.title(), NbBundle.getMessage(AssistantTopComponent.class, "LBL_ChatDefault", left(session.id(), 8)));
        return sessionService.get().getCustomTitle(session.id(), title);
    }

    /** Enable the new-session button only when at least one project is open. */
    private void updateNewSessionBtnState() {
        Project[] projects = projectContext.getAllOpenProjects();
        newSessionBtn.setEnabled(projects != null && projects.length > 0);
        updateAttentionAnimation();
    }

    private void updateAttentionAnimation() {
        Project[] projects = projectContext.getAllOpenProjects();
        boolean projectsOpen = (projects != null && projects.length > 0);
        boolean shouldAnimate = projectsOpen && !sessionActive && isOpened();

        if (shouldAnimate) {
            if (attentionPeriodicTimer == null) {
                attentionPeriodicTimer = new Timer(4000, e -> {
                    if (isOpened() && isShowing()) {
                        triggerAttentionShake();
                    }
                });
                attentionPeriodicTimer.setRepeats(true);
                attentionPeriodicTimer.start();
                if (isOpened() && isShowing()) {
                    triggerAttentionShake();
                }
            }
        } else {
            stopAttentionAnimation();
        }
    }

    private void stopAttentionAnimation() {
        if (attentionPeriodicTimer != null) {
            attentionPeriodicTimer.stop();
            attentionPeriodicTimer = null;
        }
        if (attentionShakeTimer != null) {
            attentionShakeTimer.stop();
            attentionShakeTimer = null;
        }
        if (newSessionBtn instanceof AttentionButton ab) {
            ab.setOffsetAndHighlight(0, 0, 0.0f);
        }
    }

    private void triggerAttentionShake() {
        if (!(newSessionBtn instanceof AttentionButton ab)) {
            return;
        }

        if (attentionShakeTimer != null && attentionShakeTimer.isRunning()) {
            return;
        }

        final int[] yOffsets = {0, -2, -4, -6, -7, -6, -4, -2, 0, -1, -3, -4, -3, -1, 0};
        final int[] xOffsets = {0, -2, 2, -2, 2, -1, 1, 0, 0, 0, 0, 0, 0, 0, 0};
        final float[] alphas = {0.0f, 0.3f, 0.6f, 0.9f, 1.0f, 0.9f, 0.6f, 0.3f, 0.0f, 0.2f, 0.5f, 0.6f, 0.5f, 0.2f, 0.0f};

        attentionShakeTimer = new Timer(30, new ActionListener() {
            private int frame = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (frame < yOffsets.length) {
                    ab.setOffsetAndHighlight(xOffsets[frame], yOffsets[frame], alphas[frame]);
                    frame++;
                } else {
                    ab.setOffsetAndHighlight(0, 0, 0.0f);
                    if (attentionShakeTimer != null) {
                        attentionShakeTimer.stop();
                        attentionShakeTimer = null;
                    }
                }
            }
        });
        attentionShakeTimer.start();
    }

    void showProjectPickerPopup(JComponent parent) {
        componentLifecycleHandler.showProjectPickerPopup(parent);
    }

    void createNewSession() {
        Project[] projects = projectContext.getAllOpenProjects();
        if (projects == null || projects.length == 0) {
            return;
        }
        if (projects.length == 1) {
            sessionService.get()
                .createNewSession(projects[0].getProjectDirectory().getPath());
        } else {
            componentLifecycleHandler.showProjectPickerPopup(newSessionBtn);
        }
    }

    void sendMessage() {
        if (messageSender != null) {
            messageSender.sendMessage();
        }
    }

    void stopMessage() {
        if (messageSender != null) {
            messageSender.stopMessage();
        }
    }

    void setStatus(String key, Object... args) {
        if (statusController != null) {
            statusController.setStatus(key, args);
        }
    }

    void toggleOptions() {
        if (sessionLifecycleHandler != null) {
            boolean collapsed = !sessionLifecycleHandler.isOptionsPanelCollapsed();
            setOptionsPanelVisible(!collapsed);
        }
    }

    /**
     * Shows or hides the options panel and adjusts the mainSplitPane divider
     * so that the options panel takes space from the chat panel, not the textarea.
     */
    public ConfigPanelController getConfigPanelController() {
        return configPanelController;
    }

    void setOptionsPanelVisible(boolean visible) {
        if (sessionLifecycleHandler == null) return;
        boolean collapsed = !visible;
        sessionLifecycleHandler.setOptionsPanelCollapsed(collapsed);

        // Capture the config panel's preferred height before toggling visibility
        int configHeight = configPanelController.getComponent().getPreferredSize().height;

        configPanelController.getComponent().setVisible(visible);
        toggleOptionsBtn.setIcon(ThemeManager.getIcon(visible ? "arrow-down.svg" : "settings.svg", 25));
        rocketBtn.setVisible(visible);

        // Adjust split pane divider so the textarea keeps its size:
        // expanding moves the divider UP (taking space from chat), collapsing moves it DOWN.
        if (visible && configHeight > 0) {
            int current = mainSplitPane.getDividerLocation();
            mainSplitPane.setDividerLocation(Math.max(0, current - configHeight));
        } else if (!visible && configHeight > 0) {
            int current = mainSplitPane.getDividerLocation();
            mainSplitPane.setDividerLocation(current + configHeight);
        }

        revalidate();
        repaint();
    }

    void toggleAllBlocks() {
        if (chatPanel != null) {
            boolean expanded = !chatPanel.isAllBlocksExpanded();
            chatPanel.toggleAllBlocks(expanded);
        }
    }

    void reloadCurrentSession() {
        String currentId = sessionService.get().getCurrentSessionId();
        if (currentId != null) {
            sessionService.get().loadSession(currentId);
        }
    }

    void exportConversation() {
        exportConversationAs("md");
    }

    void exportConversationAs(String format) {
        // If messages are being trimmed, ask user whether to show all first.
        if (!chatPanel.isKeepOlderMessages()) {
            int choice = javax.swing.JOptionPane.showOptionDialog(this,
                    "Some messages may be hidden. What would you like to export?",
                    "Export Conversation",
                    javax.swing.JOptionPane.DEFAULT_OPTION,
                    javax.swing.JOptionPane.QUESTION_MESSAGE,
                    null,
                    new Object[]{"Export displayed messages", "Show all & export messages"},
                    "Export displayed messages");
            if (choice == 1) {
                // Reload then export via flush timer callback.
                pendingExportFormat = format;
                chatPanel.setOnMessagesStable(() -> {
                    String fmt = pendingExportFormat;
                    pendingExportFormat = null;
                    chatPanel.setOnMessagesStable(null);
                    if (fmt != null) exportConversationAs(fmt);
                });
                chatPanel.setKeepOlderMessages(true);
                return;
            }
            if (choice == -1) return;
        }
        pendingExportFormat = null;
        String currentId = sessionService.get().getCurrentSessionId();
        String title = currentId != null ? sessionService.get().getSessionTitle(currentId) : null;
        if ("html".equals(format)) {
            String html = chatPanel.getConversationAsHtml(title);
            if (isBlank(html)) return;
            HtmlConversationExporter.export(this, html,
                    HtmlConversationExporter.defaultFileName(title));
        } else {
            String markdown = chatPanel.getConversationAsMarkdown(title);
            if (isBlank(markdown)) return;
            ConversationExporter.export(this, markdown,
                    ConversationExporter.defaultFileName(title));
        }
    }

    private void updateTabName(String modelName) {
        SwingUtilities.invokeLater(() -> {
            if (modelName != null && !modelName.isEmpty()) {
                setName(modelName);
            } else {
                setName(NbBundle.getMessage(AssistantTopComponent.class, "CTL_AssistantTopComponent"));
            }
        });
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(50, 50);
    }

    public ChatThreadPanel getChatThreadPanel() {
        return chatPanel;
    }

    private void updateCwdLabel(String path) {
        SwingUtilities.invokeLater(() -> {
            String effectivePath = path;

            if (effectivePath == null || effectivePath.isEmpty()) {
                effectivePath = sessionService.get().getCurrentSessionDirectory();
            }

            if (effectivePath == null || effectivePath.isEmpty()) {
                cwdLabel.setText("");
                cwdLabel.setToolTipText(null);
            } else {
                FontMetrics fm = cwdLabel.getFontMetrics(cwdLabel.getFont());
                int availableWidth = cwdLabel.getWidth() - 10;
                String displayPath = effectivePath;

                if (availableWidth > 30 && fm.stringWidth(effectivePath) > availableWidth) {
                    while (displayPath.length() > 5 && fm.stringWidth("..." + displayPath) > availableWidth) {
                        displayPath = displayPath.substring(1);
                    }
                    displayPath = "..." + displayPath;
                }
                cwdLabel.setText(displayPath);
                cwdLabel.setToolTipText(effectivePath);
            }
        });
    }

    @Override
    public void componentActivated() {
        componentLifecycleHandler.componentActivated();
        updateAttentionAnimation();
    }

    @Override
    public void componentDeactivated() {
        super.componentDeactivated();
        componentLifecycleHandler.componentDeactivated();
        updateAttentionAnimation();
    }

    public void toggleVisibility() {
        if (isOpened() && isShowing()) {
            close();
        } else {
            if (!isOpened()) {
                open();
            }
            requestActive();
        }
    }

    /**
     * Toggles minimize/restore for the assistant panel.
     * Resources (listeners, handlers, messages) stay alive across all close/reopen cycles.
     */
    public void minimizeToDock() {
        toggleVisibility();
    }

    /** Tracks whether first-time initialization has run. */
    private transient boolean initialized = false;

    @Override
    public void componentClosed() {
        // Resources (listeners, handlers, messages) stay alive across all close/reopen cycles.
        stopAttentionAnimation();
    }

    @Override
    public void componentOpened() {
        if (!initialized) {
            initialized = true;
            componentLifecycleHandler.componentOpened();
        }
        // On subsequent opens, resources are already alive — no reinit needed.
        updateAttentionAnimation();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        componentLifecycleHandler.registerKeyEventDispatchers();
        // Re-layout the outer hierarchy (header, split pane, bottom bar)
        // after a hide/show cycle to prevent blank panels.
        // Force layout recalculation on the split pane and its children
        if (mainSplitPane != null) {
            mainSplitPane.revalidate();
            mainSplitPane.repaint();
        }
        revalidate();
        validate();
        repaint();
        updateAttentionAnimation();
    }

    @Override
    public void removeNotify() {
        stopAttentionAnimation();
        layoutBuilder.cleanup();
        componentLifecycleHandler.removeNotify();
        super.removeNotify();
    }

    void writeProperties(Properties p) {
        p.setProperty("version", "2.0");
    }

    void readProperties(Properties p) {
    }

    public static synchronized AssistantTopComponent findInstance() {
        TopComponent tc = WindowManager.getDefault().findTopComponent("AssistantTopComponent");
        if (tc instanceof AssistantTopComponent assistantTopComponent) {
            return assistantTopComponent;
        }
        return null;
    }

    void showCwdContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        String cwd = cwdLabel.getToolTipText();
        if (cwd == null || cwd.isEmpty()) return;

        JPopupMenu popup = new JPopupMenu();
        JMenuItem locateItem = new JMenuItem("Locate in System");
        locateItem.addActionListener(ev -> openCwdInSystemBrowser(cwd));
        popup.add(locateItem);
        popup.show(cwdLabel, e.getX(), e.getY());
    }

    private void openCwdInSystemBrowser(String path) {
        try {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()
                    && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to open directory in system browser: {0}", ex);
        }
    }

    private void applyInitialTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        setBackground(theme.background());
        header.setBackground(theme.background());

        cwdLabel.setForeground(theme.foreground());
        cwdLabel.setOpaque(true);
        cwdLabel.setBackground(theme.sunkenBackground());
        Color bb = theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY;
        cwdLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bb, 1),
                new EmptyBorder(4, 8, 4, 8)));

        versionLabel.setForeground(theme.base1());

        sessionDropdown.setBackground(theme.background());
        sessionDropdown.setForeground(theme.foreground());

        inputArea.setBackground(theme.background());
        inputArea.setForeground(theme.foreground());
        inputArea.setCaretColor(theme.foreground());

        inputScrollPane.setBorder(BorderFactory.createMatteBorder(
            1, 0, 0, 0, theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY
        ));

        revalidate();
        repaint();
    }

    void promptRestartServer() {
        componentLifecycleHandler.promptRestartServer();
    }

    /**
     * Enters or exits the "binary not found" state. When entered, all toolbar
     * buttons except restart are disabled, a warning message is shown in the
     * chat panel, and the status bar displays the error. When exited, buttons
     * are restored to their normal session-aware state.
     */
    void setBinaryNotFoundState(boolean notFound) {
        SwingUtilities.invokeLater(() -> {
            if (notFound) {
                // Disable all session toolbar buttons — only restart stays enabled
                sessionDropdown.setEnabled(false);
                hideBtn.setEnabled(false);
                newSessionBtn.setEnabled(false);
                renameSessionBtn.setEnabled(false);
                toggleBlocksBtn.setEnabled(false);
                keepBtn.setEnabled(false);
                filterBtn.setEnabled(false);
                refreshBtn.setEnabled(false);
                exportBtn.setEnabled(false);
                // restartServerBtn stays enabled so user can retry after installing

                // Disable input area
                statusController.setInputEnabled(false);

                // Show warning in chat panel
                chatPanel.stopStreaming();
                chatPanel.clearMessages();
                chatPanel.addMissingBinaryBubble(
                    () -> BrowserUtils.openOrCopyUrl("https://opencode.ai/docs/", null, null),
                    this::promptRestartServer
                );
                statusController.setStatus("STATUS_BinaryNotFound");
            } else {
                // Restore normal session-aware state. Do not touch the status
                // bar — the caller (e.g. restartServer success) manages it.
                boolean hasSession = sessionService.get().getCurrentSessionId() != null;
                sessionDropdown.setEnabled(hasSession);
                hideBtn.setEnabled(hasSession);
                updateNewSessionBtnState();
                renameSessionBtn.setEnabled(hasSession);
                toggleBlocksBtn.setEnabled(hasSession);
                keepBtn.setEnabled(hasSession);
                filterBtn.setEnabled(hasSession);
                refreshBtn.setEnabled(hasSession);
                exportBtn.setEnabled(hasSession);
                helpBtn.setEnabled(true);

                statusController.setInputEnabled(true);
            }
        });
    }

    @Override
    public void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response) {
        permissionDialogManager.handlePermissionRequest(sessionId, params, response,
            () -> {
                requestActive();
                toFront();
            });
    }

}
