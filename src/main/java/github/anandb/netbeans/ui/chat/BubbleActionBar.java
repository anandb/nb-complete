package github.anandb.netbeans.ui.chat;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.session.MessageQueueManager;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.components.UIUtils;
import github.anandb.netbeans.ui.components.RoundedPanel;
import github.anandb.netbeans.ui.platform.PlatformBridge;

/**
 * Manages the action buttons (copy, pin) and queued indicator for a {@link MessageBubble}.
 * Handles hover show/hide logic, pin state persistence, copy-to-clipboard, and the
 * queued animation timer.
 *
 * <p>Package-private — internal to the {@code ui} layer.</p>
 */
class BubbleActionBar {

    private static final Logger LOG = Logger.from(BubbleActionBar.class);

    private static final String[] QUEUED_FRAMES = {"\u23F3", "\u23F4", "\u23F5", "\u23F6"};
    private static final int ACTION_ICON_SIZE = 24;
    /** Auto-hide: the icons stay up this long after they appear, then hide (user: 3-4s). */
    private static final int AUTO_HIDE_DELAY_MS = 3500;
    /** Overlay position: the icons float clear of the bubble's bottom edge. */
    private static final int OVERLAY_BOTTOM_INSET = 12;
    private static final int OVERLAY_RIGHT_INSET = 20;

    private final JPanel bubble;
    private final String role;
    private final StringBuilder text;
    private final MessageBubble owner;

    /** Lazily resolved — may remain null if not needed. */
    private String sessionId;
    private String messageId;

    // Pin state
    private boolean pinned;
    private JButton pinBtn;

    // Copy button
    private JButton copyBtn;
    private Icon copyBtnIcon;
    private JPanel actionsPlaceholder;

    // Hover timers
    private Timer showTimer;
    private Timer autoHideTimer;
    private Timer copyRevertTimer;

    /** Hover surface: the bubble, every pane inside it and the icon overlay.
     *  The rendered answer lives in child panes that swallow motion events, so the
     *  bubble alone never sees a hover over the text. */
    private MouseAdapter hoverListener;
    /** Client property marking a component already wired to {@link #hoverListener}. */
    private static final String HOVER_INSTALLED = "beanbot.hoverInstalled";

    // Queued indicator
    private boolean queued;
    private JLabel queuedLabel;
    private int queuedFrameIndex;
    private Timer queuedAnimationTimer;

    /**
     * @param bubble    the inner bubble panel (SOUTH is used for buttons)
     * @param role      message role ("assistant", "user", etc.)
     * @param messageId message ID for pin persistence (may be null)
     * @param sessionId session ID for pin persistence (may be null)
     * @param text      shared text buffer for clipboard copy
     * @param theme     current color theme for button styling
     * @param owner     the owning MessageBubble (for revalidate/repaint callbacks)
     */
    BubbleActionBar(JPanel bubble, String role, String messageId, String sessionId,
                    StringBuilder text, ColorTheme theme, MessageBubble owner) {
        this.bubble = bubble;
        this.role = role;
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.text = text;
        this.owner = owner;
    }

    // -------------------------------------------------------------------------
    // Action button setup
    // -------------------------------------------------------------------------

    /**
     * Creates and installs copy/pin buttons with hover logic into the bubble's SOUTH region.
     * For assistant messages with a messageId: both copy + pin buttons.
     * For assistant messages without a messageId: copy only.
     * For other roles: no-op.
     */
    void setupActionButtons(ColorTheme theme) {
        if (!"assistant".equals(role)) {
            return;
        }

        if (messageId != null) {
            setupCopyAndPinButtons(theme);
        } else {
            setupCopyOnlyButton(theme);
        }
    }

    private void setupCopyAndPinButtons(ColorTheme theme) {
        final PinnedMessageControl pinStore = PlatformBridge.serviceSafe(PinnedMessageControl.class);
        this.pinned = (pinStore != null && pinStore.isPinned(sessionId, messageId));

        pinBtn = UIUtils.createToolbarButton("pin.svg", ACTION_ICON_SIZE,
                NbBundle.getMessage(UIBundle.class,
                        pinned ? "HINT_UnpinMessage" : "HINT_PinMessage"),
                e -> flipPin(pinStore));
        pinBtn.setBorder(BorderFactory.createEmptyBorder());
        pinBtn.setContentAreaFilled(false);
        pinBtn.setOpaque(false);
        pinBtn.setForeground(theme.foreground());
        pinBtn.setVisible(true);

        if (pinned) {
            pinBtn.setIcon(ThemeManager.getIcon("pinned.svg", ACTION_ICON_SIZE));
            applyPinAccent(true);
        }

        copyBtn = UIUtils.createToolbarButton("copy.svg", ACTION_ICON_SIZE,
                NbBundle.getMessage(UIBundle.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard());
        copyBtn.setBorder(BorderFactory.createEmptyBorder());
        copyBtn.setContentAreaFilled(false);
        copyBtn.setOpaque(false);
        copyBtn.setForeground(theme.foreground());
        this.copyBtnIcon = copyBtn.getIcon();

        // Floating overlay in the bubble's GridBag cell. Added at index 0 so it
        // paints above the content row; MessageBubble.doLayout() positions it at
        // the bubble's visible bottom-right, where GridBagLayout cannot clip it.
        JPanel actionsOverlay = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actionsOverlay.setOpaque(false);
        actionsOverlay.add(copyBtn);
        actionsOverlay.add(pinBtn);
        actionsOverlay.setVisible(false);
        this.actionsPlaceholder = actionsOverlay;
        GridBagConstraints ogbc = new GridBagConstraints();
        ogbc.gridx = 0;
        ogbc.gridy = 0;
        ogbc.weightx = 0;
        ogbc.weighty = 0;
        ogbc.anchor = GridBagConstraints.SOUTHEAST;
        ogbc.insets = new Insets(0, 0, OVERLAY_BOTTOM_INSET, OVERLAY_RIGHT_INSET);
        owner.add(actionsOverlay, ogbc, 0);

        installHoverListeners();
    }

    private void setupCopyOnlyButton(ColorTheme theme) {
        copyBtn = UIUtils.createToolbarButton("copy.svg", ACTION_ICON_SIZE,
                NbBundle.getMessage(UIBundle.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard());
        copyBtn.setBorder(BorderFactory.createEmptyBorder());
        copyBtn.setContentAreaFilled(false);
        copyBtn.setOpaque(false);
        copyBtn.setForeground(theme.foreground());
        this.copyBtnIcon = copyBtn.getIcon();

        JPanel copyOverlay = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        copyOverlay.setOpaque(false);
        copyOverlay.add(copyBtn);
        copyOverlay.setVisible(false);
        this.actionsPlaceholder = copyOverlay;
        GridBagConstraints ogbc = new GridBagConstraints();
        ogbc.gridx = 0;
        ogbc.gridy = 0;
        ogbc.weightx = 0;
        ogbc.weighty = 0;
        ogbc.anchor = GridBagConstraints.SOUTHEAST;
        ogbc.insets = new Insets(0, 0, OVERLAY_BOTTOM_INSET, OVERLAY_RIGHT_INSET);
        owner.add(copyOverlay, ogbc, 0);

        installHoverListeners();
    }

    /**
     * Pins the overlay to the bubble's bottom-right, clamped to the owner's visible
     * area. GridBagLayout can place it outside that area when the bubble's content
     * overflows the cell, which cut the pin button off; doing it from the bubble's
     * real bounds also survives the overlay being re-added when an id is assigned.
     */
    void layoutOverlay() {
        JPanel overlay = actionsPlaceholder;
        if (overlay == null || owner.getWidth() <= 0) {
            return;
        }
        Dimension pref = overlay.getPreferredSize();
        Rectangle content = contentBounds();
        int overlayRight = content.x + content.width - OVERLAY_RIGHT_INSET;
        int overlayBottom = content.y + content.height - OVERLAY_BOTTOM_INSET;
        BaseCollapsiblePane trailing = trailingCollapsedPane();
        if (trailing != null) {
            Rectangle pane = SwingUtilities.convertRectangle(
                    trailing.getParent(), trailing.getBounds(), owner);
            if (pane.y - 4 - pref.height >= content.y) {
                // Float clear above a trailing collapsed block instead of covering its
                // header copy button.
                overlayBottom = pane.y - 4;
            } else {
                // No room above (a block-only bubble): keep the bottom row but stop
                // left of the block's copy button.
                int copyWidth = Math.max(0, trailing.copyButton.getPreferredSize().width);
                overlayRight = Math.min(overlayRight, pane.x + pane.width - copyWidth - 12);
            }
        }
        int x = Math.max(content.x, overlayRight - pref.width);
        int y = Math.max(content.y, Math.min(overlayBottom - pref.height,
                content.y + content.height - pref.height));
        overlay.setBounds(x, y, pref.width, pref.height);
        // Lay the buttons out even while the overlay is hidden: showing it must not
        // need a revalidate (the first layout pass happens before any hover).
        overlay.doLayout();
    }

    /**
     * The bubble's content rectangle in owner coordinates. The owner's GridBag cell can
     * be taller than the bubble — the overlay contributes a minimum height and extra
     * rows such as the queued label add more — which used to leave the buttons floating
     * above the bubble; the content row is the truth.
     */
    private Rectangle contentBounds() {
        Insets ins = owner.getInsets();
        int left = ins.left;
        int top = ins.top;
        int right = owner.getWidth() - ins.right;
        int bottom = owner.getHeight() - ins.bottom;
        Component parent = bubble.getParent();
        if (parent != null && parent != owner && parent.getWidth() > 0 && parent.getHeight() > 0) {
            Rectangle row = SwingUtilities.convertRectangle(
                    parent, new Rectangle(0, 0, parent.getWidth(), parent.getHeight()), owner);
            left = Math.max(left, row.x);
            top = Math.max(top, row.y);
            right = Math.min(right, row.x + row.width);
            bottom = Math.min(bottom, row.y + row.height);
        }
        return new Rectangle(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    /** The last visible content block, when it is a collapsed header we must not cover. */
    private BaseCollapsiblePane trailingCollapsedPane() {
        if (bubble.getComponentCount() != 1 || !(bubble.getComponent(0) instanceof Container segments)) {
            return null;
        }
        for (int i = segments.getComponentCount() - 1; i >= 0; i--) {
            Component child = segments.getComponent(i);
            if (!child.isVisible()) {
                continue;
            }
            return child instanceof BaseCollapsiblePane pane && !pane.isExpanded() ? pane : null;
        }
        return null;
    }

    private void installHoverListeners() {
        // Bubbles that get no action buttons (user, info, error) have nothing to
        // reveal: wiring hover on them would arm a timer that dereferences a null
        // copyBtn in showButtons().
        if (copyBtn == null) {
            return;
        }
        // Hover: show buttons after 400ms delay. Overlay floats above the
        // bubble, so showing it never changes layout — repaint only.
        // The handler is registered on the bubble, every pane nested in it and the
        // icon overlay: the rendered text and the buttons are child components, and
        // mouse events go to the deepest component, so a listener on the bubble alone
        // never sees a hover over the message.
        if (hoverListener == null) {
            hoverListener = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    startShowTimer();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // Exit also fires when the pointer moves onto a child pane of the
                    // bubble: hide only when the pointer really left the bubble.
                    if (!pointerInsideBubble(e)) {
                        hideButtons();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    onHoverMotion();
                }
            };
        }
        installHover(bubble);
        installHover(actionsPlaceholder);
    }

    /** Whether an exit event left the pointer within the bubble's bounds. */
    private boolean pointerInsideBubble(MouseEvent e) {
        Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), bubble);
        return bubble.contains(p);
    }

    /**
     * Registers the hover handler on a component and everything nested in it. Idempotent —
     * a content rebuild replaces the panes, so this runs again after every update.
     */
    private void installHover(Component c) {
        if (c == null || hoverListener == null) {
            return;
        }
        if (c instanceof JComponent jc) {
            if (Boolean.TRUE.equals(jc.getClientProperty(HOVER_INSTALLED))) {
                return;
            }
            jc.putClientProperty(HOVER_INSTALLED, Boolean.TRUE);
        }
        c.addMouseListener(hoverListener);
        c.addMouseMotionListener(hoverListener);
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                installHover(child);
            }
        }
    }

    /** Re-arms hover detection after the bubble rebuilt its content panes. */
    void refreshHoverTargets() {
        installHoverListeners();
    }

    /** Pointer moved within the bubble: while hidden it starts the delayed show. Once the
     *  icons are up the auto-hide timer owns them — motion does not extend the window. */
    private void onHoverMotion() {
        if (copyBtn != null && !buttonsShowing()) {
            startShowTimer();
        }
    }

    /** True while the copy/pin overlay is on screen. The buttons stay visible inside it,
     *  so hiding/showing the overlay never changes their layout size. */
    private boolean buttonsShowing() {
        return actionsPlaceholder != null && actionsPlaceholder.isVisible();
    }

    /** Shows the buttons and arms the auto-hide timer. */
    void showButtons() {
        // A timer armed before the buttons existed (or a bubble that never gets
        // them) must not crash on the still-null copy button.
        if (copyBtn == null) {
            return;
        }
        if (actionsPlaceholder != null) {
            actionsPlaceholder.setVisible(true);
        }
        // Reset icon — a stale check icon may remain from a copy where the
        // timer fired while the button was hidden.
        if (copyRevertTimer != null && copyRevertTimer.isRunning()) {
            copyRevertTimer.stop();
        }
        copyBtn.setIcon(copyBtnIcon);
        if (autoHideTimer == null) {
            autoHideTimer = new Timer(AUTO_HIDE_DELAY_MS, evt -> {
                // While the pointer rests on the icons themselves, postpone —
                // vanishing under a cursor aiming at a button loses the click.
                if (actionsPlaceholder != null && isMouseInsideComponent(actionsPlaceholder)) {
                    autoHideTimer.restart();
                } else {
                    hideButtons();
                }
            });
            autoHideTimer.setRepeats(false);
        }
        autoHideTimer.restart();
        owner.repaint();
    }

    /** Starts the delayed show timer. A burst of motion shows the icons once, 400ms after
     *  the first movement; while they are already up this is a no-op, so moving around the
     *  bubble does not push the auto-hide deadline back. */
    private void startShowTimer() {
        if (copyBtn == null || buttonsShowing()) {
            return;
        }
        if (showTimer == null) {
            // No pointer re-check: an exit that really leaves the bubble stops this timer.
            showTimer = new Timer(400, evt -> showButtons());
            showTimer.setRepeats(false);
        }
        if (!showTimer.isRunning()) {
            showTimer.start();
        }
    }

    /** Hover check for a bubble that appeared, grew or scrolled under a
     *  stationary pointer — no {@code mouseEntered} fires for those. */
    void checkHover() {
        if (copyBtn != null && !buttonsShowing() && isMouseInsideComponent(bubble)) {
            // Panes built after the last content update (an expanded code block, a diff)
            // carry no handler yet: this is the point where we know the pointer is here.
            installHover(bubble);
            startShowTimer();
        }
    }

    private void hideButtons() {
        if (showTimer != null) {
            showTimer.stop();
        }
        if (autoHideTimer != null) {
            autoHideTimer.stop();
        }
        if (actionsPlaceholder != null) {
            actionsPlaceholder.setVisible(false);
        }
        owner.repaint();
    }

    // -------------------------------------------------------------------------
    // Clipboard
    // -------------------------------------------------------------------------

    private void copyMessageToClipboard() {
        String textToCopy = text.toString();
        if (textToCopy.isEmpty()) {
            return;
        }

        StringSelection selection = new StringSelection(textToCopy);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Icon originalIcon = copyBtn.getIcon();
        Icon checkIcon = ThemeManager.getIcon("check.svg", 12);
        copyBtn.setIcon(checkIcon);

        // Cancel any previous revert timer to avoid leaking timers on rapid clicks.
        if (copyRevertTimer != null) {
            copyRevertTimer.stop();
        }
        copyRevertTimer = new Timer(1500, e -> copyBtn.setIcon(originalIcon));
        copyRevertTimer.setRepeats(false);
        copyRevertTimer.start();
    }

    // -------------------------------------------------------------------------
    // Pin state
    // -------------------------------------------------------------------------

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
        applyPinVisual();
        owner.revalidate();
        owner.repaint();
    }

    /** Applies the icon, tooltip and accent for the current {@link #pinned} state. */
    private void applyPinVisual() {
        pinBtn.setIcon(ThemeManager.getIcon(
                pinned ? "pinned.svg" : "pin.svg", ACTION_ICON_SIZE));
        pinBtn.setToolTipText(NbBundle.getMessage(UIBundle.class,
                pinned ? "HINT_UnpinMessage" : "HINT_PinMessage"));
        pinBtn.getAccessibleContext().setAccessibleName(pinBtn.getToolTipText());
        pinBtn.getAccessibleContext().setAccessibleDescription(pinBtn.getToolTipText());
        applyPinAccent(pinned);
    }

    private void applyPinAccent(boolean apply) {
        if (bubble instanceof RoundedPanel rp) {
            // Accent, not error red: a pinned message is a saved state, not a failure.
            rp.setLeftAccent(apply ? ThemeManager.getCurrentTheme().accent() : null);
        }
    }

    /** Resolves the current session ID via Lookup (defensive fallback only). */
    private String resolveSessionId() {
        if (sessionId != null) {
            return sessionId;
        }
        LOG.warn("BubbleActionBar sessionId was null; falling back to Lookup. messageId={0}", messageId);
        SessionControl sc = PlatformBridge.sessionControl();
        return sc != null ? sc.getCurrentSessionId() : null;
    }

    /** Returns {@code true} if this message is pinned. */
    boolean isPinned() {
        return pinned;
    }

    /** Sets the pinned state (visual only — does NOT persist to store). */
    void setPinned(boolean pinned) {
        this.pinned = pinned;
        if (pinBtn != null) {
            applyPinVisual();
        } else {
            applyPinAccent(pinned);
        }
    }

    /**
     * Updates the message ID (e.g., when a client-generated echo ID is replaced
     * by the server-assigned ID). Re-queries pin state for the new ID.
     */
    void setMessageId(String messageId) {
        String oldId = this.messageId;
        this.messageId = messageId;
        // If messageId was null at construction (streaming), pin button was never
        // created. Re-run setup to install copy+pin buttons now that we have an ID.
        if (oldId == null && messageId != null && pinBtn == null) {
            // Remove the old copy-only overlay before installing copy+pin.
            if (actionsPlaceholder != null) {
                owner.remove(actionsPlaceholder);
            }
            setupActionButtons(ThemeManager.getCurrentTheme());
            return;
        }
        if (pinBtn != null) {
            PinnedMessageControl pinStore = PlatformBridge.serviceSafe(PinnedMessageControl.class);
            String sid = resolveSessionId();
            if (pinStore != null && sid != null) {
                this.pinned = pinStore.isPinned(sid, messageId);
            }
            setPinned(this.pinned);
        }
    }

    // -------------------------------------------------------------------------
    // Queued indicator
    // -------------------------------------------------------------------------

    /**
     * Applies or removes a queued indicator on the bubble — an amber left accent
     * bar and a pulsing hourglass icon at the bottom-right.
     */
    void setQueued(boolean queued) {
        this.queued = queued;
        applyQueuedAccent(queued);
        if (queued) {
            if (queuedLabel == null) {
                queuedLabel = new JLabel("\u23F3"); // hourglass emoji
                queuedLabel.setFont(ThemeManager.getFont().deriveFont(12f));
                queuedLabel.setForeground(ThemeManager.getCurrentTheme().foreground());
            }
            GridBagConstraints gbc = UIUtils.createGbc(0, 1, 1.0, 0,
                    GridBagConstraints.NONE, GridBagConstraints.SOUTHEAST,
                    new Insets(0, 12, 2, 12));
            owner.add(queuedLabel, gbc);
            startQueuedAnimation();
        } else if (queuedLabel != null) {
            stopQueuedAnimation();
            owner.remove(queuedLabel);
        }
        owner.revalidate();
        owner.repaint();
    }

    private void startQueuedAnimation() {
        if (queuedAnimationTimer != null && queuedAnimationTimer.isRunning()) {
            return;
        }
        queuedFrameIndex = 0;
        queuedAnimationTimer = new Timer(400, e -> {
            if (queuedLabel != null && queued) {
                queuedFrameIndex = (queuedFrameIndex + 1) % QUEUED_FRAMES.length;
                queuedLabel.setText(QUEUED_FRAMES[queuedFrameIndex]);
            }
        });
        queuedAnimationTimer.setRepeats(true);
        queuedAnimationTimer.start();
    }

    void stopQueuedAnimation() {
        if (queuedAnimationTimer != null) {
            queuedAnimationTimer.stop();
            queuedAnimationTimer = null;
        }
        queuedFrameIndex = 0;
    }

    private void applyQueuedAccent(boolean apply) {
        if (bubble instanceof RoundedPanel rp) {
            rp.setLeftAccent(apply ? MessageQueueManager.getAccentColor() : null);
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Stops all timers — must be called from the owning bubble's removeNotify. */
    void stopAllTimers() {
        stopQueuedAnimation();
        if (copyRevertTimer != null) {
            copyRevertTimer.stop();
            copyRevertTimer = null;
        }
        if (showTimer != null) {
            showTimer.stop();
        }
        if (autoHideTimer != null) {
            autoHideTimer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Checks whether the mouse pointer is currently within the screen bounds of
     * the given component.
     */
    static boolean isMouseInsideComponent(Component c) {
        if (!c.isShowing()) return false;
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) return false;
        try {
            Point screenLoc = pi.getLocation();
            Rectangle bounds = c.getBounds();
            bounds.setLocation(c.getLocationOnScreen());
            return bounds.contains(screenLoc);
        } catch (IllegalComponentStateException ex) {
            return false;
        }
    }
}
