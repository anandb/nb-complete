package github.anandb.netbeans.ui.rendering;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.Icon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Optical centering wrapper: paint position moves up, icon metrics do not. */
class RaisedIconTest {

    private static final int SIZE = 10;

    /** Solid square icon so the paint position is measurable. */
    private static final Icon SOLID = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.fillRect(x, y, SIZE, SIZE);
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    };

    private static boolean inkedRow(BufferedImage image, int row) {
        for (int x = 0; x < SIZE; x++) {
            if ((image.getRGB(x, row) >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    private static BufferedImage paint(Icon icon) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        try {
            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Test
    void unreleasedIconFillsTheWholeBox() {
        BufferedImage image = paint(SOLID);
        assertTrue(inkedRow(image, 0));
        assertTrue(inkedRow(image, SIZE - 1));
    }

    @Test
    void raisedIconPaintsHigherAndKeepsItsMetrics() {
        Icon raised = new RaisedIcon(SOLID, 2);
        BufferedImage image = paint(raised);
        assertTrue(inkedRow(image, 0), "top rows must still be painted");
        assertTrue(inkedRow(image, SIZE - 3), "row shifted up by two keeps ink at 7");
        assertFalse(inkedRow(image, SIZE - 2), "row 8 is now empty");
        assertFalse(inkedRow(image, SIZE - 1), "row 9 is now empty");
        assertEquals(SIZE, raised.getIconWidth());
        assertEquals(SIZE, raised.getIconHeight());
    }
}
