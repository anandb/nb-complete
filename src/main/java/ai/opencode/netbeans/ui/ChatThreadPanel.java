package ai.opencode.netbeans.ui;

import ai.opencode.netbeans.model.Message;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
        String text = "user".equals(type) ? message.prompt().text() : message.completion().text();
        addMessage(type, text);
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
                lastBubble.appendText(text);
                messagesContainer.revalidate();
                messagesContainer.repaint();
                scrollToBottom();
            } else {
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
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
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
        clearMessages();
        for (Message m : messages) {
            addMessage(m);
        }
    }
}
