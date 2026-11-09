package github.anandb.netbeans.ui.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;

import org.junit.jupiter.api.Test;

/**
 * The welcome accents are drawn, not typed: an emoji codepoint renders as an empty box when the
 * user's font lacks it (and Java cannot render a colour-emoji font at all). Each icon must have
 * its requested size and put real ink on the canvas in the colour it was given.
 */
class WelcomeAccentIconsTest {

    @Test
    void everyAccentPaintsInkAtItsRequestedSize() {
        Icon[] icons = {
            WelcomeAccentIcons.sparkle(Color.BLACK, 16),
            WelcomeAccentIcons.bulb(Color.BLACK, 14),
            WelcomeAccentIcons.magnifier(Color.BLACK, 14),
            WelcomeAccentIcons.checkBox(Color.BLACK, 14),
        };

        for (Icon icon : icons) {
            assertEquals(icon.getIconWidth(), icon.getIconHeight(), "accents are square");
            assertTrue(ink(icon, icon.getIconWidth()) > 8, "the accent must actually draw");
        }
    }

    @Test
    void anAccentUsesTheColourItWasGiven() {
        int red = ink(WelcomeAccentIcons.sparkle(Color.RED, 16), 16, Color.RED);
        int green = ink(WelcomeAccentIcons.sparkle(Color.RED, 16), 16, Color.GREEN);

        assertTrue(red > 0, "the requested colour must be on the canvas");
        assertEquals(0, green, "no other colour may leak into the accent");
    }

    private static int ink(Icon icon, int size) {
        BufferedImage img = paint(icon, size);
        int n = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Pixels close to {@code color} — anti-aliasing keeps the edges honest. */
    private static int ink(Icon icon, int size, Color color) {
        BufferedImage img = paint(icon, size);
        int n = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 40) {
                    continue;
                }
                Color px = new Color(argb, true);
                if (Math.abs(px.getRed() - color.getRed()) < 40
                        && Math.abs(px.getGreen() - color.getGreen()) < 40
                        && Math.abs(px.getBlue() - color.getBlue()) < 40) {
                    n++;
                }
            }
        }
        return n;
    }

    private static BufferedImage paint(Icon icon, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }
}
