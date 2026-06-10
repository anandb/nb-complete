package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ColorThemeTest {

    @Test
    void testColorThemeGetters() {
        // Clear the static cache in ColorTheme using reflection to avoid flakiness
        try {
            java.lang.reflect.Field field = ColorTheme.class.getDeclaredField("cachedTheme");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            // fall through
        }

        // Initialize UIManager defaults for the test environment
        TestUiUtils.setupTestUIManager();

        ColorTheme theme = ThemeManager.getCurrentTheme();
        assertNotNull(theme.background());
        assertNotNull(theme.foreground());
        assertNotNull(theme.bubbleUser());
        assertNotNull(theme.assistantForeground());
        assertNotNull(theme.headerForeground());
    }
}
