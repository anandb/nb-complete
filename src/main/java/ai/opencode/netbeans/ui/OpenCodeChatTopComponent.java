package ai.opencode.netbeans.ui;

import ai.opencode.netbeans.manager.OpenCodeManager;
import ai.opencode.netbeans.model.Session;
import ai.opencode.netbeans.model.SessionUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

@ConvertAsProperties(
        dtd = "-//ai.opencode.netbeans.ui//OpenCodeChat//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "OpenCodeChatTopComponent",
        iconBase = "ai/opencode/netbeans/ui/logo.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = true)
@ActionID(category = "Window", id = "ai.opencode.netbeans.ui.OpenCodeChatTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_OpenCodeChatAction",
        preferredID = "OpenCodeChatTopComponent"
)
public final class OpenCodeChatTopComponent extends TopComponent {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(OpenCodeChatTopComponent.class.getName());
    private static OpenCodeChatTopComponent instance;

    private final ChatThreadPanel chatPanel;
    private final JTextArea inputArea;
    private final JLabel statusLabel;
    private final JButton sendBtn;
    private final JButton stopBtn;
    private String currentSessionId;

    public OpenCodeChatTopComponent() {
        LOG.info("Initializing OpenCodeChatTopComponent...");
        setName(NbBundle.getMessage(OpenCodeChatTopComponent.class, "CTL_OpenCodeChatTopComponent"));
        setToolTipText(NbBundle.getMessage(OpenCodeChatTopComponent.class, "HINT_OpenCodeChatTopComponent"));
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        header.setOpaque(false);
        
        JLabel titleLabel = new JLabel("OpenCode", SwingConstants.LEFT);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLabel.setForeground(UIManager.getColor("Label.foreground"));
        header.add(titleLabel, BorderLayout.WEST);

        JButton newSessionBtn = new JButton("New Chat");
        newSessionBtn.setFocusPainted(false);
        newSessionBtn.addActionListener(e -> createNewSession());
        header.add(newSessionBtn, BorderLayout.EAST);
        
        add(header, BorderLayout.NORTH);

        // Chat History
        chatPanel = new ChatThreadPanel();
        add(chatPanel, BorderLayout.CENTER);

        // Footer / Input
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setBorder(new EmptyBorder(16, 16, 12, 16));
        footer.setOpaque(false);
        
        JPanel inputWrapper = new JPanel(new BorderLayout());
        inputWrapper.setOpaque(false);
        
        inputArea = new JTextArea(3, 20);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.bubbleBorder != null ? theme.bubbleBorder : Color.LIGHT_GRAY, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));
        
        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        // Let it insert a newline
                    } else {
                        // Send message
                        e.consume();
                        sendMessage();
                    }
                }
            }
        });
        
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(null);
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputWrapper.add(inputScroll, BorderLayout.CENTER);

        sendBtn = new JButton("Send");
        sendBtn.setFocusPainted(false);
        sendBtn.setPreferredSize(new Dimension(80, 32));
        sendBtn.addActionListener(e -> sendMessage());

        stopBtn = new JButton("Stop");
        stopBtn.setFocusPainted(false);
        stopBtn.setPreferredSize(new Dimension(80, 32));
        stopBtn.setVisible(false);
        stopBtn.addActionListener(e -> stopMessage());
        
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setOpaque(false);
        buttonPanel.add(sendBtn, BorderLayout.EAST);
        buttonPanel.add(stopBtn, BorderLayout.CENTER);
        
        inputWrapper.add(buttonPanel, BorderLayout.SOUTH);
        footer.add(inputWrapper, BorderLayout.CENTER);
        
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statusLabel.setForeground(Color.GRAY);
        footer.add(statusLabel, BorderLayout.SOUTH);

        add(footer, BorderLayout.SOUTH);

        // Register for SSE events
        OpenCodeManager.getInstance().addSseListener(sseUpdate -> {
            SessionUpdate.UpdateData update = sseUpdate.update();
            if (update == null) return;
            
            String type = update.type();
            
            // Check for explicit thinking status
            Boolean isThinking = update.isThinking();
            if (isThinking != null) {
                SwingUtilities.invokeLater(() -> {
                    if (isThinking) {
                        statusLabel.setText("Thinking...");
                        updateButtonState(true);
                    } else if (statusLabel.getText().equals("Thinking...") || statusLabel.getText().equals("Responding...")) {
                        // Reset if we received isThinking: false and we are currently in a processing state
                        resetStatus();
                    }
                });
            }

            String role = (type != null && (type.contains("user") || type.contains("user_message"))) ? "user" : "assistant";
            
            // Handle full message object if present
            if (update.message() != null) {
                String text = (update.message().prompt() != null) ? update.message().prompt().text() : 
                             (update.message().completion() != null) ? update.message().completion().text() : null;
                if (text != null && !text.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        chatPanel.addMessage(role, text);
                        if ("assistant".equals(role)) resetStatus();
                    });
                }
            } 
            // Handle chunked content if present
            else if (update.content() != null) {
                JsonNode content = update.content();
                String text = null;
                
                if (content.isArray() && content.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode node : content) {
                        if (node.has("text")) sb.append(node.get("text").asText());
                    }
                    text = sb.toString();
                } else if (content.isObject() && content.has("text")) {
                    text = content.get("text").asText();
                }

                if (text != null && !text.isEmpty()) {
                    if ("agent_thought_chunk".equals(type)) {
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Thinking..."));
                    } else {
                        final String finalText = text;
                        final String finalRole = role;
                        SwingUtilities.invokeLater(() -> {
                            // If we get real content, we are definitely Responding (not just thinking)
                            if ("assistant".equals(finalRole)) {
                                statusLabel.setText("Responding...");
                                updateButtonState(true);
                            }
                            
                            // Only append for assistant; user messages should be separate bubbles
                            if ("user".equals(finalRole)) {
                                chatPanel.addMessage(finalRole, finalText);
                            } else {
                                chatPanel.appendOrAddMessage(finalRole, finalText);
                            }
                        });
                    }
                }
            } 
            
            // End of turn signals
            if ("usage_update".equals(type) || "completion_chunk".equals(type) && update.content() == null) {
                SwingUtilities.invokeLater(this::resetStatus);
            }
        });
        
        OpenCodeManager.getInstance().addProjectChangeListener(path -> {
            if (path != null) {
                SwingUtilities.invokeLater(() -> {
                    // Automatically clear and start fresh session for new project context
                    createNewSession();
                    statusLabel.setText("Context switched: " + new java.io.File(path).getName());
                });
            }
        });
        
        initChat();
    }

    private void initChat() {
        statusLabel.setText("Connecting...");
        OpenCodeManager manager = OpenCodeManager.getInstance();
        manager.whenReady()
            .thenCompose(v -> manager.getSessions())
            .thenAccept(sessions -> {
                String activeDir = manager.getActiveProjectDir();
                
                // Prioritize session matching current project directory
                Session bestSession = null;
                if (activeDir != null) {
                    bestSession = sessions.stream()
                        .filter(s -> activeDir.equals(s.cwd()) || activeDir.equals(s.directory()))
                        .sorted((s1, s2) -> {
                            long t1 = (s1.time() != null) ? s1.time().updated() : 0;
                            long t2 = (s2.time() != null) ? s2.time().updated() : 0;
                            return Long.compare(t2, t1); // Descending
                        })
                        .findFirst()
                        .orElse(null);
                }
                
                if (bestSession != null) {
                    loadSession(bestSession.id());
                } else if (!sessions.isEmpty() && activeDir == null) {
                    // Fallback to most recent if no active project
                    sessions.sort((s1, s2) -> {
                        long t1 = (s1.time() != null) ? s1.time().updated() : 0;
                        long t2 = (s2.time() != null) ? s2.time().updated() : 0;
                        return Long.compare(t2, t1); // Descending
                    });
                    loadSession(sessions.get(0).id());
                } else {
                    createNewSession();
                }
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
        OpenCodeManager.getInstance().getMessages(sessionId)
            .thenAccept(messages -> {
                chatPanel.setMessages(messages);
                statusLabel.setText("Ready");
            })
            .exceptionally(ex -> {
                statusLabel.setText("Error loading messages: " + ex.getMessage());
                return null;
            });
    }

    private void createNewSession() {
        LOG.info("Attempting to create new session...");
        statusLabel.setText("Creating new session...");
        OpenCodeManager.getInstance().createSession(null)
            .thenAccept(session -> {
                this.currentSessionId = session.id();
                LOG.log(Level.INFO, "New session created: {0}", currentSessionId);
                SwingUtilities.invokeLater(() -> {
                    chatPanel.clearMessages();
                    statusLabel.setText("Session created: " + currentSessionId);
                });
            })
            .exceptionally(ex -> {
                LOG.log(Level.SEVERE, "Failed to create session", ex);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Failed to create session: " + ex.getMessage()));
                return null;
            });
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        
        if (currentSessionId == null) {
            statusLabel.setText("Error: No active session.");
            return;
        }
        
        inputArea.setText("");
        statusLabel.setText("Sending...");
        updateButtonState(true);
        
        OpenCodeManager.getInstance().sendMessage(currentSessionId, text)
            .thenAccept(v -> {
                SwingUtilities.invokeLater(() -> {
                    String currentStatus = statusLabel.getText();
                    if ("Sending...".equals(currentStatus)) {
                        statusLabel.setText("Ready");
                        updateButtonState(false);
                    }
                });
            })
            .exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + ex.getMessage());
                    inputArea.setText(text);
                    updateButtonState(false);
                });
                return null;
            });
    }

    private void stopMessage() {
        if (currentSessionId == null) return;
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

    private void updateButtonState(boolean isProcessing) {
        SwingUtilities.invokeLater(() -> {
            sendBtn.setEnabled(!isProcessing);
            stopBtn.setVisible(isProcessing);
            stopBtn.setEnabled(isProcessing);
        });
    }

    private void resetStatus() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Ready");
            updateButtonState(false);
        });
    }

    @Override
    public void componentOpened() {
        // Component opened logic
    }

    @Override
    public void componentClosed() {
        // Component closed logic
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
}
