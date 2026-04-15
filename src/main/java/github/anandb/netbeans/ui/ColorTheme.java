package github.anandb.netbeans.ui;

import java.awt.Color;

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

    public static final ColorTheme LIGHT = new Builder(false)
            .background(Color.decode("#D9D0B4"))       // secondary3: main panel bg
            .foreground(Color.decode("#321A01"))      // primary1: dark brown text
            .selection(Color.decode("#CC9966"))        // primary3: selection highlight
            .accent(Color.decode("#996633"))           // primary2: focused accent
            .sunkenBackground(Color.decode("#F9F4E0")) // slightly darker than bg for sunken feel
            .ghostBackground(new Color(102, 51, 0, 15)) // primary1 at low alpha
            .bubbleUser(Color.decode("#FEFEFE"))       // mid-tone warm brown for user bubbles
            .assistantForeground(Color.decode("#4D2600")) // secondary1: darkest for readability
            .bubbleBorder(Color.decode("#804D1A"))     // secondary2: border brown
            .panelHeader(Color.decode("#EEE8D5"))      // slightly recessed header
            .panelHeaderHover(Color.decode("#D4CCB5")) // secondary3 as hover (brighter)
            .base1(Color.decode("#804D1A"))            // secondary2: metadata/subtle text
            .base2(Color.decode("#EEE8D5"))            // sunken areas
            .base3(Color.decode("#D9D0B4"))            // secondary3: lightest bg
            .yellow(Color.decode("#996633"))           // primary2 as warm accent
            .codeBackground(Color.decode("#002B36"))
            .codeForeground(Color.decode("#839496"))
            .codeSelection(Color.decode("#073642"))
            .permissionBg(Color.decode("#FFF3E0"))
            .permissionBorder(Color.decode("#FFA000"))
            .permissionTitle(Color.decode("#E65100"))
            .build();

    public static final ColorTheme DARK = new Builder(true)
            .background(Color.decode("#2B2B2B"))
            .foreground(Color.decode("#A9B7C6"))
            .selection(Color.decode("#214283"))
            .accent(Color.decode("#589DF6"))
            .sunkenBackground(Color.decode("#1A1C1E"))
            .ghostBackground(new Color(255, 255, 255, 15))
            .bubbleUser(Color.decode("#3E362E"))
            .assistantForeground(Color.decode("#A9B7C6"))
            .bubbleBorder(Color.decode("#555555"))
            .panelHeader(Color.decode("#151515"))
            .panelHeaderHover(Color.decode("#252525"))
            .base1(Color.decode("#909090"))
            .base2(Color.decode("#3C3F41"))
            .base3(Color.decode("#2B2B2B"))
            .yellow(Color.decode("#BBB529"))
            .codeBackground(Color.decode("#002B36"))
            .codeForeground(Color.decode("#839496"))
            .codeSelection(Color.decode("#073642"))
            .permissionBg(Color.decode("#32230A"))
            .permissionBorder(Color.decode("#B46E00"))
            .permissionTitle(Color.decode("#FFA726"))
            .build();

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
        
        public ColorTheme build() { return new ColorTheme(this); }
    }

    public String toCss(Color bubbleBg, boolean isAssistant) {
        String fg = toHtmlHex(isAssistant ? assistantForeground : foreground);
        String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";
        String linkColor = toHtmlHex(accent);
        String codeBg = String.format("rgba(%d, %d, %d, %.2f)", 
                ghostBackground.getRed(), ghostBackground.getGreen(), ghostBackground.getBlue(), 
                ghostBackground.getAlpha() / 255.0);

        String preBg = toHtmlHex(codeBackground);
        String preFg = toHtmlHex(codeForeground);

        java.awt.Font labelFont = ThemeManager.getFont();
        String fontFamily = "'" + labelFont.getFamily() + "'";
        String fontSize = labelFont.getSize() + "pt";
        String monoStack = "'JetBrains Mono This', 'JetBrains Mono', 'Monaco', 'Fira Code', 'Monospace', Monospaced, monospace";

        return "body { font-family: " + fontFamily + "; font-size: " + fontSize + "; color: " + fg + "; background-color: " + bg + "; margin: 0 0 8px 0; line-height: 1.4; }" +
               "code { background-color: " + codeBg + "; padding: 2px 4px; border-radius: 3px; font-family: " + monoStack + "; font-size: 12px; }" +
               "pre { background-color: " + preBg + "; color: " + preFg + "; padding: 10px; border-radius: 4px; font-family: " + monoStack + "; font-size: 13px; overflow-x: auto; margin: 10px 0; }" +
                "p { margin: 8px 0; }" +
                "ul, ol { padding-left: 20px; margin: 8px 0; }" +
                "li { margin: 4px 0; }" +
                "a { color: " + linkColor + "; text-decoration: none; font-weight: 500; }" +
                "a:hover { text-decoration: underline; }";
    }

    private String toHtmlHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
