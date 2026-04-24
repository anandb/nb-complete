package github.anandb.netbeans.ui;

import java.awt.Font;
import javax.swing.Icon;
import javax.swing.UIManager;
import org.openide.util.ImageUtilities;

/**
 * Handles non-color theme resources (Icons, Fonts) and coordinates with ColorTheme.
 */
public class ThemeManager {
    public static Icon getIcon(String name) {
        if (name == null) return null;
        String resourcePath = "github/anandb/netbeans/ui/icons/" + getThemeAwareName(name);
        java.awt.Image img = ImageUtilities.loadImage(resourcePath, true);
        if (img == null) {
            java.net.URL url = ThemeManager.class.getClassLoader().getResource(resourcePath);
            if (url != null) {
                img = new javax.swing.ImageIcon(url).getImage();
            }
        }
        return img != null ? ImageUtilities.image2Icon(img) : null;
    }

    private static String getThemeAwareName(String name) {
        if (ColorTheme.getNativeTheme().isDark()) {
            String darkName = name.replace(".svg", "_dark.svg");
            // Check if the dark version exists
            if (ImageUtilities.loadImage("github/anandb/netbeans/ui/icons/" + darkName, true) != null) {
                return darkName;
            }
        }
        return name;
    }

    public static Icon getIcon(String name, int size) {
        if (name == null) return null;
        String resourcePath = "github/anandb/netbeans/ui/icons/" + getThemeAwareName(name);
        java.awt.Image img = ImageUtilities.loadImage(resourcePath, true);
        if (img == null) {
            java.net.URL url = ThemeManager.class.getClassLoader().getResource(resourcePath);
            if (url != null) {
                img = new javax.swing.ImageIcon(url).getImage();
            }
        }
        if (img == null) return null;
        if (size > 0 && (img.getWidth(null) != size || img.getHeight(null) != size)) {
            java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(img, 0, 0, size, size, null);
            g2.dispose();
            img = bi;
        }
        return ImageUtilities.image2Icon(img);
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
        return ColorTheme.getNativeTheme();
    }
}
