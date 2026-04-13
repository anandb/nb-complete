package github.anandb.netbeans.ui;

import java.awt.Color;

public class ThemeManager {

    public static class Theme {
        private boolean isDark;
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
            String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";
            String linkColor = isDark ? "#589DF6" : "#268BD2";
            String codeBg = isDark ? "rgba(255, 255, 255, 0.1)" : "rgba(7, 54, 66, 0.1)";

            return "body { font-family: 'Segoe UI', 'Ubuntu', sans-serif; font-size: 13px; color: " + fg + "; background-color: " + bg + "; margin: 0 0 8px 0; line-height: 1.4; }" +
                   "code { background-color: " + codeBg + "; padding: 2px 4px; border-radius: 3px; font-family: 'JetBrains Mono', 'Input Mono', monospace; font-size: 12px; }" +
                   "pre { background-color: #002B36; color: #839496; padding: 10px; border-radius: 4px; font-family: 'JetBrains Mono', 'Input Mono', monospace; font-size: 12px; overflow-x: auto; margin: 10px 0; }" +
                   "p { margin: 8px 0; }" +
                   "ul, ol { padding-left: 20px; margin: 8px 0; }" +
                   "li { margin: 4px 0; }" +
                   "a { color: " + linkColor + "; text-decoration: none; font-weight: 500; }" +
                   "a:hover { text-decoration: underline; }";
        }

        private String toHtmlHex(Color color) {
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }

        public boolean isDark() { return isDark; }
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
        boolean isDark = org.openide.util.NbPreferences.forModule(ThemeManager.class).getBoolean("isDarkMode", false);
        return isDark ? getDarkTheme() : getLightTheme();
    }

    public static void setDarkMode(boolean isDark) {
        org.openide.util.NbPreferences.forModule(ThemeManager.class).putBoolean("isDarkMode", isDark);
    }

    private static Theme getLightTheme() {
        Theme theme = new Theme();
        theme.isDark = false;

        Color base3 = Color.decode("#D9D0B4"); // Background
        Color base2 = Color.decode("#EEE8D5"); // Bubbles
        Color base1 = Color.decode("#93A1A1"); // Metadata
        Color base02 = Color.decode("#073642"); // Dark Text
        Color yellow = Color.decode("#B58900");
        Color blue = Color.decode("#268BD2");

        theme.base3 = base3;
        theme.base2 = base2;
        theme.base1 = base1;
        theme.yellow = yellow;
        theme.background = base3;
        theme.foreground = base02;
        theme.assistantForeground = base02;
        theme.selection = blue;
        theme.accent = blue;

        theme.bubbleUser = base2;
        theme.bubbleAssistant = null;
        theme.bubbleBorder = Color.decode("#DCD6C1");
        theme.ghostBackground = new Color(base02.getRed(), base02.getGreen(), base02.getBlue(), 15);

        return theme;
    }

    private static Theme getDarkTheme() {
        Theme theme = new Theme();
        theme.isDark = true;

        Color bg = Color.decode("#2B2B2B");
        Color fg = Color.decode("#A9B7C6");
        Color selection = Color.decode("#214283");
        Color bubbleUser = Color.decode("#3E434C");

        theme.background = bg;
        theme.foreground = fg;
        theme.assistantForeground = fg;
        theme.bubbleUser = bubbleUser;
        theme.bubbleAssistant = null;
        theme.bubbleBorder = Color.decode("#555555");
        theme.selection = selection;
        theme.accent = Color.decode("#589DF6");
        theme.yellow = Color.decode("#BBB529");
        theme.base1 = Color.decode("#909090");
        theme.base2 = Color.decode("#3C3F41");
        theme.base3 = bg;
        theme.ghostBackground = new Color(255, 255, 255, 15);

        return theme;
    }
    
}
