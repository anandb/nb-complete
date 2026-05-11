package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
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
import javax.swing.border.EmptyBorder;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.manager.SessionTitleMapper;
import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageTransformer;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.support.Logger;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.left;
import org.openide.util.NbBundle;

@NbBundle.Messages({
    "HINT_ScrollToBottom=Scroll to bottom",
    "BTN_Allow=Allow",
    "BTN_Deny=Deny",
    "MSG_PermissionGranted=Permission Granted",
    "MSG_PermissionDenied=Permission Denied",
    "LBL_PermissionRequired=Permission Required",
    "LBL_WelcomeToACP=Welcome to ACP",
    "LBL_WelcomeBack=Welcome back!",
    "MSG_NewChatPrompt=Ask questions, generate code, or have me explain anything.",
    "MSG_ExistingChatPrompt=Continue a recent chat or start a new one.",
    "BTN_StartNewChat=✨ Start New Chat",
    "LBL_InFolder=in {0}"
})
public class ChatThreadPanel extends JPanel {
    private static final Logger LOG = new Logger(ChatThreadPanel.class);
    private static final long serialVersionUID = 1L;
    private static final Pattern SECTION_SPLIT = Pattern.compile("(?m)^---[ \\t]*$");
    private static final Color SCROLL_BTN_COLOR_A = new Color(41, 98, 255, 200);
    private static final Color SCROLL_BTN_COLOR_B = new Color(41, 98, 255, 240);
    private long lastUserTimestamp = -1L;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JLayeredPane layeredPane;
    private final JButton scrollDownBtn;
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
        messagesContainer.setBorder(new EmptyBorder(0, 0, 90, 0));

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

        layeredPane = new JLayeredPane();
        add(layeredPane, BorderLayout.CENTER);

        scrollDownBtn = new JButton(ThemeManager.getIcon("scroll-down.svg", 24)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SCROLL_BTN_COLOR_A);
                g2.fillOval(2, 2, getWidth() - 5, getHeight() - 5);
                g2.setColor(SCROLL_BTN_COLOR_B);
                g2.fillOval(3, 3, getWidth() - 7, getHeight() - 7);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        scrollDownBtn.setOpaque(false);
        scrollDownBtn.setContentAreaFilled(false);
        scrollDownBtn.setBorderPainted(false);
        scrollDownBtn.setFocusPainted(false);
        scrollDownBtn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        scrollDownBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        scrollDownBtn.setToolTipText(NbBundle.getMessage(ChatThreadPanel.class, "HINT_ScrollToBottom"));
        scrollDownBtn.setVisible(false);
        scrollDownBtn.addActionListener(e -> {
            scrollToBottom(true);
            scrollDownBtn.setVisible(false);
        });

        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
        layeredPane.add(scrollDownBtn, JLayeredPane.PALETTE_LAYER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = getWidth();
                int h = getHeight();
                layeredPane.setBounds(0, 0, w, h);
                scrollPane.setBounds(0, 0, w, h);
                positionScrollDownBtn();
            }
        });

        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateScrollDownBtnVisibility();
            }
        });

        streamFlushTimer = new javax.swing.Timer(100, e -> {
            if (!isShowing()) {
                return;
            }
            if (activeStreamBubble != null && activeStreamBubble.flushUpdate()) {
                activeStreamBubble.revalidate();
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
                        if (keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN) {
                            Component c = src;
                            while (c != null) {
                                if (c instanceof JComboBox) {
                                    return false;
                                }
                                c = c.getParent();
                            }
                        }
                        JScrollBar vertical = scrollPane.getVerticalScrollBar();
                        switch (keyCode) {
                            case KeyEvent.VK_PAGE_UP -> vertical.setValue(vertical.getValue() - vertical.getVisibleAmount());
                            case KeyEvent.VK_PAGE_DOWN -> vertical.setValue(vertical.getValue() + vertical.getVisibleAmount());
                            case KeyEvent.VK_HOME -> vertical.setValue(vertical.getMinimum());
                            case KeyEvent.VK_END -> vertical.setValue(vertical.getMaximum());
                            default -> {}
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
        if (pm.isIgnorable()) {
            return;
        }

        String text = pm.text();
        final String role = pm.messageType().roleName();

        SwingUtilities.invokeLater(() -> {
            String[] parts = SECTION_SPLIT.split(text);
            MessageBubble lastBubble = findLastNonIgnorableBubble();

            for (String part : parts) {
                if (part.trim().isEmpty() && parts.length > 1) {
                    continue;
                }

                boolean canMerge = (lastBubble != null && role.equals(lastBubble.getRole()));
                if (canMerge) {
                    // Require matching messageIds (or allow if either is null).
                    // Never merge successive user messages.
                    canMerge = !"user".equals(role) && ("thought".equals(role) || canMergeMessages(pm.messageId(), lastBubble.getMessageId()));
                }

                if (lastBubble != null && canMerge) {
                    lastBubble.appendText(part, pm.toolTitle());
                } else {
                    if (lastBubble != null) {
                        // Stop timer + clear activeStreamRef when switching away from streaming bubble
                        if (lastBubble == activeStreamBubble) {
                            activeStreamBubble = null;
                            if (streamFlushTimer.isRunning()) {
                                streamFlushTimer.stop();
                            }
                        }
                        lastBubble.flushUpdate(true);
                        // No-op for non-tool/thought types, restores toolbar default for collapsible panes
                        lastBubble.finalizeStreaming(allBlocksExpanded);
                    }

                    addSingleBubble(pm.messageType(), part, pm.messageId(), pm.toolTitle(), pm.streaming());
                }
            }
        });
    }

    private void addSingleBubble(MessageType type, String text, String messageId, String toolTitle, boolean streaming) {
        MessageBubble bubble = new MessageBubble(type, text, messageId, toolTitle);
        if (type.isTool() || type.isThought()) {
            bubble.setExpanded(allBlocksExpanded);
        }

        if ("user".equals(type.roleName())) {
            lastUserTimestamp = System.currentTimeMillis();
        } else if (lastUserTimestamp > 0) {
            long elapsed = System.currentTimeMillis() - lastUserTimestamp;
            bubble.setResponseTimeMs(elapsed);
            lastUserTimestamp = -1L;
        }

        boolean visible = !MessageFilterManager.isTypeHidden(type.roleName());
        bubble.setVisible(visible);
        Component strut = Box.createVerticalStrut(4);
        strut.setVisible(visible);
        messagesContainer.add(bubble);
        messagesContainer.add(strut);
        if (!streaming) {
            messagesContainer.revalidate();
            scrollToBottom();
        }
        // Set for both streaming and non-streaming: used by stopStreaming() + timer identity check in addMessage()
        activeStreamBubble = bubble;
        if (streaming) {
            streamFlushTimer.start();
        }
    }

    public void stopStreaming() {
        SwingUtilities.invokeLater(() -> {
            if (activeStreamBubble != null) {
                boolean didUpdate = activeStreamBubble.flushUpdate(true);
                // Restore tool/thought panes to toolbar default after streaming ends
                activeStreamBubble.finalizeStreaming(allBlocksExpanded);
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

    public void addPermissionRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture) {
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
        public PermissionBubble(String prompt, JsonNode options, CompletableFuture<String> responseFuture) {
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

            JLabel titleLabel = new JLabel(NbBundle.getMessage(ChatThreadPanel.class, "LBL_PermissionRequired"), ThemeManager.getIcon("shield.svg", 18), SwingConstants.LEFT);
            titleLabel.setIconTextGap(8);
            titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
            titleLabel.setForeground(theme.permissionTitle());
            content.add(titleLabel, BorderLayout.NORTH);

            JLabel promptLabel = new JLabel("<html>" + prompt.replace("\n", "<br>") + "</html>");
            promptLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            content.add(promptLabel, BorderLayout.CENTER);

            int numOptions = (options != null && options.isArray() && options.size() > 0) ? options.size() : 2;
            JPanel buttons = new JPanel(new GridLayout(1, numOptions, 4, 0));
            buttons.setOpaque(false);

            if (options != null && options.isArray() && options.size() > 0) {
                LOG.fine("PermissionBubble: rendering {0} options", options.size());
                for (JsonNode opt : options) {
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
                JButton allowBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Allow"));
                allowBtn.setFocusPainted(false);

                JButton denyBtn = new JButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_Deny"));
                denyBtn.setFocusPainted(false);

                allowBtn.addActionListener(e -> {
                    responseFuture.complete("allow");
                    collapse(content, NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionGranted"), ThemeManager.getIcon("check.svg", 16),
                             new Color(46, 125, 50), new Color(232, 245, 233), new Color(76, 175, 80));
                });

                denyBtn.addActionListener(e -> {
                    responseFuture.complete("reject");
                    collapse(content, NbBundle.getMessage(ChatThreadPanel.class, "MSG_PermissionDenied"), ThemeManager.getIcon("x.svg", 16),
                            new Color(198, 40, 40), new Color(255, 235, 238), new Color(244, 67, 54));
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
        return (value + extent >= maximum - 16);
    }

    private void positionScrollDownBtn() {
        int btnSize = 36;
        int margin = 12;
        scrollDownBtn.setBounds(
            getWidth() - btnSize - margin,
            getHeight() - btnSize - 37,
            btnSize,
            btnSize
        );
    }

    private void updateScrollDownBtnVisibility() {
        scrollDownBtn.setVisible(!isAtBottom());
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
            scrollDownBtn.setVisible(false);

            if (scrollTimer.isRunning()) {
                scrollTimer.restart();
            } else {
                scrollTimer.start();
            }
        });
    }

    public void toggleAllBlocks(boolean expanded) {
        LOG.info("Toggling all blocks to {0}", expanded);
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
                String role = bubble.getRole();
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
                    boolean visible = !MessageFilterManager.isTypeHidden(bubble.getRole());
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
            lastUserTimestamp = -1L;
            messagesContainer.revalidate();
        });
    }

    public void setMessages(List<Message> messages) {
        SwingUtilities.invokeLater(() -> {
            clearMessages();
            for (Message m : messages) {
                addMessage(messageTransformer.convert(m));
            }
        });
    }

    public void setSessionList(List<Session> sessions, Consumer<String> onSessionSelected, Runnable onNewChat) {
        LOG.fine("setSessionList: received {0} sessions, onSessionSelected={1}", new Object[]{sessions.size(), onSessionSelected});
        SwingUtilities.invokeLater(() -> {
            try {
                clearMessages();

                JLabel titleLabel = new JLabel(sessions.isEmpty()
                    ? NbBundle.getMessage(ChatThreadPanel.class, "LBL_WelcomeToACP")
                    : NbBundle.getMessage(ChatThreadPanel.class, "LBL_WelcomeBack"));
                titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
                titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 12, 10, 12));
                messagesContainer.add(titleLabel);

                JLabel subtitle = new JLabel(sessions.isEmpty()
                    ? NbBundle.getMessage(ChatThreadPanel.class, "MSG_NewChatPrompt")
                    : NbBundle.getMessage(ChatThreadPanel.class, "MSG_ExistingChatPrompt"));
                subtitle.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
                subtitle.setForeground(Color.GRAY);
                subtitle.setBorder(BorderFactory.createEmptyBorder(0, 12, 20, 12));
                messagesContainer.add(subtitle);

                JButton newChatBtn = createSelectionButton(NbBundle.getMessage(ChatThreadPanel.class, "BTN_StartNewChat"), null);
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
                        JButton sessionBtn = createSelectionButton(label, dir);
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
            JLabel subLabel = new JLabel(NbBundle.getMessage(ChatThreadPanel.class, "LBL_InFolder", folder));
            subLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            subLabel.setForeground(Color.GRAY);
            textPanel.add(subLabel);
        }

        btn.add(textPanel, BorderLayout.CENTER);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(btn.getPreferredSize().height, 60)));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setOpaque(false);
                btn.setBackground(new Color(0, 0, 0, 10));
                btn.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
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
                if (ProcessedMessage.isIgnorable(mb.getRole(), mb.getRawText())) {
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
