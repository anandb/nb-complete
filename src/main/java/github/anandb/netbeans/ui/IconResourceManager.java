package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.UIManager;
import org.openide.util.ImageUtilities;
import github.anandb.netbeans.support.Logger;

/**
 * Manages icon caching, font resolution, and dark mode detection.
 * Extracted from ThemeManager for better separation of concerns.
 */
final class IconResourceManager {

    private static final Logger LOG = new Logger(IconResourceManager.class);

    private IconResourceManager() {}

    private static volatile Font cachedFont;
    private static volatile Font cachedMonospaceFont;
    private static volatile Boolean cachedIsDark;

    static {
        UIManager.addPropertyChangeListener(e -> {
            cachedFont = null;
            cachedMonospaceFont = null;
            cachedIsDark = null;
        });
    }

    private static final Map<String, Icon> ICON_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, Icon>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Icon> eldest) {
                return size() > 256;
            }
        }
    );

    static void clearIconCache() {
        ICON_CACHE.clear();
    }

    static Icon getIcon(String name, int size) {
        if (name == null) {
            return null;
        }
        String cacheKey = name + "@" + size;
        Icon cached = ICON_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String resourcePath = "github/anandb/netbeans/ui/icons/" + getThemeAwareName(name);
        java.awt.Image img = ImageUtilities.loadImage(resourcePath, true);
        if (img == null) {
            java.net.URL url = IconResourceManager.class.getClassLoader().getResource(resourcePath);
            if (url != null) {
                img = new javax.swing.ImageIcon(url).getImage();
            }
        }
        if (img == null) {
            LOG.severe("Failed to load icon: {0}", resourcePath);
            return null;
        }
        if (size > 0 && (img.getWidth(null) != size || img.getHeight(null) != size)) {
            BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(img, 0, 0, size, size, null);
            g2.dispose();
            img = bi;
        }
        Icon icon = ImageUtilities.image2Icon(img);
        ICON_CACHE.put(cacheKey, icon);
        return icon;
    }

    static Font getFont() {
        Font f = cachedFont;
        if (f != null) return f;
        f = UIManager.getFont("Label.font");
        if (f == null) {
            f = UIManager.getFont("controlFont");
        }
        if (f == null) {
            f = new Font("Dialog", Font.PLAIN, 12);
        }
        cachedFont = f;
        return f;
    }

    static Font getMonospaceFont() {
        Font f = cachedMonospaceFont;
        if (f != null) return f;

        Font editorFont = UIManager.getFont("EditorPane.font");
        int size = (editorFont != null) ? editorFont.getSize() : 13;

        Font meslo = new Font("Fira Code", Font.PLAIN, size);
        if (meslo.getFamily().equalsIgnoreCase("Fira Code")) {
            cachedMonospaceFont = meslo;
            return meslo;
        }

        f = (editorFont != null)
                ? new Font(editorFont.getName(), editorFont.getStyle(), size)
                : new Font(Font.MONOSPACED, Font.PLAIN, size);
        cachedMonospaceFont = f;
        return f;
    }

    static boolean isDark() {
        Boolean cached = cachedIsDark;
        if (cached != null) return cached;
        boolean result = UIManager.getBoolean("nb.dark.theme");
        if (!UIManager.getDefaults().containsKey("nb.dark.theme")) {
            Color bg = UIManager.getColor("Panel.background");
            if (bg != null) {
                result = (bg.getRed() * 0.299 + bg.getGreen() * 0.587 + bg.getBlue() * 0.114) < 128;
            } else {
                LOG.severe("Theme detection fallback failed: Panel.background color is missing from UIManager");
            }
        }
        cachedIsDark = result;
        return result;
    }

    private static String getThemeAwareName(String name) {
        if (isDark()) {
            String darkName = name.replace(".svg", "_dark.svg");
            if (ImageUtilities.loadImage("github/anandb/netbeans/ui/icons/" + darkName, true) != null) {
                return darkName;
            }
        }
        return name;
    }
}
