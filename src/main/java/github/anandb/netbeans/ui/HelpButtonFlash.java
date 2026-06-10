package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.Timer;
import github.anandb.netbeans.support.TimingConstants;

/**
 * Flashes a help button on startup for discoverability.
 * Self-contained — creates its own timer and cleans up when done.
 */
final class HelpButtonFlash {

    private HelpButtonFlash() {}

    /**
     * Flashes the given button with alternating highlight/background for
     * {@code TimingConstants.HELP_FLASH_TICKS} ticks, then restores.
     *
     * @param button the help button to flash
     */
    static void flash(JButton button) {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color flashBg = theme.isDark()
                ? new Color(128, 128, 128, 180)
                : new Color(66, 133, 244, 180);

        Timer timer = new Timer(TimingConstants.HELP_FLASH_INTERVAL_MS, new ActionListener() {
            int tick = 0;

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
                    ((Timer) e.getSource()).stop();
                    button.setOpaque(false);
                    button.setContentAreaFilled(false);
                    button.repaint();
                }
            }
        });
        timer.setInitialDelay(TimingConstants.HELP_FLASH_INITIAL_DELAY_MS);
        timer.start();
    }
}
