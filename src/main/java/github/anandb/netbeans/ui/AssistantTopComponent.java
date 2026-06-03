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

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
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
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.ProcessManager;
import github.anandb.netbeans.manager.AgentUtils;
import github.anandb.netbeans.manager.SessionManager;
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
public final class AssistantTopComponent extends TopComponent implements PermissionHandler {

    private static final Logger LOG = Logger.from(AssistantTopComponent.class);
    private static final long serialVersionUID = 1L;
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
    private final JButton helpBtn;
    private final JButton toggleOptionsBtn;
    private final JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel cwdLabel;

    private final JScrollPane inputScrollPane;
    private final JPanel header;
    private final transient MessageHistory messageHistory = new MessageHistory();
    private transient StatusController statusController;
    private final transient AttachmentUiHandler attachmentUiHandler;
    private final transient SessionDropdownHandler sessionDropdownHandler;
    private transient ComponentLifecycleHandler componentLifecycleHandler;
    private final transient InputHandler inputHandler;
    private final transient SessionLifecycleHandler sessionLifecycleHandler;
    private final transient MessageSender messageSender;

    private final transient AttachmentManager attachmentManager = new AttachmentManager();
    private final transient ConfigPanelController configPanelController;
    private final transient AutocompleteManager autocompleteManager;

    public AssistantTopComponent() {
        setName(NbBundle.getMessage(AssistantTopComponent.class, "CTL_AssistantTopComponent"));
        setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_AssistantTopComponent"));

        setLayout(new BorderLayout());
        chatPanel = new ChatThreadPanel();

        configPanelController = new ConfigPanelController(this::updateTabName);

        header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        sessionDropdown = new UIUtils.WrappingComboBox<>();

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

        String quickstartUrl = "https://github.com/anandb/nb-complete/blob/main/QUICKSTART.md";
        String feedbackUrl = "https://forms.gle/ZQn5Wy2aDSSpkzkaA";

        helpBtn = UIUtils.createToolbarButton("help.svg",
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_QuickstartGuide"), null);
        helpBtn.setContentAreaFilled(false);
        helpBtn.setBorderPainted(false);
        helpBtn.addActionListener(e -> openOrCopyUrl(quickstartUrl, "STATUS_QuickstartCopied"));

        JButton feedbackBtn = UIUtils.createToolbarButton("feedback.svg",
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_Feedback"), null);
        feedbackBtn.setContentAreaFilled(false);
        feedbackBtn.setBorderPainted(false);
        feedbackBtn.addActionListener(e -> openOrCopyUrl(feedbackUrl, "STATUS_FeedbackCopied"));

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(feedbackBtn);
        rightButtons.add(helpBtn);
        cwdRow.add(rightButtons, BorderLayout.EAST);

        startHelpButtonFlash();

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
        int editorFontSize = ThemeManager.getMonospaceFont().getSize();
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
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(
            NbBundle.getMessage(AssistantTopComponent.class, "MSG_EnterTitle"),
            NbBundle.getMessage(AssistantTopComponent.class, "HINT_RenameSession")
        );
        input.setInputText(currentTitle);
        Object result = DialogDisplayer.getDefault().notify(input);
        if (result == NotifyDescriptor.OK_OPTION) {
            String newTitle = input.getInputText();
            if (newTitle != null && !newTitle.trim().isEmpty()) {
                SessionManager.getInstance().renameSession(currentId, newTitle.trim());
            }
        }
    }

    private String selectedIdToTitle(Session session) {
        String title = defaultIfBlank(session.title(), NbBundle.getMessage(AssistantTopComponent.class, "LBL_ChatDefault", left(session.id(), 8)));
        return SessionManager.getCustomTitle(session.id(), title);
    }

    private JButton createFilterButton() {
        final JButton[] btnRef = new JButton[1];
        JButton btn = UIUtils.createToolbarButton("filter.svg", 25, NbBundle.getMessage(AssistantTopComponent.class, "HINT_FilterMessages"), e -> {
            JPopupMenu popup = new JPopupMenu();
            for (String type : ChatThreadPanel.MessageFilterManager.getMessageTypes()) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(type, !ChatThreadPanel.MessageFilterManager.isTypeHidden(type));
                item.addActionListener(ev -> {
                    ChatThreadPanel.MessageFilterManager.setTypeHidden(type, !item.isSelected());
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
    public void componentActivated() {
        componentLifecycleHandler.componentActivated();
    }

    @Override
    public void componentDeactivated() {
        super.componentDeactivated();
        componentLifecycleHandler.componentDeactivated();
    }

    public void toggleVisibility() {
        if (isOpened()) {
            close();
        } else {
            open();
            requestActive();
        }
    }

    /**
     * Toggles minimize/restore for the assistant panel.
     * Resources (listeners, handlers, messages) stay alive across all close/reopen cycles.
     */
    public void minimizeToDock() {
        if (isOpened()) {
            close();
        } else {
            open();
            requestActive();
        }
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

        String prompt = NbBundle.getMessage(AssistantTopComponent.class, "MSG_PermissionRequested");
        if (params.has("message")) {
            prompt = params.get("message").asText();
        } else if (params.has("content")) {
            prompt = params.get("content").asText();
        } else if (params.has("toolCall") || params.has("tool_call")) {
            JsonNode tc = params.has("toolCall") ? params.get("toolCall") : params.get("tool_call");
            String title = tc.has("title") ? tc.get("title").asText()
                    : tc.has("name") ? tc.get("name").asText() : "tool";

            // Extract meaningful context from tool call arguments
            String context = extractToolContext(tc);
            if (context != null) {
                prompt = NbBundle.getMessage(AssistantTopComponent.class, "MSG_PermissionToolWithContext", title, context);
            } else {
                prompt = NbBundle.getMessage(AssistantTopComponent.class, "MSG_PermissionTool", title);
            }
        }

        final String finalPrompt = prompt;
        SwingUtilities.invokeLater(() -> {
            chatPanel.addPermissionRequest(finalPrompt, params.get("options"), response);
            requestActive();
            toFront();
        });
    }

    /**
     * Extracts a human-readable context string from tool call arguments.
     * Returns key info like file path, command, or URL, or null if no
     * meaningful context is found.
     * <p>
     * Supports multiple formats:
     * <ul>
     *   <li>Standard tool call: {@code { args: { filePath: "..." } }}</li>
     *   <li>ACP permission format: {@code { rawInput: { filePath: "..." }, locations: [...], patterns: [...] }}</li>
     * </ul>
     */
    static String extractToolContext(JsonNode toolCall) {
        JsonNode args = toolCall.has("args") ? toolCall.get("args")
                : toolCall.has("arguments") ? toolCall.get("arguments") : null;

        // If no args/arguments, try rawInput (ACP permission metadata)
        if (args == null || !args.isObject()) {
            args = toolCall.has("rawInput") ? toolCall.get("rawInput") : null;
        }

        if (args != null && args.isObject()) {
            String result = extractContextFromArgs(args);
            if (result != null) {
                return result;
            }
        }

        // Fallback: try locations array (ACP format)
        String locPath = extractFirstLocationPath(toolCall);
        if (locPath != null) {
            return truncatePath(locPath);
        }

        // Fallback: try patterns array (ACP format)
        String pattern = extractFirstPattern(toolCall);
        if (pattern != null) {
            if (pattern.contains("/") || pattern.contains(File.separator) || pattern.startsWith(".")) {
                return truncatePath(pattern);
            }
            return truncateCommand(pattern);
        }

        return null;
    }

    /**
     * Extracts context from a JSON object containing argument key-value pairs.
     * Checks in priority order: filePath, filepath, file_path, path, command,
     * url, uri, then falls back to the first short string value.
     */
    private static String extractContextFromArgs(JsonNode args) {
        // Priority order: file path, command, URL, fallback to first string arg
        if (args.has("filePath")) {
            return truncatePath(args.get("filePath").asText());
        }
        if (args.has("filepath")) {
            return truncatePath(args.get("filepath").asText());
        }
        if (args.has("file_path")) {
            return truncatePath(args.get("file_path").asText());
        }
        if (args.has("path")) {
            return truncatePath(args.get("path").asText());
        }
        if (args.has("command")) {
            return truncateCommand(args.get("command").asText());
        }
        if (args.has("url")) {
            return args.get("url").asText();
        }
        if (args.has("uri")) {
            return args.get("uri").asText();
        }

        // Fallback: show first string field value as brief context
        Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue().isTextual()) {
                String val = entry.getValue().asText();
                if (val.length() > 5 && val.length() < 120) {
                    return entry.getKey() + ": " + val;
                }
            }
        }

        return null;
    }

    /**
     * Extracts the first path from the locations array (ACP permission format).
     * Locations are structured as {@code [{ path: "/path/to/file" }]}.
     */
    private static String extractFirstLocationPath(JsonNode toolCall) {
        if (toolCall.has("locations") && toolCall.get("locations").isArray()) {
            JsonNode locs = toolCall.get("locations");
            for (JsonNode loc : locs) {
                if (loc.has("path") && loc.get("path").isTextual()) {
                    return loc.get("path").asText();
                }
            }
        }
        return null;
    }

    /**
     * Extracts the first pattern from the patterns array (ACP permission format).
     */
    private static String extractFirstPattern(JsonNode toolCall) {
        if (toolCall.has("patterns") && toolCall.get("patterns").isArray()
                && toolCall.get("patterns").size() > 0) {
            JsonNode first = toolCall.get("patterns").get(0);
            if (first.isTextual()) {
                return first.asText();
            }
        }
        return null;
    }

    /**
     * Truncates a file path for display, keeping the last ~60 chars.
     */
    static String truncatePath(String path) {
        if (path == null || path.length() <= 65) {
            return path;
        }
        return "..." + path.substring(path.length() - 62);
    }

    /**
     * Truncates a shell command for display, keeping the first ~80 chars.
     */
    static String truncateCommand(String command) {
        if (command == null || command.length() <= 80) {
            return command;
        }
        return command.substring(0, 77) + "...";
    }

    /** Open a URL in the browser, or copy to clipboard as fallback. */
    private void openOrCopyUrl(String url, String statusKey) {
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
                return;
            } catch (IOException | URISyntaxException ex) {
                LOG.warn("Failed to open URL: {0}", ex.getMessage());
            }
        }
        // Fallback: copy URL to clipboard
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(url), null);
            if (statusKey != null) {
                statusController.setStatus(statusKey, url);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to copy URL to clipboard: {0}", ex.getMessage());
        }
    }

    /** Flash the help icon for at least 5 seconds on startup for discoverability. */
    private void startHelpButtonFlash() {
        java.awt.Color flashBg = ThemeManager.getCurrentTheme().isDark()
                ? new java.awt.Color(128, 128, 128, 180)
                : new java.awt.Color(66, 133, 244, 180);
        javax.swing.Timer timer = new javax.swing.Timer(700, null);
        timer.addActionListener(new java.awt.event.ActionListener() {
            int tick = 0;
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                tick++;
                boolean highlight = tick % 2 == 0;
                if (highlight) {
                    helpBtn.setOpaque(true);
                    helpBtn.setBackground(flashBg);
                    helpBtn.setContentAreaFilled(true);
                } else {
                    helpBtn.setOpaque(false);
                    helpBtn.setContentAreaFilled(false);
                }
                helpBtn.repaint();
                if (tick >= 8) {
                    timer.stop();
                    helpBtn.setOpaque(false);
                    helpBtn.setContentAreaFilled(false);
                    helpBtn.repaint();
                }
            }
        });
        timer.setInitialDelay(800);
        timer.start();
    }

}
