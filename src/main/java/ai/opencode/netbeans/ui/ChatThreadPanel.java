package ai.opencode.netbeans.ui;

import ai.opencode.netbeans.model.Message;
import ai.opencode.netbeans.model.Session;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

public class ChatThreadPanel extends JPanel {
    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final List<Message> messageList = new ArrayList<>();

    public ChatThreadPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        // Use a panel that tracks viewport width to force wrapping
        messagesContainer = new ScrollablePanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setOpaque(false);

        scrollPane = new JScrollPane(messagesContainer);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

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
            if (message.prompt().text() != null) sb.append(message.prompt().text());
            if (message.prompt().parts() != null) {
                for (Message.ContentPart part : message.prompt().parts()) {
                    String pt = part.getDisplayText();
                    if (pt != null && !pt.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(pt);
                    }
                }
            }
        } else {
            if (message.completion().text() != null) sb.append(message.completion().text());
            if (message.completion().parts() != null) {
                for (Message.ContentPart part : message.completion().parts()) {
                    String pt = part.getDisplayText();
                    if (pt != null && !pt.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(pt);
                    }
                }
            }
        }
        addMessage(type, sb.toString());
    }

    public void addMessage(String role, String text) {
        SwingUtilities.invokeLater(() -> {
            MessageBubble bubble = new MessageBubble(role, text);
            messagesContainer.add(bubble);
            messagesContainer.add(Box.createVerticalStrut(8));
            messagesContainer.revalidate();
            messagesContainer.repaint();
            
            scrollToBottom();
        });
    }

    public void appendOrAddMessage(String role, String text) {
        SwingUtilities.invokeLater(() -> {
            int count = messagesContainer.getComponentCount();
            MessageBubble lastBubble = null;
            for (int i = count - 1; i >= 0; i--) {
                Component c = messagesContainer.getComponent(i);
                if (c instanceof MessageBubble) {
                    lastBubble = (MessageBubble) c;
                    break;
                }
            }

            if (lastBubble != null && lastBubble.getType().equals(role)) {
                System.out.println("Appending to existing bubble for role: " + role);
                lastBubble.appendText(text);
                messagesContainer.revalidate();
                messagesContainer.repaint();
                scrollToBottom();
            } else {
                System.out.println("Adding new bubble for role: " + role);
                // Manually add since we're already on EDT
                MessageBubble bubble = new MessageBubble(role, text);
                messagesContainer.add(bubble);
                messagesContainer.add(Box.createVerticalStrut(8));
                messagesContainer.revalidate();
                messagesContainer.repaint();
                scrollToBottom();
            }
        });
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
        SwingUtilities.invokeLater(() -> {
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

            // "New Chat" button bubble
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
        });
    }

    private JButton createSelectionButton(String text, String subtext) {
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme();
        
        // Using a button styled like a selection bubble
        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(8, 0));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.bubbleBorder != null ? theme.bubbleBorder : Color.LIGHT_GRAY, 1, true),
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
        
        // Hover effect
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setOpaque(true);
                btn.setBackground(new Color(0, 0, 0, 10)); // Very subtle hover
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
