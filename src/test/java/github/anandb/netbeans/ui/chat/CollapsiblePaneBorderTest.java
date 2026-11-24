package github.anandb.netbeans.ui.chat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.ThemeManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pane outline is the only thing separating an expanded tool/activity card from the thread
 * background, and the theme border is drawn for the thin collapsed header. Expanding shifts it
 * 30% away from that surface (darker on light themes, lighter on dark ones); code blocks keep
 * their own header-rule colour.
 */
class CollapsiblePaneBorderTest {

    /** 30% toward black or white, with a pixel of slack for rounding and antialiasing. */
    private static final double SHIFT = 0.30;
    private static final int TOLERANCE = 2;

    @Test
    void expandingShiftsTheToolPaneOutlineAwayFromItsSurface() {
        CollapsibleToolPane pane = new CollapsibleToolPane("TOOL: run_command", "ls -la", true);
        ColorTheme theme = ThemeManager.getCurrentTheme();

        Color expanded = outline(pane);
        pane.setExpanded(false);
        Color collapsed = outline(pane);

        assertEquals(theme.bubbleBorder(), collapsed, "the collapsed one-line card keeps the theme border");
        assertNotEquals(collapsed, expanded, "the expanded outline must not wash out");
        Color expected = theme.isDark()
                ? blend(collapsed, Color.WHITE)
                : blend(collapsed, Color.BLACK);
        assertEquals(expected.getRed(), expanded.getRed(), TOLERANCE);
        assertEquals(expected.getGreen(), expanded.getGreen(), TOLERANCE);
        assertEquals(expected.getBlue(), expanded.getBlue(), TOLERANCE);
        assertTrue(expanded.getAlpha() > 0, "the outline must stay opaque");
    }

    @Test
    void expandingKeepsTheCodePaneOutline() {
        CollapsibleCodePane pane = new CollapsibleCodePane("java", "int a = 1;", true);
        ColorTheme theme = ThemeManager.getCurrentTheme();

        Color expanded = outline(pane);
        pane.setExpanded(false);
        Color collapsed = outline(pane);

        assertEquals(theme.codeHeaderBorder(), collapsed);
        assertEquals(collapsed, expanded, "code blocks keep their own header-rule colour");
    }

    /** Paints the pane and reads the top edge, where the 1px outline is the last thing drawn. */
    private static Color outline(BaseCollapsiblePane pane) {
        final int width = 420;
        final int height = 160;
        pane.setSize(width, height);
        pane.validate();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            pane.paint(g);
        } finally {
            g.dispose();
        }
        return new Color(image.getRGB(width / 2, 0), true);
    }

    private static Color blend(Color from, Color to) {
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * SHIFT),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * SHIFT),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * SHIFT));
    }
}
