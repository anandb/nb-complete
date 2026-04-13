package ai.opencode.netbeans.ui;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;


import ai.opencode.netbeans.model.Message;
import ai.opencode.netbeans.model.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

public class ChatThreadPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(ChatThreadPanel.class.getName());
    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final List<Message> messageList = new ArrayList<>();

    public ChatThreadPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        messagesContainer = new ScrollablePanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setOpaque(false);

        scrollPane = new JScrollPane(messagesContainer);
        scrollPane.setBorder(null);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Color.decode("#FDF6E3"));
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Color.decode("#FDF6E3"));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // Fix: Mouse wheel scrolling often breaks when mouse is over child components
        // like JEditorPane. We redirect those events to the main scroll pane.
        messagesContainer.addMouseWheelListener(e -> {
            scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(messagesContainer, e, scrollPane));
        });
        
        // Automatically fix mouse wheel for any added component
        messagesContainer.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                fixMouseWheel(e.getChild());
            }
        });

        add(scrollPane, BorderLayout.CENTER);
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel() {
            setOpaque(false);
        }
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 32;
        }
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public void addMessage(Message message) {
        String type = message.type();
        StringBuilder sb = new StringBuilder();

        if ("user".equals(type)) {
            if (message.prompt().text() != null) {
                sb.append(message.prompt().text());
            }
            if (message.prompt().parts() != null) {
                for (Message.ContentPart part : message.prompt().parts()) {
                    String pt = part.getDisplayText();
                    if (pt != null && !pt.isEmpty()) {
                        if (sb.length() > 0) {
                    sb.append("\n");
                }
                        sb.append(pt);
                    }
                }
            }
        } else {
            if (message.completion().text() != null) {
                sb.append(message.completion().text());
            }
            if (message.completion().parts() != null) {
                for (Message.ContentPart part : message.completion().parts()) {
                    String pt = part.getDisplayText();
                    if (pt != null && !pt.isEmpty()) {
                        if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                        sb.append(pt);
                    }
                }
            }
        }
        addMessage(type, sb.toString());
    }

    public void addMessage(String role, String text) {
        addMessage(role, text, null);
    }

    public void addMessage(String role, String text, String messageId) {
        SwingUtilities.invokeLater(() -> {
            MessageBubble bubble = new MessageBubble(role, text, messageId);
            messagesContainer.add(bubble);
            messagesContainer.add(Box.createVerticalStrut(8));
            messagesContainer.revalidate();
            messagesContainer.repaint();

            scrollToBottom();
        });
    }

    public void collapseLastThought() {
        SwingUtilities.invokeLater(() -> {
            int count = messagesContainer.getComponentCount();
            for (int i = count - 1; i >= 0; i--) {
                Component c = messagesContainer.getComponent(i);
                if (c instanceof MessageBubble messageBubble) {
                    if ("thought".equals(messageBubble.getType())) {
                        messageBubble.setExpanded(false);
                        break;
                    }
                }
            }
        });
    }

    public void appendOrAddMessage(String role, String text) {
        appendOrAddMessage(role, text, null);
    }

    public void appendOrAddMessage(String role, String text, String messageId) {
        SwingUtilities.invokeLater(() -> {
            int count = messagesContainer.getComponentCount();
            MessageBubble lastBubble = null;
            for (int i = count - 1; i >= 0; i--) {
                Component c = messagesContainer.getComponent(i);
                if (c instanceof MessageBubble messageBubble) {
                    lastBubble = messageBubble;
                    break;
                }
            }

            boolean canAppend = lastBubble != null && lastBubble.getType().equals(role);
            
            // If we have message IDs, they must match
            if (canAppend && messageId != null && lastBubble.getMessageId() != null) {
                if (!messageId.equals(lastBubble.getMessageId())) {
                    canAppend = false;
                }
            }

            if (canAppend) {
                lastBubble.appendText(text);
                messagesContainer.revalidate();
                messagesContainer.repaint();
                scrollToBottom();
            } else {
                addMessage(role, text, messageId);
            }
        });
    }

    public void addPermissionRequest(String prompt, com.fasterxml.jackson.databind.JsonNode options, java.util.concurrent.CompletableFuture<String> responseFuture) {
        SwingUtilities.invokeLater(() -> {
            PermissionBubble bubble = new PermissionBubble(prompt, options, responseFuture);
            messagesContainer.add(bubble);
            messagesContainer.add(Box.createVerticalStrut(8));
            messagesContainer.revalidate();
            messagesContainer.repaint();
            scrollToBottom();
        });
    }

    private static class PermissionBubble extends JPanel {
        public PermissionBubble(String prompt, com.fasterxml.jackson.databind.JsonNode options, java.util.concurrent.CompletableFuture<String> responseFuture) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            ThemeManager.Theme theme = ThemeManager.getCurrentTheme();

            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.setBackground(new Color(255, 243, 224)); // Light orange/amber background
            content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 160, 0), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
            ));

            JLabel titleLabel = new JLabel("🛡️ Permission Required");
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            titleLabel.setForeground(new Color(230, 81, 0));
            content.add(titleLabel, BorderLayout.NORTH);

            JLabel promptLabel = new JLabel("<html>" + prompt.replace("\n", "<br>") + "</html>");
            promptLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
            content.add(promptLabel, BorderLayout.CENTER);

            int numOptions = (options != null && options.isArray() && options.size() > 0) ? options.size() : 2;
            JPanel buttons = new JPanel(new java.awt.GridLayout(1, numOptions, 4, 0));
            buttons.setOpaque(false);

            if (options != null && options.isArray() && options.size() > 0) {
                LOG.log(Level.INFO, "PermissionBubble: rendering {0} options", options.size());
                for (com.fasterxml.jackson.databind.JsonNode opt : options) {
                    String optionId = opt.has("optionId") ? opt.get("optionId").asText() : "";
                    String name = opt.has("name") ? opt.get("name").asText() : optionId;
                    String kind = opt.has("kind") ? opt.get("kind").asText() : "";
                    
                    JButton btn = new JButton(name);
                    btn.setFocusPainted(false);
                    if (kind.contains("allow")) {
                        btn.setBackground(new Color(76, 175, 80));
                        btn.setForeground(Color.WHITE);
                    } else if (kind.contains("reject")) {
                        btn.setBackground(new Color(244, 67, 54));
                        btn.setForeground(Color.WHITE);
                    }
                    
                    btn.addActionListener(e -> {
                        responseFuture.complete(optionId);
                        boolean allowed = kind.contains("allow");
                        String statusText = (allowed ? "✅ " : "❌ ") + name;
                        Color fg = allowed ? new Color(46, 125, 50) : new Color(198, 40, 40);
                        Color bg = allowed ? new Color(232, 245, 233) : new Color(255, 235, 238);
                        Color border = allowed ? new Color(76, 175, 80) : new Color(244, 67, 54);
                        collapse(content, statusText, fg, bg, border);
                    });
                    buttons.add(btn);
                }
            } else {
                JButton allowBtn = new JButton("Allow");
                allowBtn.setBackground(new Color(76, 175, 80));
                allowBtn.setForeground(Color.WHITE);
                allowBtn.setFocusPainted(false);

                JButton denyBtn = new JButton("Deny");
                denyBtn.setBackground(new Color(244, 67, 54));
                denyBtn.setForeground(Color.WHITE);
                denyBtn.setFocusPainted(false);

                allowBtn.addActionListener(e -> {
                    responseFuture.complete("allow");
                    collapse(content, "✅ Permission Granted", new Color(46, 125, 50), new Color(232, 245, 233), new Color(76, 175, 80));
                });

                denyBtn.addActionListener(e -> {
                    responseFuture.complete("reject");
                    collapse(content, "❌ Permission Denied", new Color(198, 40, 40), new Color(255, 235, 238), new Color(244, 67, 54));
                });

                buttons.add(denyBtn);
                buttons.add(allowBtn);
            }
            
            content.add(buttons, BorderLayout.SOUTH);
            add(content, BorderLayout.CENTER);
            
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            if (getParent() != null && getParent().getWidth() > 0) {
                return new Dimension((int) (getParent().getWidth() * 0.8), pref.height);
            }
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }

        private void collapse(JPanel content, String status, Color fg, Color bg, Color border) {
            content.removeAll();
            content.setLayout(new BorderLayout());
            content.setBackground(bg);
            content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
            
            JLabel lbl = new JLabel(status);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            lbl.setForeground(fg);
            content.add(lbl, BorderLayout.CENTER);
            
            revalidate();
            repaint();
            // Recalculate max size to collapse vertically
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }
    }

    private void fixMouseWheel(Component c) {
        c.addMouseWheelListener(e -> {
            scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(c, e, scrollPane));
        });
        if (c instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                fixMouseWheel(child);
            }
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            SwingUtilities.invokeLater(() -> {
                JScrollBar vertical = scrollPane.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            });
        });
    }

    public void toggleAllBlocks(boolean expanded) {
        SwingUtilities.invokeLater(() -> {
            for (Component c : messagesContainer.getComponents()) {
                if (c instanceof MessageBubble bubble) {
                    bubble.toggleAllBlocks(expanded);
                }
            }
            messagesContainer.revalidate();
            messagesContainer.repaint();
        });
    }

    public String getConversationAsMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# OpenCode Conversation Export\n\n");
        
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble bubble) {
                String role = bubble.getType();
                String text = bubble.getRawText();
                
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }

                if ("user".equals(role)) {
                    sb.append("## USER\n\n");
                    sb.append(text).append("\n\n");
                } else if ("thought".equals(role)) {
                    sb.append("> **Thinking:**\n> ").append(text.replace("\n", "\n> ")).append("\n\n");
                } else if ("tool".equals(role)) {
                    sb.append("`Tool Output: ").append(text.replace("`", "'")).append("`\n\n");
                } else {
                    sb.append("## ASSISTANT\n\n");
                    sb.append(text).append("\n\n");
                }
                sb.append("---\n\n");
            }
        }
        return sb.toString();
    }

    public void clearMessages() {
        SwingUtilities.invokeLater(() -> {
            messageList.clear();
            messagesContainer.removeAll();
            messagesContainer.revalidate();
            messagesContainer.repaint();
        });
    }

    public void setMessages(List<Message> messages) {
        SwingUtilities.invokeLater(() -> {
            clearMessages();
            for (Message m : messages) {
                addMessage(m);
            }
        });
    }

    public void setSessionList(List<Session> sessions, Consumer<String> onSessionSelected, Runnable onNewChat) {
        LOG.log(Level.INFO, "setSessionList: received {0} sessions, onSessionSelected={1}", new Object[]{sessions.size(), onSessionSelected});
        SwingUtilities.invokeLater(() -> {
            try {
                clearMessages();

                JLabel title = new JLabel(sessions.isEmpty() ? "Welcome to OpenCode" : "Welcome back!");
                title.setFont(new Font("SansSerif", Font.BOLD, 18));
                title.setBorder(BorderFactory.createEmptyBorder(20, 12, 10, 12));
                messagesContainer.add(title);

                JLabel subtitle = new JLabel(sessions.isEmpty() ?
                    "Ask questions, generate code, or have me explain anything." :
                    "Continue a recent chat or start a new one.");
                subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
                subtitle.setForeground(Color.GRAY);
                subtitle.setBorder(BorderFactory.createEmptyBorder(0, 12, 20, 12));
                messagesContainer.add(subtitle);

                JButton newChatBtn = createSelectionButton("✨ Start New Chat", null);
                newChatBtn.addActionListener(e -> onNewChat.run());
                messagesContainer.add(newChatBtn);
                messagesContainer.add(Box.createVerticalStrut(12));

                if (!sessions.isEmpty()) {
                    JSeparator sep = new JSeparator();
                    sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                    messagesContainer.add(sep);
                    messagesContainer.add(Box.createVerticalStrut(12));

                    for (Session s : sessions) {
                        String label = s.title();
                        if (label == null || label.isEmpty()) {
                            label = "Chat " + s.id().substring(0, Math.min(8, s.id().length()));
                        }
                        JButton sessionBtn = createSelectionButton(label, s.cwd());
                        sessionBtn.addActionListener(e -> onSessionSelected.accept(s.id()));
                        messagesContainer.add(sessionBtn);
                        messagesContainer.add(Box.createVerticalStrut(8));
                    }
                }

                messagesContainer.revalidate();
                messagesContainer.repaint();
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "setSessionList error: {0}", ex.getMessage());
            }
        });
    }

    private JButton createSelectionButton(String text, String subtext) {
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();

        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(8, 0));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.getBubbleBorder() != null ? theme.getBubbleBorder() : Color.LIGHT_GRAY, 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel textPanel = new JPanel(new GridLayout(subtext != null ? 2 : 1, 1));
        textPanel.setOpaque(false);

        JLabel mainLabel = new JLabel(text);
        mainLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        textPanel.add(mainLabel);

        if (subtext != null) {
            String folder = new File(subtext).getName();
            JLabel subLabel = new JLabel("in " + folder);
            subLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            subLabel.setForeground(Color.GRAY);
            textPanel.add(subLabel);
        }

        btn.add(textPanel, BorderLayout.CENTER);

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setOpaque(true);
                btn.setBackground(new Color(0, 0, 0, 10));
                btn.repaint();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setOpaque(false);
                btn.repaint();
            }
        });

        return btn;
    }
}