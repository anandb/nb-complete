package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.ArrayList;
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
import org.openide.util.NbPreferences;


public class ChatThreadPanel extends JPanel {

    // --- inner class: merged from MessageFilterManager -----------------------
    static final class MessageFilterManager {
        private static final String PREF_PREFIX = "messageFilter.";
        private static final String[] MESSAGE_TYPES = {"tool", "thought", "assistant", "user"};
        private MessageFilterManager() {}

        static String[] getMessageTypes() { return MESSAGE_TYPES.clone(); }

        /** Return filter types shown in the UI menu. When combine is on, "activity"
         *  replaces separate "tool" and "thought" entries. */
        static String[] getEffectiveMessageTypes() {
            if (NbPreferences.forModule(ACPOptionsPanel.class).getBoolean("combineToolThought", true)) {
                return new String[]{"activity", "assistant", "user"};
            }
            return MESSAGE_TYPES.clone();
        }

        static boolean isTypeHidden(String type) {
            if (type == null) return false;
            // "activity" is a virtual type — hidden if either tool or thought is hidden
            if ("activity".equals(type)) {
                return NbPreferences.forModule(ACPOptionsPanel.class)
                        .getBoolean(PREF_PREFIX + "tool", false)
                    || NbPreferences.forModule(ACPOptionsPanel.class)
                        .getBoolean(PREF_PREFIX + "thought", false);
            }
            return NbPreferences.forModule(ACPOptionsPanel.class).getBoolean(PREF_PREFIX + type, false);
        }

        static void setTypeHidden(String type, boolean hidden) {
            if (type == null) return;
            // "activity" toggles both tool and thought together
            if ("activity".equals(type)) {
                NbPreferences.forModule(ACPOptionsPanel.class).putBoolean(PREF_PREFIX + "tool", hidden);
                NbPreferences.forModule(ACPOptionsPanel.class).putBoolean(PREF_PREFIX + "thought", hidden);
                return;
            }
            NbPreferences.forModule(ACPOptionsPanel.class).putBoolean(PREF_PREFIX + type, hidden);
        }
    }
    // -------------------------------------------------------------------------

    private static final Logger LOG = Logger.from(ChatThreadPanel.class);
    private static final long serialVersionUID = 1L;
    private static final Pattern SECTION_SPLIT = Pattern.compile("(?m)^---[ \\t]*$");
    private static final int MAX_MESSAGES = 100;
    private long lastUserTimestamp = -1L;
    private int userMessageCount = 0;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JLayeredPane layeredPane;
    private final Timer streamFlushTimer;

    /** Cached full message list so filter/preference changes can re-render
     *  the conversation without a server round-trip. */
    private volatile List<Message> cachedMessages;
    private volatile boolean allBlocksExpanded = false;
    private volatile boolean keepOlderMessages = false;
    private final transient ScrollController scrollController;
    private final transient MessageTransformer messageTransformer;
    private MessageBubble activeStreamBubble;

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
        messagesContainer.setBorder(new EmptyBorder(0, 0, 0, 0));

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

        streamFlushTimer = new Timer(150, e -> {
            if (!isShowing()) {
                return;
            }
            if (activeStreamBubble != null) {
                // Check wasAtBottom BEFORE flushUpdate changes content height
                boolean wasAtBottom = scrollController.isAtBottom();
                boolean didUpdate = activeStreamBubble.flushUpdate();
                if (didUpdate) {
                    activeStreamBubble.revalidate();
                    scrollController.scrollToBottom(wasAtBottom);
                }
            }
        });
        streamFlushTimer.setRepeats(true);
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        ScrollablePanel() {
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
            if (pm.streaming()) {
                processMessageSections(pm, text, role);
            } else {
                // Non-streaming messages are complete — finalize any in-flight
                // streaming bubble before creating a new one, otherwise the old
                // streaming bubble is orphaned without a FitEditorPane.
                stopStreaming();
                addSingleBubble(pm.messageType(), text, pm.messageId(), pm.toolTitle(), false);
            }
            trimMessages();
        });
    }

    /** Process message sections on EDT (shared by addMessage and setMessages). */
    private void processMessageSections(ProcessedMessage pm, String text, String role) {
        String[] parts = SECTION_SPLIT.split(text);
        MessageBubble lastBubble = findLastNonIgnorableBubble();

        for (String part : parts) {
            if (part.trim().isEmpty() && parts.length > 1) {
                continue;
            }

            boolean canMerge = (lastBubble != null && role.equals(lastBubble.getRole()));
            if (canMerge) {
                if ("thought".equals(role)) {
                    canMerge = true;
                } else if ("tool".equals(role)) {
                    // Merge if message IDs match (incl. streaming prefixes) OR
                    // if the toolTitle is the same (same tool call in flight).
                    // Also merge when the current fragment has a title but the
                    // last bubble didn't (first fragment lacked a toolTitle).
                    String lastTitle = lastBubble.getToolTitle();
                    canMerge = canMergeMessages(pm.messageId(), lastBubble.getMessageId())
                            || (pm.toolTitle() != null && pm.toolTitle().equals(lastTitle))
                            || (pm.toolTitle() != null && pm.streaming()
                                    && (lastTitle == null || lastTitle.isEmpty()));
                } else {
                    canMerge = !"user".equals(role) && canMergeMessages(pm.messageId(), lastBubble.getMessageId());
                }
            }

            if (lastBubble != null && canMerge) {
                lastBubble.appendText(part, pm.toolTitle());
                // If the bubble was already finalized (not streaming), re-render
                // immediately so late deltas are reflected in the HTML content.
                if (!lastBubble.isStreaming()) {
                    lastBubble.flushUpdate(true);
                    scrollController.scrollToBottom();
                }
            } else {
                if (lastBubble != null) {
                    if (lastBubble == activeStreamBubble) {
                        activeStreamBubble = null;
                        if (streamFlushTimer.isRunning()) {
                            streamFlushTimer.stop();
                        }
                    }
                    lastBubble.flushUpdate(true);
                    lastBubble.finalizeStreaming(allBlocksExpanded);
                }

                addSingleBubble(pm.messageType(), part, pm.messageId(), pm.toolTitle(), pm.streaming());
                lastBubble = findLastNonIgnorableBubble();
            }
        }
    }

    private void addSingleBubble(MessageType type, String text, String messageId, String toolTitle, boolean streaming) {
        // Tool/thought messages create individual bubbles immediately (not accumulated)
        if (type.isTool() || type.isThought()) {
            // Use "Thinking Process" title for thought bubbles so they show brain icon
            if (type.isThought() && (toolTitle == null || toolTitle.isEmpty())) {
                toolTitle = NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ThinkingProcess");
            }
            MessageBubble.AvatarPosition avatarPos = MessageBubble.AvatarPosition.NONE;
            MessageBubble bubble = new MessageBubble(type, text, messageId, toolTitle, avatarPos, streaming);

            boolean visible = !ChatThreadPanel.MessageFilterManager.isTypeHidden(type.roleName());
            bubble.setVisible(visible);
            Component strut = Box.createVerticalStrut(4);
            strut.setVisible(visible);
            messagesContainer.add(bubble);
            messagesContainer.add(strut);

            messagesContainer.revalidate();
            scrollController.scrollToBottom();

            if (streaming) {
                activeStreamBubble = bubble;
                streamFlushTimer.start();
            }
            return;
        }

        // Combine individual tool/thought bubbles before user/assistant messages
        if (type.isUser() || type.isAssistant()) {
            ToolThoughtCombiner.combine(messagesContainer, allBlocksExpanded);
        }

        // Reset turn state for new user message
        if (type.isUser()) {
            userMessageCount++;
        }

        MessageBubble.AvatarPosition avatarPos = "user".equals(type.roleName())
                ? (userMessageCount % 2 == 1 ? MessageBubble.AvatarPosition.LEFT : MessageBubble.AvatarPosition.RIGHT)
                : MessageBubble.AvatarPosition.NONE;
        MessageBubble bubble = new MessageBubble(type, text, messageId, toolTitle, avatarPos, streaming);

        if ("user".equals(type.roleName())) {
            lastUserTimestamp = System.currentTimeMillis();
        } else if (lastUserTimestamp > 0) {
            long elapsed = System.currentTimeMillis() - lastUserTimestamp;
            bubble.setResponseTimeMs(elapsed);
            lastUserTimestamp = -1L;
        }

        boolean visible = !ChatThreadPanel.MessageFilterManager.isTypeHidden(type.roleName());
        bubble.setVisible(visible);
        Component strut = Box.createVerticalStrut(4);
        strut.setVisible(visible);
        messagesContainer.add(bubble);
        messagesContainer.add(strut);
        boolean shouldScroll = !streaming || type.isTool();
        if (shouldScroll) {
            messagesContainer.revalidate();
            scrollController.scrollToBottom();
        }
        activeStreamBubble = bubble;
        if (streaming) {
            streamFlushTimer.start();
        }
    }

    private void trimMessages() {
        if (MAX_MESSAGES <= 0 || keepOlderMessages) return;

        int count = 0;
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble || c instanceof PermissionBubble) {
                count++;
            }
        }

        int excess = count - MAX_MESSAGES;
        if (excess <= 0) return;

        int removed = 0;
        for (int i = 0; i < messagesContainer.getComponentCount() && removed < excess; ) {
            Component c = messagesContainer.getComponent(i);
            if (c instanceof MessageBubble || c instanceof PermissionBubble) {
                messagesContainer.remove(i);
                // Remove trailing strut
                if (i < messagesContainer.getComponentCount()
                        && messagesContainer.getComponent(i) instanceof Box.Filler) {
                    messagesContainer.remove(i);
                }
                removed++;
            } else {
                i++;
            }
        }
    }

    public void stopStreaming() {
        // Already on EDT — all callers (Timer, SessionLifecycleHandler) invoke via EDT.
        // Do NOT wrap in SwingUtilities.invokeLater: that defers execution, creating a
        // race where late SSE deltas can clear activeStreamBubble before we finalize it.
        if (activeStreamBubble != null) {
            activeStreamBubble.flushUpdate(true);
            activeStreamBubble.finalizeStreaming(allBlocksExpanded, true);
            activeStreamBubble = null;
            messagesContainer.revalidate();
            scrollController.scrollToBottom();
        }
        // Scan for any remaining streaming bubbles missed by the
        // activeStreamBubble path (e.g. interrupted by tool/thought
        // chunks that reset activeStreamBubble, or late SSE deltas
        // arriving after the responding_finished timer fired).
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble mb && mb.isStreaming()) {
                mb.flushUpdate(true);
                mb.finalizeStreaming(allBlocksExpanded, true);
                messagesContainer.revalidate();
                scrollController.scrollToBottom();
            }
        }
        if (streamFlushTimer.isRunning()) {
            streamFlushTimer.stop();
        }
        // Combine any remaining individual tool/thought bubbles
        ToolThoughtCombiner.combine(messagesContainer, allBlocksExpanded);
    }

    public void addPermissionRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture) {
        SwingUtilities.invokeLater(() -> {
            PermissionBubble bubble = new PermissionBubble(prompt, options, responseFuture);
            messagesContainer.add(bubble);
            messagesContainer.add(Box.createVerticalStrut(4));
            messagesContainer.revalidate();
            scrollController.scrollToBottom(true);
            trimMessages();
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(scrollController);
    }

    @Override
    public void removeNotify() {
        // Finalize any in-flight streaming bubble before tearing down —
        // otherwise the bubble stays as a plain JTextArea indefinitely.
        if (activeStreamBubble != null) {
            activeStreamBubble.finalizeStreaming(allBlocksExpanded, true);
            activeStreamBubble = null;
        }
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

    public void setKeepOlderMessages(boolean keep) {
        keepOlderMessages = keep;
    }

    public boolean isKeepOlderMessages() {
        return keepOlderMessages;
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

                if (null == role) {
                    sb.append("## ASSISTANT\n\n");
                    sb.append(text).append("\n\n");
                } else switch (role) {
                    case "user" -> {
                        sb.append("## USER\n\n");
                        sb.append(text).append("\n\n");
                    }
                    case "thought" -> sb.append("> **Thinking:**\n> ").append(text.replace("\n", "\n> ")).append("\n\n");
                    case "tool" -> sb.append("`Tool Output: ").append(text.replace("`", "'")).append("`\n\n");
                    default -> {
                        sb.append("## ASSISTANT\n\n");
                        sb.append(text).append("\n\n");
                    }
                }
                sb.append("---\n\n");
            }
        }
        return sb.toString();
    }

    public void applyTypeFilters() {
        // Re-render from cached messages when available — avoids subtle
        // visibility/revalidation bugs with hidden-shown combined bubbles.
        List<Message> msgs = cachedMessages;
        if (msgs != null && !msgs.isEmpty()) {
            setMessages(msgs);
            return;
        }
        // Fallback: visibility-only toggle (legacy path when cache not populated).
        SwingUtilities.invokeLater(() -> {
            boolean toolHidden = MessageFilterManager.isTypeHidden("tool");
            boolean thoughtHidden = MessageFilterManager.isTypeHidden("thought");

            Component[] comps = messagesContainer.getComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i] instanceof MessageBubble bubble) {
                    // Combined bubbles need segment-level re-filtering
                    if (Boolean.TRUE.equals(bubble.getClientProperty("nb-complete.combined"))) {
                        @SuppressWarnings("unchecked")
                        List<CollapsibleToolPane.ToolSegment> allSegments =
                                (List<CollapsibleToolPane.ToolSegment>) bubble.getClientProperty("nb-complete.segments");
                        if (allSegments != null) {
                            List<CollapsibleToolPane.ToolSegment> visibleSegments = new ArrayList<>();
                            for (CollapsibleToolPane.ToolSegment seg : allSegments) {
                                if ((seg.isThought() && !thoughtHidden)
                                        || (!seg.isThought() && !toolHidden)) {
                                    visibleSegments.add(seg);
                                }
                            }
                            if (visibleSegments.isEmpty()) {
                                bubble.setVisible(false);
                                if (i + 1 < comps.length) comps[i + 1].setVisible(false);
                            } else {
                                String newTitle = "Execution Steps (" + visibleSegments.size() + ")";
                                bubble.setVisible(true);
                                if (i + 1 < comps.length) comps[i + 1].setVisible(true);
                                bubble.updateCombinedContent(visibleSegments, newTitle);
                                bubble.revalidate();
                            }
                            continue;
                        }
                    }
                    // Normal (non-combined) bubble visibility
                    boolean visible = !MessageFilterManager.isTypeHidden(bubble.getRole());
                    boolean wasHidden = !comps[i].isVisible();
                    comps[i].setVisible(visible);
                    if (i + 1 < comps.length) {
                        comps[i + 1].setVisible(visible);
                    }
                    // Re-shown bubbles need revalidation so internal layout
                    // (GridBagLayout, BoxLayout, etc.) recomputes child bounds.
                    if (wasHidden && visible) {
                        comps[i].revalidate();
                    }
                }
            }
            messagesContainer.revalidate();
        });
    }

    public void clearMessages() {
        cachedMessages = null;
        SwingUtilities.invokeLater(() -> {
            messagesContainer.removeAll();
            lastUserTimestamp = -1L;
            userMessageCount = 0;
            messagesContainer.revalidate();
        });
    }

    public void setMessages(List<Message> messages) {
        cachedMessages = (messages != null) ? new ArrayList<>(messages) : null;
        SwingUtilities.invokeLater(() -> {
            clearMessages();
            if (messages != null) {
                for (Message m : messages) {
                    addMessage(messageTransformer.convert(m));
                }
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
        if (messageId == null || existingMessageId == null) {
            return (messageId == null && existingMessageId == null);
        }
        if (messageId.equals(existingMessageId)) {
            return true;
        }
        // Handle streaming suffix IDs like "msg1_0"/"msg1_1" or "msg1-0"/"msg1-1".
        // If one is a strict prefix of the other followed by a separator, merge.
        return messageId.startsWith(existingMessageId + "-") || existingMessageId.startsWith(messageId + "-")
            || messageId.startsWith(existingMessageId + "_") || existingMessageId.startsWith(messageId + "_");
    }
}
