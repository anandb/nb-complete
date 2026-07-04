package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.Timer;

import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.length;


// DSL-LEAF: keep imperative, wrap via UI.of(...) — Scrollable impl + heavy
// delegation (segments/codeStates/accordionManager/contentRenderer/streamer/
// themeApplier). Bubble shell stays a leaf; its sub-specs (header, code
// toolbar) are extracted to ui/spec/.
public class MessageBubble extends JPanel implements Scrollable {

    private static final Logger LOG = Logger.from(MessageBubble.class);
    private static final long serialVersionUID = 1L;

    /** Determines where the user avatar is positioned relative to the message bubble. */
    public enum AvatarPosition { LEFT, RIGHT, NONE }

    private JPanel bubble;
    private String toolTitle;
    private Timer copyRevertTimer;
    private final ArrayList<BubbleContentRenderer.CollapsibleState> codeStates = new ArrayList<>();
    private final JPanel segments;
    private final MessageType type;
    private final String messageId;
    private final String role;
    private final StringBuilder text;
    private final transient BubbleAccordionManager accordionManager;
    private final transient BubbleContentRenderer contentRenderer;
    private final transient BubbleStreamer streamer;
    private final transient BubbleThemeApplier themeApplier;
    private transient HierarchyListener hierarchyListener;

    @Override
    public float getAlignmentX() {
        return Component.LEFT_ALIGNMENT;
    }

    private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);

    @Override
    public Dimension getMaximumSize() {
        return MAX_SIZE;
    }

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle, AvatarPosition avatarPosition) {
        this(type, text, messageId, toolTitle, avatarPosition, false);
    }

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle, AvatarPosition avatarPosition, boolean streaming) {
        this.type = type;
        this.role = type.roleName();
        this.messageId = messageId;
        this.text = new StringBuilder(text);
        this.toolTitle = toolTitle;

        ColorTheme theme = ThemeManager.getCurrentTheme();
        setLayout(new GridBagLayout());
        setAlignmentY(Component.CENTER_ALIGNMENT);
        setOpaque(true);
        setBackground(theme.sunkenBackground());
        setDoubleBuffered(true);
        setBorder(new EmptyBorder(2, 8, 2, 8));

        // Accessible context for screen readers
        getAccessibleContext().setAccessibleName(NbBundle.getMessage(MessageBubble.class,
                "ACR_MessageBubble_" + role));
        getAccessibleContext().setAccessibleDescription(NbBundle.getMessage(MessageBubble.class,
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
                    RoundedPanel p = new RoundedPanel(32);
                    p.setLayout(new BorderLayout());
                    p.setBorder(new EmptyBorder(10, 8, 10, 8));
                    this.bubble = p;
                }
            case "assistant" -> {
                    RoundedPanel p = new RoundedPanel(32);
                    p.setLayout(new BorderLayout());
                    p.setBorder(new EmptyBorder(8, 10, 8, 10));
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
                    for (java.awt.Component c : segments.getComponents()) {
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

        // Copy button for assistant messages at bottom right — visible only on hover
        if ("assistant".equals(role)) {
            final JButton[] copyBtn = new JButton[1];
            copyBtn[0] = UIUtils.createToolbarButton("copy.svg", 20,
                NbBundle.getMessage(MessageBubble.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard(copyBtn[0]));
            copyBtn[0].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.bubbleBorder()),
                    BorderFactory.createEmptyBorder(1, 2, 1, 2)));
            copyBtn[0].setContentAreaFilled(false);
            copyBtn[0].setOpaque(false);
            copyBtn[0].setForeground(theme.foreground());
            copyBtn[0].setVisible(false);

            // Reserve space in SOUTH with a fixed-size placeholder so the
            // layout never shifts when toggling button visibility (avoids jitter).
            JPanel copyPlaceholder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            copyPlaceholder.setOpaque(false);
            Dimension btnSize = copyBtn[0].getPreferredSize();
            copyPlaceholder.setPreferredSize(btnSize);
            copyPlaceholder.setMinimumSize(btnSize);
            copyPlaceholder.setMaximumSize(btnSize);
            copyPlaceholder.add(copyBtn[0]);
            bubble.add(copyPlaceholder, BorderLayout.SOUTH);

            // Show copy button + frame on hover over the bubble.
            // mouseExited only hides when truly leaving the bubble, not when
            // entering a child (otherwise the button vanishes on approach).
            bubble.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    copyBtn[0].setVisible(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!bubble.contains(e.getPoint())) {
                        copyBtn[0].setVisible(false);
                    }
                }
            });
        }

        if ("user".equals(role) && avatarPosition != AvatarPosition.NONE) {
            // Wrap bubble + external avatar in a content row
            JPanel contentRow = new JPanel(new BorderLayout());
            contentRow.setOpaque(false);
            contentRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel userLabel = themeApplier.createUserAvatar();
            JPanel avatarWrapper = UIUtils.createTransparentPanel(new BorderLayout());
            avatarWrapper.add(userLabel, BorderLayout.NORTH);

            if (avatarPosition == AvatarPosition.LEFT) {
                contentRow.add(avatarWrapper, BorderLayout.WEST);
            } else {
                contentRow.add(avatarWrapper, BorderLayout.EAST);
            }
            contentRow.add(bubble, BorderLayout.CENTER);
            add(contentRow, UIUtils.createGbc(0, 0, 1.0, 0, fill, anchor, gbcInsets));
        } else {
            add(bubble, UIUtils.createGbc(0, 0, 1.0, 0, fill, anchor, gbcInsets));
        }

        // Fix for "garbled" text in scroll panes: force a repaint when the component becomes visible.
        // Only repaint the outermost component — Swing's repaint manager handles children.
        if ("user".equals(role)) {
            this.hierarchyListener = e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                    SwingUtilities.invokeLater(() -> repaint());
                }
            };
            addHierarchyListener(this.hierarchyListener);
        }

    }

    @Override
    public void addNotify() {
        super.addNotify();
        // Parent container's revalidate handles layout; no per-bubble
        // revalidate/repaint needed here.
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (hierarchyListener != null) {
            removeHierarchyListener(hierarchyListener);
            hierarchyListener = null;
        }
        streamer.stopTimer();
        if (copyRevertTimer != null) {
            copyRevertTimer.stop();
            copyRevertTimer = null;
        }
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

    private void handleToolThoughtContent(ColorTheme theme, boolean expanded) {
        contentRenderer.updateContent(theme, expanded, toolTitle);
    }

    private void updateContent(ColorTheme theme, boolean expanded) {
        contentRenderer.updateContent(theme, expanded, toolTitle);
    }

    private void copyMessageToClipboard(JButton copyBtn) {
        String textToCopy = text.toString();
        if (textToCopy.isEmpty()) {
            return;
        }

        StringSelection selection = new StringSelection(textToCopy);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Icon originalIcon = copyBtn.getIcon();
        Icon checkIcon = ThemeManager.getIcon("check.svg", 14);
        copyBtn.setIcon(checkIcon);

        // Cancel any previous revert timer to avoid leaking timers on rapid clicks.
        if (copyRevertTimer != null) {
            copyRevertTimer.stop();
        }
        copyRevertTimer = new Timer(2000, e -> {
            if (copyBtn.isShowing()) {
                copyBtn.setIcon(originalIcon);
            }
        });
        copyRevertTimer.setRepeats(false);
        copyRevertTimer.start();
    }

    /**
     * Finalizes streaming, converting from JTextArea to rich HTML.
     * @param expanded initial collapse state for code/tool panes
     */
    public void finalizeStreaming(boolean expanded) {
        streamer.finalizeStreaming(expanded, false);
    }

    /**
     * Finalizes streaming.
     * @param expanded  initial collapse state for code/tool panes
     * @param immediate if true, build HTML now; if false, defer for 300 ms
     *                  to let additional deltas accumulate (avoids repeated
     *                  expensive rebuilds during section splits).
     */
    public void finalizeStreaming(boolean expanded, boolean immediate) {
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

    /**
     * Replaces the tool/thought content with segmented (markdown-rendered) blocks.
     * Each block is a consecutive run of same-type chunks with a distinct background.
     */
    public void setSegmentedToolContent(List<CollapsibleToolPane.ToolSegment> blocks) {
        contentRenderer.setSegmentedToolContent(blocks);
    }

    /**
     * Updates both the title and segmented content of the combined activity pane.
     * Used by applyTypeFilters() when re-filtering changes the visible count.
     */
    public void updateCombinedContent(List<CollapsibleToolPane.ToolSegment> blocks, String title) {
        this.toolTitle = title;
        contentRenderer.updateCombinedContent(blocks, title);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // This is the secret sauce for wrapping
    }

    // Other Scrollable methods can return default values
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
}
