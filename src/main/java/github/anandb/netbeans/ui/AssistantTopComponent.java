package github.anandb.netbeans.ui;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.left;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import github.anandb.netbeans.model.AttachedFile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
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
import github.anandb.netbeans.manager.SlashCommandInterceptor;
import github.anandb.netbeans.contract.DataExtractionStrategy;
import github.anandb.netbeans.contract.PermissionHandler;
import github.anandb.netbeans.contract.SessionListener;
import github.anandb.netbeans.contract.SlashCommandCallback;
import github.anandb.netbeans.manager.strategy.StrategyRegistry;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionItem;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
    private JLabel statusLabel;
    private final JLabel versionLabel;
    private final JLabel cwdLabel;
    private final JScrollPane inputScrollPane;
    private final JPanel header;
    private final ArrayList<String> messageHistory = new ArrayList<>();
    private int historyIndex = -1;
    private String currentDraft = "";
    private boolean isSwitchingSessionDropdown = false;
    private Timer thinkingTimer;
    private Timer statusResetTimer;
    private transient KeyEventDispatcher pageKeyDispatcher;
    private int thinkingDots = 0;
    private static final String[] DOT_STRINGS = {"", ".", "..", "..."};

    private static final long MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 2;
    private final ArrayList<AttachedFile> attachedFiles = new ArrayList<>();
    private final JButton paperclipBtn;
    private transient final ConfigPanelController configPanelController;
    private transient final AutocompleteManager autocompleteManager;

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

        configPanelController = new ConfigPanelController(this::updateTabName);

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
            boolean expanded = !chatPanel.isAllBlocksExpanded();
            chatPanel.toggleAllBlocks(expanded);
            String newState = expanded ? "collapse" : "expand";
            tb.putClientProperty("state", newState);
            tb.setToolTipText(expanded ? "Collapse All Blocks" : "Expand All Blocks");
            tb.setIcon(ThemeManager.getIcon(expanded ? "collapse.svg" : "expand.svg", 28));
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
            configPanelController.getComponent().setVisible(!optionsPanelCollapsed);
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

        setupImagePasteHandler();

        inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setPreferredSize(new Dimension(100, 100));

        JPanel inputMainPanel = new JPanel(new BorderLayout(0, 4));
        inputMainPanel.setOpaque(false);
        inputMainPanel.add(configPanelController.getComponent(), BorderLayout.NORTH);
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

        autocompleteManager = new AutocompleteManager(inputArea, this::sendMessage);

        setupListeners();

        initChat();
        applyInitialTheme();
    }

    @Override
    public void onSessionUpdate(SessionUpdate update) {
        String type = update.update() != null ? update.update().type().name() : null;
        String msgId = update.update() != null ? update.update().messageId() : null;
        LOG.fine("UI received session update: type={0}, msgId={1}", type, msgId);

        DataExtractionStrategy strategy = StrategyRegistry.getInstance().select(update);
        if (strategy != null) {
            strategy.extract(update, new UIHandler() {
                @Override
                public void displayMessage(ProcessedMessage msg) {
                    SwingUtilities.invokeLater(() -> {
                        chatPanel.addMessage(msg);
                        updateButtonState(true);
                    });
                }

                @Override
                public void updateConfig(java.util.List<SessionConfigOption> options) {
                    if (options != null) {
                        configPanelController.updateConfigControls(options);
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
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    e.consume();
                    // Trigger same behavior as /agents command
                    SlashCommandInterceptor interceptor = ProcessManager.getInstance().getSlashCommandInterceptor();
                    SlashCommandCallback cb = interceptor != null ? interceptor.getCallback() : null;
                    if (cb != null) {
                        cb.expandOptionsPanel();
                        cb.popupAgentCombo();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (autocompleteManager.handleKeyPressed(e)) {
                        // handled by autocomplete
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
                } else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_Z) {
                    e.consume();
                    inputArea.undo();
                } else if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_Y) {
                    e.consume();
                    inputArea.redo();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                autocompleteManager.handleKeyReleased(e);
            }
        });
    }

    private void setupImagePasteHandler() {
        ImagePasteTransferHandler.PasteCallback callback = new ImagePasteTransferHandler.PasteCallback() {
            @Override
            public boolean canAddAttachment() {
                return attachedFiles.size() < MAX_ATTACHMENTS;
            }

            @Override
            public void onAttachmentAdded(AttachedFile file) {
                SwingUtilities.invokeLater(() -> {
                    if (file.size() > MAX_ATTACHMENT_SIZE) {
                        if (statusLabel != null) {
                            statusLabel.setText("File too large (max 10MB)");
                            statusResetTimer.restart();
                        }
                        return;
                    }
                    attachedFiles.add(file);
                    updatePaperclipTooltip();
                    if (statusLabel != null) {
                        statusLabel.setText("Attached: " + file.filename());
                        statusResetTimer.restart();
                    }
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText(message);
                        statusResetTimer.restart();
                    }
                });
            }

            @Override
            public void onAttachmentLimitReached() {
                SwingUtilities.invokeLater(() -> {
                    if (statusLabel != null) {
                        statusLabel.setText("Max " + MAX_ATTACHMENTS + " files allowed");
                        statusResetTimer.restart();
                    }
                });
            }
        };

        ImagePasteTransferHandler handler = new ImagePasteTransferHandler(callback);
        inputArea.setTransferHandler(handler);
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

                configPanelController.ensureDefaultModelSelected();

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
                    configPanelController.getComponent().setVisible(true);
                    toggleOptionsBtn.setIcon(ThemeManager.getIcon("arrow-down.svg", 25));
                    setInputEnabled(false);
                    configPanelController.ensureDefaultModelAdded();
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
                configPanelController.updateConfigControls(configOptions, isStartup);
            }
            // If this is a new session (isStartup=true), apply any pre-selected config values
            // from the config panel that the user may have set before creating the chat
            if (isStartup) {
                configPanelController.applyPreSelectedConfigValues(sessionId, configOptions);
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
            chatPanel.stopStreaming();
            chatPanel.addMessage(ProcessedMessage.createError(MessageType.error_response, message, null, null));
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

        // Intercept local slash commands first
        boolean isForwardedSlash = text.trim().startsWith("/");
        if (isForwardedSlash) {
            SlashCommandInterceptor interceptor = ProcessManager.getInstance().getSlashCommandInterceptor();
            if (interceptor != null) {
                CompletableFuture<Boolean> handled = interceptor.intercept(text, null);
                if (handled != null && handled.isDone() && !handled.isCompletedExceptionally()) {
                    Boolean result = handled.join();
                    if (Boolean.TRUE.equals(result)) {
                        inputArea.setText("");
                        return;
                    }
                }
            }
        }

        // Add to history
        if (!isForwardedSlash) {
            if (messageHistory.isEmpty() || !messageHistory.get(messageHistory.size() - 1).equals(text)) {
                messageHistory.add(text);
                if (messageHistory.size() > 50) {
                    messageHistory.remove(0);
                }
            }
            historyIndex = -1;
            currentDraft = "";
        }

        String currentSessionId = SessionManager.getInstance().getCurrentSessionId();
        if (currentSessionId == null) {
            statusLabel.setText("Error: No active session.");
            return;
        }

        inputArea.setText("");
        statusLabel.setText("Sending");
        updateButtonState(true);

        // Build file attachment blocks (skip for forwarded slash commands)
        List<Map<String, Object>> fileBlocks = new ArrayList<>();
        if (!isForwardedSlash) {
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

                chatPanel.addMessage(new ProcessedMessage(MessageType.user_message_chunk, echoBuilder.toString(), null, null));
            }
        }

        // Editor Context
        Map<String, Object> context = isForwardedSlash ? null : captureEditorContext();
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
                        chatPanel.stopStreaming();
                        chatPanel.addMessage(ProcessedMessage.createError(MessageType.error_response, "Error: " + ex.getMessage(), null, null));
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
        if (isNotBlank(selection)) {
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
        JButton btn = UIUtils.createToolbarButton("filter.svg", 25, "Filter message types", e -> {
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
                cb.addActionListener(ev -> {
                    if (!cb.isSelected()) {
                        attachedFiles.remove(af);
                        updatePaperclipTooltip();
                    }
                });
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
            paperclipBtn.setIcon(ThemeManager.getIcon("paperclip.svg", 28));
        } else {
            paperclipBtn.setToolTipText(attachedFiles.size() + " file(s) attached");
            paperclipBtn.setIcon(ThemeManager.getIcon("paperclip-dot.svg", 28));
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
                chatPanel.stopStreaming();
                chatPanel.addMessage(ProcessedMessage.createError(MessageType.error_response, "Restart failed: " + msg, null, null));
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
            chatPanel.stopStreaming();
            chatPanel.addMessage(ProcessedMessage.createError(MessageType.error_response, "Failed to start: " + msg, null, null));
        }

        ProcessManager.getInstance().setPermissionHandler(this);
        ProcessManager.getInstance().getSlashCommandInterceptor().setCallback(new SlashCommandCallback() {
            @Override
            public void expandOptionsPanel() {
                if (optionsPanelCollapsed) {
                    optionsPanelCollapsed = false;
                    configPanelController.getComponent().setVisible(true);
                    toggleOptionsBtn.setIcon(ThemeManager.getIcon("arrow-down.svg", 25));
                    AssistantTopComponent.this.revalidate();
                    AssistantTopComponent.this.repaint();
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
        });

        // ESC key handler to close options panel and return focus to input
        KeyAdapter escHandler = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    if (!optionsPanelCollapsed) {
                        optionsPanelCollapsed = true;
                        configPanelController.getComponent().setVisible(false);
                        toggleOptionsBtn.setIcon(ThemeManager.getIcon("settings.svg", 25));
                        AssistantTopComponent.this.revalidate();
                        AssistantTopComponent.this.repaint();
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

        inputScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY));

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
