package github.anandb.netbeans.ui;

import java.awt.Color;
import javax.swing.UIManager;

/**
 * Centralized color definitions for the ACP NetBeans plugin.
 */
public record ColorTheme(
    boolean isDark,
    Color background, Color foreground, Color selection, Color accent,
    Color ghostBackground, Color sunkenBackground, Color bubbleUser,
    Color bubbleAssistant, Color bubbleBorder, Color assistantForeground,
    Color panelHeader, Color panelHeaderHover, Color base1, Color base2, Color base3,
    Color yellow, Color codeBackground, Color codeForeground, Color codeSelection,
    Color headerForeground, Color errorBackground, Color codeHeaderBackground,
    Color codeHeaderForeground, Color codeHeaderBorder, Color thinkingHeaderBackground, Color thinkingHeaderForeground,
    Color toolForeground, Color permissionBg, Color permissionBorder, Color permissionTitle,
    Color tableBackground, Color tableHeaderBackground, Color tableBorder
) {
    public static ColorTheme getNativeTheme(boolean darkMode) {
        Color bubbleUser;
        if (darkMode) {
            bubbleUser = UIManager.getColor("Search.background");
            if (bubbleUser == null) {
                bubbleUser = UIManager.getColor("Editor.searchBackground");
            }
            if (bubbleUser == null) {
                bubbleUser = UIManager.getColor("EditorPane.background");
            }
        } else {
            bubbleUser = Color.decode("#cfecf7");
        }

        return new ColorTheme(
                darkMode, UIManager.getColor("Panel.background"),
                UIManager.getColor("Panel.foreground"), UIManager.getColor("TextArea.selectionBackground"),
                UIManager.getColor("Button.focusColor"), UIManager.getColor("Label.background"),
                UIManager.getColor("Editor.background"), bubbleUser,
                null, UIManager.getColor("Button.borderColor"),
                UIManager.getColor("TextArea.foreground"), UIManager.getColor("Panel.background"),
                UIManager.getColor("Button.background"), UIManager.getColor("Label.foreground"),
                UIManager.getColor("controlShadow"), UIManager.getColor("control"),
                UIManager.getColor("ComboBox.selectionBackground"),
                UIManager.getColor("Terminal.background") != null ? UIManager.getColor("Terminal.background") : Color.decode("#1e1f22"),
                UIManager.getColor("Terminal.foreground") != null ? UIManager.getColor("Terminal.foreground") : Color.decode("#bcbec4"),
                Color.decode("#353739"),
                UIManager.getColor("Label.foreground"),
                darkMode ? Color.decode("#401010") : Color.decode("#FFEBEE"),
                UIManager.getColor("TableHeader.background"), UIManager.getColor("TableHeader.foreground"),
                darkMode ? Color.decode("#AAAAAA") : Color.decode("#333333"),
                darkMode ? UIManager.getColor("Panel.background") : Color.decode("#fdf6e3"),
                UIManager.getColor("Label.foreground"),
                darkMode ? Color.decode("#9CA3AF") : Color.decode("#777777"),
                UIManager.getColor("OptionPane.background"),
                UIManager.getColor("Button.focusColor"),
                UIManager.getColor("OptionPane.messageForeground"),
                darkMode ? Color.decode("#2d2d2d") : Color.decode("#fdf6e3"),
                darkMode ? Color.decode("#383838") : Color.decode("#eee8d5"),
                darkMode ? Color.decode("#454545") : Color.decode("#e8e0c8")
        );
    }

    public String getMonoStack() {
        return "'MesloLGS NF', 'Source Code Pro', 'JetBrains Mono', Monaco, 'Fira Code', monospace";
    }

    public String toCss(Color bubbleBg, boolean isAssistant) {
        String fg = toHtmlHex(isAssistant ? assistantForeground() : foreground());
        String bg = bubbleBg != null ? toHtmlHex(bubbleBg) : "transparent";
        String linkColor = isDark() ? "#589DF6" : "#268BD2";

        // Markdown inline code and blocks are now forced to Dark mode as well
        String codeBg = "rgba(255, 255, 255, 0.1)";
        String preBg = codeBackground() != null ? toHtmlHex(codeBackground()) : "#1e1f22";
        String preFg = codeForeground() != null ? toHtmlHex(codeForeground()) : "#bcbec4";
        String fontStack = "Dialog, 'Noto Sans', 'Segoe UI', 'Ubuntu', 'Helvetica Neue', Arial, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji', sans-serif";
        String monoStack = getMonoStack();

        return "html, body { font-family: " + fontStack + "; font-size: 13px; color: " + fg + "; background-color: " + bg + "; margin: 0; padding: 0; line-height: 1.4; text-align: left !important; width: 100% !important; }" +
               "code { background-color: " + codeBg + "; padding: 2px 4px; border-radius: 3px; font-family: " + monoStack + "; font-size: 12px; }" +
               "pre { background-color: " + preBg + "; color: " + preFg + "; padding: 10px; border-radius: 4px; font-family: " + monoStack + "; font-size: 13px; overflow-x: auto; margin: 6px 0; text-align: left !important; width: 100% !important; }" +
                 "p, div, h1, h2, h3, h4, h5, h6 { margin: 2px 0; text-align: left !important; width: 100% !important; }" +
                 "ul, ol { padding-left: 20px; margin: 4px 0; text-align: left !important; }" +
                 "li { margin: 2px 0; text-align: left !important; }" +
                 "table { width: 100% !important; border-collapse: collapse; text-align: left !important; }" +
                 "a { color: " + linkColor + "; text-decoration: none; font-weight: 500; } " +
                 "a:hover { text-decoration: underline; }";
    }

    public String toHtmlHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}