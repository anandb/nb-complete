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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.fasterxml.jackson.databind.JsonNode;

import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.model.Message;
import github.anandb.netbeans.model.MessageTransformer;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.TimingConstants;
import org.openide.util.Lookup;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;


// DSL-CONTROLLER: not a view — flushTimer + messageQueue EDT bridge +
// trimMessages/cachedMessages logic. The future DSL declares the
// messagesContainer/scrollPane shell; restart()/stop() hot-path calls stay here.
public class ChatThreadPanel extends JPanel {

    private static final Logger LOG = Logger.from(ChatThreadPanel.class);
    private static final long serialVersionUID = 1L;
    private static final Pattern SECTION_SPLIT = Pattern.compile("(?m)^---[ \\t]*$");
    // Maximum visible message bubbles. Configurable via NetBeans preferences
    // (Preferences > Assistant > Chat Behavior > Max Messages); 0 = unlimited.
    // See PluginSettings.getMaxMessages().

    private long lastUserTimestamp = -1L;
    private int userMessageCount = 0;
    private final ConcurrentLinkedQueue<Runnable> messageQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean draining = false;

    // Debounced flush timer. Reset on every processed message so it fires
    // 300ms after the last message is drained from the queue. Replaces the
    // one-shot timers that were created per-turn in SessionLifecycleHandler.
    private final javax.swing.Timer flushTimer;

    private final JPanel messagesContainer;
    private final JScrollPane scrollPane;
    private final JLayeredPane layeredPane;

    // Cached full message list so filter/preference changes can re-render
    // the conversation without a server round-trip.
    private transient volatile List<Message> cachedMessages;
    private final transient ScrollController scrollController;
    private final transient MessageTransformer messageTransformer;
    private final transient StreamingCoordinator streamingCoordinator;

    private volatile boolean allBlocksExpanded = false;
    private volatile boolean keepOlderMessages = false;
    private volatile boolean batchAdding = false;
    private final JProgressBar sessionProgressBar;
    private String currentSessionId;

    // Turn-end gate for flushTimer. When null or false, the deferred-finalize
    // timer restarts instead of firing — guards against premature JTextArea→
    // FitEditorPane conversion during multi-second idle gaps in a live turn.
    private transient volatile java.util.function.BooleanSupplier turnEndedSupplier;

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
        messagesContainer.setDoubleBuffered(true);

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

        // Determinate progress bar for session creation/load (4px tall, full width at top)
        sessionProgressBar = new JProgressBar(0, 100);
        sessionProgressBar.setValue(0);
        sessionProgressBar.setBorderPainted(false);
        sessionProgressBar.setStringPainted(false);
        sessionProgressBar.setPreferredSize(new Dimension(0, 4));
        sessionProgressBar.setVisible(false);
        sessionProgressBar.setFocusable(false);
        sessionProgressBar.setBackground(new java.awt.Color(220, 220, 220));
        sessionProgressBar.setForeground(new java.awt.Color(0, 120, 212));
        layeredPane.add(sessionProgressBar, JLayeredPane.PALETTE_LAYER);

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

        // Listen on the layeredPane itself (not the parent ChatThreadPanel) so
        // children get correct bounds the moment the layeredPane is sized by
        // the parent BorderLayout — avoids the (0,0,0,0) window where the
        // scrollPane has zero size during the initial layout pass.
        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = layeredPane.getWidth();
                int h = layeredPane.getHeight();
                scrollPane.setBounds(0, 0, w, h);
                sessionProgressBar.setBounds(0, 0, w, 4);
                scrollController.positionScrollDownBtn(w, h);
            }
        });

        streamingCoordinator = new StreamingCoordinator(bubble -> {
            if (!isShowing() || bubble == null) {
                return;
            }
            // Check wasAtBottom BEFORE flushUpdate changes content height
            boolean wasAtBottom = scrollController.isAtBottom();
            boolean didUpdate = bubble.flushUpdate();
            if (didUpdate) {
                // No explicit bubble.revalidate() here — the container's
                // layout pass (triggered by scrollToBottom → revalidate)
                // will lay out this bubble. Avoids a redundant layout tick.
                scrollController.scrollToBottom(wasAtBottom);
            }
        }, TimingConstants.STREAM_FLUSH_MS);

        // Shared flush timer for debounced finalization. Reset on each processed
        // message so it fires 300ms after the last message is drained from the
        // queue — no need for extra boolean flags.
        flushTimer = new Timer(TimingConstants.STREAM_FLUSH_MS, e -> {
            if (isDisplayable()) {
                stopStreaming();
            }
        });
        flushTimer.setRepeats(false);
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

    public void setScrollBlocker(java.util.function.BooleanSupplier scrollBlocker) {
        scrollController.setScrollBlocker(scrollBlocker);
    }

    public void addMessage(ProcessedMessage pm) {
        if (pm.isIgnorable()) {
            return;
        }

        messageQueue.add(() -> processMessageOnEDT(pm));
        if (!draining) {
            draining = true;
            SwingUtilities.invokeLater(this::drainMessageQueue);
        }
    }

    private void drainMessageQueue() {
        Runnable task = messageQueue.poll();
        if (task != null) {
            try {
                task.run();
            } catch (Exception ex) {
                LOG.warn("Error processing message: {0}", ex.getMessage());
            } finally {
                // Reset the debounced flush timer on every processed message so it
                // fires 300ms after the last message drains — regardless of whether
                // messages arrived via SSE during a live turn or during session reload.
                flushTimer.restart();
            }
            SwingUtilities.invokeLater(this::drainMessageQueue);
        } else {
            draining = false;
        }
    }

    private void processMessageOnEDT(ProcessedMessage pm) {
        String text = pm.text();
        final String role = pm.messageType().roleName();

        if (pm.streaming()) {
            processMessageSections(pm, text, role);
        } else {
            stopStreaming();
            addSingleBubble(pm.messageType(), text, pm.messageId(), pm.toolTitle(), false);
        }
        trimMessages();
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
                switch (role) {
                    case "thought" -> canMerge = true;
                    case "tool" -> {
                        /**
                            Merge if message IDs match (incl. streaming prefixes) OR
                            if the toolTitle is the same (same tool call in flight).
                            Also merge when the current fragment has a title but the
                            last bubble didn't (first fragment lacked a toolTitle).
                        */
                        String lastTitle = defaultString(lastBubble.getToolTitle());
                        canMerge = canMergeMessages(pm.messageId(), lastBubble.getMessageId())
                                || (pm.toolTitle() != null && pm.toolTitle().equals(lastTitle))
                                || (pm.toolTitle() != null && pm.streaming() && (isBlank(lastTitle)));
                    }
                    default -> canMerge = !"user".equals(role) && canMergeMessages(pm.messageId(), lastBubble.getMessageId());
                }
            }

            if (lastBubble != null && canMerge) {
                lastBubble.appendText(part, pm.toolTitle());
                // If the bubble was already finalized (not streaming), re-render
                // immediately so late deltas are reflected in the HTML content.
                if (!lastBubble.isStreaming()) {
                    boolean wasAtBottom = scrollController.isAtBottom();
                    lastBubble.flushUpdate(true);
                    if (wasAtBottom) {
                        scrollController.scrollToBottom(true);
                    }
                }
            } else {
                // Capture scroll state BEFORE touching content — the finalize
                // below can shrink the bubble (JTextArea→HTML), moving the
                // viewport off-bottom before addSingleBubble captures its own
                // wasAtBottom. Per the auto-scroll contract (AGENTS.md),
                // processMessageSections must capture wasAtBottom before any
                // content mutation.
                boolean wasAtBottomBeforeFinalize = scrollController.isAtBottom();
                if (lastBubble != null) {
                    if (lastBubble == streamingCoordinator.getActiveStreamBubble()) {
                        streamingCoordinator.stopStreaming();
                    }
                    lastBubble.flushUpdate(true);
                    lastBubble.finalizeStreaming(allBlocksExpanded);
                }

                addSingleBubble(pm.messageType(), part, pm.messageId(), pm.toolTitle(), pm.streaming());
                lastBubble = findLastNonIgnorableBubble();
                // Force-scroll if the user was at bottom before the finalize;
                // addSingleBubble's internal check may have seen a post-shrink
                // viewport that is no longer at bottom.
                if (wasAtBottomBeforeFinalize) {
                    scrollController.scrollToBottom(true);
                }
            }
        }
    }

    private void addSingleBubble(MessageType type, String text, String messageId, String toolTitle, boolean streaming) {
        // Capture scroll state BEFORE modifying content
        boolean wasAtBottom = scrollController.isAtBottom();

        // Failsafe: sweep for orphaned streaming JTextAreas before creating
        // any new bubble. Catches cases where a previous streaming bubble's
        // boolean flags were corrupted and never finalized.
        // Skip in batch mode — container was just cleared.
        if (!batchAdding) {
            sweepStreamingBubbles(wasAtBottom);
        }

        // Tool/thought messages create individual bubbles immediately (not accumulated)
        if (type.isTool() || type.isThought()) {
            MessageBubble bubble = BubbleFactory.createToolThoughtBubble(type, text, messageId, toolTitle, streaming);

            boolean visible = !MessageFilterManager.isTypeHidden(type.roleName());
            bubble.setVisible(visible);
            Component strut = Box.createVerticalStrut(4);
            strut.setVisible(visible);
            messagesContainer.add(bubble);
            messagesContainer.add(strut);

            // Streaming bubbles revalidate via the streamer's deferred-finalize
            // path; revalidating here would force a layout pass for each tool
            // chunk before its content is even known to be visible.
            // Skip revalidate in batch mode — single pass at end of setMessages.
            if (!streaming) {
                if (!batchAdding) {
                    messagesContainer.revalidate();
                    if (wasAtBottom) {
                        scrollController.scrollToBottom(true);
                    }
                }
            } else if (wasAtBottom) {
                scrollController.scrollToBottom(true);
            }

            if (streaming) {
                streamingCoordinator.startStreaming(bubble);
            }
            return;
        }

        // Combine individual tool/thought bubbles before user/assistant messages.
        // Runs even in batch mode — the boundary optimization (scanStart finds
        // last user/assistant/combined) limits per-call scan to the current
        // turn's tail, so batch combine cost is O(N) total, not O(N²).
        if (type.isUser() || type.isAssistant()) {
            ToolThoughtCombiner.combine(messagesContainer, allBlocksExpanded, scrollController);
        }

        // Reset turn state for new user message
        if (type.isUser()) {
            userMessageCount++;
        }

        MessageBubble bubble = BubbleFactory.createRoleBubble(type, text, messageId, toolTitle, streaming, userMessageCount, currentSessionId);

        if (lastUserTimestamp > 0 && !"user".equals(type.roleName())) {
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
        // For non-streaming messages, force revalidation so the bubble is laid out.
        // For streaming bubbles, addNotify() revalidates the bubble itself, and
        // the streamer's deferred-finalize path revalidates the container on flush —
        // skipping revalidate here avoids forcing a layout pass per initial chunk.
        // Skip revalidate + scroll in batch mode — single pass at end of setMessages.
        if (!streaming) {
            if (!batchAdding) {
                messagesContainer.revalidate();
            }
        }
        if (wasAtBottom && !batchAdding) {
            scrollController.scrollToBottom(true);
        }
        if (streaming) {
            streamingCoordinator.startStreaming(bubble);
        }
    }

    private void trimMessages() {
        int max = currentMaxMessages();
        if (max <= 0 || keepOlderMessages) return;

        // Count only unpinned bubbles — pinned never count toward the cap.
        int count = 0;
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble mb) {
                if (!mb.isPinned()) {
                    count++;
                }
            } else if (c instanceof PermissionBubble) {
                count++;
            }
        }

        int excess = count - max;
        if (excess <= 0) return;

        int removed = 0;
        for (int i = 0; i < messagesContainer.getComponentCount() && removed < excess; ) {
            Component c = messagesContainer.getComponent(i);
            if (c instanceof MessageBubble mb) {
                if (mb.isPinned()) {
                    i++;
                    continue;
                }
                messagesContainer.remove(i);
                scrollController.unfixMouseWheel(c);
                // Remove trailing strut
                if (i < messagesContainer.getComponentCount()
                        && messagesContainer.getComponent(i) instanceof Box.Filler) {
                    messagesContainer.remove(i);
                }
                removed++;
            } else if (c instanceof PermissionBubble) {
                messagesContainer.remove(i);
                scrollController.unfixMouseWheel(c);
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

    /** Restart the debounced flush timer. Called when a turn-end signal arrives
     *  ({@code responding_finished}) or when a session is loaded. The timer is also
     *  restarted on every processed message in {@link #drainMessageQueue()}, so it
     *  fires 300ms after the last message drains — no extra boolean flags needed. */
    public void restartFlushTimer() {
        flushTimer.restart();
    }

    public void stopStreaming() {
        // Already on EDT — all callers (Timer, SessionLifecycleHandler) invoke via EDT.
        // Do NOT wrap in SwingUtilities.invokeLater: that defers execution, creating a
        // race where late SSE deltas can clear activeStreamBubble before we finalize it.
        boolean anyFinalized = false;
        boolean wasAtBottom = scrollController.isAtBottom();

        MessageBubble activeBubble = streamingCoordinator.stopStreaming();
        if (activeBubble != null) {
            activeBubble.flushUpdate(true);
            if (activeBubble.streamingFlagsSet()) {
                activeBubble.finalizeStreaming(allBlocksExpanded, true);
            } else if (activeBubble.hasStreamingTextArea()) {
                // Boolean flags were corrupted (e.g. exception midway through a
                // previous finalization) but the streaming JTextArea is still
                // in the tree. Normal finalize would short-circuit, so force it.
                activeBubble.forceFinalize(allBlocksExpanded);
            }
            anyFinalized = true;
        }
        // Scan for any remaining streaming bubbles missed by the
        // activeStreamBubble path (e.g. interrupted by tool/thought
        // chunks that reset activeStreamBubble, or late SSE deltas
        // arriving after the responding_finished timer fired).
        // Uses both boolean flags and physical JTextArea presence as
        // dual source of truth for the failsafe sweep.
        anyFinalized |= sweepStreamingBubbles(wasAtBottom);
        if (anyFinalized) {
            messagesContainer.revalidate();
            if (wasAtBottom) {
                scrollController.scrollToBottom(true);
            }
        }
        // Tool/thought combining is NOT done here — it is triggered only by a
        // new user/assistant message arriving via addSingleBubble(). The flush
        // timer is for finalizing streaming assistant content only; calling
        // combine here would prematurely merge tools between messages when
        // the timer fires during a gap in tool streaming.
    }

    /**
     * Sweeps all bubbles in the container for orphaned streaming JTextAreas.
     * Checks both {@link MessageBubble#streamingFlagsSet()} (boolean flags) and
     * {@link MessageBubble#hasStreamingTextArea()} (physical component tree)
     * as dual source of truth.
     * <p>
     * Three cases:
     * <ol>
     *   <li><b>Flags say streaming, no JTextArea</b> — tool/thought bubble
     *       with content-updater streaming (no JTextArea). Normal finalize.
     *   <li><b>Flags say streaming, has JTextArea</b> — normal assistant
     *       stream bubble. Normal finalize works.
     *   <li><b>Flags say NOT streaming, has JTextArea</b> — flag corruption.
     *       {@link MessageBubble#finalizeStreaming} would short-circuit
     *       because it checks the private {@code isStreaming} field directly.
     *       Use {@link MessageBubble#forceFinalize} to bypass the flag check.
     * </ol>
     *
     * @param wasAtBottom whether the scroll was at bottom before this sweep
     * @return true if any bubble was finalized
     */
    private boolean sweepStreamingBubbles(boolean wasAtBottom) {
        boolean anyFinalized = false;
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble mb) {
                boolean flagSaysStreaming = mb.streamingFlagsSet();
                boolean hasTextArea = mb.hasStreamingTextArea();
                if (hasTextArea) {
                    mb.flushUpdate(true);
                    if (flagSaysStreaming) {
                        mb.finalizeStreaming(allBlocksExpanded, true);
                    } else {
                        // Flags corrupted — finalizeStreaming would short-circuit
                        // because it checks the private isStreaming flag directly.
                        mb.forceFinalize(allBlocksExpanded);
                    }
                    anyFinalized = true;
                } else if (flagSaysStreaming) {
                    // No JTextArea (tool/thought content-updater path),
                    // but flags still say streaming. Normal finalize.
                    mb.flushUpdate(true);
                    mb.finalizeStreaming(allBlocksExpanded, true);
                    anyFinalized = true;
                }
            }
        }
        if (anyFinalized) {
            messagesContainer.revalidate();
            if (wasAtBottom) {
                scrollController.scrollToBottom(true);
            }
        }
        return anyFinalized;
    }

    public void addPermissionRequest(String prompt, JsonNode options, CompletableFuture<String> responseFuture, JsonNode toolCall) {
        SwingUtilities.invokeLater(() -> {
            PermissionBubble bubble = new PermissionBubble(prompt, options, responseFuture, toolCall);
            messagesContainer.add(bubble);
            messagesContainer.add(Box.createVerticalStrut(4));
            messagesContainer.revalidate();
            scrollController.scrollToBottom(true);
            trimMessages();
        });
    }

    /** Called by MessageSender when a user message is actually sent via RPC. */
    public void recordUserMessageSent() {
        lastUserTimestamp = System.currentTimeMillis();
    }

    public void setSessionLoading(boolean loading) {
        SwingUtilities.invokeLater(() -> {
            sessionProgressBar.setVisible(loading);
            if (!loading) {
                sessionProgressBar.setValue(100);
            }
        });
    }

    public void setSessionProgress(int percent) {
        SwingUtilities.invokeLater(() -> {
            sessionProgressBar.setVisible(true);
            sessionProgressBar.setValue(Math.max(0, Math.min(100, percent)));
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(scrollController);
        // Re-register wheel-redirect listeners for bubbles that survived a
        // close/reopen cycle. removeNotify → cleanup() clears the wheel map
        // and removes listeners; the ContainerListener only fires for NEW
        // components, so existing bubbles would otherwise have no wheel
        // redirect after restore. Re-adding is safe: the map is empty here.
        for (Component c : messagesContainer.getComponents()) {
            if (c instanceof MessageBubble || c instanceof PermissionBubble) {
                scrollController.fixMouseWheel(c);
            }
        }
    }

    @Override
    public void removeNotify() {
        // Finalize ALL in-flight streaming bubbles before tearing down —
        // otherwise non-active streaming bubbles (interrupted by tool/thought
        // chunks that reset activeStreamBubble, or late SSE deltas) stay as
        // plain JTextArea indefinitely after a close/reopen. stopStreaming()
        // finalizes the active bubble AND scans for any remaining streaming
        // bubbles. Must run before streamingCoordinator.cleanup() stops the
        // flush timer.
        stopStreaming();
        flushTimer.stop();
        streamingCoordinator.cleanup();
        if (scrollController != null) {
            scrollController.cleanup();
        }
        super.removeNotify();
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

    /** Sets the current session ID. Called by SessionLifecycleHandler on session start. */
    public void setSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    public void setKeepOlderMessages(boolean keep) {
        keepOlderMessages = keep;
        // When the user chooses to forget (keep=false), immediately trim the
        // currently-displayed bubbles that exceed MAX_MESSAGES rather than
        // waiting for a future addMessage/trimMessages tick. The forget/remember button
        // listener always fires on the EDT, so it is safe to mutate the
        // component tree synchronously here. Adding a revalidate is required
        // because trimMessages() only removes components — callers are
        // otherwise expected to revalidate themselves, and this path had none.
        if (!keep) {
            trimMessages();
            messagesContainer.revalidate();
            boolean wasAtBottom = scrollController.isAtBottom();
            if (wasAtBottom) {
                scrollController.scrollToBottom(true);
            }
        }
    }

    public boolean isKeepOlderMessages() {
        return keepOlderMessages;
    }

    /** Returns the configured max visible message bubbles — 0 means unlimited.
     *  Reads from {@link PluginSettings} so Options-panel edits take effect
     *  immediately without restarting the IDE. The in-process default (100)
     *  is used only when the preference has never been seeded. */
    private int currentMaxMessages() {
        int max = PluginSettings.getMaxMessages();
        return max;
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
            scrollController.unfixAllMouseWheel();
            lastUserTimestamp = -1L;
            userMessageCount = 0;
            messagesContainer.revalidate();
        });
    }

    public void setMessages(List<Message> messages) {
        cachedMessages = (messages != null) ? new ArrayList<>(messages) : null;
        SwingUtilities.invokeLater(() -> {
            // Clear synchronously — avoid clearMessages()'s own invokeLater
            // which would add an extra EDT tick.
            messagesContainer.removeAll();
            scrollController.unfixAllMouseWheel();
            lastUserTimestamp = -1L;
            userMessageCount = 0;

            if (messages != null) {
                // Prime the pinned-message store so isPinned() reads are fast
                // during bubble construction.
                if (currentSessionId != null) {
                    PinnedMessageControl pinStore = Lookup.getDefault()
                            .lookup(PinnedMessageControl.class);
                    if (pinStore != null) {
                        List<String> ids = new ArrayList<>(messages.size());
                        for (Message m : messages) {
                            if (m.id() != null) {
                                ids.add(m.id());
                            }
                        }
                        pinStore.loadSession(currentSessionId, ids);
                    }
                }

                // Batch mode: skip per-bubble revalidate, sweep, and combine.
                // Single revalidate + scroll at end cuts O(N²) to O(N).
                batchAdding = true;
                try {
                    // Build the render list: the tail that fits the configured
                    // maximum, plus any pinned messages that fall before the tail.
                    List<Message> toRender;
                    int max = currentMaxMessages();
                    if (max > 0 && !keepOlderMessages && messages.size() > max) {
                        int tailStart = messages.size() - max;
                        // Collect pinned messages that occur before the tail.
                        PinnedMessageControl pinStore2 = (currentSessionId != null)
                                ? Lookup.getDefault().lookup(PinnedMessageControl.class) : null;
                        List<Message> pinnedBefore = new ArrayList<>();
                        for (int i = 0; i < tailStart; i++) {
                            Message m = messages.get(i);
                            if (m.id() != null && pinStore2 != null
                                    && pinStore2.isPinned(currentSessionId, m.id())) {
                                pinnedBefore.add(m);
                            }
                        }
                        // Merge: pinned-before first, then the tail, preserving order.
                        toRender = new ArrayList<>(pinnedBefore.size() + max);
                        toRender.addAll(pinnedBefore);
                        toRender.addAll(messages.subList(tailStart, messages.size()));
                    } else {
                        toRender = messages;
                    }
                    for (Message m : toRender) {
                        ProcessedMessage pm = messageTransformer.convert(m);
                        if (pm.isIgnorable()) {
                            continue;
                        }
                        // Bypass addMessage()'s invokeLater — we are already on EDT.
                        if (pm.streaming()) {
                            processMessageSections(pm, pm.text(), pm.messageType().roleName());
                        } else {
                            addSingleBubble(pm.messageType(), pm.text(), pm.messageId(), pm.toolTitle(), false);
                        }
                    }
                } finally {
                    batchAdding = false;
                }
            }
            trimMessages();
            messagesContainer.revalidate();
            scrollController.scrollToBottom(true);
        });
    }

    public void setSessionList(List<Session> sessions, Consumer<String> onSessionSelected, Runnable onNewChat) {
        int count = sessions != null ? sessions.size() : 0;
        LOG.fine("setSessionList: received {0} sessions, onSessionSelected={1}", new Object[]{count, onSessionSelected});
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
