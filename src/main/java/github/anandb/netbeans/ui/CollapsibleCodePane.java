package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public class CollapsibleCodePane extends BaseCollapsiblePane {

    private String language;
    private String code;
    private final RSyntaxTextArea codeTextArea;
    private final JButton copyButton;

    public CollapsibleCodePane(String language, String code, boolean expandedByDefault) {
        super(12, (language != null && !language.isEmpty() ? language : "Code").toUpperCase(),
                ThemeManager.getIcon("file.svg"), expandedByDefault);
        this.language = language != null && !language.isEmpty() ? language : "Code";
        this.code = code;

        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(theme.getBase2());
        header.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 3));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        // Copy button
        Icon copyIcon = ThemeManager.getIcon("copy.svg", 20);
        copyButton = new JButton(copyIcon);
        copyButton.setToolTipText("Copy code");
        copyButton.setFocusPainted(false);
        copyButton.setContentAreaFilled(false);
        copyButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        copyButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        copyButton.setForeground(theme.getHeaderForeground());
        copyButton.addActionListener(e -> copyCodeToClipboard());
        header.add(copyButton, BorderLayout.EAST);

        codeTextArea = new RSyntaxTextArea();
        codeTextArea.setEditable(false);
        codeTextArea.setHighlightCurrentLine(false);
        codeTextArea.setAnimateBracketMatching(false);
        codeTextArea.setLineWrap(true);
        codeTextArea.setFont(ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN));

        applySyntaxStyle();
        applySolarizedDarkTheme();

        codeTextArea.setText(code);
        codeTextArea.setCaretPosition(0);

        JPanel codeWrapper = new JPanel(new BorderLayout());
        codeWrapper.setBackground(codeTextArea.getBackground());
        codeWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        codeWrapper.add(codeTextArea, BorderLayout.CENTER);

        contentPanel.add(codeWrapper, BorderLayout.CENTER);

        refreshTheme();
    }

    public void updateContent(String language, String code) {
        if (code == null) code = "";
        if (!code.equals(this.code)) {
            this.code = code;
            this.language = (language != null && !language.isEmpty()) ? language : "Code";
            codeTextArea.setText(code);
            codeTextArea.setCaretPosition(0);
            headerLabel.setText(this.language.toUpperCase());
            applySyntaxStyle();
            revalidate();
            repaint();
        }
    }

    private void copyCodeToClipboard() {
        StringSelection selection = new StringSelection(code);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Icon originalIcon = copyButton.getIcon();
        Icon checkIcon = ThemeManager.getIcon("check.svg", 20);
        copyButton.setIcon(checkIcon);
        
        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> {
            copyButton.setIcon(originalIcon);
        });
        timer.setRepeats(false);
        timer.start();
    }

    @Override
    public void refreshTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(theme.getBase2());
        
        Color headerFg = theme.getHeaderForeground();
        headerLabel.setForeground(headerFg);
        toggleIcon.setForeground(headerFg);
        copyButton.setForeground(headerFg);

        applySolarizedDarkTheme();
        if (codeTextArea.getParent() != null) {
            codeTextArea.getParent().setBackground(codeTextArea.getBackground());
        }
        revalidate();
        repaint();
    }

    private void applySyntaxStyle() {
        String style = SyntaxConstants.SYNTAX_STYLE_NONE;
        String lang = language.toLowerCase();
        if (lang.contains("java")) style = SyntaxConstants.SYNTAX_STYLE_JAVA;
        else if (lang.contains("python") || lang.equals("py")) style = SyntaxConstants.SYNTAX_STYLE_PYTHON;
        else if (lang.contains("javascript") || lang.equals("js")) style = SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
        else if (lang.contains("typescript") || lang.equals("ts")) style = SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
        else if (lang.contains("html")) style = SyntaxConstants.SYNTAX_STYLE_HTML;
        else if (lang.contains("xml")) style = SyntaxConstants.SYNTAX_STYLE_XML;
        else if (lang.contains("css")) style = SyntaxConstants.SYNTAX_STYLE_CSS;
        else if (lang.contains("json")) style = SyntaxConstants.SYNTAX_STYLE_JSON;
        else if (lang.contains("yaml") || lang.equals("yml")) style = SyntaxConstants.SYNTAX_STYLE_YAML;
        else if (lang.contains("sql")) style = SyntaxConstants.SYNTAX_STYLE_SQL;
        else if (lang.contains("shell") || lang.equals("sh") || lang.equals("bash")) style = SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
        else if (lang.contains("docker")) style = SyntaxConstants.SYNTAX_STYLE_DOCKERFILE;
        else if (lang.contains("markdown") || lang.equals("md")) style = SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
        else if (lang.contains("c++") || lang.equals("cpp")) style = SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
        else if (lang.contains("c#") || lang.equals("cs")) style = SyntaxConstants.SYNTAX_STYLE_CSHARP;
        else if (lang.equals("c")) style = SyntaxConstants.SYNTAX_STYLE_C;
        else if (lang.contains("go")) style = SyntaxConstants.SYNTAX_STYLE_GO;
        else if (lang.contains("rust") || lang.equals("rs")) style = SyntaxConstants.SYNTAX_STYLE_RUST;
        else if (lang.contains("php")) style = SyntaxConstants.SYNTAX_STYLE_PHP;
        else if (lang.contains("ruby") || lang.equals("rb")) style = SyntaxConstants.SYNTAX_STYLE_RUBY;

        codeTextArea.setSyntaxEditingStyle(style);
    }

    private void applySolarizedDarkTheme() {
        try {
            java.io.InputStream in = getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml");
            if (in == null) {
                manualSolarizedDark();
                return;
            }
            org.fife.ui.rsyntaxtextarea.Theme rTheme = org.fife.ui.rsyntaxtextarea.Theme.load(in);
            ColorTheme theme = ThemeManager.getCurrentTheme();
            rTheme.apply(codeTextArea);
            codeTextArea.setBackground(theme.getCodeBackground());
            codeTextArea.setSelectionColor(theme.getCodeSelection());
        } catch (Exception ioe) {
            manualSolarizedDark();
        }
    }

    private void manualSolarizedDark() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        codeTextArea.setBackground(theme.getCodeBackground());
        codeTextArea.setForeground(theme.getCodeForeground());
        codeTextArea.setSelectionColor(theme.getCodeSelection());
    }
}
