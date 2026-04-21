package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;

import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.settings.ConvertAsProperties;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import github.anandb.netbeans.model.Message;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;
import org.netbeans.api.project.ui.OpenProjects;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.ACPManager;
import github.anandb.netbeans.manager.SessionManager;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.model.SessionUpdate;

@ConvertAsProperties(
        dtd = "-//github.anandb.netbeans.ui//Assistant//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "AssistantTopComponent",
        iconBase = "github/anandb/netbeans/ui/ai.svg",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = true)
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
public final class AssistantTopComponent extends TopComponent implements ACPManager.PermissionHandler, SessionManager.SessionListener {

    private static final Logger LOG = Logger.getLogger(AssistantTopComponent.class.getName());
    private static AssistantTopComponent instance;

    private final ChatThreadPanel chatPanel;
    private final JTextArea inputArea;
    private final JButton sendBtn;
    private final JButton stopBtn;
    private final JComboBox<SessionItem> sessionDropdown;
    private final JButton newSessionBtn;
    private final JButton renameSessionBtn;
    private final JButton exportBtn;
    private final JButton restartServerBtn;
    private final JButton toggleBlocksBtn;
    private final JButton toggleOptionsBtn;
    private final JPanel configPanel;
    private final JComboBox<ConfigItem> modeCombo;
    private final JComboBox<ConfigItem> modelCombo;
    private final JComboBox<ConfigItem> thinkingCombo;
    private final JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel cwdLabel;
    private final JScrollPane inputScrollPane;
    private final JPanel header;
    private final JPopupMenu autocompletePopup;
    private final JList<SessionUpdate.AvailableCommand> commandList;
    private final List<String> messageHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String currentDraft = "";
    private final List<ConfigItem> allModels = new ArrayList<>();
    private static String lastSelectedModelId;

    private Consumer<SessionUpdate> sseListener;
    private boolean isSwitchingSessionDropdown = false;
    private boolean isUpdatingConfigControls = false;
    private javax.swing.Timer thinkingTimer;
    private int thinkingDots = 0;

    private final Map<String, List<ConfigItem>> modelVariants = new LinkedHashMap<>();
    private String currentConfigModelId = null;

    public AssistantTopComponent() {
        instance = this;
        initComponents();
        setName(NbBundle.getMessage(AssistantTopComponent.class, "CTL_AssistantTopComponent"));
        setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_AssistantTopComponent"));

        setLayout(new BorderLayout());
        chatPanel = new ChatThreadPanel();

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
        gbc.gridx = 3; gbc.weightx = 0.4;
        configPanel.add(modelCombo, gbc);

        gbc.gridx = 4; gbc.weightx = 0;
        configPanel.add(new JLabel("Thinking:"), gbc);
        gbc.gridx = 5; gbc.weightx = 0.4;
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

        newSessionBtn = new JButton();
        newSessionBtn.setIcon(ThemeManager.getIcon("new.png", 28));
        newSessionBtn.setToolTipText("New Session");
        newSessionBtn.setFocusPainted(false);
        newSessionBtn.addActionListener(e -> SessionManager.getInstance().createNewSession(null));

        renameSessionBtn = new JButton();
        renameSessionBtn.setIcon(ThemeManager.getIcon("rename.png", 28));
        renameSessionBtn.setToolTipText("Rename Session");
        renameSessionBtn.setFocusPainted(false);
        renameSessionBtn.addActionListener(e -> renameCurrentSession());

        exportBtn = new JButton();
        exportBtn.setIcon(ThemeManager.getIcon("export.png", 28));
        exportBtn.setToolTipText("Export Conversation");
        exportBtn.setFocusPainted(false);
        exportBtn.addActionListener(e -> exportConversation());

        restartServerBtn = new JButton();
        restartServerBtn.setIcon(ThemeManager.getIcon("restart.png", 28));
        restartServerBtn.setToolTipText("Restart Server");
        restartServerBtn.setFocusPainted(false);
        restartServerBtn.addActionListener(e -> promptRestartServer());
        
        // Block control buttons
        toggleBlocksBtn = new JButton();
        toggleBlocksBtn.setIcon(ThemeManager.getIcon("expand.png", 28));
        toggleBlocksBtn.setToolTipText("Expand All Blocks");
        toggleBlocksBtn.setFocusPainted(false);
        toggleBlocksBtn.putClientProperty("state", "expand");
        toggleBlocksBtn.addActionListener(e -> {
            boolean isCollapse = "collapse".equals(toggleBlocksBtn.getClientProperty("state"));
            chatPanel.toggleAllBlocks(!isCollapse);
            String newState = isCollapse ? "expand" : "collapse";
            toggleBlocksBtn.putClientProperty("state", newState);
            toggleBlocksBtn.setToolTipText(isCollapse ? "Expand All Blocks" : "Collapse All Blocks");
            toggleBlocksBtn.setIcon(ThemeManager.getIcon(isCollapse ? "expand.png" : "collapse.png", 28));
        });

        sessionControls.add(newSessionBtn);
        sessionControls.add(renameSessionBtn);
        sessionControls.add(toggleBlocksBtn);
        sessionControls.add(exportBtn);
        sessionControls.add(restartServerBtn);

        topBar.add(sessionDropdown, BorderLayout.CENTER);
        topBar.add(sessionControls, BorderLayout.EAST);

        cwdLabel = new JLabel("");
        cwdLabel.setFont(cwdLabel.getFont().deriveFont(Font.BOLD));
        cwdLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cwdLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String path = cwdLabel.getToolTipText();
                if (path != null && !path.isEmpty()) {
                    File f = new File(path);
                    if (f.exists()) {
                        try {
                            java.awt.Desktop.getDesktop().open(f);
                        } catch (IOException ex) {
                            LOG.log(Level.WARNING, "Failed to open directory", ex);
                        }
                    }
                }
            }
        });

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

        toggleOptionsBtn = new JButton();
        toggleOptionsBtn.setIcon(ThemeManager.getIcon("settings.svg", 16));
        toggleOptionsBtn.setToolTipText("Options");
        toggleOptionsBtn.setBorderPainted(false);
        toggleOptionsBtn.setContentAreaFilled(false);
        toggleOptionsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleOptionsBtn.addActionListener(e -> {
            configPanel.setVisible(!configPanel.isVisible());
            AssistantTopComponent.this.revalidate();
            AssistantTopComponent.this.repaint();
        });

        statusPanel.add(toggleOptionsBtn, BorderLayout.EAST);

        bottomPanel.add(statusPanel, BorderLayout.NORTH);

        inputArea = new JTextArea();
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(12, 12, 12, 12));
        inputArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));

        inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setPreferredSize(new Dimension(100, 100));

        JPanel inputMainPanel = new JPanel(new BorderLayout(0, 4));
        inputMainPanel.setOpaque(false);
        inputMainPanel.add(configPanel, BorderLayout.NORTH);
        inputMainPanel.add(inputScrollPane, BorderLayout.CENTER);

        JPanel btnCard = new JPanel(new CardLayout());
        btnCard.setOpaque(false);
        sendBtn = new JButton("Go");
        sendBtn.setFocusPainted(false);
        sendBtn.addActionListener(e -> sendMessage());

        stopBtn = new JButton("Stop");
        stopBtn.setFocusPainted(false);
        stopBtn.addActionListener(e -> stopMessage());

        btnCard.add(sendBtn, "SEND");
        btnCard.add(stopBtn, "STOP");

        versionLabel = new JLabel("v" + getVersion());
        versionLabel.setFont(versionLabel.getFont().deriveFont(9f));
        versionLabel.setForeground(Color.GRAY);
        versionLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

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
        commandList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        commandList.setFocusable(false);
        commandList.setCellRenderer(new AutocompleteRenderer());
        
        JScrollPane scrollPane = new JScrollPane(commandList);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(250, 150));
        autocompletePopup.add(scrollPane);

        setupListeners();

        // Register for SSE events
        this.sseListener = update -> {
            String type = update.update() != null ? update.update().type() : null;
            String msgId = update.update() != null ? update.update().messageId() : null;

            if ("agent_message_chunk".equals(type) || "agent_thought_chunk".equals(type) || "user_message_chunk".equals(type)) {
                JsonNode content = update.update().content();
                
                // Filter out tool outputs which are marked for the assistant's eyes only
                boolean skip = false;
                if ("user_message_chunk".equals(type) && content != null && content.has("annotations")) {
                    JsonNode annotations = content.get("annotations");
                    if (annotations.has("audience") && annotations.get("audience").isArray()) {
                        for (JsonNode aud : annotations.get("audience")) {
                            if ("assistant".equals(aud.asText())) {
                                skip = true;
                                break;
                            }
                        }
                    }
                }
                
                if (!skip) {
                    String text = extractText(content);
                    if (text != null && !text.isEmpty()) {
                        String role = "agent_message_chunk".equals(type) ? "assistant" : 
                                      "agent_thought_chunk".equals(type) ? "thought" : "user";
                        SwingUtilities.invokeLater(() -> {
                            chatPanel.appendOrAddMessage(role, text, msgId);
                            updateButtonState(true);
                        });
                    }
                }
            } else if ("message".equals(type)) {
                Message msg = update.update().message();
                LOG.fine("Received message update: id=" + (msg != null ? msg.id() : "null") + ", type=" + (msg != null ? msg.type() : "null"));
                if (msg != null) {
                    SwingUtilities.invokeLater(() -> chatPanel.addMessage(msg));
                }
            } else if ("tool_call_update".equals(type)) {
                SwingUtilities.invokeLater(() -> {
                    chatPanel.updateToolCall(update.update());
                    updateButtonState(true);
                });
            } else if ("config_options_update".equals(type)) {
                if (update.update().configOptions() != null) {
                    updateConfigControls(update.update().configOptions());
                }
            } else if ("session_info_update".equals(type)) {
                SessionManager.getInstance().refreshSessions();
            } else if ("usage_update".equals(type)) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setToolTipText("Usage: " + update.update().used() + "/" + update.update().size() + " tokens");
                });
            }

            // Status updates
            if (msgId != null) {
                Boolean isThinking = update.isThinking();
                if (isThinking != null) {
                    SwingUtilities.invokeLater(() -> {
                        if (isThinking) {
                            statusLabel.setText("Thinking...");
                        } else {
                            // Always reset when isThinking is explicitly false, regardless of current status text
                            if ("Thinking...".equals(statusLabel.getText()) || statusLabel.getText().startsWith("Thinking")) {
                                statusLabel.setText("Responding...");
                            }
                        }
                    });
                }
            }

            // End of turn signals
            if ("responding_finished".equals(type) ||
                    "end_turn".equals(type) ||
                    "usage_update".equals(type)) {
                SwingUtilities.invokeLater(() -> {
                    resetStatus();
                    chatPanel.stopStreaming();
                });
            }
        };

        ACPManager.getInstance().setPermissionHandler(this);

        initChat();
        refreshTheme();
    }



    private void setupListeners() {
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
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

        commandList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
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
                LOG.log(Level.FINE, "onSessionListUpdated: adding {0} sessions to dropdown", sessions.size());
                int selectIdx = -1;
                for (int i = 0; i < sessions.size(); i++) {
                    Session s = sessions.get(i);
                    String customTitle = github.anandb.netbeans.manager.SessionTitleManager.getTitle(s.id(), s.title());
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
                            LOG.log(Level.FINE, "Loading most recent session: {0}", mostRecent.id());
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
                    setInputEnabled(false);
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
                updateTabName((String) null);
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
        setInputEnabled(!isLoading);
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
        if (text.isEmpty()) {
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
        boolean localEcho = NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("echoUserInput", true);
        if (localEcho) {
            chatPanel.addMessage("user", text);
        }

        Map<String, Object> context = captureEditorContext();
        ACPManager.getInstance().sendMessage(currentSessionId, text, context)
                .thenAccept(v -> {
                    SwingUtilities.invokeLater(() -> {
                        String currentStatus = statusLabel.getText();
                        if (currentStatus != null && currentStatus.startsWith("Sending")) {
                            statusLabel.setText("Ready");
                            updateButtonState(false);
                            inputArea.requestFocusInWindow();
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
        SwingUtilities.invokeLater(() -> {
            inputArea.setText(text);
            inputArea.requestFocusInWindow();
        });
    }

    private void stopMessage() {
        String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
        if (currentSessionId == null) {
            return;
        }
        statusLabel.setText("Stopping...");
        ACPManager.getInstance().stopMessage(currentSessionId)
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
        String title = StringUtils.defaultIfBlank(session.title(), "Chat " + StringUtils.left(session.id(), 8));
        return github.anandb.netbeans.manager.SessionTitleManager.getTitle(session.id(), title);
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
                LOG.log(Level.FINE, "Conversation exported to {0}", file.getAbsolutePath());
            } catch (java.io.IOException ex) {
                LOG.log(Level.SEVERE, "Failed to export conversation", ex);
            }
        }
    }

    private void promptRestartServer() {
        int choice = javax.swing.JOptionPane.showConfirmDialog(this,
                "Are you sure you want to restart the ACP server?\nThis will terminate current operations and relaunch the server.",
                "Restart Server",
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
        ACPManager.getInstance().restartServer();

        // Wait for server to be ready and reload session
        ACPManager.getInstance().whenReady().thenAccept(v -> {
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
                statusLabel.setText("Restart failed: " + ex.getMessage());
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
                if (thinkingTimer == null) {
                    thinkingTimer = new javax.swing.Timer(500, e -> {
                        if (statusLabel != null && statusLabel.getText() != null) {
                            String txt = statusLabel.getText();
                            if (txt.startsWith("Thinking") || txt.startsWith("Responding")
                                    || txt.startsWith("Sending")) {
                                String base = txt.replace(".", "");
                                thinkingDots = (thinkingDots + 1) % 4;
                                String dots = "";
                                for (int i = 0; i < thinkingDots; i++) {
                                    dots += ".";
                                }
                                statusLabel.setText(base + dots);
                            }
                        }
                    });
                }
                thinkingTimer.start();
            } else {
                if (thinkingTimer != null) {
                    thinkingTimer.stop();
                }
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
        if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
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
            ? ACPManager.getInstance().getAvailableCommands()
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
                if (text.charAt(start) == '/' || text.charAt(start) == '@') break;
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

    private class AutocompleteRenderer extends javax.swing.DefaultListCellRenderer {
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
                    if (current.name.equalsIgnoreCase(val) || current.value.equalsIgnoreCase(val) 
                            || truncateConfigName(current.name, 25).equalsIgnoreCase(val)) {
                        item = current;
                        combo.setSelectedItem(current);
                        break;
                    }
                }
            }

            if (item != null && SessionManager.getInstance().getCurrentSessionId() != null && !item.isInternalUpdate) {
                String currentId = SessionManager.getInstance().getCurrentSessionId();

                if (combo == modelCombo) {
                    lastSelectedModelId = item.value;
                    updateThinkingComboForModel(item.value);
                    updateTabName(item.name);
                }

                LOG.log(Level.FINE, "Config update: {0}={1} for session {2}", new Object[]{configId, item.value, currentId});
                ACPManager.getInstance().setSessionConfigOption(currentId, configId, item.value);
            } else if (item != null && item.isInternalUpdate) {
                LOG.log(Level.FINE, "Skipping internal config update: {0}={1}", new Object[]{configId, item.value});
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
                    if (v.value.equalsIgnoreCase(currentConfigModelId)) {
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
                String selectedValue = selectedItem.value;
                String currentValue = opt.currentValue();
                // Only apply if the selected value differs from the server's default
                if (selectedValue != null && !selectedValue.isEmpty() && !selectedValue.equals(currentValue)) {
                    LOG.log(Level.FINE, "Applying pre-selected config: {0}={1} (server default was {2})",
                            new Object[]{opt.id(), selectedValue, currentValue});
                    ACPManager.getInstance().setSessionConfigOption(sessionId, opt.id(), selectedValue);
                }
            }
        }
    }

    private void setupSearchableCombo(JComboBox<ConfigItem> combo, List<ConfigItem> allItems) {
        combo.setEditable(true);
        combo.setRenderer(new ConfigItemRenderer(25));
        javax.swing.text.JTextComponent editor = (javax.swing.text.JTextComponent) combo.getEditor().getEditorComponent();

        editor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                Object selected = combo.getSelectedItem();
                if (selected instanceof ConfigItem item) {
                    editor.setText(item.name);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                Object selected = combo.getSelectedItem();
                if (selected instanceof ConfigItem item) {
                    editor.setText(truncateConfigName(item.name, 25));
                }
            }
        });
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_UP 
                        || e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    return;
                }
                String text = editor.getText();
                if (text == null) text = "";
                
                final String filterText = text.toLowerCase();
                final String finalOriginalText = text;
                List<ConfigItem> filtered = allItems.stream()
                        .filter(item -> item.name.toLowerCase().contains(filterText) 
                                || item.value.toLowerCase().contains(filterText))
                        .collect(java.util.stream.Collectors.toList());
                
                SwingUtilities.invokeLater(() -> {
                    isUpdatingConfigControls = true;
                    try {
                        combo.removeAllItems();
                        for (ConfigItem item : filtered) {
                            combo.addItem(item);
                        }
                        editor.setText(filterText.isEmpty() ? "" : finalOriginalText);
                        if (!filtered.isEmpty() && combo.isShowing()) {
                            combo.showPopup();
                        }
                    } finally {
                        isUpdatingConfigControls = false;
                    }
                });
            }
        });
    }

    private void updateConfigControls(List<SessionConfigOption> options, boolean forceStartupDefaults) {
        SwingUtilities.invokeLater(() -> {
            isUpdatingConfigControls = true;
            try {
                LOG.log(Level.FINE, "updateConfigControls: force={0}", new Object[]{forceStartupDefaults});

                for (SessionConfigOption opt : options) {
                    JComboBox<ConfigItem> combo = null;
                    boolean isThinkingCategory = false;
                    if ("mode".equals(opt.category())) {
                        combo = modeCombo;
                    } else if ("model".equals(opt.category())) {
                        combo = modelCombo;
                        this.currentConfigModelId = opt.currentValue();

                        // Parse variants from model list
                        modelVariants.clear();
                        for (SessionConfigSelectOption o : opt.options()) {
                            String value = o.value();
                            String name = o.name();

                            String[] segments = value.split("/");
                            String baseId;
                            String variantName;

                            if (segments.length >= 3) {
                                // last part is variant
                                baseId = String.join("/", Arrays.copyOfRange(segments, 0, segments.length - 1));
                                variantName = segments[segments.length - 1];
                            } else {
                                // provider/model or other
                                baseId = value;
                                variantName = "default";
                            }

                            String displayName = name;
                            if (displayName.contains("(") && displayName.endsWith(")")) {
                                displayName = displayName.substring(0, displayName.lastIndexOf("(")).trim();
                            }

                            modelVariants.computeIfAbsent(baseId, k -> new ArrayList<>())
                                        .add(new ConfigItem(variantName, value, displayName));
                        }
                    } else if (opt.category() != null
                            && (opt.category().contains("thinking") || opt.category().contains("thought"))) {
                        combo = thinkingCombo;
                        isThinkingCategory = true;
                    }

                    if (combo != null) {
                        combo.removeAllItems();
                        ConfigItem selected = null;

                        String valueToSelect = opt.currentValue();
                        if (forceStartupDefaults) {
                            String forcedValue = null;
                            if ("mode".equals(opt.category())) {
                                if (opt.options().stream().anyMatch(o -> "build".equalsIgnoreCase(o.value()))) {
                                    forcedValue = "build";
                                } else if (opt.options().stream().anyMatch(o -> "plan".equalsIgnoreCase(o.value()))) {
                                    forcedValue = "plan";
                                }
                            } else if (isThinkingCategory) {
                                if (opt.options().stream().anyMatch(o -> "default".equalsIgnoreCase(o.value()))) {
                                    forcedValue = "default";
                                }
                            }

                            if (forcedValue != null && !forcedValue.equalsIgnoreCase(opt.currentValue()) && SessionManager.getInstance().getCurrentSessionId() != null) {
                                String currentId = SessionManager.getInstance().getCurrentSessionId();
                                LOG.log(Level.FINE, "Forcing default: {0}={1} (was {2})", new Object[]{opt.id(), forcedValue, opt.currentValue()});
                                valueToSelect = forcedValue;
                                ACPManager.getInstance().setSessionConfigOption(currentId, opt.id(), forcedValue);
                            } else if ("model".equals(opt.category()) && lastSelectedModelId != null && !lastSelectedModelId.equalsIgnoreCase(opt.currentValue())) {
                                String currentId = SessionManager.getInstance().getCurrentSessionId();
                                valueToSelect = lastSelectedModelId;
                                ACPManager.getInstance().setSessionConfigOption(currentId, opt.id(), lastSelectedModelId);
                            }
                        }

                        if ("model".equals(opt.category())) {
                            allModels.clear();
                            for (Map.Entry<String, List<ConfigItem>> entry : modelVariants.entrySet()) {
                                List<ConfigItem> variants = entry.getValue();
                                ConfigItem baseItem = variants.get(0);
                                ConfigItem item = new ConfigItem(baseItem.baseName, entry.getKey());
                                combo.addItem(item);
                                allModels.add(item);

                                // Check if current model ID belongs to this base
                                for (ConfigItem v : variants) {
                                    if (v.value.equalsIgnoreCase(valueToSelect)) {
                                        selected = item;
                                        break;
                                    }
                                }
                            }
                            setupSearchableCombo(combo, allModels);
                        } else {
                            for (SessionConfigSelectOption o : opt.options()) {
                                ConfigItem item = new ConfigItem(o.name(), o.value());
                                combo.addItem(item);
                                if (o.value() != null && valueToSelect != null && o.value().equalsIgnoreCase(valueToSelect)) {
                                    selected = item;
                                }
                            }
                        }

                        // Initialize the listener if not already done
                        if (combo.getActionListeners().length == 0) {
                            setupConfigCombo(combo, opt.id());
                        }

                        if (selected != null) {
                            selected.isInternalUpdate = true;
                            combo.setSelectedItem(selected);
                            selected.isInternalUpdate = false;
                        } else if (combo.getItemCount() > 0) {
                            // Fallback to first if nothing selected
                            combo.setSelectedIndex(0);
                        }

                        if ("model".equals(opt.category())) {
                            combo.setEditable(true);
                            updateTabName(selected != null ? selected.name : null);
                            updateThinkingComboForModel(((ConfigItem)combo.getSelectedItem()).value);
                        }
                    }
                }
                thinkingCombo.setEnabled(thinkingCombo.getItemCount() > 0);
            } finally {
                isUpdatingConfigControls = false;
            }
        });
    }

    private static class ConfigItem {
        final String name;
        final String value;
        final String baseName;
        boolean isInternalUpdate = false;

        ConfigItem(String name, String value) {
            this(name, value, name);
        }

        ConfigItem(String name, String value, String baseName) {
            this.name = name;
            this.value = value;
            this.baseName = baseName;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    
    private String truncateConfigName(String name, int max) {
        if (name == null || name.length() <= max) {
            return name;
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash != -1) {
            String suffix = name.substring(lastSlash);
            if (suffix.length() + 3 <= max) {
                return "..." + suffix;
            }
        }
        return name.substring(0, max - 3) + "...";
    }

    private class ConfigItemRenderer extends javax.swing.DefaultListCellRenderer {
        private final int maxLength;

        ConfigItemRenderer(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ConfigItem item) {
                String name = item.name;
                if (index == -1) {
                    setText(truncateConfigName(name, maxLength));
                } else {
                    setText(name);
                }
                setToolTipText(name);
            }
            return this;
        }
    }


    private void updateCwdLabel(String path) {
        SwingUtilities.invokeLater(() -> {
            String effectivePath = path;

            if (effectivePath == null || effectivePath.isEmpty()) {
                effectivePath = SessionManager.getInstance().getCurrentSessionDirectory();
            }
            if (effectivePath == null || effectivePath.isEmpty()) {
                effectivePath = ACPManager.getInstance().getActiveProjectDir();
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
        SessionManager.getInstance().addSessionListener(this);
        try {
            ACPManager.getInstance().ensureStarted();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to ensure server is started", ex);
        }
        if (sseListener != null) {
            ACPManager.getInstance().removeSseListener(sseListener);
            ACPManager.getInstance().addSseListener(sseListener);
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
        SwingUtilities.invokeLater(() -> {
            if (inputArea != null) {
                inputArea.requestFocusInWindow();
            }
        });
    }

    @Override
    public void componentClosed() {
        if (sseListener != null) {
            ACPManager.getInstance().removeSseListener(sseListener);
        }
        if (thinkingTimer != null && thinkingTimer.isRunning()) {
            thinkingTimer.stop();
        }
        if (chatPanel != null) {
            chatPanel.clearMessages();
        }
        SessionManager.getInstance().removeSessionListener(this);
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "2.0");
    }

    void readProperties(java.util.Properties p) {
    }

    public static synchronized AssistantTopComponent findInstance() {
        TopComponent tc = org.openide.windows.WindowManager.getDefault().findTopComponent("AssistantTopComponent");
        if (tc instanceof AssistantTopComponent) {
            return (AssistantTopComponent) tc;
        }
        if (instance == null) {
            instance = new AssistantTopComponent();
        }
        return instance;
    }

    private void refreshTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        setBackground(theme.getBackground());
        header.setBackground(theme.getBackground());

        cwdLabel.setForeground(theme.getForeground());
        cwdLabel.setOpaque(true);
        cwdLabel.setBackground(theme.getSunkenBackground());
        cwdLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.getBubbleBorder(), 1),
                new EmptyBorder(4, 8, 4, 8)));

        versionLabel.setForeground(theme.getBase1());

        sessionDropdown.setBackground(theme.getBackground());
        sessionDropdown.setForeground(theme.getForeground());

        inputArea.setBackground(theme.getBackground());
        inputArea.setForeground(theme.getForeground());
        inputArea.setCaretColor(theme.getForeground());

        inputScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.getBubbleBorder()));

        chatPanel.refreshTheme();

        revalidate();
        repaint();
    }

    @Override
    public void handlePermissionRequest(String sessionId, JsonNode params, java.util.concurrent.CompletableFuture<String> response) {
        String currentId = SessionManager.getInstance().getCurrentSessionId();
        if (currentId == null || !currentId.equals(sessionId)) {
            LOG.log(Level.FINE, "Received permission request for session {0}, but current is {1}",
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

    private String extractText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.has("text") && node.get("text").isTextual()) {
            return node.get("text").asText();
        }
        if (node.has("content")) {
            return extractText(node.get("content"));
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode child : node) {
                sb.append(extractText(child));
            }
            return sb.toString();
        }
        return "";
    }



    private static class SessionItem {
        private final Session session;
        private final String title;

        public SessionItem(Session session, String title) {
            this.session = session;
            this.title = title;
        }

        public Session getSession() {
            return session;
        }

        @Override
        public String toString() {
            String projectName = session.projectName();
            if (projectName != null && !projectName.isEmpty()) {
                return "[" + projectName + "] " + title;
            }
            return title;
        }
    }

    private String getVersion() {
        try {
            org.openide.modules.ModuleInfo m = org.openide.modules.Modules.getDefault()
                    .findCodeNameBase("github.anandb.acp.netbeans.plugin");
            if (m != null && m.getSpecificationVersion() != null) {
                return m.getSpecificationVersion().toString();
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to get module version", e);
        }
        return "1.2.x";
    }

    private void initComponents() {
    }
}
