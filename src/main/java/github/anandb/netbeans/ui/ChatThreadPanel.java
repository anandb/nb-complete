package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
import javax.swing.Timer;

import github.anandb.netbeans.manager.SessionTitleMapper;
import github.anandb.netbeans.manager.ToolParamsExtractor;
import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageClassification;
import github.anandb.netbeans.model.MessageTransformer;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.left;

public class ChatThreadPanel extends JPanel {
    private static final Logger LOG = new Logger(ChatThreadPanel.class);
    private static final long serialVersionUID = 1L;
    private static final Pattern SECTION_SPLIT = Pattern.compile("(?m)^---[ \\t]*$");

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final Timer scrollTimer;
    private final Timer streamFlushTimer;

    private MessageBubble activeStreamBubble = null;
    private volatile boolean allBlocksExpanded = false;
    private transient final KeyEventDispatcher keyDispatcher;
    private transient final MessageTransformer messageTransformer;

    public ChatThreadPanel() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(theme.background());
        setDoubleBuffered(true);

        messageTransformer = new MessageTransformer();
        messagesContainer = new ScrollablePanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setOpaque(true);
        messagesContainer.setBackground(theme.sunkenBackground());
        messagesContainer.setBorder(new javax.swing.border.EmptyBorder(0, 0, 40, 0));

        scrollPane = new JScrollPane(messagesContainer);
        // Sunken feel border
        Color shadow = theme.isDark() ? Color.BLACK : (theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, shadow),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        scrollPane.setOpaque(true);
        scrollPane.setBackground(theme.sunkenBackground());
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(theme.sunkenBackground());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setScrollMode(JViewport.BLIT_SCROLL_MODE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Fix: Mouse wheel scrolling often breaks when mouse is over child components
        // like JTextPane. We redirect those events to the main scroll pane.
        messagesContainer.addMouseWheelListener(e -> {
            scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(messagesContainer, e, scrollPane));
        });

        // Automatically fix mouse wheel for message bubbles (skip strut spacers)
        messagesContainer.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                Component c = e.getChild();
                if (c instanceof MessageBubble || c instanceof PermissionBubble) {
                    fixMouseWheel(c);
                }
            }
        });

        add(scrollPane, BorderLayout.CENTER);

        streamFlushTimer = new javax.swing.Timer(100, e -> {
            if (!isShowing()) {
                return;
            }
            if (activeStreamBubble != null && activeStreamBubble.flushUpdate()) {
                messagesContainer.revalidate();
                scrollToBottom();
            }
        });
        streamFlushTimer.setRepeats(true);

        scrollTimer = new javax.swing.Timer(100, e -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
        scrollTimer.setRepeats(false);

        keyDispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN
                        || ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0
                            && (keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END))) {
                    Component src = e.getComponent();
                    if (src != null && SwingUtilities.isDescendingFrom(src, ChatThreadPanel.this)) {
                        JScrollBar vertical = scrollPane.getVerticalScrollBar();
                        if (keyCode == KeyEvent.VK_PAGE_UP) {
                            vertical.setValue(vertical.getValue() - vertical.getVisibleAmount());
                        } else if (keyCode == KeyEvent.VK_PAGE_DOWN) {
                            vertical.setValue(vertical.getValue() + vertical.getVisibleAmount());
                        } else if (keyCode == KeyEvent.VK_HOME) {
                            vertical.setValue(vertical.getMinimum());
                        } else if (keyCode == KeyEvent.VK_END) {
                            vertical.setValue(vertical.getMaximum());
                        }
                        return true;
                    }
                }
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

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
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : 16;
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

    public void addMessage(ProcessedMessage pm) {
        String text = pm.text();
        String kind = pm.kind();

        MessageClassification classification = ToolParamsExtractor.classify(pm.messageType(), text, kind);
        final String effectiveRole = classification.type() != null ? classification.type().roleName() : null;
        final String effectiveKind = classification.kind();

        if (ProcessedMessage.isIgnorable(effectiveRole, text)) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String[] parts = SECTION_SPLIT.split(text);
            for (String part : parts) {
                if (part.trim().isEmpty() && parts.length > 1) {
                    continue;
                }

                MessageBubble lastBubble = findLastNonIgnorableBubble();
                boolean canMerge = (lastBubble != null && effectiveRole.equals(lastBubble.getType()));
                if (canMerge) {
                    // Require matching messageIds (or allow if either is null).
                    canMerge = "thought".equals(effectiveRole) || canMergeMessages(pm.messageId(), lastBubble.getMessageId());
                }

                if (canMerge) {
                    lastBubble.appendText(part);
                    lastBubble.flushUpdate(true);
                } else {
                    addSingleBubble(effectiveRole, part, pm.messageId(), effectiveKind, "");
                }
            }
        });
    }

    private void addSingleBubble(String role, String text, String messageId, String kind, String toolTitle) {
        MessageBubble bubble = new MessageBubble(role, text, messageId, kind, toolTitle);
        if ("tool".equals(role) || "thought".equals(role)) {
            bubble.setExpanded(allBlocksExpanded);
        }

        boolean visible = !MessageFilterManager.isTypeHidden(role);
        bubble.setVisible(visible);
        Component strut = Box.createVerticalStrut(4);
        strut.setVisible(visible);
        messagesContainer.add(bubble);
        messagesContainer.add(strut);
        messagesContainer.revalidate();
        scrollToBottom(true);
    }

    public void addProcessedMessage(ProcessedMessage pm) {
        if (pm == null) return;
        if (pm.isIgnorable()) return;

        SwingUtilities.invokeLater(() -> {
            String r = pm.role();
            String t = pm.text();
            String[] parts = SECTION_SPLIT.split(t);

            for (String part : parts) {
                if (part.trim().isEmpty() && parts.length > 1) {
                    continue;
                }
                addSingleBubble(r, part, pm.messageId(), pm.kind(), pm.toolTitle());
            }
        });
    }

    public void appendOrAddProcessedMessage(ProcessedMessage pm) {
        if (pm == null) return;
        if (pm.isIgnorable()) return;

        final String role = pm.role();
        final String text = pm.text();
        final String messageId = pm.messageId();

        SwingUtilities.invokeLater(() -> {
            MessageBubble lastBubble = findLastNonIgnorableBubble();
            boolean canAppend = lastBubble != null && lastBubble.getType().equals(role);
            if (canAppend) {
                // Always merge consecutive thoughts during streaming.
                // For other types, require matching messageIds (or allow if either is null).
                if ("thought".equals(role) || canMergeMessages(messageId, lastBubble.getMessageId())) {
                    canAppend = true;
                }
            }

            if (canAppend) {
                // Check for split delimiter in the incoming chunk
                // We handle both \n---\n and start-of-chunk ---\n
                if (text.contains("\n---") || text.startsWith("---")) {
                    String[] parts = SECTION_SPLIT.split(text);
                    if (parts.length > 1 || text.trim().equals("---")) {
                        // Finalize current bubble with the first part
                        if (parts.length > 0 && !parts[0].isEmpty()) {
                            lastBubble.appendText(parts[0]);
                        }
                        lastBubble.flushUpdate(true);
                        lastBubble.finalizeStreaming(allBlocksExpanded);

                        // Create new bubbles for remaining parts
                        for (int i = 1; i < parts.length; i++) {
                            String part = parts[i];
                            if (part.trim().isEmpty() && i < parts.length - 1) continue;
                            addSingleBubble(role, part, messageId, null, "");
                        }

                        if (!streamFlushTimer.isRunning()) {
                            streamFlushTimer.start();
                        }
                        return;
                    }
                }

                lastBubble.appendText(text);
                activeStreamBubble = lastBubble;
                if ("tool".equals(role) || "thought".equals(role)) {
                    activeStreamBubble.setExpanded(true);
                }
                if (!streamFlushTimer.isRunning()) {
                    streamFlushTimer.start();
                }
            } else {
                if (activeStreamBubble != null) {
                    activeStreamBubble.flushUpdate(true);
                    activeStreamBubble.finalizeStreaming(allBlocksExpanded);
                    activeStreamBubble = null;
                } else {
                    MessageBubble bubbleToFinalize = findLastNonIgnorableBubble();
                    if (bubbleToFinalize != null) {
                        bubbleToFinalize.finalizeStreaming(allBlocksExpanded);
                    }
                }
                if (streamFlushTimer.isRunning()) {
                    streamFlushTimer.stop();
                }

                addMessage(pm);
            }
        });
    }

    public void stopStreaming() {
        SwingUtilities.invokeLater(() -> {
            if (activeStreamBubble != null) {
                boolean didUpdate = activeStreamBubble.flushUpdate(true);
                if (didUpdate) {
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
            scrollToBottom(true);
        });
    }

    private static class PermissionBubble extends JPanel {
        private static final long serialVersionUID = 1L;
        public PermissionBubble(String prompt, com.fasterxml.jackson.databind.JsonNode options, java.util.concurrent.CompletableFuture<String> responseFuture) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            ColorTheme theme = ThemeManager.getCurrentTheme();

            JPanel content = new JPanel(new BorderLayout(0, 10));
            content.setOpaque(true);
            content.setBackground(theme.permissionBg());
            content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.permissionBorder(), 1, true),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
            ));

            JLabel titleLabel = new JLabel("Permission Required", ThemeManager.getIcon("shield.svg", 18), SwingConstants.LEFT);
            titleLabel.setIconTextGap(8);
            titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
            titleLabel.setForeground(theme.permissionTitle());
            content.add(titleLabel, BorderLayout.NORTH);

            JLabel promptLabel = new JLabel("<html>" + prompt.replace("\n", "<br>") + "</html>");
            promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            content.add(promptLabel, BorderLayout.CENTER);

            int numOptions = (options != null && options.isArray() && options.size() > 0) ? options.size() : 2;
            JPanel buttons = new JPanel(new java.awt.GridLayout(1, numOptions, 4, 0));
            buttons.setOpaque(false);

            if (options != null && options.isArray() && options.size() > 0) {
                LOG.fine("PermissionBubble: rendering {0} options", options.size());
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
            if (getParent() != null) {
                int pw = Math.max(getParent().getWidth(), 100);
                return new Dimension((int) (pw * 0.8), pref.height);
            }
            return new Dimension(pref.width, pref.height);
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
            SwingUtilities.invokeLater(() -> {
                Dimension pref = getPreferredSize();
                setMaximumSize(new Dimension(pref.width, pref.height));
            });
        }
    }

    private void fixMouseWheel(Component c) {
        c.addMouseWheelListener(e -> {
            scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(c, e, scrollPane));
            e.consume();
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        }
        if (streamFlushTimer != null && streamFlushTimer.isRunning()) {
            streamFlushTimer.stop();
        }
        if (scrollTimer != null && scrollTimer.isRunning()) {
            scrollTimer.stop();
        }
    }

    private boolean isAtBottom() {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        int extent = vertical.getModel().getExtent();
        int value = vertical.getValue();
        int maximum = vertical.getMaximum();
        // Use a tight threshold to avoid overriding user scroll intent
        return (value + extent >= maximum - 20);
    }

    public void scrollByBlock(boolean pageUp) {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        if (pageUp) {
            vertical.setValue(vertical.getValue() - vertical.getVisibleAmount());
        } else {
            vertical.setValue(vertical.getValue() + vertical.getVisibleAmount());
        }
    }

    public void scrollToTop() {
        scrollPane.getVerticalScrollBar().setValue(0);
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

            if (scrollTimer.isRunning()) {
                scrollTimer.restart();
            } else {
                scrollTimer.start();
            }
        });
    }

    public void toggleAllBlocks(boolean expanded) {
        allBlocksExpanded = expanded;
        BaseCollapsiblePane.setBatchMode(true);
        try {
            for (Component c : messagesContainer.getComponents()) {
                if (c instanceof MessageBubble bubble) {
                    bubble.toggleAllBlocks(expanded);
                }
            }
        } finally {
            BaseCollapsiblePane.setBatchMode(false);
        }
        messagesContainer.revalidate();
    }

    public boolean isAllBlocksExpanded() {
        return allBlocksExpanded;
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


    public void applyTypeFilters() {
        SwingUtilities.invokeLater(() -> {
            Component[] comps = messagesContainer.getComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i] instanceof MessageBubble bubble) {
                    boolean visible = !MessageFilterManager.isTypeHidden(bubble.getType());
                    comps[i].setVisible(visible);
                    // Trailing strut is always at i+1 (see addSingleBubble)
                    if (i + 1 < comps.length) {
                        comps[i + 1].setVisible(visible);
                    }
                }
            }
            messagesContainer.revalidate();
        });
    }

    public void clearMessages() {
        SwingUtilities.invokeLater(() -> {
            messagesContainer.removeAll();
            messagesContainer.revalidate();
        });
    }

    public void setMessages(List<Message> messages) {
        SwingUtilities.invokeLater(() -> {
            clearMessages();
            for (Message m : messages) {
                addMessage(messageTransformer.convert(m, m.type()));
            }
        });
    }

    public void setSessionList(List<Session> sessions, Consumer<String> onSessionSelected, Runnable onNewChat) {
        LOG.fine("setSessionList: received {0} sessions, onSessionSelected={1}", new Object[]{sessions.size(), onSessionSelected});
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
                        String title = defaultIfBlank(s.title(), "Chat " + left(s.id(), 8));
                        String label = SessionTitleMapper.getTitle(s.id(), title);
                        String dir = s.effectiveDirectory();
                        JButton sessionBtn = createSelectionButtonWithBadge(label, s.projectName(), dir);
                        sessionBtn.addActionListener(e -> onSessionSelected.accept(s.id()));
                        messagesContainer.add(sessionBtn);
                        messagesContainer.add(Box.createVerticalStrut(4));
                    }
                }

                messagesContainer.revalidate();
            } catch (Exception ex) {
                LOG.warn("setSessionList error: {0}", ex.getMessage());
            }
        });
    }

    private JButton createSelectionButton(String text, String subtext) {
        ColorTheme theme = ThemeManager.getCurrentTheme();

        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(8, 0));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY, 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(btn.getPreferredSize().height, 60)));

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
            BorderFactory.createLineBorder(theme.bubbleBorder() != null ? theme.bubbleBorder() : Color.LIGHT_GRAY, 1, true),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(btn.getPreferredSize().height, 60)));

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

    private MessageBubble findLastNonIgnorableBubble() {
        int count = messagesContainer.getComponentCount();
        for (int i = count - 1; i >= 0; i--) {
            Component c = messagesContainer.getComponent(i);
            if (c instanceof MessageBubble mb) {
                if (ProcessedMessage.isIgnorable(mb.getType(), mb.getRawText())) {
                    continue;
                }

                return mb;
            }
        }

        return null;
    }

    private static boolean canMergeMessages(String messageId, String existingMessageId) {
        if (messageId != null && existingMessageId != null) {
            return messageId.equals(existingMessageId);
        }

        return (messageId == null && existingMessageId == null);
    }
}