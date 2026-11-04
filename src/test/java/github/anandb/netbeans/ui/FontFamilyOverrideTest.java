package github.anandb.netbeans.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Font;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
        System.setProperty(UIUtils.MONO_FONT_FAMILY_PROPERTY, Font.SERIF);
        IconResourceManager.clearFontCache();

        assertEquals(Font.SERIF, IconResourceManager.getMonospaceFont().getFamily());
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
