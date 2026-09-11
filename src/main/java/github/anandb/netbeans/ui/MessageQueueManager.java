package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openide.util.NbBundle;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Manages a queue of user messages posted while the bot is processing.
 * Provides a toolbar button with a badge count, context menu to cancel,
 * and a wobble animation that fires every 5 seconds while the queue has items.
 */
public final class MessageQueueManager {

    private static final Color ACCENT_COLOR = new Color(0xFF, 0x8C, 0x00); // amber for queued bubbles
    private static final int WOBBLE_INTERVAL_MS = 5000;
    private static final int WOBBLE_DURATION_MS = 300;
    private static final double WOBBLE_ANGLE = Math.toRadians(12);

    private final Queue<String> queuedMessages = new ConcurrentLinkedQueue<>();
    private final JButton queueBtn;
    private final Icon baseIcon;
    private final int iconSize;
    private Timer wobbleTimer;
    private Timer wobbleAnimTimer;
    private boolean wobbling;
    private Runnable sendNowCallback;
    /** When false (e.g. non-goose agents), queueing is disabled and the button stays hidden. */
    private volatile boolean enabled = true;

    MessageQueueManager() {
        int iconSize = Math.max(PluginSettings.getToolbarIconSize(), 32);
        this.iconSize = iconSize;
        baseIcon = ThemeManager.getIcon("queue.svg", iconSize);
        queueBtn = UIUtils.createToolbarButton("queue.svg", iconSize,
                NbBundle.getMessage(MessageQueueManager.class, "HINT_MessageQueue", 0), null);
        queueBtn.setIcon(baseIcon);
        queueBtn.setVisible(false);
        // Show popup on right-click (and platform-native popup trigger).
        queueBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupAt(e.getX(), e.getY());
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupAt(e.getX(), e.getY());
                }
            }
            @Override
            public void mouseClicked(MouseEvent e) {
                // Left-click also opens the menu (button is hidden while the
                // queue is empty, so this only ever fires with items queued).
                // Left-only guard: on macOS a right-click release reports
                // popupTrigger == false in mouseClicked, which would open a
                // second menu right after mousePressed already opened one.
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
                    showPopupAt(e.getX(), e.getY());
                }
            }
        });
    }

    /** Returns the toolbar button to be added to the layout. */
    JButton getButton() {
        return queueBtn;
    }

    /** Sets the callback invoked when "Send Now" is chosen from the context menu. */
    void setSendNowCallback(Runnable callback) {
        this.sendNowCallback = callback;
    }

    /** Enables or disables queueing. When disabled, {@link #enqueue} is a no-op
     *  and the toolbar button is forced hidden (queueing is only used by the
     *  goose agent). */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            cancelAll();
        }
    }

    /** Returns the accent color used for queued bubble indicators. */
    static Color getAccentColor() {
        return ACCENT_COLOR;
    }

    /** Returns the current queue size. */
    int size() {
        return queuedMessages.size();
    }

    /** Returns true if the queue is empty. */
    boolean isEmpty() {
        return queuedMessages.isEmpty();
    }

    /**
     * Adds a message to the queue. The message text will be sent as part of
     * the combined prompt when the turn ends.
     */
    void enqueue(String text) {
        if (!enabled) {
            return;
        }
        queuedMessages.add(text);
        // enqueue() may be invoked off the EDT (send pipeline paths) — badge
        // and timer state are EDT-confined, so marshal the UI side.
        runOnEdt(() -> {
            updateBadge();
            startWobbleIfNeeded();
        });
    }

    /**
     * Returns all queued messages as a single combined text (newline-separated),
     * then clears the queue and hides the button.
     *
     * @return combined text, or {@code null} if the queue was empty
     */
    String flushAll() {
        if (queuedMessages.isEmpty()) {
            return null;
        }
        List<String> messages = new ArrayList<>();
        String msg;
        while ((msg = queuedMessages.poll()) != null) {
            messages.add(msg);
        }
        runOnEdt(() -> {
            updateBadge();
            stopWobble();
        });
        StringJoiner joiner = new StringJoiner("\n");
        messages.forEach(joiner::add);
        // Returns text only — attachments are not queued (see MessageSender queue
        // branch). Concatenating several queued messages into one prompt makes
        // per-message file blocks ambiguous, so the merged send carries no files.
        return joiner.toString();
    }

    /** Clears the queue and hides the button. Called by the Cancel menu item
     *  and by {@link #setEnabled(boolean)} when queueing is switched off —
     *  deliberately unconditional: the disable path relies on the wipe even
     *  though {@code enabled} is already false. */
    void cancelAll() {
        queuedMessages.clear();
        runOnEdt(() -> {
            updateBadge();
            stopWobble();
        });
    }

    /** Marshals {@code r} onto the EDT — queue entry points may be called from any thread. */
    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** Updates button visibility and tooltip. Must run on the EDT. */
    private void updateBadge() {
        int count = queuedMessages.size();
        queueBtn.setVisible(enabled && count > 0);
        if (count > 0) {
            queueBtn.setIcon(baseIcon);
            queueBtn.setToolTipText(
                    NbBundle.getMessage(MessageQueueManager.class, "HINT_MessageQueue", count));
        } else {
            queueBtn.setIcon(baseIcon);
            queueBtn.setToolTipText(
                    NbBundle.getMessage(MessageQueueManager.class, "HINT_MessageQueue", 0));
        }
        queueBtn.revalidate();
        queueBtn.repaint();
    }

    private void showPopupAt(int x, int y) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem sendNowItem = new JMenuItem(
                NbBundle.getMessage(MessageQueueManager.class, "MENU_SendNow"));
        sendNowItem.addActionListener(ev -> {
            if (sendNowCallback != null) {
                sendNowCallback.run();
            }
        });
        popup.add(sendNowItem);
        JMenuItem cancelItem = new JMenuItem(
                NbBundle.getMessage(MessageQueueManager.class, "MENU_CancelQueue"));
        cancelItem.addActionListener(ev -> cancelAll());
        popup.add(cancelItem);
        popup.show(queueBtn, x, y);
    }

    /** Starts the periodic wobble timer. EDT-confined: every entry point
     *  marshals through {@link #runOnEdt}, so this check-then-create sequence
     *  is race-free (previously two threads could create duplicate timers). */
    private void startWobbleIfNeeded() {
        assert SwingUtilities.isEventDispatchThread();
        if (wobbling || wobbleTimer != null) {
            return;
        }
        wobbling = true;
        wobbleTimer = new Timer(WOBBLE_INTERVAL_MS, e -> triggerWobble());
        wobbleTimer.setInitialDelay(0);
        wobbleTimer.start();
    }

    private void triggerWobble() {
        assert SwingUtilities.isEventDispatchThread();
        if (queuedMessages.isEmpty()) {
            stopWobble();
            return;
        }
        // Keep the animation timer in a field so stopWobble() can cancel an
        // in-flight animation; otherwise the 30ms timer kept running (leak,
        // holding queueBtn) and a new wobble could stack a second animation
        // on top of it.
        stopWobbleAnim();
        wobbleAnimTimer = new Timer(30, null) {
            long start = System.currentTimeMillis();
            @Override
            protected void fireActionPerformed(ActionEvent ae) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed > WOBBLE_DURATION_MS) {
                    updateBadge();
                    queueBtn.repaint();
                    stopWobbleAnim();
                    return;
                }
                double progress = (double) elapsed / WOBBLE_DURATION_MS;
                double angle = WOBBLE_ANGLE * Math.sin(progress * Math.PI * 4);
                Icon currentIcon = queueBtn.getIcon();
                Icon badgedBase = (currentIcon instanceof WobbleIcon wi) ? wi.base : currentIcon;
                queueBtn.setIcon(new WobbleIcon(badgedBase, angle));
                queueBtn.repaint();
            }
        };
        wobbleAnimTimer.setInitialDelay(0);
        wobbleAnimTimer.start();
    }

    private void stopWobble() {
        assert SwingUtilities.isEventDispatchThread();
        wobbling = false;
        stopWobbleAnim();
        if (wobbleTimer != null) {
            wobbleTimer.stop();
            wobbleTimer = null;
        }
    }

    /** Stops and releases the one-shot wobble animation timer. */
    private void stopWobbleAnim() {
        if (wobbleAnimTimer != null) {
            wobbleAnimTimer.stop();
            wobbleAnimTimer = null;
        }
    }

    /** Icon wrapper that applies a rotation transform for the wobble animation. */
    private static final class WobbleIcon implements Icon {
        private final Icon base;
        private final double angle;

        WobbleIcon(Icon base, double angle) {
            this.base = base;
            this.angle = angle;
        }

        @Override
        public int getIconWidth() {
            return base.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return base.getIconHeight();
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                int w = getIconWidth();
                int h = getIconHeight();
                double cx = x + w / 2.0;
                double cy = y + h / 2.0;
                AffineTransform old = g2.getTransform();
                g2.rotate(angle, cx, cy);
                base.paintIcon(c, g2, x, y);
                g2.setTransform(old);
            } finally {
                g2.dispose();
            }
        }
    }
}
