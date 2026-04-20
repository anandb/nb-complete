package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;
import javax.swing.Icon;
import java.awt.Color;
import static org.junit.jupiter.api.Assertions.*;

class ThemeManagerTest {

    @Test
    void testGetCurrentTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        assertNotNull(theme);
        assertNotNull(theme.getBackground());
        assertNotNull(theme.getForeground());
    }

    @Test
    void testGetIcon() {
        // Test with an icon that should exist
        Icon icon = ThemeManager.getIcon("new.png", 24);
        assertNotNull(icon);
        assertEquals(24, icon.getIconWidth());
        assertEquals(24, icon.getIconHeight());
    }
}
