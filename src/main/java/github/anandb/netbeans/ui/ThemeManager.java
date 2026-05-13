package github.anandb.netbeans.ui;

import java.awt.Font;
import javax.swing.Icon;

/**
 * Unified theme manager facade.
 * Delegates resource operations to IconResourceManager, color operations to ColorTheme.
 */
public class ThemeManager {
    private ThemeManager() {}

    public static void clearIconCache() {
        IconResourceManager.clearIconCache();
    }

    public static Icon getIcon(String name) {
        return getIcon(name, 0);
    }

    public static Icon getIcon(String name, int size) {
        return IconResourceManager.getIcon(name, size);
    }

    public static Font getFont() {
        return IconResourceManager.getFont();
    }

    public static Font getMonospaceFont() {
        return IconResourceManager.getMonospaceFont();
    }

    public static boolean isDark() {
        return IconResourceManager.isDark();
    }

    public static ColorTheme getCurrentTheme() {
        return ColorTheme.getNativeTheme(isDark());
    }
}
