package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.left;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.netbeans.api.project.Project;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.ProcessManager;
import github.anandb.netbeans.manager.AgentUtils;
import github.anandb.netbeans.manager.SessionManager;
import github.anandb.netbeans.manager.SessionTitleMapper;
import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.project.ACPProjectManager;
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
@TopComponent.Registration(mode = "properties", openAtStartup = true)
@ActionID(category = "Window", id = "github.anandb.netbeans.ui.AssistantTopComponent")
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_AssistantAction",
    preferredID = "AssistantTopComponent"
)
@NbBundle.Messages({
    "CTL_AssistantAction=Assistant",
    "CTL_AssistantTopComponent=Assistant",
    "HINT_AssistantTopComponent=This is an Assistant window",
    "STATUS_Ready=Ready",
    "STATUS_Thinking=Thinking...",
    "STATUS_Responding=Responding...",
    "STATUS_FileTooLarge=File too large (max 10MB)",
    "STATUS_OpenProject=Open a project to start chatting",
    "STATUS_NewChat=Click '+ New Chat' to start",
    "STATUS_CreatingSession=Creating new session...",
    "STATUS_LoadingChat=Loading chat...",
    "STATUS_NoSession=Error: No active session.",
    "STATUS_Sending=Sending",
    "STATUS_Stopping=Stopping...",
    "STATUS_Stopped=Stopped",
    "STATUS_McpInitializing=MCP initializing...",
    "STATUS_RestartingServer=Restarting server...",
    "STATUS_ServerRestarted=Server restarted. Reloading session...",
    "# {0} - error message",
    "STATUS_RestartFailed=Restart failed: {0}",
    "# {0} - error message",
    "STATUS_FailedToStart=Failed to start: {0}",
    "# {0} - error message",
    "STATUS_Error=Error: {0}",
    "# {0} - max file count",
    "STATUS_MaxFiles=Max {0} files allowed",
    "# {0} - filename",
    "STATUS_Attached=Attached: {0}",
    "# {0} - filename",
    "# {1} - additional file count",
    "STATUS_AttachedMore=Attached: {0} +{1} more",
    "HINT_NewSession=New Session",
    "HINT_RenameSession=Rename Session",
    "HINT_ReloadConversation=Reload Conversation",
    "HINT_ExportConversation=Export Conversation",
    "HINT_RestartServer=Restart ACP server",
    "HINT_ExpandAll=Expand All Blocks",
    "HINT_CollapseAll=Collapse All Blocks",
    "HINT_Options=Options",
    "HINT_FilterMessages=Filter message types",
    "HINT_AttachFiles=Attach files",
    "# {0} - file count",
    "HINT_FilesAttached={0} file(s) attached",
    "HINT_KeepMessages=Pin to keep messages",
    "HINT_TruncateMessages=Unpin to progressively hide messages",
    "# {0} - used tokens",
    "# {1} - total tokens",
    "HINT_ContextUsage=Context Usage: {0}/{1} tokens",
    "BTN_SelectFile=Select File...",
    "BTN_Go=Go",
    "BTN_Stop=Stop",
    "MSG_EnterTitle=Enter new title for this session:",
    "MSG_ConfirmRestart=Are you sure you want to restart ACP server ?\nThis will terminate current operations and relaunch the connection.",
    "TITLE_RestartServer=Restart ACP server",
    "TITLE_ExportConv=Export Conversation",
    "# {0} - session id prefix",
    "LBL_ChatDefault=Chat {0}",
    "LBL_TypeMessage= Type Message Here"
})
public final class AssistantTopComponent extends TopComponent implements PermissionHandler {

    private static final Logger LOG = new Logger(AssistantTopComponent.class);
    private static final long serialVersionUID = 1L;
    private static volatile AssistantTopComponent instance;

    private final ChatThreadPanel chatPanel;
    private final PlaceholderTextArea inputArea;
    private final JButton sendBtn;
    private final JButton stopBtn;
    private final JComboBox<SessionItem> sessionDropdown;
    private final JButton newSessionBtn;
    private final JButton renameSessionBtn;
    private final JButton toggleBlocksBtn;
    private final JButton keepBtn;
    private final JButton filterBtn;
    private final JButton toggleOptionsBtn;
    private JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel cwdLabel;

    private final JScrollPane inputScrollPane;
    private final JPanel header;
    private final MessageHistory messageHistory = new MessageHistory();
    private final StatusController statusController;
    private final AttachmentUiHandler attachmentUiHandler;
    private final SessionDropdownHandler sessionDropdownHandler;
    private ComponentLifecycleHandler componentLifecycleHandler;
    private final InputHandler inputHandler;
    private final SessionLifecycleHandler sessionLifecycleHandler;
    private final MessageSender messageSender;

    private final AttachmentManager attachmentManager = new AttachmentManager();
    private final transient ConfigPanelController configPanelController;
    private final transient AutocompleteManager autocompleteManager;

    public AssistantTopComponent() {
        instance = this;
        setName(NbBundle.getMessage(AssistantTopComponent.class, "CTL_AssistantTopComponent"));
        setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_AssistantTopComponent"));

        putClientProperty("TabDisplayer.Closable", false);
        setLayout(new BorderLayout());
        chatPanel = new ChatThreadPanel();

        configPanelController = new ConfigPanelController(this::updateTabName);

        header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        sessionDropdown = new JComboBox<>();

        JPanel sessionControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sessionControls.setOpaque(false);

        newSessionBtn = UIUtils.createToolbarButton("new.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_NewSession"), e -> {
            Project[] projects = ACPProjectManager.getInstance().getAllOpenProjects();
            if (projects == null || projects.length == 0) {
                return;
            }
            if (projects.length == 1) {
                SessionManager.getInstance().createNewSession(projects[0].getProjectDirectory().getPath());
            } else {
                componentLifecycleHandler.showProjectPickerPopup((JComponent) e.getSource());
            }
        });
        String renameHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_RenameSession");
        renameSessionBtn = UIUtils.createToolbarButton("rename.svg", renameHint, e -> renameCurrentSession());
        JButton refreshBtn = UIUtils.createToolbarButton("reload.svg",
                NbBundle.getMessage(AssistantTopComponent.class, "HINT_ReloadConversation"), e -> {
            reloadCurrentSession();
        });

        String exportHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExportConversation");
        JButton exportBtn = UIUtils.createToolbarButton("export.svg", exportHint, e -> {
            exportConversation();
        });
        String restartHint = NbBundle.getMessage(AssistantTopComponent.class, "HINT_RestartServer");
        JButton restartServerBtn = UIUtils.createToolbarButton("restart.svg", restartHint, e -> {
            promptRestartServer();
        });

        JButton tb = UIUtils.createToolbarButton("expand.svg", NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExpandAll"), null);
        tb.addActionListener(e -> {
            boolean expanded = !chatPanel.isAllBlocksExpanded();
            chatPanel.toggleAllBlocks(expanded);
            String newState = expanded ? "collapse" : "expand";
            tb.putClientProperty("state", newState);
            tb.setToolTipText(expanded
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_CollapseAll")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_ExpandAll"));
            tb.setIcon(ThemeManager.getIcon(expanded ? "collapse.svg" : "expand.svg", 28));
        });
        toggleBlocksBtn = tb;
        toggleBlocksBtn.putClientProperty("state", "expand");

        final boolean savedKeepState = NbPreferences.forModule(AssistantTopComponent.class).getBoolean("keepOlderMessages", false);
        chatPanel.setKeepOlderMessages(savedKeepState);
        JButton pinBtn = UIUtils.createToolbarButton(savedKeepState ? "pin.svg" : "pin_off.svg",
                NbBundle.getMessage(AssistantTopComponent.class, savedKeepState ? "HINT_TruncateMessages" : "HINT_KeepMessages"), null);
        pinBtn.addActionListener(e -> {
            boolean keep = !chatPanel.isKeepOlderMessages();
            chatPanel.setKeepOlderMessages(keep);
            NbPreferences.forModule(AssistantTopComponent.class).putBoolean("keepOlderMessages", keep);
            pinBtn.setIcon(ThemeManager.getIcon(keep ? "pin.svg" : "pin_off.svg", 28));
            pinBtn.setToolTipText(keep
                ? NbBundle.getMessage(AssistantTopComponent.class, "HINT_TruncateMessages")
                : NbBundle.getMessage(AssistantTopComponent.class, "HINT_KeepMessages"));
        });
        keepBtn = pinBtn;
        keepBtn.putClientProperty("state", savedKeepState ? "pinned" : "unpinned");

        filterBtn = createFilterButton();

        sessionControls.add(newSessionBtn);
        sessionControls.add(renameSessionBtn);
        sessionControls.add(refreshBtn);
        sessionControls.add(keepBtn);
        sessionControls.add(toggleBlocksBtn);
        sessionControls.add(filterBtn);
        sessionControls.add(exportBtn);
        sessionControls.add(restartServerBtn);

        topBar.add(sessionDropdown, BorderLayout.CENTER);
        topBar.add(sessionControls, BorderLayout.EAST);

        cwdLabel = new JLabel("");
        cwdLabel.setFont(cwdLabel.getFont().deriveFont(Font.BOLD));

        JPanel headerContent = new JPanel(new BorderLayout(0, 4));
        headerContent.setOpaque(false);

        JPanel cwdRow = new JPanel(new BorderLayout(4, 0));
        cwdRow.setOpaque(false);
        cwdRow.add(cwdLabel, BorderLayout.CENTER);
        headerContent.add(cwdRow, BorderLayout.NORTH);
        headerContent.add(topBar, BorderLayout.SOUTH);

        header.add(headerContent, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        add(chatPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new EmptyBorder(4, 12, 4, 12));
        statusPanel.setOpaque(false);

        statusLabel = new JLabel(NbBundle.getMessage(AssistantTopComponent.class, "STATUS_Ready"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        toggleOptionsBtn = UIUtils.createToolbarButton("settings.svg", 25, NbBundle.getMessage(AssistantTopComponent.class, "HINT_Options"), null);

        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rightStatusPanel.setOpaque(false);

        rightStatusPanel.add(toggleOptionsBtn);

        statusPanel.add(rightStatusPanel, BorderLayout.EAST);

        bottomPanel.add(statusPanel, BorderLayout.NORTH);

        inputArea = new PlaceholderTextArea(NbBundle.getMessage(AssistantTopComponent.class, "LBL_TypeMessage"));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        float editorFontSize = (float) ThemeManager.getMonospaceFont().getSize();
        inputArea.setFont(ThemeManager.getFont().deriveFont(editorFontSize));

        inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setPreferredSize(new Dimension(100, 100));

        JPanel inputMainPanel = new JPanel(new BorderLayout(0, 4));
        inputMainPanel.setOpaque(false);
        inputMainPanel.add(configPanelController.getComponent(), BorderLayout.NORTH);
        inputMainPanel.add(inputScrollPane, BorderLayout.CENTER);

        JPanel btnCard = UIUtils.createTransparentPanel(new CardLayout());
        sendBtn = UIUtils.createTextButton(NbBundle.getMessage(AssistantTopComponent.class, "BTN_Go"), null);
        stopBtn = UIUtils.createTextButton(NbBundle.getMessage(AssistantTopComponent.class, "BTN_Stop"), null);

        btnCard.add(sendBtn, "SEND");
        btnCard.add(stopBtn, "STOP");

        versionLabel = new JLabel("v" + AgentUtils.getVersion());
        Font labelFont = UIManager.getFont("Label.font");
        versionLabel.setFont(versionLabel.getFont().deriveFont(labelFont != null ? labelFont.getSize() - 1f : 9f));
        versionLabel.setForeground(Color.GRAY);
        versionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout(0, 4));
        rightPanel.setOpaque(false);
        rightPanel.add(btnCard, BorderLayout.CENTER);
        rightPanel.add(versionLabel, BorderLayout.SOUTH);

        inputMainPanel.add(rightPanel, BorderLayout.EAST);

        bottomPanel.add(inputMainPanel, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        statusController = new StatusController(statusLabel, sendBtn, stopBtn, inputArea, toggleOptionsBtn);
        attachmentUiHandler = new AttachmentUiHandler(attachmentManager, statusController, inputArea, AssistantTopComponent.this);
        rightStatusPanel.add(attachmentUiHandler.getButton(), 0);
        sessionDropdownHandler = new SessionDropdownHandler(sessionDropdown, inputArea);
        sessionLifecycleHandler = new SessionLifecycleHandler(
            chatPanel, sessionDropdown, newSessionBtn, renameSessionBtn,
            toggleOptionsBtn, configPanelController, inputArea, statusController,
            this::showProjectPickerPopup, this::updateTabName, this::updateCwdLabel
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

        initChat();
        applyInitialTheme();
    }

    private void initChat() {
        SessionManager.getInstance().refreshSessions();
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

    private void renameCurrentSession() {
        String currentId = SessionManager.getInstance().getCurrentSessionId();
        if (currentId == null) {
            return;
        }

        SessionItem selectedItem = (SessionItem) sessionDropdown.getSelectedItem();
        if (selectedItem == null) {
            return;
        }

        String currentTitle = selectedIdToTitle(selectedItem.getSession());
        String newTitle = JOptionPane.showInputDialog(this, NbBundle.getMessage(AssistantTopComponent.class, "MSG_EnterTitle"), currentTitle);

        if (newTitle != null && !newTitle.trim().isEmpty()) {
            SessionManager.getInstance().renameSession(currentId, newTitle.trim());
        }
    }

    private String selectedIdToTitle(Session session) {
        String title = defaultIfBlank(session.title(), NbBundle.getMessage(AssistantTopComponent.class, "LBL_ChatDefault", left(session.id(), 8)));
        return SessionTitleMapper.getTitle(session.id(), title);
    }

    private JButton createFilterButton() {
        final JButton[] btnRef = new JButton[1];
        JButton btn = UIUtils.createToolbarButton("filter.svg", 25, NbBundle.getMessage(AssistantTopComponent.class, "HINT_FilterMessages"), e -> {
            JPopupMenu popup = new JPopupMenu();
            for (String type : MessageFilterManager.getMessageTypes()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(type, !MessageFilterManager.isTypeHidden(type));
                item.addActionListener(ev -> {
                    MessageFilterManager.setTypeHidden(type, !item.isSelected());
                    chatPanel.applyTypeFilters();
                });
                popup.add(item);
            }
            popup.show(btnRef[0], 0, btnRef[0].getHeight());
        });
        btnRef[0] = btn;
        return btn;
    }

    private void showProjectPickerPopup(JComponent parent) {
        componentLifecycleHandler.showProjectPickerPopup(parent);
    }

    private void reloadCurrentSession() {
        String currentId = SessionManager.getInstance().getCurrentSessionId();
        if (currentId != null) {
            SessionManager.getInstance().loadSession(currentId);
        }
    }

    private void exportConversation() {
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

    private void promptRestartServer() {
        int choice = JOptionPane.showConfirmDialog(this,
                NbBundle.getMessage(AssistantTopComponent.class, "MSG_ConfirmRestart"),
                NbBundle.getMessage(AssistantTopComponent.class, "TITLE_RestartServer"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            restartServer();
        }
    }

    private void restartServer() {
        String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
        statusController.setStatus("STATUS_RestartingServer");
        statusController.setInputEnabled(false);

        // Perform restart
        ProcessManager.getInstance().restartServer();

        // Wait for server to be ready and reload session
        ProcessManager.getInstance().whenReady().thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
                statusController.setStatus("STATUS_ServerRestarted");
                if (currentSessionId != null) {
                    SessionManager.getInstance().loadSession(currentSessionId);
                } else {
                    SessionManager.getInstance().refreshSessions();
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                statusController.setStatus("STATUS_RestartFailed", msg);
                chatPanel.stopStreaming();
                chatPanel.addMessage(ProcessedMessage.createError(
                    MessageType.error_response, NbBundle.getMessage(AssistantTopComponent.class, "STATUS_RestartFailed", msg), null, null
                ));
                statusController.setInputEnabled(true);
            });
            return null;
        });
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
                effectivePath = SessionManager.getInstance().getCurrentSessionDirectory();
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
    public void componentOpened() {
        instance = this;
        componentLifecycleHandler.componentOpened();
    }

    @Override
    public void componentActivated() {
        instance = this;
        componentLifecycleHandler.componentActivated();
    }

    @Override
    public void componentDeactivated() {
        super.componentDeactivated();
        componentLifecycleHandler.componentDeactivated();
    }

    private boolean allowClose = false;

    @Override
    public boolean canClose() {
        return allowClose;
    }

    public void toggleVisibility() {
        if (isOpened()) {
            // Hide by closing, but prevent full dispose
            allowClose = true;
            close();
            allowClose = false;
        } else {
            open();
            requestActive();
        }
    }

    @Override
    public void componentClosed() {
        componentLifecycleHandler.componentClosed();
        if (instance == this) {
            instance = null;
        }
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

    @Override
    public void handlePermissionRequest(String sessionId, JsonNode params, CompletableFuture<String> response) {
        String currentId = SessionManager.getInstance().getCurrentSessionId();
        if (currentId == null || !currentId.equals(sessionId)) {
            LOG.fine("Received permission request for session {0}, but current is {1}",
                    new Object[] { sessionId, currentId });
        }

        String prompt = "Permission requested";
        if (params.has("message")) {
            prompt = params.get("message").asText();
        } else if (params.has("content")) {
            prompt = params.get("content").asText();
        } else if (params.has("toolCall") || params.has("tool_call")) {
            JsonNode tc = params.has("toolCall") ? params.get("toolCall") : params.get("tool_call");
            String title = tc.has("title") ? tc.get("title").asText()
                    : tc.has("name") ? tc.get("name").asText() : "tool";
            prompt = "The agent wants to use the tool: '" + title + "'. Do you want to allow it?";
        }

        final String finalPrompt = prompt;
        SwingUtilities.invokeLater(() -> {
            chatPanel.addPermissionRequest(finalPrompt, params.get("options"), response);
            requestActive();
            toFront();
        });
    }

}
