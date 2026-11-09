package github.anandb.netbeans.ui.rendering;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.logging.Level;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.editor.Coloring;
import org.openide.util.ImageUtilities;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.ui.components.UIUtils;

/**
 * Manages icon caching, font resolution, and dark mode detection.
 * Extracted from ThemeManager for better separation of concerns.
 */
public final class IconResourceManager {

    private static final Logger LOG = Logger.from(IconResourceManager.class);

    private IconResourceManager() {}

    private static volatile Font cachedFont;
    private static volatile Font cachedMonospaceFont;
    private static volatile Boolean cachedIsDark;

    static {
        UIManager.addPropertyChangeListener(e -> {
            clearFontCache();
            cachedIsDark = null;
        });
    }

    /** Drops the resolved fonts so the next read picks up a new LAF or system property. */
    static void clearFontCache() {
        cachedFont = null;
        cachedMonospaceFont = null;
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
        f = withFamilyOverride(f);
        cachedFont = f;
        return f;
    }

    /**
     * Applies the Options font-family setting (or {@code -J-Dbeanbot.font.family}) over the LAF
     * family, keeping its style and size.
     */
    private static Font withFamilyOverride(Font base) {
        String family = UIUtils.chatFontFamilyOverride();
        if (family == null || family.equals(base.getFamily())) {
            return base;
        }
        return new Font(family, base.getStyle(), base.getSize());
    }

    /**
     * The font for code: code blocks, tool output, the permission context line and the diff panes.
     *
     * <p>Order: the {@code beanbot.mono.font.family} override, then the IDE editor font from
     * <em>Options &gt; Fonts &amp; Colors</em> (the user's own monospace choice, read from the
     * editor's default colouring), then the JDK's logical {@code Monospaced} family. Whatever
     * comes out is checked for real monospacing — a proportional family here would silently turn
     * every diff into a sans-serif pane.</p>
     */
    public static Font getMonospaceFont() {
        Font f = cachedMonospaceFont;
        if (f != null) return f;

        Font editorFont = editorCodeFont();
        int size = (editorFont != null) ? editorFont.getSize() : 13;

        String monoFamily = System.getProperty(UIUtils.MONO_FONT_FAMILY_PROPERTY);
        if (monoFamily != null && !monoFamily.isBlank()) {
            cachedMonospaceFont = monospacedOrFallback(new Font(monoFamily, Font.PLAIN, size), size);
            return cachedMonospaceFont;
        }
        if (editorFont != null) {
            cachedMonospaceFont = monospacedOrFallback(editorFont.deriveFont(Font.PLAIN), size);
            return cachedMonospaceFont;
        }
        cachedMonospaceFont = new Font(Font.MONOSPACED, Font.PLAIN, size);
        return cachedMonospaceFont;
    }

    /** The editor's default colouring font, or null when the editor settings are not available. */
    private static Font editorCodeFont() {
        try {
            FontColorSettings settings = MimeLookup.getLookup(MimePath.get("text/x-java"))
                    .lookup(FontColorSettings.class);
            if (settings == null) {
                settings = MimeLookup.getLookup(MimePath.EMPTY).lookup(FontColorSettings.class);
            }
            if (settings == null) {
                return null;
            }
            AttributeSet colors = settings.getFontColors(FontColorNames.DEFAULT_COLORING);
            if (colors == null) {
                return null;
            }
            Font font = Coloring.fromAttributeSet(colors).getFont();
            return font != null && font.getFamily() != null && !font.getFamily().isBlank() ? font : null;
        } catch (RuntimeException | LinkageError ex) {
            // Unit tests, or a runtime without the editor modules: the logical family covers it.
            LOG.log(Level.FINE, "Editor font unavailable, using the logical monospace family", ex);
            return null;
        }
    }

    /** Guards against a proportional family (e.g. a user's proportional editor font). */
    private static Font monospacedOrFallback(Font candidate, int size) {
        return isMonospaced(candidate)
                ? candidate
                : new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    private static boolean isMonospaced(Font font) {
        FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                .createGraphics().getFontMetrics(font);
        int width = fm.charWidth('i');
        return width > 0
                && width == fm.charWidth('W')
                && width == fm.charWidth('0')
                && width == fm.charWidth(' ');
    }

    /**
     * Font for diff panes: one point below the editor font, clamped at 9 so a small editor font
     * cannot produce an unreadable diff. It is applied to a diff window's own component tree, so
     * the editor font the user reads and edits is never changed.
     */
    public static Font getDiffMonospaceFont() {
        Font base = getMonospaceFont();
        return base.deriveFont(Math.max(9f, base.getSize() - 1f));
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
