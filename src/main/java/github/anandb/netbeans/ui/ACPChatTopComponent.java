package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
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
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.ACPManager;
import github.anandb.netbeans.manager.SessionTitleManager;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionConfigSelectOption;
import github.anandb.netbeans.model.SessionUpdate;

@NbBundle.Messages({
        "CTL_ACPChatAction=ACP",
        "CTL_ACPChatTopComponent=ACP",
        "HINT_ACPChatTopComponent=This is an ACP window"
})
@ConvertAsProperties(dtd = "-//github.anandb.netbeans.ui//ACPChat//EN", autostore = false)
@TopComponent.Description(preferredID = "ACPChatTopComponent", iconBase = "github/anandb/netbeans/ui/logo.png", persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "explorer", openAtStartup = false)
public final class ACPChatTopComponent extends TopComponent implements ACPManager.PermissionHandler {

    @ActionID(category = "Window", id = "github.anandb.netbeans.ui.ACPToggleAction")
    @ActionRegistration(displayName = "#CTL_ACPChatAction")
    @ActionReferences({
            @ActionReference(path = "Menu/Window"),
            @ActionReference(path = "Shortcuts", name = "C-L")
    })
    public static final class ACPToggleAction implements ActionListener {
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
            .getLogger(ACPChatTopComponent.class.getName());
    private static ACPChatTopComponent instance;
    private static final long serialVersionUID = 1L;

    private final ChatThreadPanel chatPanel;
    private final JTextArea inputArea;
    private JPopupMenu autocompletePopup;
    private JList<SessionUpdate.AvailableCommand> commandList;
    private JScrollPane commandScroll;
    private final JLabel statusLabel;
    private final JButton sendBtn;
    private final JButton stopBtn;
    private final JPanel header;
    private final JScrollPane inputScrollPane;

    private final JComboBox<SessionItem> sessionDropdown;
    private boolean isSwitchingSessionDropdown = false;
    private boolean isUpdatingConfigControls = false;
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
    private final Consumer<SessionUpdate> sseListener;
    
    private boolean isSessionLoading = false;
    
    // Test message state
    private static boolean testMessageSent = false;
    
    // Message history
    private final List<String> messageHistory = new java.util.ArrayList<>();
    private int historyIndex = -1;
    private String currentDraft = "";

    public ACPChatTopComponent() {
        instance = this;
        LOG.info("Initializing ACPChatTopComponent...");
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
        setName(NbBundle.getMessage(ACPChatTopComponent.class, "CTL_ACPChatTopComponent"));
        setToolTipText(NbBundle.getMessage(ACPChatTopComponent.class, "HINT_ACPChatTopComponent"));
        setLayout(new BorderLayout());
        setOpaque(true);

        Color base3 = theme.getBackground();
        setBackground(base3);

        header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 10, 10, 10)); // Reduced margins
        header.setOpaque(true);
        header.setBackground(base3);

        chatPanel = new ChatThreadPanel();

        cwdLabel = new JLabel("");
        cwdLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cwdLabel.setForeground(theme.getForeground());
        cwdLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.getBubbleBorder(), 1),
                new EmptyBorder(4, 8, 4, 8)));
        cwdLabel.setBackground(theme.getBase2());
        cwdLabel.setOpaque(true);

        JButton newSessionBtn = new JButton("+ New Chat");
        newSessionBtn.setFocusPainted(false);
        newSessionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        newSessionBtn.setBackground(theme.getSelection());
        newSessionBtn.setForeground(Color.WHITE);
        newSessionBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        newSessionBtn.addActionListener(e -> createNewSession());

        sessionDropdown = new JComboBox<>();
        sessionDropdown.setFocusable(false);
        sessionDropdown.setToolTipText("Select Session");
        sessionDropdown.setPreferredSize(new Dimension(200, 28));

        ActionListener sessionDropdownListener = e -> {
            LOG.log(Level.INFO, "sessionDropdown: action fired, switching={0}, item={1}",
                    new Object[] { isSwitchingSessionDropdown, sessionDropdown.getSelectedItem() });
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

        JButton renameSessionBtn = new JButton("Rename");
        renameSessionBtn.setFocusPainted(false);
        renameSessionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        renameSessionBtn.setBackground(theme.getBase2());
        renameSessionBtn.setForeground(theme.getForeground());
        renameSessionBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        renameSessionBtn.addActionListener(e -> renameCurrentSession());

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsPanel.setOpaque(false);
        buttonsPanel.add(renameSessionBtn);
        buttonsPanel.add(newSessionBtn);

        JPanel actionPanel = new JPanel(new BorderLayout(0, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(sessionDropdown, BorderLayout.CENTER);
        actionPanel.add(buttonsPanel, BorderLayout.EAST);

        JPanel headerContent = new JPanel(new GridLayout(0, 1, 0, 18));
        headerContent.setOpaque(false);
        headerContent.add(cwdLabel);
        headerContent.add(actionPanel);
        
        // Block control buttons
        JPanel blockControls = new JPanel(new BorderLayout());
        blockControls.setOpaque(false);
        blockControls.setBorder(new EmptyBorder(4, 0, 0, 0));
        
        JButton expandAllBtn = new JButton("Expand All");
        expandAllBtn.setFocusPainted(false);
        expandAllBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        expandAllBtn.setBackground(theme.getSelection());
        expandAllBtn.setForeground(Color.WHITE);
        expandAllBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        expandAllBtn.addActionListener(e -> chatPanel.toggleAllBlocks(true));
        
        JButton collapseAllBtn = new JButton("Collapse All");
        collapseAllBtn.setFocusPainted(false);
        collapseAllBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        collapseAllBtn.setBackground(theme.getSelection());
        collapseAllBtn.setForeground(Color.WHITE);
        collapseAllBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        collapseAllBtn.addActionListener(e -> chatPanel.toggleAllBlocks(false));
        
        JButton exportBtn = new JButton("Export Markdown");
        exportBtn.setFocusPainted(false);
        exportBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportBtn.setBackground(theme.getSelection());
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        exportBtn.addActionListener(e -> exportConversation());
        
        JButton themeToggleBtn = new JButton(theme.isDark() ? "☀️ Light" : "🌙 Dark");
        themeToggleBtn.setFocusPainted(false);
        themeToggleBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        themeToggleBtn.setBackground(theme.getSelection());
        themeToggleBtn.setForeground(Color.WHITE);
        themeToggleBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        themeToggleBtn.addActionListener(e -> toggleDarkMode());

        JPanel blockControlsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        blockControlsRight.setOpaque(false);
        blockControlsRight.add(expandAllBtn);
        blockControlsRight.add(collapseAllBtn);
        blockControlsRight.add(exportBtn);

        blockControls.add(themeToggleBtn, BorderLayout.WEST);
        blockControls.add(blockControlsRight, BorderLayout.EAST);
        headerContent.add(blockControls);

        header.add(headerContent, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        // Initial CWD
        String initialDir = ACPManager.getInstance().getActiveProjectDir();
        updateCwdLabel(initialDir != null ? initialDir : System.getProperty("user.dir"));
        ACPManager.getInstance().addProjectChangeListener(this::updateCwdLabel);

        // Chat History (Center)
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
        inputArea.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        inputArea.setBackground(Color.WHITE);
        inputArea.setForeground(Color.decode("#073642")); // Solarized Base02 (Darker than current foreground)
        inputArea.setMargin(new Insets(8, 8, 8, 8));

        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        theme.getBubbleBorder() != null && theme.getBubbleBorder().getAlpha() > 0
                                ? theme.getBubbleBorder()
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
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
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
        commandScroll.setPreferredSize(new Dimension(800, 250));
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
                    } else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                        int index = commandList.getSelectedIndex();
                        int size = commandList.getModel().getSize();
                        int next = Math.min(size - 1, index + 10);
                        if (next >= 0) {
                            commandList.setSelectedIndex(next);
                            commandList.ensureIndexIsVisible(next);
                        }
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                        int index = commandList.getSelectedIndex();
                        int next = Math.max(0, index - 10);
                        if (index >= 0) {
                            commandList.setSelectedIndex(next);
                            commandList.ensureIndexIsVisible(next);
                        }
                        e.consume();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    navigateHistory(-1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    navigateHistory(1);
                    e.consume();
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

        inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setBorder(null);
        inputScrollPane.setOpaque(false);
        inputScrollPane.getViewport().setOpaque(false);
        inputWrapper.add(inputScrollPane, BorderLayout.CENTER);

        sendBtn = new JButton("Go");
        sendBtn.setFocusPainted(false);
        sendBtn.setBackground(theme.getSelection());
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendBtn.setPreferredSize(new Dimension(80, 0));
        sendBtn.addActionListener(e -> sendMessage());

        stopBtn = new JButton("Stop");
        stopBtn.setFocusPainted(false);
        stopBtn.setBackground(Color.decode("#DC322F")); // Solarized Red for stop
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
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
        toggleOptionsBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
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
        modeCombo.setPreferredSize(new Dimension(300, 28));
        modelCombo = new JComboBox<>();
        modelCombo.setPreferredSize(new Dimension(300, 28));
        thinkingCombo = new JComboBox<>();
        thinkingCombo.setPreferredSize(new Dimension(300, 28));        

        // Options will be populated via updateConfigControls when session is
        // created/loaded

        configPanel.add(modeCombo);
        configPanel.add(modelCombo);
        configPanel.add(thinkingCombo);

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setForeground(Color.GRAY);
        inputContainer.add(statusLabel, BorderLayout.SOUTH);

        footer.add(inputContainer, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // Initially disable input
        setInputEnabled(false);

        // Register for SSE events
        sseListener = (SessionUpdate sseUpdate) -> {
            SessionUpdate.UpdateData update = sseUpdate.update();
            if (update == null) {
                LOG.log(Level.INFO, "Received SSE update with null data");
                return;
            }

            String type = update.type();

            if ("session_info_update".equals(type)) {
                SwingUtilities.invokeLater(() -> refreshSessions(currentSessionId, false));
                return;
            }

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

            final String msgId = update.messageId();

            // Handle full message object if present
            if (update.message() != null) {
                String text = (update.message().prompt() != null) ? update.message().prompt().text()
                        : (update.message().completion() != null) ? update.message().completion().text() : null;
                if (text != null && !text.isEmpty()) {
                    final String mRole = role;
                    final String mText = text;
                    SwingUtilities.invokeLater(() -> {
                        chatPanel.addMessage(mRole, mText, msgId);
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

                text = extractText(content);
                if (text != null && !text.isEmpty()) {
                    if ("agent_thought_chunk".equals(type)) {
                        final String thoughtText = text;
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Thinking...");
                            chatPanel.appendOrAddMessage("thought", thoughtText, msgId);
                        });
                    } else if ("tool_call".equals(type) || "tool_call_update".equals(type)) {
                        String status = update.status();
                        String title = update.title();
                        String toolText = text;
                        if (toolText == null || toolText.isEmpty()) {
                            if (status != null && !status.isEmpty()) {
                                String displayTitle = (title != null) ? title : type;
                                toolText = "Tool: " + displayTitle + " (" + status + ")";
                            } else {
                                toolText = extractText(update.rawOutput());
                            }
                        }
                        
                        if (toolText != null && !toolText.isEmpty()) {
                            final String finalToolText = toolText;
                            SwingUtilities.invokeLater(() -> {
                                chatPanel.appendOrAddMessage("tool", finalToolText, msgId);
                            });
                        }
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
                                chatPanel.appendOrAddMessage(finalRole, finalText, msgId);
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
                                        outText = "```\n" + outText;
                                    }

                                    boolean chunkEndsWithContent = outText.endsWith("</content>");
                                    if (chunkEndsWithContent) {
                                        outText += "\n```";
                                    }

                                    if (chunkStartsWithPath) {
                                        chatPanel.addMessage(finalRole, outText, msgId);
                                    } else {
                                        chatPanel.appendOrAddMessage(finalRole, outText, msgId);
                                    }

                                    if (chunkEndsWithContent) {
                                        // End of path based resource
                                    }
                                } else {
                                    String outText = finalText.replaceAll("<!--\\s*\\{.*?\\}\\s*-->", "").trim();
                                    if (!outText.isEmpty()) {
                                        chatPanel.addMessage(finalRole, outText, msgId);
                                    }
                                }
                            } else {
                                chatPanel.appendOrAddMessage(finalRole, finalText, msgId);
                            }
                        });
                    }
                }
            }

            // End of turn signals
            if ("usage_update".equals(type) ||
                    ("completion_chunk".equals(type) && update.content() == null) ||
                    "thought_finished".equals(type) ||
                    "responding_finished".equals(type) ||
                    "end_turn".equals(type)) {
                SwingUtilities.invokeLater(() -> {
                    resetStatus();
                    chatPanel.collapseLastThought();
                });
            }
        };

        ACPManager.getInstance().addSseListener(sseListener);
        ACPManager.getInstance().setPermissionHandler(this);

        ACPManager.getInstance().addProjectChangeListener(path -> {
            if (path != null) {
                SwingUtilities.invokeLater(() -> {
                    // Only automatically clear if the project has actually changed
                    if (lastProjectDir != null && !lastProjectDir.equals(path)) {
                        LOG.log(Level.INFO, "Project changed from {0} to {1}, creating new session",
                                new Object[] { lastProjectDir, path });
                        createNewSession();
                        statusLabel.setText("Context switched: " + new java.io.File(path).getName());
                    }
                    lastProjectDir = path;
                });
            }
        });

        initChat();
        refreshTheme();
    }

    private void initChat() {
        refreshSessions(null, true);
    }

    private void refreshSessions(String autoselectId, boolean loadMessages) {
        statusLabel.setText("Connecting...");
        ACPManager manager = ACPManager.getInstance();
        LOG.log(Level.INFO, "refreshSessions: initialized={0}", manager.isInitialized());
        manager.whenReady()
                .thenCompose(v -> manager.getSessions())
                .thenAccept(sessions -> {
                    LOG.log(Level.INFO, "refreshSessions: received {0} sessions, before sorting", sessions.size());
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
                            LOG.log(Level.INFO, "refreshSessions: adding {0} sessions to dropdown", sortedSessions.size());
                            int selectIdx = -1;
                            for (int i = 0; i < sortedSessions.size(); i++) {
                                Session s = sortedSessions.get(i);
                                String customTitle = github.anandb.netbeans.manager.SessionTitleManager.getTitle(s.id(), s.title());
                                sessionDropdown.addItem(new SessionItem(s, customTitle));
                                if (autoselectId != null && s.id().equals(autoselectId)) {
                                    selectIdx = i;
                                }
                            }

                            if (!sortedSessions.isEmpty()) {
                                if (selectIdx == -1) selectIdx = 0;
                                String selectedId = sortedSessions.get(selectIdx).id();
                                currentSessionId = selectedId;
                                sessionDropdown.setSelectedIndex(selectIdx);
                                if (loadMessages) {
                                    loadSession(selectedId, true);
                                }
                            } else {
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
                    LOG.log(Level.SEVERE, "Failed to refresh sessions", ex);
                    return null;
                });
    }

    private void loadSession(String sessionId) {
        loadSession(sessionId, false);
    }

    private void loadSession(String sessionId, boolean isStartup) {
        this.currentSessionId = sessionId;
        this.isSessionLoading = true;
        statusLabel.setText("Loading chat...");
        LOG.log(Level.INFO, "loadSession: clearing and calling loadSession for {0}", sessionId);

        chatPanel.clearMessages();

        // Use active project directory as working directory
        String projectCwd = ACPManager.getInstance().getActiveProjectDir();

        ACPManager.getInstance().getSessions().thenAccept(sessions -> {
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

            LOG.log(Level.INFO, "loadSession: projectCwd={0}, sessionCwd={1}, using={2}",
                    new Object[] { projectCwd, sessionCwd, workingCwd });
            updateCwdLabel(workingCwd);
            this.lastProjectDir = workingCwd;
            final String targetSessionId = sessionId;
            ACPManager.getInstance().loadSession(sessionId, workingCwd)
                    .thenAccept(configOptions -> {
                        SwingUtilities.invokeLater(() -> {
                            // Verify we are still on the same session
                            if (!targetSessionId.equals(this.currentSessionId)) {
                                return;
                            }
                            this.isSessionLoading = false;
                            statusLabel.setText("Ready");
                            if (configOptions != null) {
                                updateConfigControls(configOptions, isStartup);
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
                            chatPanel.scrollToBottom();
                        });
                    })
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            this.isSessionLoading = false;
                            statusLabel.setText("Error loading session: " + ex.getMessage());
                            chatPanel.addMessage("error", "Failed to load session: " + ex.getMessage());
                        });
                        return null;
                    });
        });
    }

    private void renameCurrentSession() {
        if (currentSessionId == null) {
            return;
        }

        SessionItem selectedItem = (SessionItem) sessionDropdown.getSelectedItem();
        if (selectedItem == null) {
            return;
        }

        String currentTitle = selectedIdToTitle(selectedItem.getSession());
        String newTitle = javax.swing.JOptionPane.showInputDialog(this, "Enter new title for this session:", currentTitle);
        
        if (newTitle != null && !newTitle.trim().isEmpty()) {
            SessionTitleManager.setTitle(currentSessionId, newTitle.trim());
            // Refresh the session list in the dropdown
            refreshSessions(currentSessionId, false);
        }
    }

    private String selectedIdToTitle(Session session) {
        String title = session.title();
        if (title == null || title.isEmpty()) {
            title = "Chat " + session.id().substring(0, Math.min(8, session.id().length()));
        }
        return SessionTitleManager.getTitle(session.id(), title);
    }

    private void createNewSession() {
        LOG.info("Attempting to create new session...");
        updateTabName(null);
        statusLabel.setText("Creating new session...");
        ACPManager.getInstance().createSession(null)
                .thenAccept(session -> {
                    this.currentSessionId = session.id();
                    String sessCwd = session.cwd() != null ? session.cwd() : session.directory();
                    this.lastProjectDir = sessCwd;
                    LOG.log(Level.INFO, "New session created: {0}, CWD: {1}",
                            new Object[] { currentSessionId, lastProjectDir });
                    final String targetSessionId = session.id();
                    SwingUtilities.invokeLater(() -> {
                        if (!targetSessionId.equals(this.currentSessionId)) {
                            return;
                        }
                        chatPanel.clearMessages();
                        statusLabel.setText("Session created: " + currentSessionId);
                        updateCwdLabel(sessCwd);
                        if (session.configOptions() != null) {
                            updateConfigControls(session.configOptions(), true);
                        }


                        setInputEnabled(true);

                        // Sync both dropdown and sidebar list
                        isSwitchingSessionDropdown = true;
                        try {
                            SessionItem newItem = new SessionItem(session, session.title());

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
        String text = inputArea.getText().trim();
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

        if (currentSessionId == null) {
            statusLabel.setText("Error: No active session.");
            return;
        }

        inputArea.setText("");
        statusLabel.setText("Sending");
        updateButtonState(true);

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

    private void stopMessage() {
        if (currentSessionId == null) {
            return;
        }
        statusLabel.setText("Stopping...");
        ACPManager.getInstance().stopMessage(currentSessionId)
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
        List<SessionUpdate.AvailableCommand> allCommands = ACPManager.getInstance().getAvailableCommands();
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
                    if (current.name.equalsIgnoreCase(val) || current.value.equalsIgnoreCase(val)) {
                        item = current;
                        combo.setSelectedItem(current);
                        break;
                    }
                }
            }

            if (item != null && currentSessionId != null && !item.isInternalUpdate) {
                LOG.log(Level.INFO, "Config changed: {0}={1} for session {2}", new Object[]{configId, item.value, currentSessionId});
                ACPManager.getInstance().setSessionConfigOption(currentSessionId, configId, item.value);
                if (combo == modelCombo) {
                    updateTabName(item.name);
                }
            }
        });
    }

    private void updateTabName(String modelName) {
        SwingUtilities.invokeLater(() -> {
            if (modelName != null && !modelName.isEmpty()) {
                setName(modelName);
            } else {
                setName(NbBundle.getMessage(ACPChatTopComponent.class, "CTL_ACPChatTopComponent"));
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
        updateConfigControls(options, false);
    }

    private void updateConfigControls(List<SessionConfigOption> options, boolean forceStartupDefaults) {
        SwingUtilities.invokeLater(() -> {
            isUpdatingConfigControls = true;
            try {
                String defaultModel = NbPreferences.forModule(ACPOptionsPanel.class)
                        .get("defaultModel", "acp/big-pickle");
                LOG.log(Level.INFO, "updateConfigControls: force={0}, defaultModel={1}", new Object[]{forceStartupDefaults, defaultModel});

                for (SessionConfigOption opt : options) {
                    JComboBox<ConfigItem> combo = null;
                    boolean isThinking = false;
                    if ("mode".equals(opt.category())) {
                        combo = modeCombo;
                    } else if ("model".equals(opt.category())) {
                        combo = modelCombo;
                    } else if (opt.category() != null
                            && (opt.category().contains("thinking") || opt.category().contains("thought"))) {
                        combo = thinkingCombo;
                        isThinking = true;
                    }

                    if (combo != null) {
                        combo.removeAllItems();
                        ConfigItem selected = null;

                        String valueToSelect = opt.currentValue();
                        if (forceStartupDefaults) {
                            String forcedValue = null;
                            if ("mode".equals(opt.category())) {
                                if (opt.options().stream().anyMatch(o -> "plan".equalsIgnoreCase(o.value()))) {
                                    forcedValue = "plan";
                                }
                            } else if ("model".equals(opt.category())) {
                                if (opt.options().stream().anyMatch(o -> defaultModel.equalsIgnoreCase(o.value()))) {
                                    forcedValue = defaultModel;
                                }
                            } else if (isThinking) {
                                if (opt.options().stream().anyMatch(o -> "default".equalsIgnoreCase(o.value()))) {
                                    forcedValue = "default";
                                }
                            }

                            if (forcedValue != null && !forcedValue.equalsIgnoreCase(opt.currentValue()) && currentSessionId != null) {
                                LOG.log(Level.INFO, "Forcing default: {0}={1} (was {2})", new Object[]{opt.id(), forcedValue, opt.currentValue()});
                                valueToSelect = forcedValue;
                                ACPManager.getInstance().setSessionConfigOption(currentSessionId, opt.id(), forcedValue);
                            }
                        }

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
                            if (o.value() != null && valueToSelect != null && o.value().equalsIgnoreCase(valueToSelect)) {
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
                        } else if (combo.getItemCount() > 0) {
                            // Fallback to first if nothing selected
                            combo.setSelectedIndex(0);
                        }

                        if ("model".equals(opt.category())) {
                            combo.setEditable(true);
                            updateTabName(selected != null ? selected.name : null);
                        }
                        if (opt.category() != null
                                && (opt.category().contains("thinking") || opt.category().contains("thought"))) {
                            combo.setEditable(true);
                        }
                    }
                }
            } finally {
                isUpdatingConfigControls = false;
            }
        });
    }

    private static class ConfigItem {
        final String name;
        final String value;
        boolean isInternalUpdate = false;

        ConfigItem(String name, String value) {
            this.name = name;
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
            if (!testMessageSent) {
                checkAndSendTestMessage();
            }
        });
    }

    private void checkAndSendTestMessage() {
        ACPManager.getInstance().whenReady().thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
                if (testMessageSent) {
                    return;
                }
                
                boolean pingEnabled = org.openide.util.NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("pingAtStartup", false);
                if (!pingEnabled) {
                    testMessageSent = true; // Don't ask again
                    return;
                }
                
                // Wait until we have a session (either loaded or click-to-start state)
                // and make sure it's not currently loading historical messages
                if (currentSessionId == null || isSessionLoading) {
                    // Try again in a bit
                    Timer t = new Timer(1000, e -> checkAndSendTestMessage());
                    t.setRepeats(false);
                    t.start();
                    return;
                }
                
                testMessageSent = true;
                inputArea.setText("Say 'ok' if you can hear me");
                sendMessage();
            });
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
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }

    public static synchronized ACPChatTopComponent findInstance() {
        if (instance == null) {
            instance = new ACPChatTopComponent();
        }
        return instance;
    }

    private void toggleDarkMode() {
        ThemeManager.Theme current = ThemeManager.getCurrentTheme();
        ThemeManager.setDarkMode(!current.isDark());
        refreshTheme();
    }

    private void refreshTheme() {
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
        
        setBackground(theme.getBackground());
        header.setBackground(theme.getBackground());
        
        cwdLabel.setForeground(theme.getForeground());
        cwdLabel.setBackground(theme.getBase2());
        cwdLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.getBubbleBorder(), 1),
                new EmptyBorder(4, 8, 4, 8)));
        
        sessionDropdown.setBackground(theme.getBackground());
        sessionDropdown.setForeground(theme.getForeground());
        
        // Update all buttons in header
        Component[] comps = header.getComponents();
        updateButtonsRecursive(header, theme);
        
        inputArea.setBackground(theme.getBackground());
        inputArea.setForeground(theme.getForeground());
        inputArea.setCaretColor(theme.getForeground());
        
        inputScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.getBubbleBorder()));
        
        chatPanel.refreshTheme();
        
        revalidate();
        repaint();
    }

    private void updateButtonsRecursive(Component container, ThemeManager.Theme theme) {
        if (container instanceof JButton btn) {
            if (btn.getBackground().equals(Color.WHITE) || btn.getForeground().equals(Color.WHITE)) {
                // Primary buttons (white on blue)
                btn.setBackground(theme.getSelection());
                btn.setForeground(Color.WHITE);
            } else {
                // Secondary buttons
                btn.setBackground(theme.getBase2());
                btn.setForeground(theme.getForeground());
            }
            if (btn.getText().contains("Light") || btn.getText().contains("Dark")) {
                btn.setText(theme.isDark() ? "☀️ Light" : "🌙 Dark");
            }
        } else if (container instanceof java.awt.Container cont) {
            for (Component c : cont.getComponents()) {
                updateButtonsRecursive(c, theme);
            }
        }
    }

    private void refreshAllSessionsList() {
        ACPManager manager = ACPManager.getInstance();
        manager.getSessions().thenAccept(sessions -> {
            List<github.anandb.netbeans.model.Session> sortedSessions = sessions.stream()
                    .sorted((s1, s2) -> Long.compare(parseTimestamp(s2.updatedAt()), parseTimestamp(s1.updatedAt())))
                    .toList();
            SwingUtilities.invokeLater(() -> {
                chatPanel.setSessionList(sortedSessions, this::loadSession, this::createNewSession);
            });
        });
    }

    @Override
    public void handlePermissionRequest(String sessionId, JsonNode params, java.util.concurrent.CompletableFuture<String> response) {
        if (this.currentSessionId == null || !this.currentSessionId.equals(sessionId)) {
            LOG.log(Level.FINE, "Received permission request for session {0}, but current is {1}",
                    new Object[] { sessionId, this.currentSessionId });
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
        private final String customTitle;

        public SessionItem(Session session) {
            this(session, null);
        }

        public SessionItem(Session session, String customTitle) {
            this.session = session;
            this.customTitle = customTitle;
        }

        public Session getSession() {
            return session;
        }

        @Override
        public String toString() {
            if (customTitle != null && !customTitle.trim().isEmpty()) {
                return customTitle;
            }
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

    public void setInputText(String text) {
        SwingUtilities.invokeLater(() -> {
            inputArea.setText(text);
            inputArea.requestFocusInWindow();
        });
    }

    private void navigateHistory(int delta) {
        if (messageHistory.isEmpty()) {
            return;
        }

        if (historyIndex == -1) {
            currentDraft = inputArea.getText();
            historyIndex = messageHistory.size();
        }

        int newIndex = historyIndex + delta;
        if (newIndex >= 0 && newIndex <= messageHistory.size()) {
            historyIndex = newIndex;
            if (historyIndex == messageHistory.size()) {
                inputArea.setText(currentDraft);
            } else {
                inputArea.setText(messageHistory.get(historyIndex));
            }
            // Move caret to end
            inputArea.setCaretPosition(inputArea.getText().length());
        }
    }

    private void exportConversation() {
        String markdown = chatPanel.getConversationAsMarkdown();
        if (markdown == null || markdown.trim().isEmpty()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Conversation as Markdown");
            fileChooser.setFileFilter(new FileNameExtensionFilter("Markdown File (*.md)", "md"));

            // Suggest a filename
            String baseName = "conversation";
            if (currentSessionId != null) {
                baseName = "chat-" + currentSessionId.substring(0, Math.min(8, currentSessionId.length()));
            }
            fileChooser.setSelectedFile(new File(baseName + ".md"));

            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                if (!fileToSave.getName().toLowerCase().endsWith(".md")) {
                    fileToSave = new File(fileToSave.getAbsolutePath() + ".md");
                }

                try (FileWriter writer = new FileWriter(fileToSave)) {
                    writer.write(markdown);
                    statusLabel.setText("Exported to " + fileToSave.getName());
                    LOG.log(Level.INFO, "Conversation exported to: {0}", fileToSave.getAbsolutePath());
                    // Reset status after a few seconds
                    Timer timer = new Timer(3000, e -> resetStatus());
                    timer.setRepeats(false);
                    timer.start();
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Failed to export conversation", ex);
                    javax.swing.JOptionPane.showMessageDialog(this,
                            "Error saving file: " + ex.getMessage(),
                            "Export Error",
                            javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        });
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
