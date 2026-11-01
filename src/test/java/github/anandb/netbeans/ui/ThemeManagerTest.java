package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ThemeManagerTest {

    @Test
    void testGetCurrentTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        assertNotNull(theme);
        assertNotNull(theme.background());
        assertNotNull(theme.foreground());
    }

    @Test
    void testGetIcon() {
        // Test with an icon that should exist
        Icon icon = ThemeManager.getIcon("new.svg", 24);
        assertNotNull(icon);
        assertEquals(24, icon.getIconWidth());
        assertEquals(24, icon.getIconHeight());
    }

    /** Both break-mode states must resolve; a missing asset silently renders a blank button. */
    @Test
    void testBreakModeIconsResolve() {
        assertNotNull(ThemeManager.getIcon("lock.svg", 24));
        assertNotNull(ThemeManager.getIcon("take-break.svg", 24));
    }
}
