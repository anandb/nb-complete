package github.anandb.netbeans.ui.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arming pulse is one controlled beat: full, two gentle dips, full, then still. */
class PulsingToggleButtonTest {

    private static final float MIN_ALPHA = 0.55f;
    private static final float DELTA = 0.02f;

    @Test
    void startsAndEndsFullyOpaque() {
        assertEquals(1f, PulsingToggleButton.alphaFor(0), 0.0001f);
        assertEquals(1f, PulsingToggleButton.alphaFor(30), 0.0001f);
        assertEquals(1f, PulsingToggleButton.alphaFor(-5), 0.0001f);
    }

    @Test
    void hasTwoDipsAtFullOpacityBetween() {
        assertEquals(MIN_ALPHA, PulsingToggleButton.alphaFor(7), DELTA);
        assertEquals(1f, PulsingToggleButton.alphaFor(15), 0.0001f);
        assertEquals(MIN_ALPHA, PulsingToggleButton.alphaFor(22), DELTA);
    }

    @Test
    void neverDimsBelowTheConfiguredFloor() {
        for (int frame = 0; frame <= 30; frame++) {
            float alpha = PulsingToggleButton.alphaFor(frame);
            assertTrue(alpha >= MIN_ALPHA - 0.0001f, "frame " + frame + " alpha " + alpha);
            assertTrue(alpha <= 1f + 0.0001f, "frame " + frame + " alpha " + alpha);
        }
    }

    @Test
    void selectedStatePaintsAnAccentPillBehindTheIcon() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PulsingToggleButton button = new PulsingToggleButton(new FixedIcon(8), new FixedIcon(8));
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setSize(32, 32);

            int idle = pixelAt(button, 4, 16);
            button.setSelected(true);
            int armed = pixelAt(button, 4, 16);

            assertEquals(Color.WHITE.getRGB(), idle, "released state must stay a bare toolbar button");
            assertNotEquals(idle, armed, "armed state must paint the accent pill");
        });
    }

    private static int pixelAt(JToggleButton button, int x, int y) {
        BufferedImage image = new BufferedImage(button.getWidth(), button.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            button.paint(g);
        } finally {
            g.dispose();
        }
        return image.getRGB(x, y);
    }

    /** Paints nothing, so the pill is measured beside an empty icon area. */
    private record FixedIcon(int size) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            // Intentionally blank: this test isolates the selected-state background.
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
