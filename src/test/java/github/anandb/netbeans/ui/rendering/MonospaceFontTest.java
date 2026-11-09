package github.anandb.netbeans.ui.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.ui.components.UIUtils;

/**
 * Code, tool output and the diff panes share one font, and it has to be a monospace font: a
 * proportional family silently turns every diff into a sans-serif pane.
 */
class MonospaceFontTest {

    @AfterEach
    void reset() {
        System.clearProperty(UIUtils.MONO_FONT_FAMILY_PROPERTY);
        ThemeManager.clearFontCache();
    }

    @Test
    void theCodeFontIsMonospaced() {
        assertMonospaced(ThemeManager.getMonospaceFont(), "code font");
    }

    @Test
    void theDiffFontIsMonospacedAndSmaller() {
        Font mono = ThemeManager.getMonospaceFont();
        Font diff = ThemeManager.getDiffMonospaceFont();

        assertMonospaced(diff, "diff font");
        assertTrue(diff.getSize() <= mono.getSize(), "the diff font is never larger than the code font");
    }

    @Test
    void aProportionalOverrideIsRejected() {
        System.setProperty(UIUtils.MONO_FONT_FAMILY_PROPERTY, "Dialog");
        ThemeManager.clearFontCache();

        assertMonospaced(ThemeManager.getMonospaceFont(),
                "an override that is not monospaced must not be used for code");
    }

    private static void assertMonospaced(Font font, String what) {
        FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                .createGraphics().getFontMetrics(font);
        int width = fm.charWidth('i');
        assertTrue(width > 0, what + " must have a real glyph width");
        assertEquals(width, fm.charWidth('W'), what + " must be monospaced: " + font);
        assertEquals(width, fm.charWidth('0'), what + " must be monospaced: " + font);
        assertEquals(width, fm.charWidth(' '), what + " must be monospaced: " + font);
    }
}
