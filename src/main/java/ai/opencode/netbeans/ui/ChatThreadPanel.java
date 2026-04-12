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