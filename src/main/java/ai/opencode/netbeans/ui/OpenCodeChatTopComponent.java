package ai.opencode.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;

import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.settings.ConvertAsProperties;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.fasterxml.jackson.databind.JsonNode;

import ai.opencode.netbeans.manager.OpenCodeManager;
import ai.opencode.netbeans.model.Session;
import ai.opencode.netbeans.model.SessionConfigOption;
import ai.opencode.netbeans.model.SessionConfigSelectOption;
import ai.opencode.netbeans.model.SessionUpdate;

@NbBundle.Messages({
    "CTL_OpenCodeChatAction=OpenCode",
    "CTL_OpenCodeChatTopComponent=OpenCode",
    "HINT_OpenCodeChatTopComponent=This is an OpenCode window"
})
@ConvertAsProperties(dtd = "-//ai.opencode.netbeans.ui//OpenCodeChat//EN", autostore = false)
@TopComponent.Description(preferredID = "OpenCodeChatTopComponent", iconBase = "ai/opencode/netbeans/ui/logo.png", persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "explorer", openAtStartup = false)
public final class OpenCodeChatTopComponent extends TopComponent {

    @ActionID(category = "Window", id = "ai.opencode.netbeans.ui.OpenCodeToggleAction")
    @ActionRegistration(displayName = "#CTL_OpenCodeChatAction")
    @ActionReferences({
        @ActionReference(path = "Menu/Window"),
        @ActionReference(path = "Shortcuts", name = "C-L")
    })
    public static final class OpenCodeToggleAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            TopComponent tc = findInstance();
            if (tc != null) {
                if (tc.isOpened()) {
                    tc.close();
                } else {
                    tc.open();
                    tc.requestActive();
                }
            }
        }
    }
    private static final java.util.logging.Logger LOG = java.util.logging.Logger
            .getLogger(OpenCodeChatTopComponent.class.getName());
    private static OpenCodeChatTopComponent instance;
    private static final long serialVersionUID = 1L;

    private final ChatThreadPanel chatPanel;
    private final JTextArea inputArea;
    private JPopupMenu autocompletePopup;
    private JList<SessionUpdate.AvailableCommand> commandList;
    private JScrollPane commandScroll;
    private final JLabel statusLabel;
    private final JButton sendBtn;
    private final JButton stopBtn;

    private final JComboBox<SessionItem> sessionDropdown;
    private boolean isSwitchingSessionDropdown = false;
    private final JLabel cwdLabel;
    private final JComboBox<ConfigItem> modeCombo;
    private final JComboBox<ConfigItem> modelCombo;
    private final JComboBox<ConfigItem> thinkingCombo;
    private final JPanel configPanel;
    private final JButton toggleOptionsBtn;
    private String currentSessionId;
    private String lastProjectDir;
    private javax.swing.Timer thinkingTimer;
    private int thinkingDots = 0;
    private boolean isReceivingPathBasedResource = false;

    public OpenCodeChatTopComponent() {
        LOG.info("Initializing OpenCodeChatTopComponent...");
        setName(NbBundle.getMessage(OpenCodeChatTopComponent.class, "CTL_OpenCodeChatTopComponent"));
        setToolTipText(NbBundle.getMessage(OpenCodeChatTopComponent.class, "HINT_OpenCodeChatTopComponent"));
        setLayout(new BorderLayout());

        Color labelBg = new Color(245, 245, 245);
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        header.setOpaque(false);

        cwdLabel = new JLabel("");
        cwdLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        cwdLabel.setForeground(Color.DARK_GRAY);
        cwdLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                new EmptyBorder(4, 8, 4, 8)));
        cwdLabel.setBackground(labelBg);
        cwdLabel.setOpaque(true);

        JButton newSessionBtn = new JButton("+ New Chat");
        newSessionBtn.setFocusPainted(false);
        newSessionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        newSessionBtn.addActionListener(e -> createNewSession());

        sessionDropdown = new JComboBox<>();
        sessionDropdown.setFocusable(false);
        sessionDropdown.setToolTipText("Select Session");
        sessionDropdown.setPreferredSize(new Dimension(200, 28));

        ActionListener sessionDropdownListener = e -> {
            LOG.log(Level.INFO, "sessionDropdown: action fired, switching={0}, item={1}", new Object[]{isSwitchingSessionDropdown, sessionDropdown.getSelectedItem()});
            if (isSwitchingSessionDropdown) {
                return;
            }
            SessionItem item = (SessionItem) sessionDropdown.getSelectedItem();
            LOG.log(Level.INFO, "sessionDropdown: selected item={0}", item != null ? item.getSession().id() : "null");
            if (item != null) {
                String selectedId = item.getSession().id();
                if (currentSessionId == null || !currentSessionId.equals(selectedId)) {
                    LOG.log(Level.INFO, "sessionDropdown: calling loadSession for {0}", selectedId);
                    loadSession(selectedId);
                }
            }
        };
        sessionDropdown.addActionListener(sessionDropdownListener);


        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsPanel.setOpaque(false);
        buttonsPanel.add(newSessionBtn);

        JPanel actionPanel = new JPanel(new BorderLayout(0, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(sessionDropdown, BorderLayout.CENTER);
        actionPanel.add(buttonsPanel, BorderLayout.EAST);

        JPanel headerContent = new JPanel(new GridLayout(0, 1, 0, 18));
        headerContent.setOpaque(false);
        headerContent.add(cwdLabel);
        headerContent.add(actionPanel);
        header.add(headerContent, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        // Initial CWD
        String initialDir = OpenCodeManager.getInstance().getActiveProjectDir();
        updateCwdLabel(initialDir != null ? initialDir : System.getProperty("user.dir"));
        OpenCodeManager.getInstance().addProjectChangeListener(this::updateCwdLabel);

        // Chat History (Center)
        chatPanel = new ChatThreadPanel();
        add(chatPanel, BorderLayout.CENTER);

        // Footer / Input Container (Bottom)
        JPanel footer = new JPanel(new BorderLayout(0, 0));
        footer.setBorder(new EmptyBorder(0, 0, 0, 0));
        footer.setOpaque(false);

        // Input Area Wrapper with border and shadow-like padding
        JPanel inputContainer = new JPanel(new BorderLayout(0, 8));
        inputContainer.setBorder(new EmptyBorder(16, 16, 12, 16));
        inputContainer.setOpaque(false);

        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.setOpaque(false);

        inputArea = new JTextArea(3, 20);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        // Explicitly matching the premium look: 14px SansSerif
        inputArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputArea.setMargin(new Insets(10, 10, 10, 10));

        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        theme.getBubbleBorder() != null && theme.getBubbleBorder().getAlpha() > 0 ? theme.getBubbleBorder()
                                : new Color(0, 0, 0, 30),
                        1, true),
                new EmptyBorder(4, 4, 4, 4)));

        autocompletePopup = new JPopupMenu();
        autocompletePopup.setFocusable(false);
        commandList = new JList<>();
        commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandList.setFocusable(false);
        commandList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SessionUpdate.AvailableCommand cmd) {
                    label.setText("/" + cmd.name() + (cmd.description() != null ? " - " + cmd.description() : ""));
                    label.setBorder(new EmptyBorder(4, 8, 4, 8));
                }
                if (isSelected) {
                    label.setBackground(theme.getSelection());
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(Color.WHITE);
                    label.setForeground(Color.BLACK);
                }
                return label;
            }
        });
        
        commandScroll = new JScrollPane(commandList);
        commandScroll.setBorder(null);
        commandScroll.setPreferredSize(new Dimension(600, 200));
        autocompletePopup.add(commandScroll);

        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (autocompletePopup.isVisible()) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        autocompletePopup.setVisible(false);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        int index = commandList.getSelectedIndex();
                        if (index < commandList.getModel().getSize() - 1) {
                            commandList.setSelectedIndex(index + 1);
                            commandList.ensureIndexIsVisible(index + 1);
                        }
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                        int index = commandList.getSelectedIndex();
                        if (index > 0) {
                            commandList.setSelectedIndex(index - 1);
                            commandList.ensureIndexIsVisible(index - 1);
                        }
                        e.consume();
                    }
                }

                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        // Allow default behavior for Shift+Enter (new line)
                        return;
                    }
                    if (autocompletePopup.isVisible()) {
                        selectCommand();
                        e.consume();
                        return;
                    }
                    e.consume();
                    sendMessage();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    if (autocompletePopup.isVisible()) {
                        autocompletePopup.setVisible(false);
                    } else if (stopBtn.isVisible() && stopBtn.isEnabled()) {
                        stopMessage();
                    } else {
                        inputArea.setText("");
                    }
                    e.consume();
                }
            }
        });

        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checkAutocomplete(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checkAutocomplete(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                checkAutocomplete(e);
            }

            private void checkAutocomplete(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        String text = inputArea.getText();
                        int caretPos = inputArea.getCaretPosition();
                        if (caretPos > 0 && text.startsWith("/") && !text.contains(" ")) {
                            showAutocomplete();
                        } else {
                            autocompletePopup.setVisible(false);
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                });
            }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(null);
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputWrapper.add(inputScroll, BorderLayout.CENTER);

        sendBtn = new JButton("Go");
        sendBtn.setFocusPainted(false);
        sendBtn.setPreferredSize(new Dimension(80, 0));
        sendBtn.addActionListener(e -> sendMessage());

        stopBtn = new JButton("Stop");
        stopBtn.setFocusPainted(false);
        stopBtn.setPreferredSize(new Dimension(80, 0));
        stopBtn.addActionListener(e -> stopMessage());

        JPanel buttonPanel = new JPanel(new java.awt.CardLayout());
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(new EmptyBorder(0, 8, 0, 0));
        buttonPanel.add(sendBtn, "SEND");
        buttonPanel.add(stopBtn, "STOP");

        inputWrapper.add(buttonPanel, BorderLayout.EAST);
        inputContainer.add(inputWrapper, BorderLayout.CENTER);

        configPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        configPanel.setBorder(new EmptyBorder(8, 2, 8, 2));
        configPanel.setOpaque(false);
        configPanel.setVisible(false);

        toggleOptionsBtn = new JButton("Options ▼");
        toggleOptionsBtn.setFocusPainted(false);
        toggleOptionsBtn.setBorderPainted(false);
        toggleOptionsBtn.setContentAreaFilled(false);
        toggleOptionsBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggleOptionsBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        toggleOptionsBtn.setHorizontalAlignment(SwingConstants.LEFT);
        toggleOptionsBtn.setForeground(Color.GRAY);
        toggleOptionsBtn.addActionListener(e -> {
            configPanel.setVisible(!configPanel.isVisible());
            updateOptionsButtonText();
            revalidate();
            repaint();
        });

        JPanel optionsHeader = new JPanel(new BorderLayout());
        optionsHeader.setOpaque(false);
        optionsHeader.add(toggleOptionsBtn, BorderLayout.WEST);

        JPanel optionsContainer = new JPanel(new BorderLayout());
        optionsContainer.setOpaque(false);
        optionsContainer.add(optionsHeader, BorderLayout.NORTH);
        optionsContainer.add(configPanel, BorderLayout.CENTER);

        inputContainer.add(optionsContainer, BorderLayout.NORTH);

        modeCombo = new JComboBox<>();
        modelCombo = new JComboBox<>();
        thinkingCombo = new JComboBox<>();

        // Options will be populated via updateConfigControls when session is
        // created/loaded

        configPanel.add(modeCombo);
        configPanel.add(modelCombo);
        configPanel.add(thinkingCombo);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(Color.GRAY);
        inputContainer.add(statusLabel, BorderLayout.SOUTH);

        footer.add(inputContainer, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // Initially disable input
        setInputEnabled(false);

        // Register for SSE events
        OpenCodeManager.getInstance().addSseListener(sseUpdate -> {
            SessionUpdate.UpdateData update = sseUpdate.update();
            if (update == null) {
                LOG.log(Level.INFO, "Received SSE update with null data");
                return;
            }

            String type = update.type();

            // Log for debugging - extremely important to find missing chunks
            LOG.log(Level.INFO, "SSE Event: type={0}, content={1}, hasMessage={2}",
                    new Object[] { type, (update.content() != null), (update.message() != null) });

            // Check for explicit thinking status
            Boolean isThinking = update.isThinking();
            if (isThinking != null) {
                SwingUtilities.invokeLater(() -> {
                    if (isThinking) {
                        statusLabel.setText("Thinking...");
                        updateButtonState(true);
                    } else if (statusLabel.getText().equals("Thinking...")
                            || statusLabel.getText().equals("Responding...")) {
                        resetStatus();
                    }
                });
            }

            // Determine role more robustly
            String role = "assistant";
            if (type != null && (type.contains("user") || type.contains("user_message"))) {
                role = "user";
            } else if (type != null && (type.contains("assistant") || type.contains("completion")
                    || type.contains("agent") || type.contains("bot") || type.contains("ai"))) {
                role = "assistant";
            }

            // Update configuration options if present
            if (update.configOptions() != null) {
                updateConfigControls(update.configOptions());
            }

            // Handle full message object if present
            if (update.message() != null) {
                String text = (update.message().prompt() != null) ? update.message().prompt().text()
                        : (update.message().completion() != null) ? update.message().completion().text() : null;
                if (text != null && !text.isEmpty()) {
                    final String mRole = role;
                    final String mText = text;
                    SwingUtilities.invokeLater(() -> {
                        chatPanel.addMessage(mRole, mText);
                        if ("assistant".equals(mRole)) {
                            resetStatus();
                        }
                    });
                }
            }
            // Handle chunked content if present
            else if (update.content() != null) {
                JsonNode content = update.content();
                String text = null;

                // Skip boolean values (they're metadata, not content)
                if (content.isBoolean()) {
                    return;
                }

                if (content.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode node : content) {
                        if (node.isTextual()) {
                            sb.append(node.asText());
                        } else if (node.has("text")) {
                            sb.append(node.get("text").asText());
                        }
                    }
                    text = sb.toString();
                } else if (content.isObject()) {
                    if (content.has("text")) {
                        text = content.get("text").asText();
                    } else if (content.has("content")) {
                        text = content.get("content").asText();
                    }
                } else if (content.isTextual()) {
                    text = content.asText();
                }

                if (text != null && !text.isEmpty()) {
                    if ("agent_thought_chunk".equals(type)) {
                        final String thoughtText = text;
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Thinking...");
                            chatPanel.appendOrAddMessage("thought", thoughtText);
                        });
                    } else if ("tool_call".equals(type) || "tool_call_update".equals(type)) {
                        final String toolText = text;
                        SwingUtilities.invokeLater(() -> {
                            chatPanel.appendOrAddMessage("tool", toolText);
                        });
                    } else {
                        final String finalText = text;
                        final String finalRole = role;
                        final String finalType = type;
                        SwingUtilities.invokeLater(() -> {
                            // If we get real content, we are definitely Responding (not just thinking)
                            if ("assistant".equals(finalRole)) {
                                LOG.log(Level.INFO, "Found assistant content: {0}", finalText);
                                statusLabel.setText("Responding...");
                                updateButtonState(true);
                                chatPanel.appendOrAddMessage(finalRole, finalText);
                            } else if ("user".equals(finalRole)) {
                                // For user messages, we ONLY append if explicitly chunked (like streaming local
                                // echo)
                                // Standard user message updates should be separate bubbles
                                if ("user_message_chunk".equals(finalType)) {
                                    String outText = finalText;
                                    boolean chunkStartsWithPath = outText.startsWith("<path>");
                                    
                                    // Strip the metadata comment from display
                                    outText = outText.replaceAll("<!--\\s*\\{.*?\\}\\s*-->", "").trim();

                                    if (chunkStartsWithPath) {
                                        isReceivingPathBasedResource = true;
                                        outText = "```\n" + outText;
                                    }

                                    boolean chunkEndsWithContent = outText.endsWith("</content>");
                                    if (chunkEndsWithContent) {
                                        outText += "\n```";
                                    }

                                    if (chunkStartsWithPath) {
                                        chatPanel.addMessage(finalRole, outText);
                                    } else {
                                        chatPanel.appendOrAddMessage(finalRole, outText);
                                    }

                                    if (chunkEndsWithContent) {
                                        isReceivingPathBasedResource = false;
                                    }
                                } else {
                                    String outText = finalText.replaceAll("<!--\\s*\\{.*?\\}\\s*-->", "").trim();
                                    if (!outText.isEmpty()) {
                                        chatPanel.addMessage(finalRole, outText);
                                    }
                                }
                            } else {
                                chatPanel.appendOrAddMessage(finalRole, finalText);
                            }
                        });
                    }
                }
            }

            // End of turn signals
            if ("available_commands_update".equals(type) && update.availableCommands() != null) {
                SwingUtilities.invokeLater(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("💡 **Hint:** Type `/` in the input box to see available commands.\n\n");
                    sb.append("**Available Commands:**\n");
                    for (SessionUpdate.AvailableCommand cmd : update.availableCommands()) {
                        sb.append("- `/").append(cmd.name()).append("`");
                        if (cmd.description() != null && !cmd.description().isEmpty()) {
                            sb.append(" - ").append(cmd.description());
                        }
                        sb.append("\n");
                    }
                    chatPanel.addMessage("system", sb.toString());
                });
            } else if ("usage_update".equals(type) ||
                    ("completion_chunk".equals(type) && update.content() == null) ||
                    "thought_finished".equals(type) ||
                    "responding_finished".equals(type) ||
                    "end_turn".equals(type)) {
                SwingUtilities.invokeLater(this::resetStatus);
            }
        });

        OpenCodeManager.getInstance().addProjectChangeListener(path -> {
            if (path != null) {
                SwingUtilities.invokeLater(() -> {
                    // Only automatically clear if the project has actually changed
                    if (lastProjectDir != null && !lastProjectDir.equals(path)) {
                        LOG.log(Level.INFO, "Project changed from {0} to {1}, creating new session", new Object[]{lastProjectDir, path});
                        createNewSession();
                        statusLabel.setText("Context switched: " + new java.io.File(path).getName());
                    }
                    lastProjectDir = path;
                });
            }
        });

        initChat();
    }

    private void initChat() {
        statusLabel.setText("Connecting...");
        OpenCodeManager manager = OpenCodeManager.getInstance();
        LOG.log(Level.INFO, "initChat: initialized={0}", manager.isInitialized());
        manager.whenReady()
                .thenCompose(v -> manager.getSessions())
                .thenAccept(sessions -> {
                    LOG.log(Level.INFO, "initChat: received {0} sessions, before sorting", sessions.size());
                    for (Session s : sessions) {
                        LOG.log(Level.INFO, "initChat: session id={0}, title=''{1}''", new Object[]{s.id(), s.title()});
                    }
                    LOG.info("initChat: after logging sessions, before sorting");
                    List<Session> sortedSessions = sessions.stream()
                            .sorted((s1, s2) -> {
                                long t1 = parseTimestamp(s1.updatedAt());
                                long t2 = parseTimestamp(s2.updatedAt());
                                return Long.compare(t2, t1);
                            })
                            .toList();

                    SwingUtilities.invokeLater(() -> {
                        isSwitchingSessionDropdown = true;
                        try {
                            sessionDropdown.removeAllItems();
                            LOG.log(Level.INFO, "initChat: adding {0} sessions to dropdown", sortedSessions.size());
                            for (ai.opencode.netbeans.model.Session s : sortedSessions) {
                                sessionDropdown.addItem(new SessionItem(s));
                            }

                            // Auto-select most recent session if available
                            if (!sortedSessions.isEmpty()) {
                                LOG.log(Level.INFO, "initChat: auto-selecting session {0}", sortedSessions.get(0).id());
                                currentSessionId = sortedSessions.get(0).id();
                                sessionDropdown.setSelectedIndex(0);
                                // loadSession will clear the panel and request messages
                                loadSession(currentSessionId);
                            } else {
                                LOG.info("initChat: no sessions, showing welcome state");
                                chatPanel.setSessionList(sortedSessions, this::loadSession, this::createNewSession);
                                sessionDropdown.setSelectedIndex(-1);
                                statusLabel.setText("Click '+ New Chat' to start");
                                setInputEnabled(false);
                            }
                        } finally {
                            isSwitchingSessionDropdown = false;
                        }
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
                    LOG.log(Level.SEVERE, "Failed to initialize chat", ex);
                    return null;
                });
    }

    private void loadSession(String sessionId) {
        this.currentSessionId = sessionId;
        statusLabel.setText("Loading chat...");
        LOG.log(Level.INFO, "loadSession: clearing and calling loadSession for {0}", sessionId);

        chatPanel.clearMessages();

        // Use active project directory as working directory
        String projectCwd = OpenCodeManager.getInstance().getActiveProjectDir();
        
        OpenCodeManager.getInstance().getSessions().thenAccept(sessions -> {
            String sessionCwd = sessions.stream()
                    .filter(s -> s.id().equals(sessionId))
                    .findFirst()
                    .map(s -> s.cwd() != null ? s.cwd() : s.directory())
                    .orElse(null);

            // Priority: projectCwd > sessionCwd > user.dir
            String workingCwd = projectCwd != null ? projectCwd : sessionCwd;
            if (workingCwd == null) {
                workingCwd = System.getProperty("user.dir");
            }
            
            LOG.log(Level.INFO, "loadSession: projectCwd={0}, sessionCwd={1}, using={2}", new Object[]{projectCwd, sessionCwd, workingCwd});
            updateCwdLabel(workingCwd);
            this.lastProjectDir = workingCwd;
            OpenCodeManager.getInstance().loadSession(sessionId, workingCwd)
                .thenAccept(configOptions -> {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Ready");
                        if (configOptions != null) {
                            updateConfigControls(configOptions);
                        }
                        
                        // Set default mode to "plan" if not already set
                        if (modeCombo.getItemCount() > 0 && modeCombo.getSelectedIndex() == -1) {
                            for (int i = 0; i < modeCombo.getItemCount(); i++) {
                                ConfigItem item = modeCombo.getItemAt(i);
                                if ("plan".equals(item.value)) {
                                    modeCombo.setSelectedIndex(i);
                                    OpenCodeManager.getInstance().setSessionConfigOption(sessionId, "mode", "plan");
                                    break;
                                }
                            }
                        }
                        
                        setInputEnabled(true);

                        isSwitchingSessionDropdown = true;
                        try {
                            for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                                SessionItem item = sessionDropdown.getItemAt(i);
                                if (item.getSession().id().equals(sessionId)) {
                                    sessionDropdown.setSelectedIndex(i);
                                    break;
                                }
                            }
                        } finally {
                            isSwitchingSessionDropdown = false;
                        }

                        inputArea.requestFocusInWindow();
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error loading session: " + ex.getMessage());
                        chatPanel.addMessage("error", "Failed to load session: " + ex.getMessage());
                    });
                    return null;
                });
        });
    }

    private void createNewSession() {
        LOG.info("Attempting to create new session...");
        statusLabel.setText("Creating new session...");
        OpenCodeManager.getInstance().createSession(null)
                .thenAccept(session -> {
                    this.currentSessionId = session.id();
                    String sessCwd = session.cwd() != null ? session.cwd() : session.directory();
                    this.lastProjectDir = sessCwd;
                    LOG.log(Level.INFO, "New session created: {0}, CWD: {1}", new Object[]{currentSessionId, lastProjectDir});
                    SwingUtilities.invokeLater(() -> {
                        chatPanel.clearMessages();
                        statusLabel.setText("Session created: " + currentSessionId);
                        updateCwdLabel(sessCwd);
                        if (session.configOptions() != null) {
                            updateConfigControls(session.configOptions());
                        }
                        
                        // Set default mode to "plan" if mode combo is populated
                        if (modeCombo.getItemCount() > 0) {
                            for (int i = 0; i < modeCombo.getItemCount(); i++) {
                                ConfigItem item = modeCombo.getItemAt(i);
                                if ("plan".equals(item.value)) {
                                    modeCombo.setSelectedIndex(i);
                                    OpenCodeManager.getInstance().setSessionConfigOption(currentSessionId, "mode", "plan");
                                    break;
                                }
                            }
                        }
                        
                        setInputEnabled(true);

                        // Sync both dropdown and sidebar list
                        isSwitchingSessionDropdown = true;
                        try {
                            SessionItem newItem = new SessionItem(session);

                            // Check if it's already in the dropdown, if not add it at the top
                            boolean found = false;
                            for (int i = 0; i < sessionDropdown.getItemCount(); i++) {
                                if (sessionDropdown.getItemAt(i).equals(newItem)) {
                                    sessionDropdown.setSelectedIndex(i);
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                sessionDropdown.insertItemAt(newItem, 0);
                                sessionDropdown.setSelectedIndex(0);
                            }

                            // Also refresh the sidebar panel's list of sessions
                            refreshAllSessionsList();
                        } finally {
                            isSwitchingSessionDropdown = false;
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Failed to create session", ex);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Failed to create session: " + ex.getMessage());
                        chatPanel.addMessage("error", "Failed to create session: " + ex.getMessage());
                        setInputEnabled(false);
                    });
                    return null;
                });
    }

    private void sendMessage() {
        String text = inputArea.getText().trim() + "\n";
        if (text.isEmpty()) {
            return;
        }

        if (currentSessionId == null) {
            statusLabel.setText("Error: No active session.");
            return;
        }

        inputArea.setText("");
        statusLabel.setText("Sending");
        updateButtonState(true);

        Map<String, Object> context = captureEditorContext();
        OpenCodeManager.getInstance().sendMessage(currentSessionId, text, context)
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

    private void stopMessage() {
        if (currentSessionId == null) {
            return;
        }
        statusLabel.setText("Stopping...");
        OpenCodeManager.getInstance().stopMessage(currentSessionId)
                .thenAccept(v -> {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Stopped");
                        updateButtonState(false);
                    });
                })
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Stop failed: " + ex.getMessage()));
                    return null;
                });
    }


    private void updateOptionsButtonText() {
        toggleOptionsBtn.setText(configPanel.isVisible() ? "Options ▲" : "Options ▼");
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
                            if (txt.startsWith("Thinking") || txt.startsWith("Responding") || txt.startsWith("Sending")) {
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
            if (currentSessionId != null) {
                setInputEnabled(true);
            }
        });
    }

    private void setInputEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            inputArea.setEnabled(enabled);
            sendBtn.setEnabled(enabled);
            toggleOptionsBtn.setVisible(enabled);
            // Don't force configPanel visibility here, let the toggle handle it
            if (!enabled) {
                inputArea.setBackground(UIManager.getColor("TextArea.background"));
                configPanel.setVisible(false);
                updateOptionsButtonText();
            }
        });
    }

    private boolean textIsEmpty() {
        return inputArea.getText().trim().isEmpty();
    }

    private void showAutocomplete() {
        List<SessionUpdate.AvailableCommand> allCommands = OpenCodeManager.getInstance().getAvailableCommands();
        if (allCommands.isEmpty()) {
            autocompletePopup.setVisible(false);
            return;
        }

        // Filter based on prefix
        String prefix = inputArea.getText().substring(1); // after '/'
        List<SessionUpdate.AvailableCommand> filtered = allCommands.stream()
                .filter(c -> c.name().toLowerCase().startsWith(prefix.toLowerCase()))
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
            java.awt.Rectangle rect = inputArea.modelToView2D(0).getBounds();
            // Show above input area
            int height = autocompletePopup.getPreferredSize().height;
            autocompletePopup.show(inputArea, rect.x, -height - 5); 
        } catch (Exception e) {
            autocompletePopup.show(inputArea, 0, 0);
        }
        
        // Ensure requestFocus stays with inputArea
        SwingUtilities.invokeLater(() -> inputArea.requestFocusInWindow());
    }

    private void selectCommand() {
        SessionUpdate.AvailableCommand selected = commandList.getSelectedValue();
        if (selected != null) {
            inputArea.setText("/" + selected.name() + " ");
            autocompletePopup.setVisible(false);
            inputArea.requestFocusInWindow();
        }
    }

    private void setupConfigCombo(JComboBox<ConfigItem> combo, String configId) {
        Font btnFont = UIManager.getFont("Button.font");
        if (btnFont != null) {
            combo.setFont(btnFont);
        }
        combo.addActionListener(e -> {
            Object selected = combo.getSelectedItem();
            ConfigItem item = null;
            if (selected instanceof ConfigItem configItem) {
                item = configItem;
            } else if (selected instanceof String val) {
                // Try to find matching item by name or value
                for (int i = 0; i < combo.getItemCount(); i++) {
                    ConfigItem current = combo.getItemAt(i);
                    if (current.name.equalsIgnoreCase(val) || current.value.equalsIgnoreCase(val)) {
                        item = current;
                        combo.setSelectedItem(current);
                        break;
                    }
                }
            }

            if (item != null && currentSessionId != null && !item.isInternalUpdate) {
                OpenCodeManager.getInstance().setSessionConfigOption(currentSessionId, configId, item.value);
            }
        });
    }

    private JPanel createLabelledCombo(String labelText, JComboBox<ConfigItem> combo) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        JLabel label = new JLabel(labelText);
        Font btnFont = UIManager.getFont("Button.font");
        if (btnFont != null) {
            label.setFont(btnFont);
            combo.setFont(btnFont);
        }
        p.add(label);
        p.add(combo);
        return p;
    }

    private void updateConfigControls(List<SessionConfigOption> options) {
        SwingUtilities.invokeLater(() -> {
            for (SessionConfigOption opt : options) {
                JComboBox<ConfigItem> combo = null;
                if ("mode".equals(opt.category())) {
                    combo = modeCombo;
                } else if ("model".equals(opt.category())) {
                    combo = modelCombo;
                } else if (opt.category() != null
                        && (opt.category().contains("thinking") || opt.category().contains("thought"))) {
                    combo = thinkingCombo;
                }

                if (combo != null) {
                    combo.removeAllItems();
                    ConfigItem selected = null;
                    for (SessionConfigSelectOption o : opt.options()) {
                        String displayName = o.name();
                        // If model is free, show it
                        if ("model".equals(opt.category())) {
                            String lowerName = (o.name() != null) ? o.name().toLowerCase() : "";
                            String lowerValue = (o.value() != null) ? o.value().toLowerCase() : "";
                            String lowerDesc = o.description() != null ? o.description().toLowerCase() : "";
                            if (lowerName.contains("free") || lowerValue.contains("free")
                                    || lowerDesc.contains("free")) {
                                if (!displayName.toLowerCase().contains("(free)")) {
                                    displayName += " (Free)";
                                }
                            }
                        }
                        ConfigItem item = new ConfigItem(displayName, o.value());
                        combo.addItem(item);
                        if (o.value().equals(opt.currentValue())) {
                            selected = item;
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
                    }

                    if ("model".equals(opt.category())) {
                        combo.setEditable(true);
                    }
                    if (opt.category() != null
                            && (opt.category().contains("thinking") || opt.category().contains("thought"))) {
                        combo.setEditable(true);
                    }
                }
            }
        });
    }

    private static class ConfigItem {
        final String name;
        final String value;
        boolean isInternalUpdate = false;

        ConfigItem(String name, String value) {
            // If name has slashes, display only the text after the last slash
            if (name != null && name.contains("/")) {
                this.name = name.substring(name.lastIndexOf("/") + 1);
            } else {
                this.name = name;
            }
            this.value = value;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private void updateCwdLabel(String path) {
        SwingUtilities.invokeLater(() -> {
            String effectivePath = path;
            if (effectivePath == null || effectivePath.isEmpty()) {
                effectivePath = OpenCodeManager.getInstance().getActiveProjectDir();
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

                if (availableWidth > 20 && fm.stringWidth(effectivePath) > availableWidth) {
                    while (displayPath.length() > 10 && fm.stringWidth("..." + displayPath) > availableWidth) {
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
        SwingUtilities.invokeLater(() -> {
            if (inputArea != null) {
                inputArea.requestFocusInWindow();
            }
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
        if (thinkingTimer != null && thinkingTimer.isRunning()) {
            thinkingTimer.stop();
        }
        if (chatPanel != null) {
            chatPanel.clearMessages();
        }
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }

    public static synchronized OpenCodeChatTopComponent findInstance() {
        if (instance == null) {
            instance = new OpenCodeChatTopComponent();
        }
        return instance;
    }

    private void refreshAllSessionsList() {
        OpenCodeManager manager = OpenCodeManager.getInstance();
        manager.getSessions().thenAccept(sessions -> {
            List<ai.opencode.netbeans.model.Session> sortedSessions = sessions.stream()
                    .sorted((s1, s2) -> Long.compare(parseTimestamp(s2.updatedAt()), parseTimestamp(s1.updatedAt())))
                    .toList();
            SwingUtilities.invokeLater(() -> {
                chatPanel.setSessionList(sortedSessions, this::loadSession, this::createNewSession);
            });
        });
    }

    private static class SessionItem {
        private final Session session;

        public SessionItem(Session session) {
            this.session = session;
        }

        public Session getSession() {
            return session;
        }

        @Override
        public String toString() {
            String title = session.title();
            if (title != null && !title.trim().isEmpty()) {
                return title;
            }
            return session.id();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SessionItem that = (SessionItem) o;
            return session.id().equals(that.session.id());
        }

        @Override
        public int hashCode() {
            return session.id().hashCode();
        }
    }

    private long parseTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) {
            return 0;
        }
        try {
            if (ts.contains("T")) {
                java.time.Instant instant = java.time.Instant.parse(ts);
                return instant.toEpochMilli();
            }
            return Long.parseLong(ts);
        } catch (Exception e) {
            return 0;
        }
    }
}
