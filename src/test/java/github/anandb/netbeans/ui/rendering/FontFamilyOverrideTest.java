package github.anandb.netbeans.ui.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import github.anandb.netbeans.ui.components.UIUtils;

/**
 * {@code -J-Dbeanbot.font.family} / {@code beanbot.mono.font.family} must reach the Swing chat
 * font too: user bubbles render as a {@code JTextArea} sized from {@link ThemeManager#getFont()},
 * so a CSS-only override left them in the LAF family.
 */
class FontFamilyOverrideTest {

    @AfterEach
    void clearOverrides() {
        System.clearProperty(UIUtils.FONT_FAMILY_PROPERTY);
        System.clearProperty(UIUtils.MONO_FONT_FAMILY_PROPERTY);
        IconResourceManager.clearFontCache();
    }

    @Test
    void familyOverrideDrivesTheSwingChatFont() {
        System.setProperty(UIUtils.FONT_FAMILY_PROPERTY, Font.SERIF);
        IconResourceManager.clearFontCache();

        assertEquals(Font.SERIF, ThemeManager.getFont().getFamily());
    }

    @Test
    void familyOverrideKeepsTheLafSizeAndStyle() {
        IconResourceManager.clearFontCache();
        Font base = IconResourceManager.getFont();

        System.setProperty(UIUtils.FONT_FAMILY_PROPERTY, Font.MONOSPACED);
        IconResourceManager.clearFontCache();
        Font overridden = IconResourceManager.getFont();

        assertEquals(Font.MONOSPACED, overridden.getFamily());
        assertEquals(base.getSize(), overridden.getSize());
        assertEquals(base.getStyle(), overridden.getStyle());
    }

    @Test
    void monospaceOverrideDrivesTheCodeFont() {
        String current = IconResourceManager.getMonospaceFont().getFamily();
        String other = monospacedFamilyOtherThan(current);
        assumeTrue(other != null, "no second monospaced family is installed");

        System.setProperty(UIUtils.MONO_FONT_FAMILY_PROPERTY, other);
        IconResourceManager.clearFontCache();

        assertEquals(other, IconResourceManager.getMonospaceFont().getFamily());
    }

    /** A code font must stay monospaced, so a proportional value is not an override. */
    @Test
    void aProportionalOverrideDoesNotDriveTheCodeFont() {
        System.setProperty(UIUtils.MONO_FONT_FAMILY_PROPERTY, Font.SERIF);
        IconResourceManager.clearFontCache();

        assertTrue(isMonospaced(IconResourceManager.getMonospaceFont()),
                "a proportional family must fall back to a monospace one");
    }

    private static String monospacedFamilyOtherThan(String exclude) {
        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            if (!family.equalsIgnoreCase(exclude)
                    && isMonospaced(new Font(family, Font.PLAIN, 12))) {
                return family;
            }
        }
        return null;
    }

    private static boolean isMonospaced(Font font) {
        FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                .createGraphics().getFontMetrics(font);
        int width = fm.charWidth('i');
        return width > 0 && width == fm.charWidth('W') && width == fm.charWidth('0');
    }

    @Test
    void blankOverrideIsIgnored() {
        IconResourceManager.clearFontCache();
        Font base = IconResourceManager.getFont();

        System.setProperty(UIUtils.FONT_FAMILY_PROPERTY, "  ");
        IconResourceManager.clearFontCache();

        assertEquals(base.getFamily(), IconResourceManager.getFont().getFamily());
    }
}
