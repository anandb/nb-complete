package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageTransformer;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.support.Logger;

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
    "BTN_StartNewChat=\u2728 Start New Chat",
    "# {0} - folder path",
    "LBL_InFolder=in {0}"
})
public class ChatThreadPanel extends JPanel {
    private static final Logger LOG = new Logger(ChatThreadPanel.class);
    private static final long serialVersionUID = 1L;
    private static final Pattern SECTION_SPLIT = Pattern.compile("(?m)^---[ \\t]*$");
    private long lastUserTimestamp = -1L;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JLayeredPane layeredPane;
    private final ScrollController scrollController;
    private final Timer streamFlushTimer;

    private MessageBubble activeStreamBubble = null;
    private volatile boolean allBlocksExpanded = false;
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

        layeredPane = new JLayeredPane();
        add(layeredPane, BorderLayout.CENTER);

        scrollController = new ScrollController(scrollPane, this, layeredPane);

        layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);

        // Fix: Mouse wheel scrolling often breaks when mouse is over child components
        // like JTextPane. We redirect those events to the main scroll pane.
        messagesContainer.addMouseWheelListener(e -> {
            scrollController.redirectMouseWheel(messagesContainer, e);
        });

        // Automatically fix mouse wheel for message bubbles (skip strut spacers)
        messagesContainer.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                Component c = e.getChild();
                if (c instanceof MessageBubble || c instanceof PermissionBubble) {
                    scrollController.fixMouseWheel(c);
                }
            }
        });

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = getWidth();
                int h = getHeight();
                layeredPane.setBounds(0, 0, w, h);
                scrollPane.setBounds(0, 0, w, h);
                scrollController.positionScrollDownBtn(w, h);
            }
        });

        streamFlushTimer = new Timer(100, e -> {
            if (!isShowing()) {
                return;
            }
            if (activeStreamBubble != null) {
                // Check wasAtBottom BEFORE flushUpdate changes content height
                boolean wasAtBottom = scrollController.isAtBottom();
                if (activeStreamBubble.flushUpdate()) {
                    activeStreamBubble.revalidate();
                    scrollController.scrollToBottom(wasAtBottom);
                }
            }
        });
        streamFlushTimer.setRepeats(true);
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
        // Tool updates are discrete (not incremental chunks), so always scroll.
        // Other streaming types rely on the timer for scroll-to-bottom.
        boolean shouldScroll = !streaming || type.isTool();
        if (shouldScroll) {
            messagesContainer.revalidate();
            scrollController.scrollToBottom();
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
                    scrollController.scrollToBottom();
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
            scrollController.scrollToBottom(true);
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (streamFlushTimer != null && streamFlushTimer.isRunning()) {
            streamFlushTimer.stop();
        }
        if (scrollController != null) {
            scrollController.cleanup();
        }
    }

    public void scrollByBlock(boolean pageUp) {
        scrollController.scrollByBlock(pageUp);
    }

    public void scrollToTop() {
        scrollController.scrollToTop();
    }

    public void scrollToBottom() {
        scrollController.scrollToBottom();
    }

    public void scrollToBottom(boolean force) {
        scrollController.scrollToBottom(force);
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
                WelcomeScreen.show(messagesContainer, sessions, onSessionSelected, onNewChat);
            } catch (Exception ex) {
                LOG.warn("setSessionList error: {0}", ex.getMessage());
            }
        });
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
