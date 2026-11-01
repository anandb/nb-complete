package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
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
    private static final String KEY_ORIG_ICON = "HelpButtonFlash.origIcon";
    /** Extra space around the glyph for the highlight pill. */
    private static final int PILL_PAD = 2;
    /** Highlight opacity: clearly visible, still see-through. */
    private static final int PILL_ALPHA = 90;
    private static final int PILL_ARC = 8;

    private HelpButtonFlash() {}

    /**
     * Flashes the given button with an alternating translucent pill behind its icon for
     * {@code TimingConstants.HELP_FLASH_TICKS} ticks, then restores the icon.
     *
     * @param button the button to flash
     */
    static void flash(JButton button) {
        stop(button);
        Icon original = button.getIcon();
        if (original == null) {
            return;
        }
        button.putClientProperty(KEY_ORIG_ICON, original);
        Color flashColor = flashColor();

        Timer timer = new Timer(TimingConstants.HELP_FLASH_INTERVAL_MS, new ActionListener() {
            private int tick = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                tick++;
                boolean highlight = tick % 2 == 0;
                button.setIcon(highlight ? new FlashIcon(original, flashColor) : original);
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

    /** Stops any running flash on the button and restores its icon. */
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

        Object original = button.getClientProperty(KEY_ORIG_ICON);
        if (original instanceof Icon icon) {
            button.setIcon(icon);
        }
        button.putClientProperty(KEY_ORIG_ICON, null);
        button.repaint();
    }

    /** Theme accent at a translucent alpha; the button itself stays transparent. */
    private static Color flashColor() {
        Color accent = ThemeManager.getCurrentTheme().accent();
        if (accent == null) {
            accent = new Color(66, 133, 244);
        }
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), PILL_ALPHA);
    }

    /** Paints a rounded translucent pill, then the delegate glyph on top. */
    private record FlashIcon(Icon delegate, Color color) implements Icon {

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(x - PILL_PAD, y - PILL_PAD,
                        getIconWidth() + PILL_PAD * 2, getIconHeight() + PILL_PAD * 2,
                        PILL_ARC, PILL_ARC);
            } finally {
                g2.dispose();
            }
            delegate.paintIcon(c, g, x, y);
        }

        @Override
        public int getIconWidth() {
            return delegate.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return delegate.getIconHeight();
        }
    }
}
