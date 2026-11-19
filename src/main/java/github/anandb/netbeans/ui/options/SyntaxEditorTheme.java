package github.anandb.netbeans.ui.options;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;

import javax.swing.UIManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

import github.anandb.netbeans.ui.rendering.ThemeManager;

/**
 * Applies the NetBeans LAF surface to an {@link RSyntaxTextArea}. The light/dark token
 * palette is chosen from the actual {@code TextArea.background}, because the plugin's chat
 * code palette stays dark on light themes by design.
 */
final class SyntaxEditorTheme {

    private SyntaxEditorTheme() {
    }

    static void apply(RSyntaxTextArea area, RTextScrollPane scroll, Font font) {
        Color background = uiColor("TextArea.background", ThemeManager.getCurrentTheme().background());
        Color foreground = uiColor("TextArea.foreground", ThemeManager.getCurrentTheme().foreground());
        Color selection = uiColor("TextArea.selectionBackground",
                ThemeManager.getCurrentTheme().selection());
        Color gutterBackground = background;
        Color gutterBorder = uiColor("Separator.foreground",
                ThemeManager.getCurrentTheme().bubbleBorder());
        Color lineNumberColor = ThemeManager.getCurrentTheme().mutedForeground();
        Color currentLineNumberColor = foreground;

        String resource = isDark(background)
                ? "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
        try (InputStream in = SyntaxEditorTheme.class.getResourceAsStream(resource)) {
            if (in != null) {
                Theme areaTheme = Theme.load(in);
                areaTheme.apply(area);
                if (areaTheme.gutterBackgroundColor != null) {
                    gutterBackground = areaTheme.gutterBackgroundColor;
                }
                if (areaTheme.gutterBorderColor != null) {
                    gutterBorder = areaTheme.gutterBorderColor;
                }
                if (areaTheme.lineNumberColor != null) {
                    lineNumberColor = areaTheme.lineNumberColor;
                }
                if (areaTheme.currentLineNumberColor != null) {
                    currentLineNumberColor = areaTheme.currentLineNumberColor;
                }
            }
        } catch (Exception e) {
            // Default RSyntax scheme: still readable on the LAF surface.
        }

        area.setBackground(background);
        area.setForeground(foreground);
        area.setSelectionColor(selection);
        if (font != null) {
            area.setFont(font);
        }
        if (scroll != null) {
            Gutter gutter = scroll.getGutter();
            gutter.setBackground(gutterBackground);
            gutter.setBorderColor(gutterBorder);
            gutter.setLineNumberColor(lineNumberColor);
            gutter.setCurrentLineNumberColor(currentLineNumberColor);
            gutter.setActiveLineRangeColor(gutterBackground);
            gutter.setLineNumberFont(area.getFont());
        }
    }

    static boolean isDarkSurface() {
        return isDark(uiColor("TextArea.background", ThemeManager.getCurrentTheme().background()));
    }

    static Color uiColor(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value != null ? value : fallback;
    }

    private static boolean isDark(Color color) {
        return color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114 < 128;
    }
}
