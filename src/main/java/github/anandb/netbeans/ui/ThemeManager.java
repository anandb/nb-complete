package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;

import javax.swing.Icon;
import javax.swing.UIManager;
import org.openide.util.ImageUtilities;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles non-color theme resources (Icons, Fonts) and coordinates with ColorTheme.
 */
public class ThemeManager {
    private static final Map<String, Icon> iconCache = new HashMap<>();

    public static Icon getIcon(String name, int size) {
        if (name == null) return null;
        String key = name + "_" + size;
        if (!iconCache.containsKey(key)) {
            java.awt.Image img = ImageUtilities.loadImage("github/anandb/netbeans/ui/icons/" + name, true);
            if (img != null) {
                java.awt.Image scaled = img.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH);
                iconCache.put(key, ImageUtilities.image2Icon(scaled));
            } else {
                return null;
            }
        }
        return iconCache.get(key);
    }

    public static Icon getIcon(String name) {
        return getIcon(name, 16);
    }

    public static Font getFont() {
        Font f = UIManager.getFont("Label.font");
        if (f == null) f = UIManager.getFont("controlFont");
        if (f == null) f = new Font("Dialog", Font.PLAIN, 12);
        return f;
    }

    public static Font getMonospaceFont() {
        Font editorFont = UIManager.getFont("EditorPane.font");
        int size = (editorFont != null) ? editorFont.getSize() : 13;
        return (editorFont != null)
            ? new Font(editorFont.getName(), editorFont.getStyle(), size)
            : new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    public static ColorTheme getCurrentTheme() {
        boolean isDark = org.openide.util.NbPreferences.forModule(ThemeManager.class).getBoolean("isDarkMode", false);
        return isDark ? ColorTheme.DARK : ColorTheme.LIGHT;
    }

    public static void setDarkMode(boolean isDark) {
        org.openide.util.NbPreferences.forModule(ThemeManager.class).putBoolean("isDarkMode", isDark);
    }
}
