package github.anandb.netbeans.ui;

import java.awt.Color;
import javax.swing.UIManager;

/**
 * Centralized color definitions for the ACP NetBeans plugin.
 */
public class ColorTheme {
    private final boolean isDark;
    
    // UI Framework
    private final Color background;
    private final Color foreground;
    private final Color selection;
    private final Color accent;
    private final Color ghostBackground;
    private final Color sunkenBackground;
    
    // Bubbles
    private final Color bubbleUser;
    private final Color bubbleAssistant;
    private final Color bubbleBorder;
    private final Color assistantForeground;
    
    // Components (Panels/Blocks)
    private final Color panelHeader;
    private final Color panelHeaderHover;
    private final Color base1;
    private final Color base2;
    private final Color base3;
    private final Color yellow;
    
    // Code Blocks
    private final Color codeBackground;
    private final Color codeForeground;
    private final Color codeSelection;
    
    // Generic
    private final Color headerForeground;
    private final Color errorBackground;

    // Permission Panel
    private final Color permissionBg;
    private final Color permissionBorder;
    private final Color permissionTitle;

    private ColorTheme(Builder builder) {
        this.isDark = builder.isDark;
        this.background = builder.background;
        this.foreground = builder.foreground;
        this.selection = builder.selection;
        this.accent = builder.accent;
        this.ghostBackground = builder.ghostBackground;
        this.sunkenBackground = builder.sunkenBackground;
        this.bubbleUser = builder.bubbleUser;
        this.bubbleAssistant = builder.bubbleAssistant;
        this.bubbleBorder = builder.bubbleBorder;
        this.assistantForeground = builder.assistantForeground;
        this.panelHeader = builder.panelHeader;
        this.panelHeaderHover = builder.panelHeaderHover;
        this.base1 = builder.base1;
        this.base2 = builder.base2;
        this.base3 = builder.base3;
        this.yellow = builder.yellow;
        this.codeBackground = builder.codeBackground;
        this.codeForeground = builder.codeForeground;
        this.codeSelection = builder.codeSelection;
        this.permissionBg = builder.permissionBg;
        this.permissionBorder = builder.permissionBorder;
        this.permissionTitle = builder.permissionTitle;
        this.headerForeground = builder.headerForeground;
        this.errorBackground = builder.errorBackground;
    }

    public boolean isDark() { return isDark; }
    public Color getBackground() { return background; }
    public Color getForeground() { return foreground; }
    public Color getSelection() { return selection; }
    public Color getAccent() { return accent; }
    public Color getGhostBackground() { return ghostBackground; }
    public Color getSunkenBackground() { return sunkenBackground; }
    public Color getBubbleUser() { return bubbleUser; }
    public Color getBubbleAssistant() { return bubbleAssistant; }
    public Color getBubbleBorder() { return bubbleBorder; }
    public Color getAssistantForeground() { return assistantForeground; }
    public Color getPanelHeader() { return panelHeader; }
    public Color getPanelHeaderHover() { return panelHeaderHover; }
    public Color getBase1() { return base1; }
    public Color getBase2() { return base2; }
    public Color getBase3() { return base3; }
    public Color getYellow() { return yellow; }
    public Color getCodeBackground() { return codeBackground; }
    public Color getCodeForeground() { return codeForeground; }
    public Color getCodeSelection() { return codeSelection; }
    public Color getPermissionBg() { return permissionBg; }
    public Color getPermissionBorder() { return permissionBorder; }
    public Color getPermissionTitle() { return permissionTitle; }
    public Color getHeaderForeground() { return headerForeground; }
    public Color getErrorBackground() { return errorBackground; }

    public static ColorTheme getNativeTheme() {
        boolean isDark = UIManager.getBoolean("nb.dark.theme");
        if (!UIManager.getDefaults().containsKey("nb.dark.theme")) {
            Color bg = UIManager.getColor("Panel.background");
            if (bg != null) {
                isDark = (bg.getRed() * 0.299 + bg.getGreen() * 0.587 + bg.getBlue() * 0.114) < 128;
            }
        }
        
        // Use UIManager properties to dynamically build the theme based on the current Look and Feel
        return new Builder(isDark)
            .background(UIManager.getColor("Panel.background"))
            .foreground(UIManager.getColor("Panel.foreground"))
            .selection(UIManager.getColor("TextArea.selectionBackground"))
            .accent(UIManager.getColor("Button.focusColor"))
            .sunkenBackground(UIManager.getColor("Panel.background"))
            .ghostBackground(UIManager.getColor("Label.background"))
            .bubbleUser(UIManager.getColor("TextField.background"))
            .assistantForeground(UIManager.getColor("TextArea.foreground"))
            .bubbleBorder(UIManager.getColor("Button.borderColor"))
            .panelHeader(UIManager.getColor("Panel.background"))
            .panelHeaderHover(UIManager.getColor("Button.background"))
            .base1(UIManager.getColor("Label.foreground"))
            .base2(UIManager.getColor("controlShadow"))
            .base3(UIManager.getColor("control"))
            .yellow(UIManager.getColor("ComboBox.selectionBackground"))
            .codeBackground(UIManager.getColor("TextArea.background"))
            .codeForeground(UIManager.getColor("TextArea.foreground"))
            .codeSelection(UIManager.getColor("TextArea.selectionBackground"))
            .permissionBg(UIManager.getColor("OptionPane.background"))
            .permissionBorder(UIManager.getColor("Button.focusColor"))
            .permissionTitle(UIManager.getColor("OptionPane.messageForeground"))
            .headerForeground(UIManager.getColor("Label.foreground"))
            .errorBackground(Color.decode(isDark ? "#401010" : "#FFEBEE"))
            .build();
    }

    private static class Builder {
        private boolean isDark;
        private Color background;
        private Color foreground;
        private Color selection;
        private Color accent;
        private Color ghostBackground;
        private Color sunkenBackground;
        private Color bubbleUser;
        private Color bubbleAssistant;
        private Color bubbleBorder;
        private Color assistantForeground;
        private Color panelHeader;
        private Color panelHeaderHover;
        private Color base1;
        private Color base2;
        private Color base3;
        private Color yellow;
        private Color codeBackground;
        private Color codeForeground;
        private Color codeSelection;
        private Color permissionBg;
        private Color permissionBorder;
        private Color permissionTitle;
        private Color headerForeground;
        private Color errorBackground;

        public Builder(boolean isDark) { this.isDark = isDark; }
        public Builder background(Color c) { this.background = c; return this; }
        public Builder foreground(Color c) { this.foreground = c; return this; }
        public Builder selection(Color c) { this.selection = c; return this; }
        public Builder accent(Color c) { this.accent = c; return this; }
        public Builder ghostBackground(Color c) { this.ghostBackground = c; return this; }
        public Builder sunkenBackground(Color c) { this.sunkenBackground = c; return this; }
        public Builder bubbleUser(Color c) { this.bubbleUser = c; return this; }        
        public Builder bubbleBorder(Color c) { this.bubbleBorder = c; return this; }
        public Builder assistantForeground(Color c) { this.assistantForeground = c; return this; }
        public Builder panelHeader(Color c) { this.panelHeader = c; return this; }
        public Builder panelHeaderHover(Color c) { this.panelHeaderHover = c; return this; }
        public Builder base1(Color c) { this.base1 = c; return this; }
        public Builder base2(Color c) { this.base2 = c; return this; }
        public Builder base3(Color c) { this.base3 = c; return this; }
        public Builder yellow(Color c) { this.yellow = c; return this; }
        public Builder codeBackground(Color c) { this.codeBackground = c; return this; }
        public Builder codeForeground(Color c) { this.codeForeground = c; return this; }
        public Builder codeSelection(Color c) { this.codeSelection = c; return this; }
        public Builder permissionBg(Color c) { this.permissionBg = c; return this; }
        public Builder permissionBorder(Color c) { this.permissionBorder = c; return this; }
        public Builder permissionTitle(Color c) { this.permissionTitle = c; return this; }
        public Builder headerForeground(Color c) { this.headerForeground = c; return this; }
        public Builder errorBackground(Color c) { this.errorBackground = c; return this; }
        
        public ColorTheme build() { return new ColorTheme(this); }
    }

    public String toCss(Color bubbleBg, boolean isAssistant) {
        String fg = toHtmlHex(isAssistant ? assistantForeground : foreground);
        String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";
        String linkColor = isDark ? "#589DF6" : "#268BD2";
        String codeBg = isDark ? "rgba(255, 255, 255, 0.1)" : "rgba(7, 54, 66, 0.1)";
        String preBg = isDark ? "#002B36" : "#EEE8D5";
        String preFg = isDark ? "#839496" : "#57685E";
        String fontStack = "Dialog, 'Noto Sans', 'Segoe UI', 'Ubuntu', 'Helvetica Neue', 'Arial', 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji', sans-serif";
        String monoStack = "'JetBrains Mono', 'Monaco', 'Fira Code', 'Monospace', Monospaced, monospace";

        return "body { font-family: " + fontStack + "; font-size: 13px; color: " + fg + "; background-color: " + bg + "; margin: 0; line-height: 1.4; }" +
               "code { background-color: " + codeBg + "; padding: 2px 4px; border-radius: 3px; font-family: " + monoStack + "; font-size: 12px; }" +
               "pre { background-color: " + preBg + "; color: " + preFg + "; padding: 10px; border-radius: 4px; font-family: " + monoStack + "; font-size: 13px; overflow-x: auto; margin: 6px 0; }" +
                 "p { margin: 2px 0; }" +
                 "ul, ol { padding-left: 20px; margin: 4px 0; }" +
                 "li { margin: 2px 0; }" +
                 "a { color: " + linkColor + "; text-decoration: none; font-weight: 500; }" +
                 "a:hover { text-decoration: underline; }";
    }

    private String toHtmlHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
