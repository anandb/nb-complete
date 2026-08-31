package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.openide.util.ImageUtilities;
import github.anandb.netbeans.support.Logger;

/**
 * Manages icon caching, font resolution, and dark mode detection.
 * Extracted from ThemeManager for better separation of concerns.
 */
final class IconResourceManager {

    private static final Logger LOG = Logger.from(IconResourceManager.class);

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

    private static final Cache<String, Icon> ICON_CACHE = Caffeine.newBuilder()
            .maximumSize(256)
            .build();

    static void clearIconCache() {
        ICON_CACHE.invalidateAll();
    }

    static Icon getIcon(String name, int size) {
        if (name == null) {
            return null;
        }
        String cacheKey = name + "@" + size;
        return ICON_CACHE.get(cacheKey, k -> loadAndCreateIcon(name, size));
    }

    private static Icon loadAndCreateIcon(String name, int size) {
        String baseName = name.substring(0, name.lastIndexOf('.'));
        String ext = name.substring(name.lastIndexOf('.'));

        // Try pre-rendered PNG at exact size first (Inkscape-quality, no scaling)
        if (size > 0) {
            String pngPath = "github/anandb/netbeans/ui/icons/" + getThemeAwareName(baseName + "_" + size + ".png");
            Image png = ImageUtilities.loadImage(pngPath, true);
            if (png != null) {
                return ImageUtilities.image2Icon(png);
            }
        }

        String resourcePath = "github/anandb/netbeans/ui/icons/" + getThemeAwareName(name);
        Image img = ImageUtilities.loadImage(resourcePath, true);
        if (img == null) {
            URL url = IconResourceManager.class.getClassLoader().getResource(resourcePath);
            if (url != null) {
                img = new ImageIcon(url).getImage();
            }
        }
        if (img == null) {
            LOG.severe("Failed to load icon: {0}", resourcePath);
            return null;
        }
        if (size > 0 && (img.getWidth(null) != size || img.getHeight(null) != size)) {
            // Multi-pass downscale: first render at 2x, then to target size.
            // This avoids aliasing artifacts from a single large downscale.
            int intermediate = Math.min(img.getWidth(null), Math.max(size * 2, size + 16));
            if (intermediate != img.getWidth(null) && intermediate > size) {
                BufferedImage scaled = new BufferedImage(intermediate, intermediate, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gs = scaled.createGraphics();
                gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                gs.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                gs.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                gs.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                gs.drawImage(img, 0, 0, intermediate, intermediate, null);
                gs.dispose();
                img = scaled;
            }
            BufferedImage bi = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.drawImage(img, 0, 0, size, size, null);
            g2.dispose();
            img = bi;
        }
        return ImageUtilities.image2Icon(img);
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

        Font fira = new Font("Fira Code", Font.PLAIN, size);
        if (fira.getFamily().equalsIgnoreCase("Fira Code")) {
            cachedMonospaceFont = fira;
            return fira;
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
            int dot = name.lastIndexOf('.');
            String darkName = dot > 0
                ? name.substring(0, dot) + "_dark" + name.substring(dot)
                : name + "_dark";
            if (ImageUtilities.loadImage("github/anandb/netbeans/ui/icons/" + darkName, true) != null) {
                return darkName;
            }
        }
        return name;
    }
}
