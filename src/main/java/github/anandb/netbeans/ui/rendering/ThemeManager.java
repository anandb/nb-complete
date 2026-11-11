package github.anandb.netbeans.ui.rendering;

import java.awt.Font;
import javax.swing.Icon;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

/**
 * Unified theme manager facade.
 * Delegates resource operations to IconResourceManager, color operations to ColorTheme.
 */
public class ThemeManager {
    private static final Logger LOG = Logger.from(ThemeManager.class);
    private static volatile boolean themeLogged;

    private ThemeManager() {}

    public static void clearIconCache() {
        IconResourceManager.clearIconCache();
    }

    /** Drops the cached Swing font after the font-family setting changes. */
    public static void clearFontCache() {
        IconResourceManager.clearFontCache();
    }

    /** Rebuilds the theme and its CSS after a colour setting changes. */
    public static void reloadTheme() {
        ColorTheme.invalidate();
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

    /** The editor font one point smaller, clamped at 9 — for the diff panes. */
    public static Font getDiffMonospaceFont() {
        return IconResourceManager.getDiffMonospaceFont();
    }

    /**
     * Monospace for the permission surfaces (request panel, mini assistant and permission bubble):
     * the diff font while <em>Use a smaller font size on diffs</em> is on, the editor monospace
     * otherwise, so permission dialogs read exactly like the diff windows they preview.
     */
    public static Font getPermissionMonospaceFont() {
        return PluginSettings.isDiffIdeFontEnabled() ? getDiffMonospaceFont() : getMonospaceFont();
    }

    public static boolean isDark() {
        return IconResourceManager.isDark();
    }

    public static ColorTheme getCurrentTheme() {
        if (!themeLogged) {
            themeLogged = true;
            Font f = getFont();
            LOG.info("Font family: {0} (size {1}), monospace: {2}", f.getFamily(), f.getSize(), getMonospaceFont().getFamily());
        }
        return ColorTheme.getNativeTheme(isDark());
    }
}
