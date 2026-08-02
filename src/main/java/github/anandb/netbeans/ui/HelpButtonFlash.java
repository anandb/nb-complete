package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.Timer;
import github.anandb.netbeans.support.TimingConstants;

/**
 * Flashes a help button on startup for discoverability.
 * Self-contained — creates its own timer and cleans up when done.
 * Idempotent: calling {@link #flash} on an already-flashing button restarts
 * the flash cleanly, and {@link #stop} can be called externally to end it.
 */
// DSL-CONTROLLER: not a view — flashTimer drives the help button's
// "new-update" pulse animation. Stays imperative; the button it animates is
// bound by the future ChatToolbarSpec.
final class HelpButtonFlash {

    private static final String KEY_TIMER = "HelpButtonFlash.timer";
    private static final String KEY_LISTENER = "HelpButtonFlash.listener";
    private static final String KEY_ORIG_OPAQUE = "HelpButtonFlash.origOpaque";
    private static final String KEY_ORIG_FILLED = "HelpButtonFlash.origContentAreaFilled";
    private static final String KEY_ORIG_BG = "HelpButtonFlash.origBackground";

    private HelpButtonFlash() {}

    /**
     * Flashes the given button with alternating highlight/background for
     * {@code TimingConstants.HELP_FLASH_TICKS} ticks, then restores.
     *
     * @param button the button to flash
     */
    static void flash(JButton button) {
        stop(button);
        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color flashBg = theme.isDark()
                ? new Color(128, 128, 128, 180)
                : new Color(66, 133, 244, 180);

        // Remember the original appearance so stop() can restore it exactly.
        button.putClientProperty(KEY_ORIG_OPAQUE, button.isOpaque());
        button.putClientProperty(KEY_ORIG_FILLED, button.isContentAreaFilled());
        button.putClientProperty(KEY_ORIG_BG, button.getBackground());

        Timer timer = new Timer(TimingConstants.HELP_FLASH_INTERVAL_MS, new ActionListener() {
            private int tick = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                tick++;
                boolean highlight = tick % 2 == 0;
                if (highlight) {
                    button.setOpaque(true);
                    button.setBackground(flashBg);
                    button.setContentAreaFilled(true);
                } else {
                    button.setOpaque(false);
                    button.setContentAreaFilled(false);
                }
                button.repaint();
                if (tick >= TimingConstants.HELP_FLASH_TICKS) {
                    stop(button);
                }
            }
        });
        button.putClientProperty(KEY_TIMER, timer);

        MouseAdapter listener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                stop(button);
            }
        };
        button.putClientProperty(KEY_LISTENER, listener);
        button.addMouseListener(listener);
        timer.setInitialDelay(TimingConstants.HELP_FLASH_INITIAL_DELAY_MS);
        timer.start();
    }

    /** Stops any running flash on the button and restores its appearance. */
    static void stop(JButton button) {
        Timer timer = (Timer) button.getClientProperty(KEY_TIMER);
        if (timer != null && timer.isRunning()) {
            timer.stop();
        }
        button.putClientProperty(KEY_TIMER, null);

        MouseAdapter listener = (MouseAdapter) button.getClientProperty(KEY_LISTENER);
        if (listener != null) {
            button.removeMouseListener(listener);
            button.putClientProperty(KEY_LISTENER, null);
        }

        // Restore the pre-flash appearance, if we captured one.
        Object origOpaque = button.getClientProperty(KEY_ORIG_OPAQUE);
        Object origFilled = button.getClientProperty(KEY_ORIG_FILLED);
        Object origBg = button.getClientProperty(KEY_ORIG_BG);
        if (origOpaque instanceof Boolean) {
            button.setOpaque((Boolean) origOpaque);
        }
        if (origFilled instanceof Boolean) {
            button.setContentAreaFilled((Boolean) origFilled);
        }
        if (origBg instanceof Color) {
            button.setBackground((Color) origBg);
        }
        button.putClientProperty(KEY_ORIG_OPAQUE, null);
        button.putClientProperty(KEY_ORIG_FILLED, null);
        button.putClientProperty(KEY_ORIG_BG, null);
        button.repaint();
    }
}
