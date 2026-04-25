package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

public class CollapsibleCodePane extends BaseCollapsiblePane {

    private static final long serialVersionUID = 1L;

    private String language;
    private String code;
    private final RSyntaxTextArea codeTextArea;
    private final JButton copyButton;

    private static final java.util.Map<String, String> LANGUAGE_MAP = new java.util.HashMap<>();

    static {
        // C/C++ / C#
        LANGUAGE_MAP.put("c", SyntaxConstants.SYNTAX_STYLE_C);
        LANGUAGE_MAP.put("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        LANGUAGE_MAP.put("c++", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        LANGUAGE_MAP.put("cs", SyntaxConstants.SYNTAX_STYLE_CSHARP);
        LANGUAGE_MAP.put("c#", SyntaxConstants.SYNTAX_STYLE_CSHARP);

        // Java & JVM
        LANGUAGE_MAP.put("java", SyntaxConstants.SYNTAX_STYLE_JAVA);
        LANGUAGE_MAP.put("kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        LANGUAGE_MAP.put("kt", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        LANGUAGE_MAP.put("groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY);
        LANGUAGE_MAP.put("scala", SyntaxConstants.SYNTAX_STYLE_SCALA);
        LANGUAGE_MAP.put("clojure", SyntaxConstants.SYNTAX_STYLE_CLOJURE);

        // Web
        LANGUAGE_MAP.put("html", SyntaxConstants.SYNTAX_STYLE_HTML);
        LANGUAGE_MAP.put("htm", SyntaxConstants.SYNTAX_STYLE_HTML);
        LANGUAGE_MAP.put("css", SyntaxConstants.SYNTAX_STYLE_CSS);
        LANGUAGE_MAP.put("javascript", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        LANGUAGE_MAP.put("js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        LANGUAGE_MAP.put("typescript", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
        LANGUAGE_MAP.put("ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
        LANGUAGE_MAP.put("json", SyntaxConstants.SYNTAX_STYLE_JSON);
        LANGUAGE_MAP.put("php", SyntaxConstants.SYNTAX_STYLE_PHP);

        // Scripting
        LANGUAGE_MAP.put("python", SyntaxConstants.SYNTAX_STYLE_PYTHON);
        LANGUAGE_MAP.put("py", SyntaxConstants.SYNTAX_STYLE_PYTHON);
        LANGUAGE_MAP.put("ruby", SyntaxConstants.SYNTAX_STYLE_RUBY);
        LANGUAGE_MAP.put("rb", SyntaxConstants.SYNTAX_STYLE_RUBY);
        LANGUAGE_MAP.put("lua", SyntaxConstants.SYNTAX_STYLE_LUA);
        LANGUAGE_MAP.put("perl", SyntaxConstants.SYNTAX_STYLE_PERL);
        LANGUAGE_MAP.put("pl", SyntaxConstants.SYNTAX_STYLE_PERL);
        LANGUAGE_MAP.put("tcl", SyntaxConstants.SYNTAX_STYLE_TCL);

        // Shell & Scripts
        LANGUAGE_MAP.put("sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        LANGUAGE_MAP.put("bash", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        LANGUAGE_MAP.put("zsh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        LANGUAGE_MAP.put("ksh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        LANGUAGE_MAP.put("shell", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        LANGUAGE_MAP.put("batch", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        LANGUAGE_MAP.put("bat", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);

        // Config & Data
        LANGUAGE_MAP.put("xml", SyntaxConstants.SYNTAX_STYLE_XML);
        LANGUAGE_MAP.put("yaml", SyntaxConstants.SYNTAX_STYLE_YAML);
        LANGUAGE_MAP.put("yml", SyntaxConstants.SYNTAX_STYLE_YAML);
        LANGUAGE_MAP.put("sql", SyntaxConstants.SYNTAX_STYLE_SQL);
        LANGUAGE_MAP.put("dockerfile", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE);
        LANGUAGE_MAP.put("docker", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE);
        LANGUAGE_MAP.put("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
        LANGUAGE_MAP.put("ini", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);

        // Others
        LANGUAGE_MAP.put("go", SyntaxConstants.SYNTAX_STYLE_GO);
        LANGUAGE_MAP.put("rust", SyntaxConstants.SYNTAX_STYLE_RUST);
        LANGUAGE_MAP.put("rs", SyntaxConstants.SYNTAX_STYLE_RUST);
        LANGUAGE_MAP.put("markdown", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        LANGUAGE_MAP.put("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        LANGUAGE_MAP.put("makefile", SyntaxConstants.SYNTAX_STYLE_MAKEFILE);
        LANGUAGE_MAP.put("make", SyntaxConstants.SYNTAX_STYLE_MAKEFILE);
        LANGUAGE_MAP.put("dart", SyntaxConstants.SYNTAX_STYLE_DART);
        LANGUAGE_MAP.put("latex", SyntaxConstants.SYNTAX_STYLE_LATEX);
        LANGUAGE_MAP.put("tex", SyntaxConstants.SYNTAX_STYLE_LATEX);
        LANGUAGE_MAP.put("actionscript", SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT);
        LANGUAGE_MAP.put("as", SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT);
    }

    public CollapsibleCodePane(String language, String code, boolean expandedByDefault) {
        super(12, (language != null && !language.isEmpty() ? language : "Code").toUpperCase(),
                ThemeManager.getIcon("file.svg"), expandedByDefault);
        this.language = language != null && !language.isEmpty() ? language : "Code";
        this.code = code;

        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(theme.codeHeaderBackground());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.codeHeaderBorder()),
                BorderFactory.createEmptyBorder(6, 12, 6, 3)
        ));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        headerLabel.setForeground(theme.codeHeaderForeground());

        // Copy button
        copyButton = UIUtils.createToolbarButton("copy.svg", 20, "Copy code", e -> copyCodeToClipboard());
        copyButton.setContentAreaFilled(false);
        copyButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        copyButton.setForeground(theme.codeHeaderForeground());
        header.add(copyButton, BorderLayout.EAST);

        codeTextArea = new RSyntaxTextArea();
        codeTextArea.setEditable(false);
        codeTextArea.setHighlightCurrentLine(false);
        codeTextArea.setAnimateBracketMatching(false);
        codeTextArea.setLineWrap(true);
        codeTextArea.setWrapStyleWord(true);

        codeTextArea.setFont(ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN));

        applySyntaxStyle();
        applySolarizedDarkTheme();

        codeTextArea.setText(code);
        codeTextArea.setCaretPosition(0);

        JPanel codeWrapper = new JPanel(new BorderLayout());
        codeWrapper.setBackground(codeTextArea.getBackground());
        codeWrapper.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        codeWrapper.add(codeTextArea, BorderLayout.CENTER);

        contentPanel.add(codeWrapper, BorderLayout.CENTER);

        setBaseColor(theme.codeBackground());
        setBorderColor(theme.codeHeaderBorder());
        setShowBorder(true);
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
            SwingUtilities.invokeLater(() -> {
                revalidate();
                repaint();
            });
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
    protected void onHeaderHover(boolean hover) {
        // No color change for code block headers
        header.setBackground(getDefaultHeaderBackground());
    }

    @Override
    protected java.awt.Color getDefaultHeaderBackground() {
        return ThemeManager.getCurrentTheme().codeHeaderBackground();
    }

    private void applySyntaxStyle() {
        String lang = (language != null) ? language.toLowerCase() : "";
        String style = LANGUAGE_MAP.getOrDefault(lang, SyntaxConstants.SYNTAX_STYLE_NONE);

        // Cache syntax style/theme application to minimize parsing overhead on subsequent calls
        if (!style.equals("CACHE_MISS")) { // Simple flag check to prevent constant re-parsing
            codeTextArea.setSyntaxEditingStyle(style);
        } else {
            // Fallback for compound identifiers (e.g., 'custom-java' -> 'java')
            for (java.util.Map.Entry<String, String> entry : LANGUAGE_MAP.entrySet()) {
                if (lang.endsWith(entry.getKey())) {
                    style = entry.getValue();
                    codeTextArea.setSyntaxEditingStyle(style);
                    break;
                }
            }
        }
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
            codeTextArea.setBackground(theme.codeBackground());
            codeTextArea.setSelectionColor(theme.codeSelection());
        } catch (Exception ioe) {
            manualSolarizedDark();
        }
    }

    private void manualSolarizedDark() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        codeTextArea.setBackground(theme.codeBackground());
        codeTextArea.setForeground(theme.codeForeground());
        codeTextArea.setSelectionColor(theme.codeSelection());
    }
}
