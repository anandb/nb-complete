package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.left;
import static org.apache.commons.lang3.StringUtils.split;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;

import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.api.settings.ConvertAsProperties;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;

import com.fasterxml.jackson.databind.JsonNode;

import java.awt.KeyEventDispatcher;

import javax.swing.Timer;

import github.anandb.netbeans.manager.ProcessManager;
import github.anandb.netbeans.manager.AgentUtils;
import github.anandb.netbeans.manager.SessionManager;
import github.anandb.netbeans.manager.SessionTitleMapper;
import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.manager.strategy.StrategyRegistry;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.ConfigItem;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

@ConvertAsProperties(
    dtd = "-//github.anandb.netbeans.ui//Assistant//EN",
    autostore = false
)
@TopComponent.Description(
    preferredID = "AssistantTopComponent",
    iconBase = "github/anandb/netbeans/ui/icons/logo.svg",
    persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "properties", openAtStartup = true)
@ActionID(category = "Window", id = "github.anandb.netbeans.ui.AssistantTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_AssistantAction",
    preferredID = "AssistantTopComponent"
)
@NbBundle.Messages({
    "CTL_AssistantAction=Assistant",
    "CTL_AssistantTopComponent=Assistant",
    "HINT_AssistantTopComponent=This is an Assistant window"
})
public final class AssistantTopComponent extends TopComponent implements PermissionHandler, SessionListener {

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
    private final JButton filterBtn;
    private final JButton toggleOptionsBtn;
    private boolean optionsPanelCollapsed = true;
    private final JPanel configPanel;
    private final JComboBox<ConfigItem> modeCombo;
    private final JComboBox<ConfigItem> modelCombo;
    private final JComboBox<ConfigItem> thinkingCombo;
    private JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel cwdLabel;
    private final JScrollPane inputScrollPane;
    private final JPanel header;
    private final JPopupMenu autocompletePopup;
    private final JList<SessionUpdate.AvailableCommand> commandList;
    private final ArrayList<String> messageHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String currentDraft = "";
    private static String lastSelectedModelId;

    private boolean isSwitchingSessionDropdown = false;
    private boolean isUpdatingConfigControls = false;
    private Timer thinkingTimer;
    private Timer statusResetTimer;
    private transient KeyEventDispatcher pageKeyDispatcher;
    private int thinkingDots = 0;
    private static final String[] DOT_STRINGS = {"", ".", "..", "..."};

    private final LinkedHashMap<String, List<ConfigItem>> modelVariants = new LinkedHashMap<>();
    private String currentConfigModelId = null;

    private static final long MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 2;
    private final ArrayList<AttachedFile> attachedFiles = new ArrayList<>();
    private JButton paperclipBtn;

    public AssistantTopComponent() {
        instance = this;
        setName(NbBundle.getMessage(AssistantTopComponent.class, "CTL_AssistantTopComponent"));
        setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_AssistantTopComponent"));

        thinkingTimer = new javax.swing.Timer(500, e -> {
            if (statusLabel != null && statusLabel.getText() != null) {
                String txt = statusLabel.getText();
                if (txt.startsWith("Thinking") || txt.startsWith("Responding") || txt.startsWith("Sending")) {
                    String base = txt.replace(".", "");
                    thinkingDots = (thinkingDots + 1) % 4;
                    statusLabel.setText(base + DOT_STRINGS[thinkingDots]);
                }
            }
        });

        statusResetTimer = new javax.swing.Timer(1500, e -> {
            if (statusLabel != null) {
                statusLabel.setText("Ready");
            }
        });
        statusResetTimer.setRepeats(false);

        setLayout(new BorderLayout());
        chatPanel = new ChatThreadPanel();

        pageKeyDispatcher = e -> {
            if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                int keyCode = e.getKeyCode();
                if (keyCode == java.awt.event.KeyEvent.VK_PAGE_UP
                        || keyCode == java.awt.event.KeyEvent.VK_PAGE_DOWN
                        || ((e.getModifiersEx() & java.awt.event.KeyEvent.CTRL_DOWN_MASK) != 0
                            && (keyCode == java.awt.event.KeyEvent.VK_HOME
                                || keyCode == java.awt.event.KeyEvent.VK_END))) {
                    java.awt.Component src = e.getComponent();
                    if (src != null && javax.swing.SwingUtilities.isDescendingFrom(src, AssistantTopComponent.this)
                            && !javax.swing.SwingUtilities.isDescendingFrom(src, chatPanel)) {
                        if (keyCode == java.awt.event.KeyEvent.VK_PAGE_UP) {
                            chatPanel.scrollByBlock(true);
                        } else if (keyCode == java.awt.event.KeyEvent.VK_PAGE_DOWN) {
                            chatPanel.scrollByBlock(false);
                        } else if (keyCode == java.awt.event.KeyEvent.VK_HOME) {
                            chatPanel.scrollToTop();
                        } else if (keyCode == java.awt.event.KeyEvent.VK_END) {
                            chatPanel.scrollToBottom(true);
                        }
                        return true;
                    }
                }
            }
            return false;
        };
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(pageKeyDispatcher);

        configPanel = new JPanel(new java.awt.GridBagLayout());
        configPanel.setVisible(false);
        configPanel.setOpaque(false);
        configPanel.setBorder(new EmptyBorder(5, 12, 5, 12));

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.insets = new java.awt.Insets(2, 0, 2, 8);

        modeCombo = new JComboBox<>();
        modelCombo = new JComboBox<>();
        thinkingCombo = new JComboBox<>();

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        configPanel.add(new JLabel("Agent:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.2;
        configPanel.add(modeCombo, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        configPanel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.7;
        configPanel.add(modelCombo, gbc);

        gbc.gridx = 4; gbc.weightx = 0; gbc.insets = new java.awt.Insets(2, 0, 2, 4);
        JButton copyModelBtn = UIUtils.createToolbarButton("copy.svg", 20, "Copy Model ID", e -> {
            ConfigItem selected = (ConfigItem) modelCombo.getSelectedItem();
            if (selected != null && selected.value() != null) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(selected.value()), null);
            }
        });
        configPanel.add(copyModelBtn, gbc);
        gbc.insets = new java.awt.Insets(2, 0, 2, 8);

        gbc.gridx = 5; gbc.weightx = 0;
        configPanel.add(new JLabel("Thinking:"), gbc);
        gbc.gridx = 6; gbc.weightx = 0.1;
        configPanel.add(thinkingCombo, gbc);

        header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        sessionDropdown = new JComboBox<>();
        sessionDropdown.addActionListener(e -> {
            if (isSwitchingSessionDropdown) {
                return;
            }
            SessionItem selected = (SessionItem) sessionDropdown.getSelectedItem();
            if (selected != null) {
                SessionManager.getInstance().loadSession(selected.getSession().id());
            }
        });

        JPanel sessionControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sessionControls.setOpaque(false);

        newSessionBtn = UIUtils.createToolbarButton("new.svg", "New Session", e -> SessionManager.getInstance().createNewSession(null));
        renameSessionBtn = UIUtils.createToolbarButton("rename.svg", "Rename Session", e -> renameCurrentSession());
        JButton refreshBtn = UIUtils.createToolbarButton("reload.svg", "Reload Conversation", e -> reloadCurrentSession());

        JButton exportBtn = UIUtils.createToolbarButton("export.svg", "Export Conversation", e -> exportConversation());
        JButton restartServerBtn = UIUtils.createToolbarButton("restart.svg", "Reconnect", e -> promptRestartServer());

        JButton tb = UIUtils.createToolbarButton("expand.svg", "Expand All Blocks", null);
        tb.addActionListener(e -> {
            boolean isCollapse = "collapse".equals(tb.getClientProperty("state"));
            chatPanel.toggleAllBlocks(!isCollapse);
            String newState = isCollapse ? "expand" : "collapse";
            tb.putClientProperty("state", newState);
            tb.setToolTipText(isCollapse ? "Expand All Blocks" : "Collapse All Blocks");
            tb.setIcon(ThemeManager.getIcon(isCollapse ? "expand.svg" : "collapse.svg", 28));
        });
        toggleBlocksBtn = tb;
        toggleBlocksBtn.putClientProperty("state", "expand");

        filterBtn = createFilterButton();

        sessionControls.add(newSessionBtn);
        sessionControls.add(renameSessionBtn);
        sessionControls.add(refreshBtn);
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
        headerContent.add(cwdLabel, BorderLayout.NORTH);
        headerContent.add(topBar, BorderLayout.SOUTH);

        header.add(headerContent, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        add(chatPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(new EmptyBorder(4, 12, 4, 12));
        statusPanel.setOpaque(false);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        JButton to = UIUtils.createToolbarButton("settings.svg", 25, "Options", null);
        to.addActionListener(e -> {
            optionsPanelCollapsed = !optionsPanelCollapsed;
            configPanel.setVisible(!optionsPanelCollapsed);
            to.setIcon(ThemeManager.getIcon(optionsPanelCollapsed ? "settings.svg" : "arrow-down.svg", 25));
            AssistantTopComponent.this.revalidate();
            AssistantTopComponent.this.repaint();
        });
        toggleOptionsBtn = to;

        JPanel rightStatusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rightStatusPanel.setOpaque(false);

        paperclipBtn = createPaperclipButton();
        rightStatusPanel.add(paperclipBtn);
        rightStatusPanel.add(toggleOptionsBtn);

        statusPanel.add(rightStatusPanel, BorderLayout.EAST);

        bottomPanel.add(statusPanel, BorderLayout.NORTH);

        inputArea = new PlaceholderTextArea(" Type Message Here");
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        float editorFontSize = (float) ThemeManager.getMonospaceFont().getSize();
        inputArea.setFont(ThemeManager.getFont().deriveFont(editorFontSize));

        inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setPreferredSize(new Dimension(100, 100));

        JPanel inputMainPanel = new JPanel(new BorderLayout(0, 4));
        inputMainPanel.setOpaque(false);
        inputMainPanel.add(configPanel, BorderLayout.NORTH);
        inputMainPanel.add(inputScrollPane, BorderLayout.CENTER);

        JPanel btnCard = UIUtils.createTransparentPanel(new CardLayout());
        sendBtn = UIUtils.createTextButton("Go", e -> sendMessage());
        stopBtn = UIUtils.createTextButton("Stop", e -> stopMessage());

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

        autocompletePopup = new JPopupMenu();
        autocompletePopup.setBorder(BorderFactory.createLineBorder(UIManager.getColor("controlShadow")));

        commandList = new JList<>();
        commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandList.setFocusable(false);
        commandList.setCellRenderer(new AutocompleteRenderer());

        JScrollPane scrollPane = new JScrollPane(commandList);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(750, 200));
        autocompletePopup.add(scrollPane);

        setupListeners();

        ProcessManager.getInstance().setPermissionHandler(this);

        initChat();
        applyInitialTheme();
    }

    @Override
    public void onSessionUpdate(SessionUpdate update) {
        String type = update.update() != null ? update.update().type() : null;
        String msgId = update.update() != null ? update.update().messageId() : null;
        LOG.info("UI received session update: type={0}, msgId={1}", type, msgId);

        ProcessedMessage pm = new ProcessedMessage();
        DataExtractionStrategy strategy = StrategyRegistry.getInstance().select(update);
        if (strategy != null) {
            strategy.extract(update, pm, new UIHandler() {
                @Override
                public void displayMessage(ProcessedMessage msg) {
                    SwingUtilities.invokeLater(() -> {
                        if (msg.streaming()) {
                            chatPanel.appendOrAddProcessedMessage(msg);
                        } else {
                            chatPanel.addProcessedMessage(msg);
                        }
                        updateButtonState(true);
                    });
                }

                @Override
                public void updateConfig(java.util.List<SessionConfigOption> options) {
                    if (options != null) {
                        updateConfigControls(options);
                    }
                }

                @Override
                public void refreshSessions() {
                    SessionManager.getInstance().refreshSessions();
                }

                @Override
                public void updateUsage(long used, long size) {
                    SwingUtilities.invokeLater(() ->
                        statusLabel.setToolTipText("Context Usage: " + used + "/" + size + " tokens")
                    );
                }
            });
        }

        // Status updates
        Boolean isThinking = update.isThinking();
        if (isThinking != null) {
            SwingUtilities.invokeLater(() -> {
                if (isThinking) {
                    statusLabel.setText("Thinking...");
                    thinkingTimer.start();
                } else {
                    String current = statusLabel.getText();
                    if (current != null && current.startsWith("Thinking")) {
                        statusLabel.setText("Responding...");
                    }
                }
            });
        }

        // End of turn signals
        if ("responding_finished".equals(type) || "end_turn".equals(type)) {
            SwingUtilities.invokeLater(() -> {
                resetStatus();
                chatPanel.stopStreaming();
            });
        }
    }


    private void setupListeners() {
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (autocompletePopup.isVisible()) {
                        e.consume();
                        selectCommand();
                    } else if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
                        inputArea.append("\n");
                    } else {
                        e.consume();
                        sendMessage();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (inputArea.getCaretPosition() == 0 && !messageHistory.isEmpty()) {
                        if (historyIndex == -1) {
                            currentDraft = inputArea.getText();
                            historyIndex = messageHistory.size() - 1;
                        } else if (historyIndex > 0) {
                            historyIndex--;
                        }
                        inputArea.setText(messageHistory.get(historyIndex));
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (inputArea.getCaretPosition() == inputArea.getText().length() && historyIndex != -1) {
                        if (historyIndex < messageHistory.size() - 1) {
                            historyIndex++;
                            inputArea.setText(messageHistory.get(historyIndex));
                        } else {
                            historyIndex = -1;
                            inputArea.setText(currentDraft);
                        }
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                checkAutocomplete(e);
            }
        });

        commandList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectCommand();
                }
            }
        });
    }

    @Override
    public void onSessionListUpdated(List<Session> sessions) {
        SwingUtilities.invokeLater(() -> {
            isSwitchingSessionDropdown = true;
            try {
                String currentId = SessionManager.getInstance().getCurrentSessionId();
                sessionDropdown.removeAllItems();
                LOG.fine("onSessionListUpdated: adding {0} sessions to dropdown", sessions.size());
                int selectIdx = -1;
                for (int i = 0; i < sessions.size(); i++) {
                    Session s = sessions.get(i);
                    String customTitle = SessionTitleMapper.getTitle(s.id(), s.title());
                    sessionDropdown.addItem(new SessionItem(s, customTitle));
                    if (currentId != null && s.id().equals(currentId)) {
                        selectIdx = i;
                    }
                }

                boolean hasSessions = !sessions.isEmpty();
                boolean hasProjects = OpenProjects.getDefault().getOpenProjects().length > 0;
                newSessionBtn.setEnabled(hasProjects);
                renameSessionBtn.setEnabled(hasSessions);

                isUpdatingConfigControls = true;
                try {
                    if (modelCombo.getItemCount() > 0 && modelCombo.getSelectedIndex() < 0) {
                        modelCombo.setSelectedIndex(0);
                    }
                } finally {
                    isUpdatingConfigControls = false;
                }

                if (hasSessions) {
                    if (selectIdx != -1) {
                        sessionDropdown.setSelectedIndex(selectIdx);
                    } else {
                        // If no current session exists, load the most recent one (index 0)
                        // Sessions are sorted by timestamp descending, so index 0 is most recent
                        Session mostRecent = sessions.get(0);
                        if (mostRecent != null) {
                            LOG.fine("Loading most recent session: {0}", mostRecent.id());
                            SessionManager.getInstance().loadSession(mostRecent.id());
                        }
                    }
                } else {
                    chatPanel.setSessionList(sessions, id -> SessionManager.getInstance().loadSession(id), () -> SessionManager.getInstance().createNewSession(null));
                    sessionDropdown.setSelectedIndex(-1);
                    if (!hasProjects) {
                        statusLabel.setText("Open a project to start chatting");
                    } else {
                        statusLabel.setText("Click '+ New Chat' to start");
                    }
                    optionsPanelCollapsed = false;
                    configPanel.setVisible(true);
                    toggleOptionsBtn.setIcon(ThemeManager.getIcon("arrow-down.svg", 25));
                    setInputEnabled(false);
                    if (lastSelectedModelId == null && System.getenv("OPENCODE_MODEL") == null && modelCombo.getItemCount() == 0) {
                        modelCombo.addItem(new ConfigItem("opencode/big-pickle", "opencode/big-pickle"));
                    }
                }
            } finally {
                isSwitchingSessionDropdown = false;
                sessionDropdown.revalidate();
                sessionDropdown.repaint();
            }
        });
    }

    @Override
    public void onSessionStarted(String sessionId) {
        SwingUtilities.invokeLater(() -> {
            chatPanel.clearMessages();
            if (sessionId == null) {
                statusLabel.setText("Creating new session...");
                updateTabName(null);
            } else {
                statusLabel.setText("Loading chat...");
            }
        });
    }

    @Override
    public void onSessionLoaded(String sessionId, List<SessionConfigOption> configOptions, boolean isStartup) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Ready");
            updateCwdLabel(null);
            if (configOptions != null) {
                updateConfigControls(configOptions, isStartup);
            }
            // If this is a new session (isStartup=true), apply any pre-selected config values
            // from the config panel that the user may have set before creating the chat
            if (isStartup) {
                applyPreSelectedConfigValues(sessionId, configOptions);
            }

            setInputEnabled(true);
            inputArea.requestFocusInWindow();
            chatPanel.scrollToBottom();
        });
    }

    @Override
    public void onSessionLoading(boolean isLoading) {
        SwingUtilities.invokeLater(() -> setInputEnabled(!isLoading));
    }

    @Override
    public void onSessionError(String message) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Error: " + message);
            chatPanel.addMessage("error", message);
        });
    }

    private void initChat() {
        SessionManager.getInstance().refreshSessions();
    }

    private void sendMessage() {
        String text = inputArea.getText(); // Don't trim user input spaces
        if (text.isEmpty() && attachedFiles.isEmpty()) {
            return;
        }

        // Add to history
        if (messageHistory.isEmpty() || !messageHistory.get(messageHistory.size() - 1).equals(text)) {
            messageHistory.add(text);
            if (messageHistory.size() > 50) {
                messageHistory.remove(0);
            }
        }
        historyIndex = -1;
        currentDraft = "";

        String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
        if (currentSessionId == null) {
            statusLabel.setText("Error: No active session.");
            return;
        }

        inputArea.setText("");
        statusLabel.setText("Sending");
        updateButtonState(true);

        // Build file attachment blocks
        List<Map<String, Object>> fileBlocks = new ArrayList<>();
        for (AttachedFile af : attachedFiles) {
            Map<String, Object> block = new HashMap<>();
            block.put("type", af.mimeType().startsWith("image/") ? "image" : "file");
            block.put("filename", af.filename());
            block.put("mimeType", af.mimeType());
            block.put("data", af.base64Data());
            fileBlocks.add(block);
        }
        attachedFiles.clear();
        updatePaperclipTooltip();

        // Local echo with file references
        boolean localEcho = NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("echoUserInput", true);
        if (localEcho) {
            StringBuilder echoBuilder = new StringBuilder(text);
            if (!fileBlocks.isEmpty()) {
                if (!text.isBlank()) echoBuilder.append("\n");
                for (Map<String, Object> block : fileBlocks) {
                    String type = (String) block.get("type");
                    String fname = (String) block.get("filename");
                    echoBuilder.append("\n[").append("image".equals(type) ? "Image" : "File").append(": ").append(fname).append("]");
                }
            }
            chatPanel.addMessage("user", echoBuilder.toString());
        }

        Map<String, Object> context = text.trim().startsWith("/") ? null : captureEditorContext();
        ProcessManager.getInstance().sendMessage(currentSessionId, text, context, fileBlocks)
                .thenAccept(result -> {
                    SwingUtilities.invokeLater(() -> {
                        String currentStatus = statusLabel.getText();
                        if (currentStatus != null && currentStatus.startsWith("Sending")) {
                            statusLabel.setText("Ready");
                            updateButtonState(false);
                            inputArea.requestFocusInWindow();
                        }

                        // Handle turn completion from RPC result
                        if (result != null && result.has("stopReason")) {
                            LOG.info("Turn finished via RPC result: stopReason={0}", result.get("stopReason").asText());
                            chatPanel.stopStreaming();
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                        chatPanel.addMessage("error", "Error: " + ex.getMessage());
                        inputArea.setText(text);
                        updateButtonState(false);
                        inputArea.requestFocusInWindow();
                    });
                    return null;
                });
    }

    private Map<String, Object> captureEditorContext() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return null;
        }

        Document doc = editor.getDocument();
        if (!(doc instanceof StyledDocument styledDoc)) {
            return null;
        }

        Map<String, Object> context = new java.util.HashMap<>();

        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo != null) {
            context.put("filePath", fo.getPath());
        }

        String selection = editor.getSelectedText();
        if (selection != null && !selection.isEmpty()) {
            context.put("selectionContent", selection);
            int selStart = editor.getSelectionStart();
            int selEnd = editor.getSelectionEnd();

            int startLine = NbDocument.findLineNumber(styledDoc, selStart) + 1;
            int endLine = NbDocument.findLineNumber(styledDoc, selEnd) + 1;
            context.put("selection", startLine + ":" + endLine);
        }

        int caretPos = editor.getCaretPosition();
        int cursorLine = NbDocument.findLineNumber(styledDoc, caretPos) + 1;
        context.put("cursor", String.valueOf(cursorLine));

        return context;
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

    private void stopMessage() {
        String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
        if (currentSessionId == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> statusLabel.setText("Stopping..."));
        ProcessManager.getInstance().stopMessage(currentSessionId)
                .thenAccept(v -> {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Stopped");
                        chatPanel.stopStreaming();
                        updateButtonState(false);
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Stop failed: " + ex.getMessage()));
                    return null;
                });
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
        String newTitle = javax.swing.JOptionPane.showInputDialog(this, "Enter new title for this session:", currentTitle);

        if (newTitle != null && !newTitle.trim().isEmpty()) {
            SessionManager.getInstance().renameSession(currentId, newTitle.trim());
        }
    }

    private String selectedIdToTitle(Session session) {
        String title = defaultIfBlank(session.title(), "Chat " + left(session.id(), 8));
        return SessionTitleMapper.getTitle(session.id(), title);
    }

    private JButton createFilterButton() {
        final JButton[] btnRef = new JButton[1];
        JButton btn = UIUtils.createToolbarButton("filter.svg", "Filter message types", e -> {
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

    private JButton createPaperclipButton() {
        JButton btn = UIUtils.createToolbarButton("paperclip.svg", "Attach files", null);
        btn.addActionListener(e -> showPaperclipMenu(e));
        return btn;
    }

    private void showPaperclipMenu(ActionEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem addItem = new JMenuItem("Select File...");
        addItem.addActionListener(ev -> selectFiles());
        menu.add(addItem);
        if (!attachedFiles.isEmpty()) {
            menu.addSeparator();
            for (AttachedFile af : attachedFiles) {
                JCheckBoxMenuItem cb = new JCheckBoxMenuItem(af.filename(), true);
                cb.setEnabled(false);
                menu.add(cb);
            }
        }
        menu.show(paperclipBtn, 0, paperclipBtn.getHeight());
    }

    private void selectFiles() {
        if (attachedFiles.size() >= MAX_ATTACHMENTS) {
            if (statusLabel != null) {
                statusLabel.setText("Max " + MAX_ATTACHMENTS + " files allowed");
                statusResetTimer.restart();
            }
            return;
        }

        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            int added = 0;
            String lastName = null;
            for (File f : fc.getSelectedFiles()) {
                if (attachedFiles.size() >= MAX_ATTACHMENTS) {
                    break;
                }
                if (f.length() > MAX_ATTACHMENT_SIZE) {
                    continue;
                }
                try {
                    attachedFiles.add(new AttachedFile(f));
                    added++;
                    lastName = f.getName();
                } catch (IOException ex) {
                    LOG.warn("Failed to attach file: {0}", f.getName(), ex);
                }
            }
            if (added > 0 && statusLabel != null) {
                if (added == 1) {
                    statusLabel.setText("Attached: " + lastName);
                } else {
                    statusLabel.setText("Attached: " + lastName + " +" + (added - 1) + " more");
                }
                statusResetTimer.restart();
            }
            updatePaperclipTooltip();
        }
    }

    private void updatePaperclipTooltip() {
        if (attachedFiles.isEmpty()) {
            paperclipBtn.setToolTipText("Attach files");
        } else {
            paperclipBtn.setToolTipText(attachedFiles.size() + " file(s) attached");
        }
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

        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Export Conversation");
        chooser.setSelectedFile(new java.io.File("conversation.md"));
        if (chooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            java.io.File file = chooser.getSelectedFile();
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(markdown);
                LOG.fine("Conversation exported to {0}", file.getAbsolutePath());
            } catch (java.io.IOException ex) {
                LOG.warn("Failed to export conversation", ex);
            }
        }
    }

    private void promptRestartServer() {
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Are you sure you want to reconnect to the ACP server?\nThis will terminate current operations and relaunch the connection.",
                "Reconnect",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);

        if (choice == javax.swing.JOptionPane.YES_OPTION) {
            restartServer();
        }
    }


    private void restartServer() {
        String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
        statusLabel.setText("Restarting server...");
        setInputEnabled(false);

        // Perform restart
        ProcessManager.getInstance().restartServer();

        // Wait for server to be ready and reload session
        ProcessManager.getInstance().whenReady().thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Server restarted. Reloading session...");
                if (currentSessionId != null) {
                    SessionManager.getInstance().loadSession(currentSessionId);
                } else {
                    SessionManager.getInstance().refreshSessions();
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                statusLabel.setText("Restart failed: " + msg);
                chatPanel.addMessage("error", "Restart failed: " + msg);
                setInputEnabled(true);
            });
            return null;
        });
    }

    private void updateButtonState(boolean isProcessing) {
        SwingUtilities.invokeLater(() -> {
            sendBtn.setEnabled(!isProcessing);
            stopBtn.setEnabled(isProcessing);
            if (sendBtn.getParent() != null && sendBtn.getParent().getLayout() instanceof java.awt.CardLayout cl) {
                cl.show(sendBtn.getParent(), isProcessing ? "STOP" : "SEND");
            }

            if (isProcessing) {
                thinkingTimer.start();
            } else {
                thinkingTimer.stop();
            }
        });
    }

    private void resetStatus() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Ready");
            updateButtonState(false);
            if (SessionManager.getInstance().getCurrentSessionId() != null) {
                setInputEnabled(true);
            }
        });
    }

    private void setInputEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            inputArea.setEnabled(enabled);
            sendBtn.setEnabled(enabled);
            toggleOptionsBtn.setVisible(enabled);
            // Keep config panel visible even when input is disabled so users can select
            // options before starting a new chat
            if (!enabled) {
                inputArea.setBackground(UIManager.getColor("TextArea.background"));
            }
        });
    }

    private void checkAutocomplete(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (keyCode == KeyEvent.VK_ESCAPE) {
            autocompletePopup.setVisible(false);
            return;
        }
        if (keyCode == KeyEvent.VK_ENTER) {
            if (autocompletePopup.isVisible()) {
                selectCommand();
            }
            autocompletePopup.setVisible(false);
            return;
        }
        if (keyCode == KeyEvent.VK_SPACE) {
            autocompletePopup.setVisible(false);
            return;
        }

        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN) {
            if (autocompletePopup.isVisible()) {
                int size = commandList.getModel().getSize();
                if (size > 0) {
                    int index = commandList.getSelectedIndex();
                    if (keyCode == KeyEvent.VK_UP) {
                        index = (index - 1 + size) % size;
                    } else {
                        index = (index + 1) % size;
                    }
                    commandList.setSelectedIndex(index);
                    commandList.ensureIndexIsVisible(index);
                }
                return;
            }
        }

        showAutocomplete();
    }

    private void showAutocomplete() {
        String text = inputArea.getText();
        int caret = inputArea.getCaretPosition();
        if (caret <= 0) {
            autocompletePopup.setVisible(false);
            return;
        }

        // Find the trigger character (/ or @) before caret
        int start = caret - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
            if (text.charAt(start) == '/' || text.charAt(start) == '@') {
                break;
            }
            start--;
        }

        if (start < 0 || (text.charAt(start) != '/' && text.charAt(start) != '@')) {
            autocompletePopup.setVisible(false);
            return;
        }

        char trigger = text.charAt(start);
        String prefix = text.substring(start + 1, caret).toLowerCase();

        List<SessionUpdate.AvailableCommand> allCommands = trigger == '/'
            ? ProcessManager.getInstance().getAvailableCommands()
            : java.util.Collections.emptyList(); // Mentions not yet implemented

        List<SessionUpdate.AvailableCommand> filtered = allCommands.stream()
                .filter(c -> c.name().toLowerCase().startsWith(prefix))
                .toList();

        if (filtered.isEmpty()) {
            autocompletePopup.setVisible(false);
            return;
        }

        DefaultListModel<SessionUpdate.AvailableCommand> model = new DefaultListModel<>();
        for (SessionUpdate.AvailableCommand cmd : filtered) {
            model.addElement(cmd);
        }
        commandList.setModel(model);
        commandList.setSelectedIndex(0);

        try {
            java.awt.geom.Rectangle2D rect2d = inputArea.modelToView2D(start);
            java.awt.Rectangle rect = rect2d.getBounds();
            int height = autocompletePopup.getPreferredSize().height;
            // Position above the caret
            autocompletePopup.show(inputArea, rect.x, rect.y - height - 2);
        } catch (Exception ex) {
            autocompletePopup.show(inputArea, 0, 0);
        }

        inputArea.requestFocusInWindow();
    }

    private void selectCommand() {
        SessionUpdate.AvailableCommand selected = commandList.getSelectedValue();
        if (selected != null) {
            String text = inputArea.getText();
            int caret = inputArea.getCaretPosition();
            int start = caret - 1;
            while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
                if (text.charAt(start) == '/' || text.charAt(start) == '@') {
                    break;
                }
                start--;
            }

            if (start >= 0) {
                String before = text.substring(0, start);
                String after = text.substring(caret);
                inputArea.setText(before + "/" + selected.name() + " " + after);
                inputArea.setCaretPosition(before.length() + selected.name().length() + 2);
            }

            autocompletePopup.setVisible(false);
            inputArea.requestFocusInWindow();
        }
    }

    private static record AttachedFile(
        String filename,
        String mimeType,
        String base64Data,
        long size
    ) {
        AttachedFile(File f) throws IOException {
            this(f.getName(), guessMimeType(f.getName()),
                 Base64.getEncoder().encodeToString(Files.readAllBytes(f.toPath())),
                 f.length());
        }

        private static String guessMimeType(String name) {
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
            return switch (ext) {
                case "png" -> "image/png";
                case "jpg", "jpeg" -> "image/jpeg";
                case "gif" -> "image/gif";
                case "svg" -> "image/svg+xml";
                case "webp" -> "image/webp";
                case "pdf" -> "application/pdf";
                case "txt" -> "text/plain";
                case "json" -> "application/json";
                case "py" -> "text/x-python";
                case "java" -> "text/x-java";
                case "md" -> "text/markdown";
                case "xml", "html", "htm" -> "text/html";
                case "yaml", "yml" -> "text/yaml";
                case "toml" -> "text/toml";
                default -> "application/octet-stream";
            };
        }
    }

    private class AutocompleteRenderer extends javax.swing.DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SessionUpdate.AvailableCommand cmd) {
                setText(" /" + cmd.name() + (cmd.description() != null ? "  - " + cmd.description() : ""));
                setFont(ThemeManager.getFont().deriveFont(13f));
                setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            }
            return this;
        }
    }

    private void setupConfigCombo(JComboBox<ConfigItem> combo, String configId) {
        Font btnFont = UIManager.getFont("Button.font");
        if (btnFont != null) {
            combo.setFont(btnFont);
        }
        combo.addActionListener(e -> {
            if (isUpdatingConfigControls) {
                return;
            }
            Object selected = combo.getSelectedItem();
            ConfigItem item = null;
            if (selected instanceof ConfigItem configItem) {
                item = configItem;
            } else if (selected instanceof String val) {
                // Try to find matching item by name or value
                for (int i = 0; i < combo.getItemCount(); i++) {
                    ConfigItem current = combo.getItemAt(i);
                    if (current.value().equalsIgnoreCase(val) || current.name().equalsIgnoreCase(val)) {
                        item = current;
                        combo.setSelectedItem(current);
                        break;
                    }
                }
            }

            if (item != null && SessionManager.getInstance().getCurrentSessionId() != null) {
                String currentId = SessionManager.getInstance().getCurrentSessionId();

                if (combo == modelCombo) {
                    lastSelectedModelId = item.value();
                    updateThinkingComboForModel(item.value());
                    updateTabName(item.name());
                }

                LOG.fine("Config update: {0}={1} for session {2}", new Object[]{configId, item.value(), currentId});
                ProcessManager.getInstance().setSessionConfigOption(currentId, configId, item.value());
            }
        });
    }

    private void updateThinkingComboForModel(String baseId) {
        boolean alreadyUpdating = isUpdatingConfigControls;
        isUpdatingConfigControls = true;
        try {
            thinkingCombo.removeAllItems();
            List<ConfigItem> variants = modelVariants.get(baseId);
            if (variants != null && !variants.isEmpty()) {
                ConfigItem selectedVariant = null;
                for (ConfigItem v : variants) {
                    thinkingCombo.addItem(v);
                    if (v.value().equalsIgnoreCase(currentConfigModelId)) {
                        selectedVariant = v;
                    }
                }
                if (selectedVariant != null) {
                    thinkingCombo.setSelectedItem(selectedVariant);
                } else {
                    thinkingCombo.setSelectedIndex(0);
                }
                thinkingCombo.setEnabled(true);
            } else {
                thinkingCombo.setEnabled(false);
            }
        } finally {
            isUpdatingConfigControls = alreadyUpdating;
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

    private void updateConfigControls(List<SessionConfigOption> options) {
        updateConfigControls(options, false);
    }

    private void applyPreSelectedConfigValues(String sessionId, List<SessionConfigOption> configOptions) {
        // Apply any values that were pre-selected in the config panel before creating the session
        for (SessionConfigOption opt : configOptions) {
            JComboBox<ConfigItem> combo = null;
            if ("mode".equals(opt.category())) {
                combo = modeCombo;
            } else if ("model".equals(opt.category())) {
                combo = modelCombo;
            } else if (opt.category() != null
                    && (opt.category().contains("thinking") || opt.category().contains("thought"))) {
                combo = thinkingCombo;
            }

            if (combo != null && combo.getSelectedItem() instanceof ConfigItem selectedItem) {
                String selectedValue = selectedItem.value();
                String currentValue = opt.currentValue();
                // Only apply if the selected value differs from the server's default
                if (selectedValue != null && !selectedValue.isEmpty() && !selectedValue.equals(currentValue)) {
                    LOG.fine("Applying pre-selected config: {0}={1} (server default was {2})",
                            new Object[]{opt.id(), selectedValue, currentValue});
                    ProcessManager.getInstance().setSessionConfigOption(sessionId, opt.id(), selectedValue);
                }
            }
        }
    }

    private void updateConfigControls(List<SessionConfigOption> options, boolean forceStartupDefaults) {
        SwingUtilities.invokeLater(() -> {
            isUpdatingConfigControls = true;
            try {
                for (SessionConfigOption opt : options) {
                    JComboBox<ConfigItem> combo = resolveComboTarget(opt.category());
                    if (combo == null) continue;

                    if ("model".equals(opt.category())) {
                        parseModelVariants(opt);
                    }

                    combo.removeAllItems();

                    String valueToSelect = resolveStartupValue(opt, isThinkingCategory(opt.category()), opt.currentValue(), forceStartupDefaults);
                    ConfigItem selected = populateComboBox(combo, opt.category(), opt.options(), valueToSelect);

                    if (combo.getActionListeners().length == 0) {
                        setupConfigCombo(combo, opt.id());
                    }

                    if (selected != null) {
                        combo.setSelectedItem(selected);
                    } else if (combo.getItemCount() > 0) {
                        combo.setSelectedIndex(0);
                    }

                    if ("model".equals(opt.category())) {
                        postProcessModel(combo, selected);
                    }
                }
                thinkingCombo.setEnabled(thinkingCombo.getItemCount() > 0);
            } finally {
                isUpdatingConfigControls = false;
            }
        });
    }

    private JComboBox<ConfigItem> resolveComboTarget(String category) {
        if ("mode".equals(category)) return modeCombo;
        if ("model".equals(category)) return modelCombo;
        if (category != null && (category.contains("thinking") || category.contains("thought"))) return thinkingCombo;
        return null;
    }

    private static boolean isThinkingCategory(String category) {
        return category != null && (category.contains("thinking") || category.contains("thought"));
    }

    private void parseModelVariants(SessionConfigOption opt) {
        this.currentConfigModelId = opt.currentValue();
        modelVariants.clear();
        for (SessionConfigSelectOption o : opt.options()) {
            String value = o.value();
            String name = o.name();
            String[] segments = split(value, '/');
            String baseId;
            String variantName;
            if (segments.length >= 3) {
                baseId = String.join("/", Arrays.copyOfRange(segments, 0, segments.length - 1));
                variantName = segments[segments.length - 1];
            } else {
                baseId = value;
                variantName = "default";
            }
            String displayName = name;
            int parenIdx = displayName.lastIndexOf("(");
            if (parenIdx > 0 && displayName.endsWith(")")) {
                displayName = displayName.substring(0, parenIdx).trim();
            }
            modelVariants.computeIfAbsent(baseId, k -> new ArrayList<>())
                        .add(new ConfigItem(variantName, value, displayName));
        }
    }

    private String resolveStartupValue(SessionConfigOption opt, boolean isThinking, String currentValue, boolean force) {
        if (!force) return currentValue;
        String currentId = SessionManager.getInstance().getCurrentSessionId();

        if ("mode".equals(opt.category())) {
            if (opt.options().stream().anyMatch(o -> "build".equalsIgnoreCase(o.value()))) {
                return sendAndReturn(opt, "build", currentId);
            }
            if (opt.options().stream().anyMatch(o -> "plan".equalsIgnoreCase(o.value()))) {
                return sendAndReturn(opt, "plan", currentId);
            }
        }

        if (isThinking) {
            if (opt.options().stream().anyMatch(o -> "default".equalsIgnoreCase(o.value()))) {
                return sendAndReturn(opt, "default", currentId);
            }
        }

        if ("model".equals(opt.category())) {
            String envModel = System.getenv("OPENCODE_MODEL");
            if (envModel != null && !envModel.isEmpty() && currentId != null) {
                String match = findModelMatch(opt, envModel);
                if (match != null) {
                    LOG.fine("Using OPENCODE_MODEL: {0}", new Object[]{match});
                    ProcessManager.getInstance().setSessionConfigOption(currentId, opt.id(), match);
                    return match;
                }
            }
            if (lastSelectedModelId != null && !lastSelectedModelId.equalsIgnoreCase(currentValue)) {
                ProcessManager.getInstance().setSessionConfigOption(currentId, opt.id(), lastSelectedModelId);
                return lastSelectedModelId;
            }
        }

        return currentValue;
    }

    private String sendAndReturn(SessionConfigOption opt, String forcedValue, String currentId) {
        if (!forcedValue.equalsIgnoreCase(opt.currentValue()) && currentId != null) {
            LOG.fine("Forcing default: {0}={1} (was {2})", new Object[]{opt.id(), forcedValue, opt.currentValue()});
            ProcessManager.getInstance().setSessionConfigOption(currentId, opt.id(), forcedValue);
            return forcedValue;
        }
        return opt.currentValue();
    }

    private String findModelMatch(SessionConfigOption opt, String envModel) {
        for (SessionConfigSelectOption o : opt.options()) {
            if (o.value().equalsIgnoreCase(envModel)) {
                return o.value();
            }
        }
        for (Map.Entry<String, List<ConfigItem>> entry : modelVariants.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(envModel)) {
                return entry.getValue().get(0).value();
            }
        }
        return null;
    }

    private ConfigItem populateComboBox(JComboBox<ConfigItem> combo, String category, List<SessionConfigSelectOption> options, String valueToSelect) {
        ConfigItem selected = null;
        if ("model".equals(category)) {
            for (Map.Entry<String, List<ConfigItem>> entry : modelVariants.entrySet()) {
                List<ConfigItem> variants = entry.getValue();
                ConfigItem baseItem = variants.get(0);
                ConfigItem item = new ConfigItem(baseItem.baseName(), entry.getKey());
                combo.addItem(item);
                for (ConfigItem v : variants) {
                    if (v.value().equalsIgnoreCase(valueToSelect)) {
                        selected = item;
                        break;
                    }
                }
            }
        } else {
            for (SessionConfigSelectOption o : options) {
                ConfigItem item = new ConfigItem(o.name(), o.value());
                combo.addItem(item);
                if (o.value() != null && valueToSelect != null && o.value().equalsIgnoreCase(valueToSelect)) {
                    selected = item;
                }
            }
        }
        return selected;
    }

    private void postProcessModel(JComboBox<ConfigItem> combo, ConfigItem selected) {
        combo.setEditable(false);
        updateTabName(selected != null ? selected.name() : null);
        ConfigItem selItem = (ConfigItem) combo.getSelectedItem();
        if (selItem != null) {
            updateThinkingComboForModel(selItem.value());
        }
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
                effectivePath = ProcessManager.getInstance().getActiveProjectDir();
            }
            if (effectivePath == null || effectivePath.isEmpty()) {
                effectivePath = System.getProperty("user.dir");
            }

            if (effectivePath == null || effectivePath.isEmpty()) {
                cwdLabel.setText("");
                cwdLabel.setToolTipText(null);
            } else {
                java.awt.FontMetrics fm = cwdLabel.getFontMetrics(cwdLabel.getFont());
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
        SessionManager.getInstance().addSessionListener(this);
        try {
            ProcessManager.getInstance().ensureStarted();
        } catch (Exception ex) {
            LOG.severe("Failed to ensure server is started", ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            chatPanel.addMessage("error", "Failed to start: " + msg);
        }
        SwingUtilities.invokeLater(() -> {
            if (inputArea != null) {
                inputArea.requestFocusInWindow();
            }
            SessionManager.getInstance().refreshSessions();
        });
    }

    @Override
    public void componentActivated() {
        instance = this;
        SwingUtilities.invokeLater(() -> {
            if (inputArea != null) {
                inputArea.requestFocusInWindow();
            }
        });
    }

    @Override
    public void componentDeactivated() {
        super.componentDeactivated();
        if (thinkingTimer != null && thinkingTimer.isRunning()) {
            thinkingTimer.stop();
        }
    }

    @Override
    public void componentClosed() {
        if (pageKeyDispatcher != null) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pageKeyDispatcher);
        }
        if (thinkingTimer != null && thinkingTimer.isRunning()) {
            thinkingTimer.stop();
        }
        if (statusResetTimer != null && statusResetTimer.isRunning()) {
            statusResetTimer.stop();
        }
        if (chatPanel != null) {
            chatPanel.clearMessages();
        }
        SessionManager.getInstance().removeSessionListener(this);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (pageKeyDispatcher != null) {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pageKeyDispatcher);
        }
        if (statusResetTimer != null && statusResetTimer.isRunning()) {
            statusResetTimer.stop();
        }
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "2.0");
    }

    void readProperties(java.util.Properties p) {
    }

    public static synchronized AssistantTopComponent findInstance() {
        TopComponent tc = org.openide.windows.WindowManager.getDefault().findTopComponent("AssistantTopComponent");
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
        cwdLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.bubbleBorder(), 1),
                new EmptyBorder(4, 8, 4, 8)));

        versionLabel.setForeground(theme.base1());

        sessionDropdown.setBackground(theme.background());
        sessionDropdown.setForeground(theme.foreground());

        inputArea.setBackground(theme.background());
        inputArea.setForeground(theme.foreground());
        inputArea.setCaretColor(theme.foreground());

        inputScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.bubbleBorder()));

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
            requestActive(); // Bring attention to the chat
        });
    }

}
