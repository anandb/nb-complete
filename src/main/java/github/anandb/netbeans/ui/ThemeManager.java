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
        java.awt.Image img = ImageUtilities.loadImage("github/anandb/netbeans/ui/icons/" + name, true);
        return img != null ? ImageUtilities.image2Icon(img) : null;
    }

    public static Icon getIcon(String name, int size) {
        if (name == null) return null;
        java.awt.Image img = ImageUtilities.loadImage("github/anandb/netbeans/ui/icons/" + name, true);
        if (img == null) return null;
        if (size > 0 && (img.getWidth(null) != size || img.getHeight(null) != size)) {
            img = img.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH);
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
