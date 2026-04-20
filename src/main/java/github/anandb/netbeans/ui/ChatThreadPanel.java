package github.anandb.netbeans.ui;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;

import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.Session;

public class ChatThreadPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(ChatThreadPanel.class.getName());
    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final List<Message> messageList = new ArrayList<>();
    private volatile MessageBubble activeStreamBubble = null;
    private javax.swing.Timer streamFlushTimer;

    public ChatThreadPanel() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(ThemeManager.getCurrentTheme().getBackground());
        setDoubleBuffered(true);

        ColorTheme theme = ThemeManager.getCurrentTheme();
        messagesContainer = new ScrollablePanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setOpaque(true);
        messagesContainer.setBackground(theme.getSunkenBackground());
        messagesContainer.setBorder(new javax.swing.border.EmptyBorder(0, 0, 40, 0));

        scrollPane = new JScrollPane(messagesContainer);
        // Sunken feel border
        Color shadow = theme.isDark() ? Color.BLACK : theme.getBubbleBorder();
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, shadow),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        scrollPane.setOpaque(true);
        scrollPane.setBackground(theme.getSunkenBackground());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(theme.getSunkenBackground());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
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

        streamFlushTimer = new javax.swing.Timer(100, e -> {
            if (activeStreamBubble != null && activeStreamBubble.flushUpdate()) {
                messagesContainer.revalidate();
                scrollToBottom();
            }
        });
        streamFlushTimer.setRepeats(true);
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel() {
            setOpaque(false);
            setDoubleBuffered(true);
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
        LOG.info("addMessage(Message) called. type=" + type);

        if ("user".equals(type)) {
            if (message.prompt() != null) {
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
            }
        } else {
            if (message.completion() != null) {
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
        }
        String text = sb.toString();
        LOG.info("addMessage(Message) final text length: " + text.length());
        String copyable = "user".equals(type) && message.prompt() != null ? message.prompt().text() : text;
        addMessage(type, text, message.id(), copyable);
    }

    public void addMessage(String role, String text) {
        addMessage(role, text, null);
    }

    public void addMessage(String role, String text, String messageId) {
        addMessage(role, text, messageId, text);
    }

    public void addMessage(String role, String text, String messageId, String copyableText) {
        if (isIgnorableToolMessage(role, text)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            MessageBubble lastBubble = findLastNonIgnorableBubble();
            
            boolean canMerge = lastBubble != null && lastBubble.getType().equals(role);
            if (canMerge) {
                if ("thought".equals(role)) {
                    // Always merge consecutive thoughts
                } else if (messageId != null && messageId.equals(lastBubble.getMessageId())) {
                    // Merge same ID
                } else {
                    canMerge = false;
                }
            }

            if (canMerge) {
                lastBubble.appendText(text);
                lastBubble.flushUpdate(true);
                return;
            }

            finalizeGroup();

            MessageBubble bubble = new MessageBubble(role, text, messageId, copyableText);
            if ("tool".equals(role) || "thought".equals(role)) {
                bubble.setExpanded(false);
            }

            messagesContainer.add(bubble);
            messagesContainer.add(Box.createVerticalStrut(4));
            messagesContainer.revalidate();
            scrollToBottom(true);
        });
    }

    private void finalizeGroup() {
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
            MessageBubble lastBubble = findLastNonIgnorableBubble();

            boolean canAppend = lastBubble != null && lastBubble.getType().equals(role);
            if (canAppend && messageId != null && lastBubble.getMessageId() != null) {
                // For "thought" segments, we want to group them together even if they have different IDs 
                // if they are consecutive. For other types, we still require matching IDs.
                if (!role.equals("thought") && !messageId.equals(lastBubble.getMessageId())) {
                    canAppend = false;
                }
            }

            if (canAppend) {
                lastBubble.appendText(text);
                activeStreamBubble = lastBubble;
                if (!streamFlushTimer.isRunning()) {
                    streamFlushTimer.start();
                }
            } else {
                if (activeStreamBubble != null) {
                    activeStreamBubble.finalizeStreaming();
                    activeStreamBubble.flushUpdate(true);
                    activeStreamBubble = null;
                }
                if (streamFlushTimer.isRunning()) {
                    streamFlushTimer.stop();
                }
                addMessage(role, text, messageId);
            }
        });
    }

    public void updateToolCall(github.anandb.netbeans.model.SessionUpdate.UpdateData update) {
        SwingUtilities.invokeLater(() -> {
            String role = "tool";
            String text = update.status();
            String messageId = update.messageId();

            if (isIgnorableToolMessage(role, text)) {
                return;
            }

            int count = messagesContainer.getComponentCount();
            MessageBubble lastBubble = null;
            if (count > 0) {
                Component c = messagesContainer.getComponent(count - 1);
                if (c instanceof MessageBubble mb) {
                    lastBubble = mb;
                }
            }

            if (lastBubble != null && "tool".equals(lastBubble.getType()) && messageId != null && messageId.equals(lastBubble.getMessageId())) {
                lastBubble.appendText(text);
                activeStreamBubble = lastBubble;
                if (!streamFlushTimer.isRunning()) {
                    streamFlushTimer.start();
                }
            } else {
                addMessage(role, text, messageId);
            }
        });
    }

    public void stopStreaming() {
        SwingUtilities.invokeLater(() -> {
            if (activeStreamBubble != null) {
                activeStreamBubble.finalizeStreaming();
                if (activeStreamBubble.flushUpdate(true)) {
                    messagesContainer.revalidate();
                    scrollToBottom();
                }
                activeStreamBubble = null;
            }
            if (streamFlushTimer.isRunning()) {
                streamFlushTimer.stop();
            }
        });
    }

    public void addPermissionRequest(String prompt, com.fasterxml.jackson.databind.JsonNode options, java.util.concurrent.CompletableFuture<String> responseFuture) {
        SwingUtilities.invokeLater(() -> {
            PermissionBubble bubble = new PermissionBubble(prompt, options, responseFuture);
            messagesContainer.add(bubble);
            messagesContainer.add(Box.createVerticalStrut(4));
            messagesContainer.revalidate();
            messagesContainer.repaint();
            scrollToBottom(true);
        });
    }

    private static class PermissionBubble extends JPanel {
        public PermissionBubble(String prompt, com.fasterxml.jackson.databind.JsonNode options, java.util.concurrent.CompletableFuture<String> responseFuture) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            ColorTheme theme = ThemeManager.getCurrentTheme();

            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.setBackground(theme.getPermissionBg());
            content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.getPermissionBorder(), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
            ));

            JLabel titleLabel = new JLabel("Permission Required", ThemeManager.getIcon("shield.svg", 18), SwingConstants.LEFT);
            titleLabel.setIconTextGap(8);
            titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
            titleLabel.setForeground(theme.getPermissionTitle());
            content.add(titleLabel, BorderLayout.NORTH);

            JLabel promptLabel = new JLabel("<html>" + prompt.replace("\n", "<br>") + "</html>");
            promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
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
                    btn.addActionListener(e -> {
                        responseFuture.complete(optionId);
                        boolean allowed = kind.contains("allow");
                        Icon statusIcon = ThemeManager.getIcon(allowed ? "check.svg" : "x.svg", 16);
                        String statusText = name;
                        Color fg = allowed ? new Color(46, 125, 50) : new Color(198, 40, 40);
                        Color bg = allowed ? new Color(232, 245, 233) : new Color(255, 235, 238);
                        Color border = allowed ? new Color(76, 175, 80) : new Color(244, 67, 54);
                        collapse(content, statusText, statusIcon, fg, bg, border);
                    });
                    buttons.add(btn);
                }
            } else {
                JButton allowBtn = new JButton("Allow");
                allowBtn.setFocusPainted(false);

                JButton denyBtn = new JButton("Deny");
                denyBtn.setFocusPainted(false);

                allowBtn.addActionListener(e -> {
                    responseFuture.complete("allow");
                    collapse(content, "Permission Granted", ThemeManager.getIcon("check.svg", 16), new Color(46, 125, 50), new Color(232, 245, 233), new Color(76, 175, 80));
                });

                denyBtn.addActionListener(e -> {
                    responseFuture.complete("reject");
                    collapse(content, "Permission Denied", ThemeManager.getIcon("x.svg", 16), new Color(198, 40, 40), new Color(255, 235, 238), new Color(244, 67, 54));
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

        private void collapse(JPanel content, String status, Icon icon, Color fg, Color bg, Color border) {
            content.removeAll();
            content.setLayout(new BorderLayout());
            content.setBackground(bg);
            content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));

            JLabel lbl = new JLabel(status, icon, SwingConstants.LEFT);
            lbl.setIconTextGap(8);
            lbl.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
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

    private javax.swing.Timer scrollTimer;

    private boolean isAtBottom() {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        int extent = vertical.getModel().getExtent();
        int value = vertical.getValue();
        int maximum = vertical.getMaximum();
        // Use a small threshold (e.g., 100 pixels) to be "at the bottom"
        return (value + extent >= maximum - 100);
    }

    public void scrollToBottom() {
        scrollToBottom(false);
    }

    public void scrollToBottom(boolean force) {
        SwingUtilities.invokeLater(() -> {
            if (!force && !isAtBottom()) {
                return;
            }

            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());

            // Re-apply after a short delay to account for dynamic component resizing
            if (scrollTimer != null && scrollTimer.isRunning()) {
                scrollTimer.restart();
            } else {
                scrollTimer = new javax.swing.Timer(50, e -> {
                    vertical.setValue(vertical.getMaximum());
                });
                scrollTimer.setRepeats(false);
                scrollTimer.start();
            }
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
            // revalidate automatically schedules repaint, removed redundant repaint() call
        });
    }

    public String getConversationAsMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ACP Conversation Export\n\n");

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

    public void refreshTheme() {
        SwingUtilities.invokeLater(() -> {
            ColorTheme theme = ThemeManager.getCurrentTheme();
            setBackground(theme.getBackground());
            scrollPane.setBackground(theme.getBackground());
            scrollPane.getViewport().setBackground(theme.getBackground());

            for (Component c : messagesContainer.getComponents()) {
                if (c instanceof MessageBubble bubble) {
                    bubble.refreshTheme();
                }
            }
            messagesContainer.revalidate();
            messagesContainer.repaint();
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

                JLabel titleLabel = new JLabel(sessions.isEmpty() ? "Welcome to ACP" : "Welcome back!");
                titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
                titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 12, 10, 12));
                messagesContainer.add(titleLabel);

                JLabel subtitle = new JLabel(sessions.isEmpty() ?
                    "Ask questions, generate code, or have me explain anything." :
                    "Continue a recent chat or start a new one.");
                subtitle.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
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
                        String title = StringUtils.defaultIfBlank(s.title(), "Chat " + StringUtils.left(s.id(), 8));
                        String label = github.anandb.netbeans.manager.SessionTitleManager.getTitle(s.id(), title);
                        String dir = s.effectiveDirectory();
                        JButton sessionBtn = createSelectionButtonWithBadge(label, s.projectName(), dir);
                        sessionBtn.addActionListener(e -> onSessionSelected.accept(s.id()));
                        messagesContainer.add(sessionBtn);
                        messagesContainer.add(Box.createVerticalStrut(4));
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
        ColorTheme theme = ThemeManager.getCurrentTheme();

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
        mainLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        textPanel.add(mainLabel);

        if (subtext != null) {
            String folder = new File(subtext).getName();
            JLabel subLabel = new JLabel("in " + folder);
            subLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            subLabel.setForeground(Color.GRAY);
            textPanel.add(subLabel);
        }

        btn.add(textPanel, BorderLayout.CENTER);

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setOpaque(false);
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

    private JButton createSelectionButtonWithBadge(String text, String projectName, String fullPath) {
        ColorTheme theme = ThemeManager.getCurrentTheme();

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

        JPanel leftPanel = new JPanel();
        leftPanel.setOpaque(false);

        if (projectName != null && !projectName.isEmpty()) {
            JLabel badge = new JLabel("[" + projectName + "]");
            badge.setFont(ThemeManager.getFont().deriveFont(Font.BOLD, 10));
            badge.setForeground(new Color(100, 100, 100));
            badge.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
            leftPanel.add(badge);
        }

        JLabel mainLabel = new JLabel(text);
        mainLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        leftPanel.add(mainLabel);

        btn.add(leftPanel, BorderLayout.CENTER);

        if (fullPath != null && !fullPath.isEmpty()) {
            btn.setToolTipText(fullPath);
        }

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setOpaque(false);
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
    private boolean isIgnorableToolMessage(String role, String text) {
        if (!"tool".equals(role)) return false;
        if (text == null) return true;
        String trimmed = text.trim().toLowerCase();
        // Remove trailing punctuation for the check
        if (trimmed.endsWith(".") || trimmed.endsWith("!")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.equals("completed") || trimmed.equals("failed") 
            || trimmed.equals("in-progress") || trimmed.equals("in progress") || trimmed.equals("in_progress") 
            || trimmed.equals("success") || trimmed.equals("done");
    }
    private MessageBubble findLastNonIgnorableBubble() {
        int count = messagesContainer.getComponentCount();
        for (int i = count - 1; i >= 0; i--) {
            Component c = messagesContainer.getComponent(i);
            if (c instanceof MessageBubble mb) {
                if (isIgnorableToolMessage(mb.getType(), mb.getRawText())) {
                    continue;
                }
                return mb;
            }
        }
        return null;
    }
}