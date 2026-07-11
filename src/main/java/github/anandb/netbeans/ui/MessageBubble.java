package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
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

import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;
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
    private String messageId;
    private final String role;
    private final StringBuilder text;
    private final transient BubbleAccordionManager accordionManager;
    private final transient BubbleContentRenderer contentRenderer;
    private final transient BubbleStreamer streamer;
    private final transient BubbleThemeApplier themeApplier;
    private transient HierarchyListener hierarchyListener;

    /** Session ID — passed through from ChatThreadPanel for pin state persistence. */
    private final String sessionId;
    private boolean pinned;
    private JButton pinBtn;

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
        this(type, text, messageId, toolTitle, avatarPosition, false, null);
    }

    public MessageBubble(MessageType type, String text, String messageId, String toolTitle, AvatarPosition avatarPosition, boolean streaming) {
        this(type, text, messageId, toolTitle, avatarPosition, streaming, null);
    }

    public MessageBubble(MessageType type, String text, String messageId,
            String toolTitle, AvatarPosition avatarPosition,
            boolean streaming, String sessionId) {
        this.type = type;
        this.role = type.roleName();
        this.messageId = messageId;
        this.text = new StringBuilder(text);
        this.toolTitle = toolTitle;
        this.sessionId = sessionId;

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

        // Pin + copy buttons for assistant messages at bottom right — visible on hover.
        // Pin first, copy last. When pinned, the pinned icon is always visible.
        // messageId must be known; sessionId is resolved lazily if not passed in.
        if ("assistant".equals(role) && messageId != null) {
            final PinnedMessageControl pinStore = Lookup.getDefault().lookup(PinnedMessageControl.class);
            this.pinned = (pinStore != null && pinStore.isPinned(sessionId, messageId));

            pinBtn = UIUtils.createToolbarButton("pin.svg", 32,
                    NbBundle.getMessage(MessageBubble.class,
                            pinned ? "HINT_UnpinMessage" : "HINT_PinMessage"),
                    e -> flipPin(pinStore));
            pinBtn.setBorder(BorderFactory.createEmptyBorder());
            pinBtn.setContentAreaFilled(false);
            pinBtn.setOpaque(false);
            pinBtn.setForeground(theme.foreground());
            pinBtn.setVisible(pinned);

            if (pinned) {
                pinBtn.setIcon(ThemeManager.getIcon("pinned.svg", 32));
                applyPinAccent(true);
            }

            final JButton[] copyBtnArr = new JButton[1];
            copyBtnArr[0] = UIUtils.createToolbarButton("copy.svg", 32,
                NbBundle.getMessage(MessageBubble.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard(copyBtnArr[0]));
            copyBtnArr[0].setBorder(BorderFactory.createEmptyBorder());
            copyBtnArr[0].setContentAreaFilled(false);
            copyBtnArr[0].setOpaque(false);
            copyBtnArr[0].setForeground(theme.foreground());
            copyBtnArr[0].setVisible(false);
            final Icon copyBtnArrIcon = copyBtnArr[0].getIcon();

            // Reserve space with both buttons so layout never shifts.
            // FlowLayout ignores invisible components when sizing, so compute
            // the size from the button preferred sizes directly.
            Dimension pinSize = pinBtn.getPreferredSize();
            Dimension copySize = copyBtnArr[0].getPreferredSize();
            int actionsW = pinSize.width + copySize.width + 4;
            int actionsH = Math.max(pinSize.height, copySize.height);
            Dimension actionsSize = new Dimension(actionsW, actionsH);

            JPanel actionsPlaceholder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            actionsPlaceholder.setOpaque(false);
            actionsPlaceholder.add(copyBtnArr[0]);
            actionsPlaceholder.add(pinBtn);
            actionsPlaceholder.setPreferredSize(actionsSize);
            actionsPlaceholder.setMinimumSize(actionsSize);
            actionsPlaceholder.setMaximumSize(actionsSize);
            bubble.add(actionsPlaceholder, BorderLayout.SOUTH);

            // Hover: show copy on hover, pin always visible when pinned.
            bubble.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    copyBtnArr[0].setVisible(true);
                    // Reset icon — a stale check icon may remain from a
                    // copy where the timer fired while the button was hidden.
                    if (copyRevertTimer != null && copyRevertTimer.isRunning()) {
                        copyRevertTimer.stop();
                    }
                    if (copyBtnArr.length > 0) {
                        copyBtnArr[0].setIcon(copyBtnArrIcon);
                    }
                    if (!pinned) {
                        pinBtn.setVisible(true);
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!isMouseInsideComponent(bubble)) {
                        copyBtnArr[0].setVisible(false);
                        if (!pinned) {
                            pinBtn.setVisible(false);
                        }
                    }
                }
            });
            // Also hide when mouse exits a child button — mouseExited on bubble
            // only fires once (when entering the child), so we re-check here.
            MouseAdapter childExit = new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        if (!isMouseInsideComponent(bubble)) {
                            copyBtnArr[0].setVisible(false);
                            if (!pinned) {
                                pinBtn.setVisible(false);
                            }
                        }
                    });
                }
            };
            copyBtnArr[0].addMouseListener(childExit);
            pinBtn.addMouseListener(childExit);
        } else if ("assistant".equals(role)) {
            // Assistant without messageId — copy only
            final JButton[] copyBtn = new JButton[1];
            copyBtn[0] = UIUtils.createToolbarButton("copy.svg", 32,
                NbBundle.getMessage(MessageBubble.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard(copyBtn[0]));
            copyBtn[0].setBorder(BorderFactory.createEmptyBorder());
            copyBtn[0].setContentAreaFilled(false);
            copyBtn[0].setOpaque(false);
            copyBtn[0].setForeground(theme.foreground());
            copyBtn[0].setVisible(false);
            final Icon copyBtnIcon = copyBtn[0].getIcon();

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

            // Show copy button on hover over the bubble.
            bubble.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    copyBtn[0].setVisible(true);
                    // Reset icon — a stale check icon may remain from a
                    // copy where the timer fired while the button was hidden.
                    if (copyRevertTimer != null && copyRevertTimer.isRunning()) {
                        copyRevertTimer.stop();
                    }
                    copyBtn[0].setIcon(copyBtnIcon);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!isMouseInsideComponent(bubble)) {
                        copyBtn[0].setVisible(false);
                    }
                }
            });
            // Also hide when mouse exits child button (same reason as above).
            copyBtn[0].addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        if (!isMouseInsideComponent(bubble)) {
                            copyBtn[0].setVisible(false);
                        }
                    });
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
            avatarWrapper.add(userLabel, BorderLayout.CENTER);

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
        // Stop timers and remove listeners BEFORE super.removeNotify()
        // to avoid callbacks firing on a partially-dismantled component tree.
        streamer.stopTimer();
        if (copyRevertTimer != null) {
            copyRevertTimer.stop();
            copyRevertTimer = null;
        }
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
        copyRevertTimer = new Timer(1500, e -> {
            copyBtn.setIcon(originalIcon);
        });
        copyRevertTimer.setRepeats(false);
        copyRevertTimer.start();
    }

    /** Resolves the current session ID via Lookup (defensive fallback only). */
    private String resolveSessionId() {
        if (sessionId != null) {
            return sessionId;
        }
        LOG.warn("MessageBubble sessionId was null at construction; falling back to Lookup. messageId={0}", messageId);
        SessionControl sc = Lookup.getDefault().lookup(SessionControl.class);
        return sc != null ? sc.getCurrentSessionId() : null;
    }

    /** Toggles pin state, persists via store, updates icon and accent. */
    private void flipPin(PinnedMessageControl store) {
        if (store == null) {
            return;
        }
        String sid = resolveSessionId();
        if (sid == null) {
            return;
        }
        pinned = !pinned;
        store.setPinned(sid, messageId, pinned);
        pinBtn.setIcon(ThemeManager.getIcon(
                pinned ? "pinned.svg" : "pin.svg", 32));
        pinBtn.setToolTipText(NbBundle.getMessage(MessageBubble.class,
                pinned ? "HINT_UnpinMessage" : "HINT_PinMessage"));
        pinBtn.getAccessibleContext().setAccessibleName(pinBtn.getToolTipText());
        pinBtn.getAccessibleContext().setAccessibleDescription(pinBtn.getToolTipText());
        applyPinAccent(pinned);
        revalidate();
        repaint();
    }

    /** Returns {@code true} if this message is pinned. */
    public boolean isPinned() {
        return pinned;
    }

    /** Sets the pinned state (visual only — does NOT persist to store). */
    public void setPinned(boolean pinned) {
        this.pinned = pinned;
        if (pinBtn != null) {
            pinBtn.setIcon(ThemeManager.getIcon(
                    pinned ? "pinned.svg" : "pin.svg", 32));
            pinBtn.setToolTipText(NbBundle.getMessage(MessageBubble.class,
                    pinned ? "HINT_UnpinMessage" : "HINT_PinMessage"));
            pinBtn.getAccessibleContext().setAccessibleName(pinBtn.getToolTipText());
            pinBtn.getAccessibleContext().setAccessibleDescription(pinBtn.getToolTipText());
        }
        applyPinAccent(pinned);
    }

    /**
     * Updates the message ID (e.g., when a client-generated echo ID is replaced
     * by the server-assigned ID). Re-queries pin state for the new ID and
     * updates the visual pin indicator.
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
        if (pinBtn != null) {
            PinnedMessageControl pinStore = Lookup.getDefault().lookup(PinnedMessageControl.class);
            String sid = resolveSessionId();
            if (pinStore != null && sid != null) {
                this.pinned = pinStore.isPinned(sid, messageId);
            }
            setPinned(this.pinned);
        }
    }

    /** Applies or removes a red left accent on the bubble when pinned. */
    private void applyPinAccent(boolean apply) {
        if (bubble instanceof RoundedPanel rp) {
            rp.setLeftAccent(apply ? new Color(0xCC, 0x33, 0x33, 255) : null);
        }
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

    /**
     * Checks whether the mouse pointer is currently within the screen bounds of
     * the given component. Uses screen coordinates to avoid the unreliable
     * relative-point check in {@link MouseEvent#getPoint()} when the mouse
     * moves quickly.
     */
    private static boolean isMouseInsideComponent(Component c) {
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return false;
        Point screenLoc = pi.getLocation();
        Rectangle bounds = c.getBounds();
        bounds.setLocation(c.getLocationOnScreen());
        return bounds.contains(screenLoc);
    }
}
