package ai.opencode.netbeans.ui;

import java.awt.Color;
import javax.swing.UIManager;

public class ThemeManager {
    
    public static class Theme {
        private Color background;
        private Color foreground;
        private Color assistantForeground;
        private Color bubbleUser;
        private Color bubbleAssistant;
        private Color bubbleBorder;
        private Color selection;
        private Color accent;
        private Color ghostBackground;
        
        public String toCss(Color bubbleBg, boolean isAssistant) {
            String fg = toHtmlHex(isAssistant ? getAssistantForeground() : getForeground());
            String sel = toHtmlHex(getSelection());
            String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";
            
            return "body { font-family: 'Outfit', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; font-size: 14px; color: " + fg + "; background-color: " + bg + "; margin: 0; line-height: 1.6; }" +
                   "code { background-color: rgba(128,128,128,0.12); padding: 2px 5px; border-radius: 4px; font-family: 'JetBrains Mono', 'Cascadia Code', monospace; font-size: 13px; }" +
                   "pre { background-color: #e9e9d0; padding: 12px; border-radius: 8px; border: 1px solid rgba(128,128,128,0.15); overflow-x: auto; margin: 12px 0; }" +
                   "p { margin: 8px 0; }" +
                   "ul, ol { padding-left: 20px; margin: 8px 0; }" +
                   "li { margin: 4px 0; }" +
                   "a { color: " + sel + "; text-decoration: none; font-weight: 500; }" +
                   "a:hover { text-decoration: underline; }";
        }
        
        private String toHtmlHex(Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }

        /**
         * @return the background
         */
        public Color getBackground() {
            return background;
        }

        /**
         * @return the foreground
         */
        public Color getForeground() {
            return foreground;
        }

        /**
         * @return the assistantForeground
         */
        public Color getAssistantForeground() {
            return assistantForeground;
        }

        /**
         * @return the bubbleUser
         */
        public Color getBubbleUser() {
            return bubbleUser;
        }

        /**
         * @return the bubbleAssistant
         */
        public Color getBubbleAssistant() {
            return bubbleAssistant;
        }

        /**
         * @return the bubbleBorder
         */
        public Color getBubbleBorder() {
            return bubbleBorder;
        }

        /**
         * @return the selection
         */
        public Color getSelection() {
            return selection;
        }

        /**
         * @return the accent
         */
        public Color getAccent() {
            return accent;
        }

        /**
         * @return the ghostBackground
         */
        public Color getGhostBackground() {
            return ghostBackground;
        }
    }

    public static Theme getCurrentTheme() {
        Theme theme = new Theme();
        
        Color bg = UIManager.getColor("Editor.background");
        if (bg == null) {
            bg = UIManager.getColor("Panel.background");
        }
        theme.background = bg;
        
        Color fg = UIManager.getColor("Editor.foreground");
        if (fg == null) {
            fg = UIManager.getColor("Label.foreground");
        }
        theme.foreground = fg;
        
        // Assistant text: Dark Brown (#3C2A21 or similar premium dark brown)
        theme.assistantForeground = new Color(0x3C2A21);
        
        Color sel = UIManager.getColor("TextArea.selectionBackground");
        if (sel == null) {
            sel = UIManager.getColor("textHighlight");
        }
        if (sel == null) {
            sel = new Color(0, 120, 215);
        }
        theme.selection = sel;

        // User bubble: Fixed hex color as requested
        theme.bubbleUser = new Color(0xCEC7AF); 
        // Assistant bubble: No background (transparent)
        theme.bubbleAssistant = null;
        // Border: No border for assistant, but keep a subtle one for user if desired?
        theme.bubbleBorder = new Color(0, 0, 0, 0); // Transparent
        
        // Custom UI accents
        theme.accent = theme.getSelection();
        theme.ghostBackground = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 15);
        
        return theme;
    }
    
    private static Color getContrastColor(Color c, float factor) {
        if (c == null) {
            return Color.GRAY;
        }
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
