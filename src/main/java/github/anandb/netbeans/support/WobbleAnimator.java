package github.anandb.netbeans.support;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Reusable side-to-side "wobble" animation used to draw attention to a panel.
 * Paints a translation offset (via {@link #x()} / {@link #y()} applied in the
 * component's paint method) and repeats every few seconds after a quiet period.
 * Holds no component reference beyond repaint, so it is safe for both the
 * sidebar permission panel and the mini-assistant dialog.
 */
public final class WobbleAnimator {

    private static final int[] Y_OFFSETS = {0, -2, -4, -6, -7, -6, -4, -2, 0, -1, -3, -4, -3, -1, 0};
    private static final int[] X_OFFSETS = {0, -2, 2, -2, 2, -1, 1, 0, 0, 0, 0, 0, 0, 0, 0};

    private static final int WOBBLE_DELAY_MS = 10_000;
    private static final int WOBBLE_INTERVAL_MS = 30;
    private static final int WOBBLE_RESTART_MS = 5_000;

    // Rapid side-to-side "buzz" burst used to draw immediate attention (e.g. when the
    // user tries to send a new message while a permission request is pending).
    private static final int BUZZ_INTERVAL_MS = 15;
    private static final int[] BUZZ_X = {0, -3, 3, -3, 3, -2, 2, -2, 2, -1, 1, -1, 1, -1, 1, 0};
    private static final int[] BUZZ_Y = {0, -2, 2, -2, 2, -1, 1, -1, 1, 0, -1, 1, -1, 1, 0, 0};

    private final JComponent target;

    private int x;
    private int y;
    private Timer wobbleTimer;
    private Timer wobbleRestartTimer;
    private Timer wobbleDelayTimer;
    private Timer buzzTimer;

    public WobbleAnimator(JComponent target) {
        this.target = target;
    }

    /** Current horizontal paint offset. */
    public int x() {
        return x;
    }

    /** Current vertical paint offset. */
    public int y() {
        return y;
    }

    /** Schedules wobble to start after 10 seconds of inactivity. */
    public void scheduleStart() {
        cancelDelay();
        wobbleDelayTimer = new Timer(WOBBLE_DELAY_MS, e -> {
            wobbleDelayTimer.stop();
            wobbleDelayTimer = null;
            start();
        });
        wobbleDelayTimer.setRepeats(false);
        wobbleDelayTimer.start();
    }

    /** Cancels any pending wobble delay. */
    public void cancelDelay() {
        if (wobbleDelayTimer != null) {
            wobbleDelayTimer.stop();
            wobbleDelayTimer = null;
        }
    }

    /** Shakes the target side-to-side, repeating every few seconds. */
    public void start() {
        if (wobbleTimer != null && wobbleTimer.isRunning()) {
            // Already wobbling — just reset the cycle (don't double-start)
            return;
        }

        wobbleTimer = new Timer(WOBBLE_INTERVAL_MS, new ActionListener() {
            private int frame = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (frame < Y_OFFSETS.length) {
                    x = X_OFFSETS[frame];
                    y = Y_OFFSETS[frame];
                    target.repaint();
                    frame++;
                } else {
                    x = 0;
                    y = 0;
                    target.repaint();
                    wobbleTimer.stop();
                    wobbleTimer = null;
                    // Schedule repeat after 5 seconds
                    wobbleRestartTimer = new Timer(WOBBLE_RESTART_MS, ev -> start());
                    wobbleRestartTimer.setRepeats(false);
                    wobbleRestartTimer.start();
                }
            }
        });
        wobbleTimer.start();
    }

    /** Performs a rapid side-to-side "buzz" to simulate an alert (e.g. when the user
     *  tries to send a new message while a permission request is pending). Runs one
     *  fast burst, then schedules the gentle periodic wobble so attention continues. */
    public void buzz() {
        // Stop any pending gentle wobble so the two animations do not fight over x/y.
        stop();
        buzzTimer = new Timer(BUZZ_INTERVAL_MS, new ActionListener() {
            private int frame = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (frame < BUZZ_X.length) {
                    x = BUZZ_X[frame];
                    y = BUZZ_Y[frame];
                    target.repaint();
                    frame++;
                } else {
                    x = 0;
                    y = 0;
                    target.repaint();
                    buzzTimer.stop();
                    buzzTimer = null;
                    // Resume periodic attention after the burst.
                    scheduleStart();
                }
            }
        });
        buzzTimer.start();
    }

    /** Stops the wobble animation and its repeat cycle. */
    public void stop() {
        cancelDelay();
        if (wobbleTimer != null) {
            wobbleTimer.stop();
            wobbleTimer = null;
        }
        if (wobbleRestartTimer != null) {
            wobbleRestartTimer.stop();
            wobbleRestartTimer = null;
        }
        if (buzzTimer != null) {
            buzzTimer.stop();
            buzzTimer = null;
        }
        x = 0;
        y = 0;
        target.repaint();
    }
}
