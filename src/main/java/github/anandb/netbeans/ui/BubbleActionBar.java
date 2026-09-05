package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.support.Logger;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

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

    // Hover timers
    private Timer showTimer;
    private Timer copyRevertTimer;

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
        final PinnedMessageControl pinStore = Lookup.getDefault().lookup(PinnedMessageControl.class);
        this.pinned = (pinStore != null && pinStore.isPinned(sessionId, messageId));

        pinBtn = UIUtils.createToolbarButton("pin.svg", 32,
                NbBundle.getMessage(BubbleActionBar.class,
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

        copyBtn = UIUtils.createToolbarButton("copy.svg", 32,
                NbBundle.getMessage(BubbleActionBar.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard());
        copyBtn.setBorder(BorderFactory.createEmptyBorder());
        copyBtn.setContentAreaFilled(false);
        copyBtn.setOpaque(false);
        copyBtn.setForeground(theme.foreground());
        copyBtn.setVisible(false);
        this.copyBtnIcon = copyBtn.getIcon();

        // Reserve space with both buttons so layout never shifts.
        Dimension pinSize = pinBtn.getPreferredSize();
        Dimension copySize = copyBtn.getPreferredSize();
        int actionsW = pinSize.width + copySize.width + 4;
        int actionsH = Math.max(pinSize.height, copySize.height);
        Dimension actionsSize = new Dimension(actionsW, actionsH);

        JPanel actionsPlaceholder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionsPlaceholder.setOpaque(false);
        actionsPlaceholder.add(copyBtn);
        actionsPlaceholder.add(pinBtn);
        actionsPlaceholder.setPreferredSize(actionsSize);
        actionsPlaceholder.setMinimumSize(actionsSize);
        actionsPlaceholder.setMaximumSize(actionsSize);
        bubble.add(actionsPlaceholder, BorderLayout.SOUTH);

        installHoverListeners();
    }

    private void setupCopyOnlyButton(ColorTheme theme) {
        copyBtn = UIUtils.createToolbarButton("copy.svg", 32,
                NbBundle.getMessage(BubbleActionBar.class, "HINT_CopyAssistantMessage"),
                e -> copyMessageToClipboard());
        copyBtn.setBorder(BorderFactory.createEmptyBorder());
        copyBtn.setContentAreaFilled(false);
        copyBtn.setOpaque(false);
        copyBtn.setForeground(theme.foreground());
        copyBtn.setVisible(false);
        this.copyBtnIcon = copyBtn.getIcon();

        JPanel copyPlaceholder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        copyPlaceholder.setOpaque(false);
        Dimension btnSize = copyBtn.getPreferredSize();
        copyPlaceholder.setPreferredSize(btnSize);
        copyPlaceholder.setMinimumSize(btnSize);
        copyPlaceholder.setMaximumSize(btnSize);
        copyPlaceholder.add(copyBtn);
        bubble.add(copyPlaceholder, BorderLayout.SOUTH);

        installHoverListeners();
    }

    private void installHoverListeners() {
        // Hover: show buttons after 400ms delay.
        bubble.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (showTimer == null) {
                    showTimer = new Timer(400, evt -> {
                        if (isMouseInsideComponent(bubble)) {
                            copyBtn.setVisible(true);
                            // Reset icon — a stale check icon may remain from a
                            // copy where the timer fired while the button was hidden.
                            if (copyRevertTimer != null && copyRevertTimer.isRunning()) {
                                copyRevertTimer.stop();
                            }
                            if (copyBtn != null) {
                                copyBtn.setIcon(copyBtnIcon);
                            }
                            if (pinBtn != null && !pinned) {
                                pinBtn.setVisible(true);
                            }
                        }
                    });
                    showTimer.setRepeats(false);
                }
                showTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isMouseInsideComponent(bubble)) {
                    hideButtons();
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
                        hideButtons();
                    }
                });
            }
        };
        if (copyBtn != null) {
            copyBtn.addMouseListener(childExit);
        }
        if (pinBtn != null) {
            pinBtn.addMouseListener(childExit);
        }
    }

    private void hideButtons() {
        if (showTimer != null) {
            showTimer.stop();
        }
        if (copyBtn != null) {
            copyBtn.setVisible(false);
        }
        if (pinBtn != null && !pinned) {
            pinBtn.setVisible(false);
        }
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
        Icon checkIcon = ThemeManager.getIcon("check.svg", 14);
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
        pinBtn.setIcon(ThemeManager.getIcon(
                pinned ? "pinned.svg" : "pin.svg", 32));
        pinBtn.setToolTipText(NbBundle.getMessage(BubbleActionBar.class,
                pinned ? "HINT_UnpinMessage" : "HINT_PinMessage"));
        pinBtn.getAccessibleContext().setAccessibleName(pinBtn.getToolTipText());
        pinBtn.getAccessibleContext().setAccessibleDescription(pinBtn.getToolTipText());
        applyPinAccent(pinned);
        owner.revalidate();
        owner.repaint();
    }

    private void applyPinAccent(boolean apply) {
        if (bubble instanceof RoundedPanel rp) {
            rp.setLeftAccent(apply ? new Color(0xCC, 0x33, 0x33, 255) : null);
        }
    }

    /** Resolves the current session ID via Lookup (defensive fallback only). */
    private String resolveSessionId() {
        if (sessionId != null) {
            return sessionId;
        }
        LOG.warn("BubbleActionBar sessionId was null; falling back to Lookup. messageId={0}", messageId);
        SessionControl sc = Lookup.getDefault().lookup(SessionControl.class);
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
            pinBtn.setIcon(ThemeManager.getIcon(
                    pinned ? "pinned.svg" : "pin.svg", 32));
            pinBtn.setToolTipText(NbBundle.getMessage(BubbleActionBar.class,
                    pinned ? "HINT_UnpinMessage" : "HINT_PinMessage"));
            pinBtn.getAccessibleContext().setAccessibleName(pinBtn.getToolTipText());
            pinBtn.getAccessibleContext().setAccessibleDescription(pinBtn.getToolTipText());
        }
        applyPinAccent(pinned);
    }

    /**
     * Updates the message ID (e.g., when a client-generated echo ID is replaced
     * by the server-assigned ID). Re-queries pin state for the new ID.
     */
    void setMessageId(String messageId) {
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
