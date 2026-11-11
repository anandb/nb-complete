package github.anandb.netbeans.ui.chat;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.JTextArea;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.TimingConstants;
import github.anandb.netbeans.support.ToolDataExtractor;
import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.length;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.components.AccordionGroup;
import github.anandb.netbeans.ui.components.UIUtils;
import github.anandb.netbeans.ui.components.RoundedPanel;


// DSL-LEAF: keep imperative, wrap via UI.of(...) — Scrollable impl + heavy
// delegation (segments/codeStates/accordionManager/contentRenderer/streamer/
// themeApplier). Bubble shell stays a leaf; its sub-specs (header, code
// toolbar) are extracted to ui/spec/.
public class MessageBubble extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    /** Whether the user avatar is shown beside the bubble. */
    public enum AvatarPosition { LEFT, NONE }

    private JPanel bubble;
    private String toolTitle;
    private final ArrayList<BubbleContentRenderer.CollapsibleState> codeStates = new ArrayList<>();
    private final JPanel segments;
    private String messageId;
    /** True when {@link #messageId} is our content hash, not a harness id: two
     *  messages with equal text share it, so it must never drive merging. */
    private boolean generatedMessageId;
    private final String role;
    private final StringBuilder text;
    private final transient BubbleAccordionManager accordionManager;
    private final transient BubbleContentRenderer contentRenderer;
    private final transient BubbleStreamer streamer;
    private final transient BubbleThemeApplier themeApplier;
    private final transient BubbleActionBar actionBar;
    private transient HierarchyListener hierarchyListener;

    @Override
    public float getAlignmentX() {
        return Component.LEFT_ALIGNMENT;
    }

    private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);

    /** Corner radius: a card, not a pill (32 made short bubbles look like capsules). */
    private static final int BUBBLE_RADIUS = 22;
    /** Bubble padding; the text panes add their own inset on top of this. */
    private static final int BUBBLE_PAD = 6;
    /**
     * User messages are right-aligned at 96% of the thread width, capped at 1024 px. The avatar
     * shares this width. The assistant bubble has no cap — long answers need the full thread width.
     */
    private static final float USER_WIDTH_RATIO = 0.96f;
    private static final int USER_MAX_WIDTH_PX = 1024;

    /**
     * Cached preferred size: the thread's BoxLayout asks every bubble for its preferred and
     * maximum size on every layout pass, and a pass runs on each streaming tick (the scroll
     * pin calls validate()). Without the cache each pass re-runs this bubble's GridBagLayout and
     * its segments' BoxLayout, so a long thread made every tick O(all bubbles). Only the bubble
     * whose content grew is invalidated, so a tick now costs O(bubbles) dimension reads plus
     * O(1) real layout.
     */
    private Dimension cachedPreferredSize;

    @Override
    public void invalidate() {
        cachedPreferredSize = null;
        super.invalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension cached = cachedPreferredSize;
        if (cached == null) {
            cached = super.getPreferredSize();
            cachedPreferredSize = cached;
        }
        return cached;
    }

    @Override
    public Dimension getMaximumSize() {
        return MAX_SIZE;
    }

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle, AvatarPosition avatarPosition) {
        this(type, text, messageId, toolTitle, avatarPosition, false, null);
    }

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle, AvatarPosition avatarPosition, boolean streaming) {
        this(type, text, messageId, toolTitle, avatarPosition, streaming, null);
    }

    public MessageBubble(MessageType type, String text, String messageId,
            String toolTitle, AvatarPosition avatarPosition,
            boolean streaming, String sessionId) {
        this.role = type.roleName();
        this.messageId = messageId;
        // User bubbles may carry <metadata> context blocks. Strip complete
        // blocks up front so non-streaming user messages — local echo,
        // session reload — never display them; the streamed chunk tails
        // are still stripped in finalizeStreaming().
        this.text = new StringBuilder("user".equals(type.roleName())
                ? ToolDataExtractor.stripMetadata(text) : text);
        this.toolTitle = toolTitle;

        ColorTheme theme = ThemeManager.getCurrentTheme();
        setLayout(new GridBagLayout());
        setAlignmentY(Component.CENTER_ALIGNMENT);
        setOpaque(true);
        setBackground(theme.sunkenBackground());
        setDoubleBuffered(true);
        setBorder(new EmptyBorder(2, 8, 2, 8));

        // Accessible context for screen readers
        getAccessibleContext().setAccessibleName(NbBundle.getMessage(UIBundle.class,
                "ACR_MessageBubble_" + role));
        getAccessibleContext().setAccessibleDescription(NbBundle.getMessage(UIBundle.class,
                "ACR_MessageBubbleDesc_" + role));

        segments = new JPanel();
        segments.setLayout(new BoxLayout(segments, BoxLayout.Y_AXIS));
        segments.setAlignmentX(Component.LEFT_ALIGNMENT);
        segments.setDoubleBuffered(true);

        this.bubble = new JPanel(new BorderLayout());
        this.bubble.setDoubleBuffered(true);
        if (null == role) {
            this.bubble.setBorder(new EmptyBorder(2, 8, 2, 8));
        } else switch (role) {
            case "user" -> {
                    RoundedPanel p = new RoundedPanel(BUBBLE_RADIUS);
                    p.setLayout(new BorderLayout());
                    p.setBorder(new EmptyBorder(BUBBLE_PAD, BUBBLE_PAD, BUBBLE_PAD, BUBBLE_PAD));
                    this.bubble = p;
                }
            case "assistant" -> {
                    RoundedPanel p = new RoundedPanel(BUBBLE_RADIUS);
                    p.setLayout(new BorderLayout());
                    p.setBorder(new EmptyBorder(BUBBLE_PAD, BUBBLE_PAD, BUBBLE_PAD, BUBBLE_PAD));
                    this.bubble = p;
                }
            default -> this.bubble.setBorder(new EmptyBorder(2, 8, 2, 8));
        }
        this.bubble.add(segments, BorderLayout.CENTER);
        this.themeApplier = new BubbleThemeApplier(this, segments, bubble, messageId, role);

        this.streamer = new BubbleStreamer(
            (t, e) -> {
                // updateContent triggers segments.revalidate() internally; the
                // call site (ChatThreadPanel / BubbleStreamer.performFinalization)
                // handles parent revalidation. Avoid redundant revalidate+repaint
                // here — this lambda fires on every deferred-finalize tick.
                updateContent(t, e);
            },
            (expanded, full) -> {
                if (!full) {
                    for (Component c : segments.getComponents()) {
                        if (c instanceof CollapsibleCodePane codePane) {
                            codePane.setExpanded(expanded);
                        } else if (c instanceof CollapsibleToolPane toolPane) {
                            toolPane.setExpanded(expanded);
                        } else if (c instanceof CollapsibleActivityPane activityPane) {
                            activityPane.setExpanded(expanded);
                        }
                    }
                    revalidate();
                    repaint();
                }
            },
            segments, this.text
        );

        this.contentRenderer = new BubbleContentRenderer(segments, this.text, role, codeStates, this.streamer);
        this.accordionManager = new BubbleAccordionManager(segments);

        if ("assistant".equals(role) && streaming) {
            JTextArea ta = streamer.createStreamingTextArea(theme, text);
            streamer.setStreamingTextArea(ta);
            segments.add(ta);
            streamer.setLastDisplayedLength(this.text.length());
        } else {
            updateContent(theme, false);
        }
        boolean isAssistant = "assistant".equals(role);

        Insets gbcInsets = new Insets(4, 12, 4, 12);
        int anchor = isAssistant ? GridBagConstraints.NORTHWEST : GridBagConstraints.WEST;
        int fill = GridBagConstraints.HORIZONTAL;

        themeApplier.applyBubbleTheme(theme, role);

        if (!"user".equals(role)) {
            // AI messages use full width
            gbcInsets.left = 12;
            gbcInsets.right = 12;
        }

        // Action buttons (copy, pin) — hover logic, timers, pin state all managed by BubbleActionBar.
        this.actionBar = new BubbleActionBar(bubble, role, messageId, sessionId, this.text, theme, this);
        actionBar.setupActionButtons(theme);

        if ("user".equals(role) && avatarPosition != AvatarPosition.NONE) {
            // Wrap bubble + external avatar in a content row
            JPanel contentRow = new JPanel(new BorderLayout());
            contentRow.setOpaque(false);
            contentRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel userLabel = themeApplier.createUserAvatar();
            JPanel avatarWrapper = UIUtils.createTransparentPanel(new BorderLayout());
            avatarWrapper.add(userLabel, BorderLayout.CENTER);

            contentRow.add(avatarWrapper, BorderLayout.WEST);
            contentRow.add(bubble, BorderLayout.CENTER);
            addCappedRow(contentRow, gbcInsets, anchor, fill);
        } else if (isAssistant || "user".equals(role)) {
            addCappedRow(bubble, gbcInsets, anchor, fill);
        } else {
            add(bubble, UIUtils.createGbc(0, 0, 1.0, 0, fill, anchor, gbcInsets));
        }

        // Fix for "garbled" text in scroll panes: force a repaint when the component becomes visible.
        // Only repaint the outermost component — Swing's repaint manager handles children.
        // revalidate recomputes the preferred height once real widths are known —
        // the first layout pass can size the wrapping JTextArea before it has a real
        // width, leaving the bubble one line short.
        if ("user".equals(role)) {
            this.hierarchyListener = e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                    SwingUtilities.invokeLater(() -> {
                        revalidate();
                        repaint();
                    });
                }
            };
            addHierarchyListener(this.hierarchyListener);
        }

    }

    /** Wraps chat content in the role's row: the user's capped block, or the assistant's full width. */
    private void addCappedRow(Component content, Insets insets, int anchor, int fill) {
        boolean user = "user".equals(role);
        AlignedRow row = user
                ? new AlignedRow(USER_WIDTH_RATIO, USER_MAX_WIDTH_PX, true)
                : AlignedRow.fullWidth();
        row.add(content);
        add(row, UIUtils.createGbc(0, 0, 1.0, 0, fill, anchor, insets));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Parent container's revalidate handles layout; no per-bubble
        // revalidate/repaint needed here.
    }

    @Override
    public void removeNotify() {
        // Stop timers and remove listeners BEFORE super.removeNotify()
        // to avoid callbacks firing on a partially-dismantled component tree.
        streamer.stopTimer();
        actionBar.stopAllTimers();
        if (hierarchyListener != null) {
            removeHierarchyListener(hierarchyListener);
            hierarchyListener = null;
        }
        super.removeNotify();
    }

    public void appendText(String newText) {
        appendText(newText, "");
    }

    public void appendText(String newText, String toolTitle) {
        if (newText == null || newText.isEmpty()) {
            return;
        }
        this.toolTitle = length(this.toolTitle) < length(toolTitle) ? toolTitle : this.toolTitle;
        streamer.appendText(newText);
    }

    public boolean flushUpdate() {
        return streamer.flushUpdate(false);
    }

    public boolean flushUpdate(boolean force) {
        return streamer.flushUpdate(force);
    }

    /**
     * Registers the tool/activity pane inside this bubble with an accordion group,
     * so it participates in cross-bubble accordion behavior.
     * No-op if this bubble does not contain a tool/thought pane.
     */
    public void registerWithAccordionGroup(AccordionGroup group) {
        accordionManager.registerWithAccordionGroup(group);
    }

    private void updateContent(ColorTheme theme, boolean expanded) {
        contentRenderer.updateContent(theme, expanded, toolTitle);
    }

    /** Applies or removes a queued indicator on the bubble. */
    void setQueued(boolean queued) {
        actionBar.setQueued(queued);
    }

    /**
     * Finalizes streaming. User bubbles stay plain text; assistant converts to HTML.
     * @param expanded initial collapse state for code/tool panes
     */
    public void finalizeStreaming(boolean expanded) {
        finalizeStreaming(expanded, false);
    }

    /**
     * Finalizes streaming.
     * @param expanded  initial collapse state for code/tool panes
     * @param immediate if true, build HTML now; if false, defer for 300 ms
     *                  to let additional deltas accumulate (avoids repeated
     *                  expensive rebuilds during section splits).
     */
    public void finalizeStreaming(boolean expanded, boolean immediate) {
        // User bubbles accumulate trailing metadata blocks (preamble echo, DCP
        // blocks, etc.); strip them before the final display. Copy already
        // strips via MessageCopyMouseAdapter — this fixes the on-screen text.
        if ("user".equals(role)) {
            String stripped = ToolDataExtractor.stripMetadata(text.toString()).trim();
            if (!stripped.equals(text.toString())) {
                text.setLength(0);
                text.append(stripped);
            }
            // If the user message is empty after stripping metadata, hide and
            // remove the bubble so it doesn't appear as a blank gap.
            if (text.isEmpty()) {
                setVisible(false);
                Container parent = getParent();
                if (parent != null) {
                    parent.remove(this);
                    // Remove the trailing strut that was added with the bubble.
                    Component[] siblings = parent.getComponents();
                    for (int i = 0; i < siblings.length; i++) {
                        if (siblings[i] == this && i + 1 < siblings.length
                                && siblings[i + 1] instanceof Box.Filler) {
                            parent.remove(siblings[i + 1]);
                            break;
                        }
                    }
                    parent.revalidate();
                    parent.repaint();
                }
                return;
            }
            updateContent(ThemeManager.getCurrentTheme(), expanded);
        }
        streamer.finalizeStreaming(expanded, immediate);
    }

    /**
     * Force-finalizes streaming without checking boolean flags. Used by the
     * sweep failsafe when flag corruption is suspected.
     * @param expanded initial collapse state for code/tool panes
     */
    void forceFinalize(boolean expanded) {
        streamer.forceFinalize(expanded);
    }

    /** Arms the safety-net cooldown for a newly created streaming bubble. */
    void scheduleStreamingCooldown(boolean expanded) {
        streamer.finalizeStreaming(expanded, false, TimingConstants.STREAM_BUBBLE_FINALIZE_MS);
    }

    /**
     * Updates both the title and segmented content of the combined activity pane.
     * Used by applyTypeFilters() when re-filtering changes the visible count.
     */
    public void updateCombinedContent(List<CollapsibleToolPane.ToolSegment> blocks, String title) {
        this.toolTitle = title;
        contentRenderer.updateCombinedContent(blocks, title);
    }

    //<editor-fold defaultstate="collapsed" desc="Properties">
    public void setExpanded(boolean expanded) {
        accordionManager.setExpanded(expanded);
    }

    public void toggleAllBlocks(boolean expanded) {
        accordionManager.toggleAllBlocks(expanded);
    }

    public String getRole() {
        return role;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getToolTitle() {
        return toolTitle;
    }

    public String getRawText() {
        return text.toString();
    }

    /** Returns true if this bubble is still in streaming mode (not yet converted to HTML)
     *  or if finalization has been deferred pending a cooldown period. */
    public boolean isStreaming() {
        return streamer.isStreaming();
    }

    /**
     * Returns true if the boolean streaming flags are set, ignoring the physical
     * JTextArea presence. Used by {@code ChatThreadPanel} to decide between
     * normal and force finalization in the sweep failsafe.
     */
    boolean streamingFlagsSet() {
        return streamer.streamingFlagsSet();
    }

    /**
     * Returns true if the streaming JTextArea is still present in the component
     * tree. This is the physical source of truth — unlike {@link #isStreaming()}
     * it catches cases where boolean flags were corrupted (exception midway
     * through finalization, double-finalize short-circuit, etc.).
     * Used by {@code ChatThreadPanel.sweepStreamingBubbles()} as a failsafe.
     */
    public boolean hasStreamingTextArea() {
        return streamer.hasStreamingTextArea();
    }

    public void setResponseTimeMs(long ms) {
        themeApplier.setResponseTimeMs(ms);
    }

    /** Returns {@code true} if this message is pinned. */
    public boolean isPinned() {
        return actionBar.isPinned();
    }

    /** Sets the pinned state (visual only — does NOT persist to store). */
    public void setPinned(boolean pinned) {
        actionBar.setPinned(pinned);
    }

    /**
     * Updates the message ID (e.g., when a client-generated echo ID is replaced
     * by the server-assigned ID). Re-queries pin state for the new ID and
     * updates the visual pin indicator.
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
        actionBar.setMessageId(messageId);
    }

    /** Marks {@link #messageId} as a locally generated content hash (see {@link
     *  github.anandb.netbeans.support.MessageIdGenerator}). Such an id is stable for
     *  a given text, so it identifies a pinned message — never a harness message. */
    public void setGeneratedMessageId(boolean generated) {
        this.generatedMessageId = generated;
    }

    public boolean isGeneratedMessageId() {
        return generatedMessageId;
    }

    public void setFontSizeOverride(int size) {
        if (contentRenderer != null) {
            contentRenderer.setFontSizeOverride(size);
        }
    }

    /**
     * Replaces the tool/thought content with segmented (markdown-rendered) blocks.
     * Each block is a consecutive run of same-type chunks with a distinct background.
     */
    public void setSegmentedToolContent(List<CollapsibleToolPane.ToolSegment> blocks) {
        contentRenderer.setSegmentedToolContent(blocks);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // This is the secret sauce for wrapping
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 10;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 100;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
    //</editor-fold>

    /**
     * Full-width row that places its single child at the role's width cap, flush left or right.
     * BoxLayout's cross-axis alignment is cooperative between siblings, so the cap cannot ride on
     * {@code getMaximumSize()} there — an explicit container has to apply it.
     */
    static final class AlignedRow extends JPanel {
        private static final long serialVersionUID = 1L;

        private final float ratio;
        private final int absoluteMax;
        private final boolean alignRight;
        private int lastCap = -1;
        /** Wide content (a table) needs the whole row; the reading-measure cap would clip it. */
        private boolean fullWidth;

        AlignedRow(float ratio, int absoluteMax, boolean alignRight) {
            super(null);
            this.ratio = ratio;
            this.absoluteMax = absoluteMax;
            this.alignRight = alignRight;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        /** A row with no cap: the assistant bubble, which spans the thread's whole width. */
        static AlignedRow fullWidth() {
            AlignedRow row = new AlignedRow(1f, Integer.MAX_VALUE, false);
            row.fullWidth = true;
            return row;
        }

        @Override
        public void doLayout() {
            if (getComponentCount() == 0) {
                return;
            }
            Component child = getComponent(0);
            int width = getWidth();
            int cap = fullWidth ? width : Math.min(absoluteMax, Math.round(width * ratio));
            int x = alignRight ? width - cap : 0;
            child.setBounds(x, 0, cap, Math.max(1, getHeight()));
            // The child re-wraps at its new width, so ask for the height after the width is set.
            int height = child.getPreferredSize().height;
            if (height != getHeight()) {
                child.setBounds(x, 0, cap, height);
            }
            if (cap != lastCap) {
                lastCap = cap;
                // Height computed before the width was known; settle it on the next pass.
                SwingUtilities.invokeLater(this::revalidate);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (getComponentCount() == 0) {
                return new Dimension(0, 0);
            }
            Dimension pref = getComponent(0).getPreferredSize();
            return new Dimension(pref.width, pref.height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return MAX_SIZE;
        }
    }
}
