package ai.opencode.netbeans.ui;

import java.awt.Color;
import javax.swing.UIManager;

public class ThemeManager {
    
    public static class Theme {
        public Color background;
        public Color foreground;
        public Color bubbleUser;
        public Color bubbleAssistant;
        public Color bubbleBorder;
        public Color selection;
        
        public String toCss() {
            String fg = toHtmlHex(foreground);
            String sel = toHtmlHex(selection);
            
            return "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size: 13px; color: " + fg + "; background-color: transparent; margin: 0; line-height: 1.4; }" +
                   "code { background-color: rgba(128,128,128,0.15); padding: 2px 4px; border-radius: 3px; font-family: 'JetBrains Mono', 'Cascadia Code', monospace; }" +
                   "pre { background-color: rgba(0,0,0,0.05); padding: 10px; border-radius: 6px; border: 1px solid rgba(128,128,128,0.2); overflow-x: auto; }" +
                   "p { margin: 2px 0; }" +
                   "a { color: " + sel + "; text-decoration: none; }" +
                   "a:hover { text-decoration: underline; }";
        }
        
        private String toHtmlHex(Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }

    public static Theme getCurrentTheme() {
        Theme theme = new Theme();
        
        Color bg = UIManager.getColor("Editor.background");
        if (bg == null) bg = UIManager.getColor("Panel.background");
        theme.background = bg;
        
        Color fg = UIManager.getColor("Editor.foreground");
        if (fg == null) fg = UIManager.getColor("Label.foreground");
        theme.foreground = fg;
        
        Color sel = UIManager.getColor("TextArea.selectionBackground");
        if (sel == null) sel = UIManager.getColor("textHighlight");
        if (sel == null) sel = new Color(0, 120, 215);
        theme.selection = sel;

        // User bubble: Fixed hex color as requested
        theme.bubbleUser = new Color(0xCEC7AF); 
        // Assistant bubble: No background (transparent)
        theme.bubbleAssistant = null;
        // Border: No border for assistant, but keep a subtle one for user if desired?
        // Let's set it to null for assistant logic.
        theme.bubbleBorder = new Color(0, 0, 0, 0); // Transparent
        
        return theme;
    }
    
    private static Color getContrastColor(Color c, float factor) {
        if (c == null) return Color.GRAY;
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();
        
        boolean isDark = (r * 0.299 + g * 0.587 + b * 0.114) < 128; // Standard luminance
        if (isDark) {
            return new Color(
                clamp((int)(r + 255 * factor)),
                clamp((int)(g + 255 * factor)),
                clamp((int)(b + 255 * factor))
            );
        } else {
            return new Color(
                clamp((int)(r - 255 * factor)),
                clamp((int)(g - 255 * factor)),
                clamp((int)(b - 255 * factor))
            );
        }
    }
    
    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}
