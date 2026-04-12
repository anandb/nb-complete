package ai.opencode.netbeans.ui;

import java.awt.Color;

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
        private Color yellow;
        private Color base1;
        private Color base2;
        private Color base3;
        private Color ghostBackground;

        public String toCss(Color bubbleBg, boolean isAssistant) {
            String fg = toHtmlHex(isAssistant ? getAssistantForeground() : getForeground());
            String sel = "#268BD2"; // Solarized Blue
            String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";

            return "body { font-family: 'Segoe UI', 'Ubuntu', sans-serif; font-size: 13px; color: " + fg + "; background-color: " + bg + "; margin: 0 0 8px 0; line-height: 1.4; }" +
                   "code { background-color: rgba(7, 54, 66, 0.1); padding: 2px 4px; border-radius: 3px; font-family: 'JetBrains Mono', 'Input Mono', monospace; font-size: 12px; }" +
                   "pre { background-color: #002B36; color: #839496; padding: 10px; border-radius: 4px; font-family: 'JetBrains Mono', 'Input Mono', monospace; font-size: 12px; overflow-x: auto; margin: 10px 0; }" +
                   "p { margin: 8px 0; }" +
                   "ul, ol { padding-left: 20px; margin: 8px 0; }" +
                   "li { margin: 4px 0; }" +
                   "a { color: " + sel + "; text-decoration: none; font-weight: 500; }" +
                   "a:hover { text-decoration: underline; }";
        }

        private String toHtmlHex(Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }

        public Color getBackground() { return background; }
        public Color getForeground() { return foreground; }
        public Color getAssistantForeground() { return assistantForeground; }
        public Color getBubbleUser() { return bubbleUser; }
        public Color getBubbleAssistant() { return bubbleAssistant; }
        public Color getBubbleBorder() { return bubbleBorder; }
        public Color getSelection() { return selection; }
        public Color getAccent() { return accent; }
        public Color getYellow() { return yellow; }
        public Color getBase1() { return base1; }
        public Color getBase2() { return base2; }
        public Color getBase3() { return base3; }
        public Color getGhostBackground() { return ghostBackground; }
    }

    public static Theme getCurrentTheme() {
        Theme theme = new Theme();

        // Solarized Light (Base 3)
        Color base3 = Color.decode("#FDF6E3");
        Color base2 = Color.decode("#EEE8D5");
        Color base1 = Color.decode("#93A1A1");
        Color base00 = Color.decode("#657B83");
        Color yellow = Color.decode("#B58900");
        Color blue = Color.decode("#268BD2");

        theme.base3 = base3;
        theme.base2 = base2;
        theme.base1 = base1;
        theme.yellow = yellow;

        theme.background = base3;
        theme.foreground = base00;
        theme.assistantForeground = base00;

        theme.selection = blue;
        theme.accent = blue;

        // User bubble: slightly darker beige
        theme.bubbleUser = base2;
        theme.bubbleAssistant = null;
        theme.bubbleBorder = Color.decode("#DCD6C1");

        theme.ghostBackground = new Color(base00.getRed(), base00.getGreen(), base00.getBlue(), 15);

        return theme;
    }
    
}
