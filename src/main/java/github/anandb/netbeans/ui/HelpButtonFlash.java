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

        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.repaint();
    }
}
