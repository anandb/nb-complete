package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.left;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Desktop;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.support.Logger;

@ConvertAsProperties(
    dtd = "-//github.anandb.netbeans.ui//Assistant//EN",
    autostore = false
)
@TopComponent.Description(
    preferredID = "AssistantTopComponent",
    iconBase = "github/anandb/netbeans/ui/icons/logo_window.svg",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = true)
@ActionID(category = "Window", id = "github.anandb.netbeans.ui.AssistantTopComponent")
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_AssistantAction",
    preferredID = "AssistantTopComponent"
)
public final class AssistantTopComponent extends TopComponent implements PermissionHandler {

    private static final Logger LOG = Logger.from(AssistantTopComponent.class);
    private static final long serialVersionUID = 1L;
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
    private final JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel cwdLabel;

    private final JScrollPane inputScrollPane;
    private final JPanel header;
    private final transient MessageHistory messageHistory = new MessageHistory();
    transient StatusController statusController;
    private final transient AttachmentUiHandler attachmentUiHandler;
    private final transient SessionDropdownHandler sessionDropdownHandler;
    transient ComponentLifecycleHandler componentLifecycleHandler;
    private final transient InputHandler inputHandler;
    private final transient SessionLifecycleHandler sessionLifecycleHandler;
    private final transient MessageSender messageSender;

    private final transient AttachmentManager attachmentManager = new AttachmentManager();
    private final transient ConfigPanelController configPanelController;
    private final transient AutocompleteManager autocompleteManager;

    private final transient PermissionDialogManager permissionDialogManager;

    public AssistantTopComponent() {
        setName(NbBundle.getMessage(AssistantTopComponent.class, "CTL_AssistantTopComponent"));
        setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_AssistantTopComponent"));

        setLayout(new BorderLayout());
        chatPanel = new ChatThreadPanel();

        configPanelController = new ConfigPanelController(this::updateTabName);

        ChatLayoutBuilder layoutBuilder = new ChatLayoutBuilder(this, chatPanel, configPanelController);

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
        statusLabel = layoutBuilder.getStatusLabel();
        versionLabel = layoutBuilder.getVersionLabel();
        cwdLabel = layoutBuilder.getCwdLabel();
        inputArea = layoutBuilder.getInputArea();
        inputScrollPane = layoutBuilder.getInputScrollPane();
        sendBtn = layoutBuilder.getSendBtn();
        stopBtn = layoutBuilder.getStopBtn();

        add(header, BorderLayout.NORTH);
        add(chatPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        statusController = new StatusController(statusLabel, sendBtn, stopBtn, inputArea, toggleOptionsBtn);
        attachmentUiHandler = new AttachmentUiHandler(attachmentManager, statusController, inputArea, AssistantTopComponent.this);

        // Add attachment button to the right status panel (before settings button)
        layoutBuilder.getRightStatusPanel().add(attachmentUiHandler.getButton(), 0);

        sessionDropdownHandler = new SessionDropdownHandler(sessionDropdown, inputArea);
        sessionLifecycleHandler = new SessionLifecycleHandler(
            chatPanel, sessionDropdown, hideBtn, newSessionBtn, renameSessionBtn,
            toggleOptionsBtn, configPanelController, inputArea, statusController,
            this::showProjectPickerPopup, this::updateTabName, this::updateCwdLabel,
            sessionActive -> {
                sessionDropdown.setEnabled(sessionActive);
                hideBtn.setEnabled(sessionActive);
                renameSessionBtn.setEnabled(sessionActive);
                toggleBlocksBtn.setEnabled(sessionActive);
                keepBtn.setEnabled(sessionActive);
                filterBtn.setEnabled(sessionActive);
                helpBtn.setEnabled(sessionActive);
                restartServerBtn.setEnabled(true);
                newSessionBtn.setEnabled(true);
            }
        );
        messageSender = new MessageSender(
            inputArea, chatPanel, attachmentManager, messageHistory,
            statusController, attachmentUiHandler::updateTooltip, inputArea::requestFocusInWindow
        );
        messageSender.setOnNewMessageCallback(sessionLifecycleHandler::onNewMessageSent);
        messageSender.setOnMessageDoneCallback(sessionLifecycleHandler::onMessageDone);

        sendBtn.addActionListener(e -> messageSender.sendMessage());
        stopBtn.addActionListener(e -> messageSender.stopMessage());

        toggleOptionsBtn.addActionListener(e -> {
            boolean collapsed = !sessionLifecycleHandler.isOptionsPanelCollapsed();
            sessionLifecycleHandler.setOptionsPanelCollapsed(collapsed);
            configPanelController.getComponent().setVisible(!collapsed);
            toggleOptionsBtn.setIcon(ThemeManager.getIcon(collapsed ? "settings.svg" : "arrow-down.svg", 25));
            AssistantTopComponent.this.revalidate();
            AssistantTopComponent.this.repaint();
        });

        autocompleteManager = new AutocompleteManager(inputArea, messageSender::sendMessage);

        componentLifecycleHandler = new ComponentLifecycleHandler(
            chatPanel, statusController, sessionLifecycleHandler,
            configPanelController, inputArea, sessionDropdown, toggleOptionsBtn,
            AssistantTopComponent.this
        );
        inputHandler = new InputHandler(inputArea, autocompleteManager, messageSender, messageHistory);

        permissionDialogManager = new PermissionDialogManager(chatPanel);

        initChat();
        applyInitialTheme();
    }

    private void initChat() {
        Lookup.getDefault().lookup(SessionControl.class).refreshSessions();
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
        String currentId = Lookup.getDefault().lookup(SessionControl.class).getCurrentSessionId();
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
            if (newTitle != null && !newTitle.trim().isEmpty()) {
                Lookup.getDefault().lookup(SessionControl.class).renameSession(currentId, newTitle.trim());
            }
        }
    }

    private String selectedIdToTitle(Session session) {
        String title = defaultIfBlank(session.title(), NbBundle.getMessage(AssistantTopComponent.class, "LBL_ChatDefault", left(session.id(), 8)));
        return Lookup.getDefault().lookup(SessionControl.class).getCustomTitle(session.id(), title);
    }

    private void showProjectPickerPopup(JComponent parent) {
        componentLifecycleHandler.showProjectPickerPopup(parent);
    }

    void reloadCurrentSession() {
        String currentId = Lookup.getDefault().lookup(SessionControl.class).getCurrentSessionId();
        if (currentId != null) {
            Lookup.getDefault().lookup(SessionControl.class).loadSession(currentId);
        }
    }

    void exportConversation() {
        String markdown = chatPanel.getConversationAsMarkdown();
        if (markdown == null || markdown.trim().isEmpty()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(NbBundle.getMessage(AssistantTopComponent.class, "TITLE_ExportConv"));
        chooser.setSelectedFile(new File("conversation.md"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(markdown);
                LOG.fine("Conversation exported to {0}", file.getAbsolutePath());
            } catch (IOException ex) {
                LOG.warn("Failed to export conversation", ex);
            }
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

    private void updateCwdLabel(String path) {
        SwingUtilities.invokeLater(() -> {
            String effectivePath = path;

            if (effectivePath == null || effectivePath.isEmpty()) {
                effectivePath = Lookup.getDefault().lookup(SessionControl.class).getCurrentSessionDirectory();
            }
            if (effectivePath == null || effectivePath.isEmpty()) {
                effectivePath = System.getProperty("user.dir");
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
    }

    @Override
    public void componentDeactivated() {
        super.componentDeactivated();
        componentLifecycleHandler.componentDeactivated();
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
    }

    @Override
    public void componentOpened() {
        if (!initialized) {
            initialized = true;
            componentLifecycleHandler.componentOpened();
        }
        // On subsequent opens, resources are already alive — no reinit needed.
    }

    @Override
    public void addNotify() {
        super.addNotify();
        componentLifecycleHandler.registerKeyEventDispatchers();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        componentLifecycleHandler.removeNotify();
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

    void showCwdContextMenu(java.awt.event.MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        String cwd = cwdLabel.getToolTipText();
        if (cwd == null || cwd.isEmpty()) return;

        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem locateItem = new javax.swing.JMenuItem("Locate in System");
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
                new javax.swing.border.EmptyBorder(4, 8, 4, 8)));

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

    @Override
    public void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response) {
        permissionDialogManager.handlePermissionRequest(sessionId, params, response,
            () -> {
                requestActive();
                toFront();
            });
    }

}
