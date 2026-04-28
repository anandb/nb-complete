package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Map;

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

    private static volatile org.fife.ui.rsyntaxtextarea.Theme cachedRTheme;

    private static org.fife.ui.rsyntaxtextarea.Theme loadCodeTheme() {
        org.fife.ui.rsyntaxtextarea.Theme theme = cachedRTheme;
        if (theme != null) {
            return theme;
        }
        try {
            java.io.InputStream in = CollapsibleCodePane.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml");
            if (in != null) {
                theme = org.fife.ui.rsyntaxtextarea.Theme.load(in);
                cachedRTheme = theme;
                return theme;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final java.util.Map<String, String> LANGUAGE_MAP = new java.util.HashMap<>();
    private static final java.util.Map<String, String> LANGUAGE_FALLBACK_MAP = new java.util.HashMap<>();

    static {
        // C/C++ / C#
        addLang("c", SyntaxConstants.SYNTAX_STYLE_C);
        addLang("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        addLang("c++", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        addLang("cs", SyntaxConstants.SYNTAX_STYLE_CSHARP);
        addLang("c#", SyntaxConstants.SYNTAX_STYLE_CSHARP);

        // Java & JVM
        addLang("java", SyntaxConstants.SYNTAX_STYLE_JAVA);
        addLang("kotlin", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        addLang("kt", SyntaxConstants.SYNTAX_STYLE_KOTLIN);
        addLang("groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY);
        addLang("scala", SyntaxConstants.SYNTAX_STYLE_SCALA);
        addLang("clojure", SyntaxConstants.SYNTAX_STYLE_CLOJURE);

        // Web
        addLang("html", SyntaxConstants.SYNTAX_STYLE_HTML);
        addLang("htm", SyntaxConstants.SYNTAX_STYLE_HTML);
        addLang("css", SyntaxConstants.SYNTAX_STYLE_CSS);
        addLang("javascript", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        addLang("js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        addLang("typescript", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
        addLang("ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT);
        addLang("json", SyntaxConstants.SYNTAX_STYLE_JSON);
        addLang("php", SyntaxConstants.SYNTAX_STYLE_PHP);

        // Scripting
        addLang("python", SyntaxConstants.SYNTAX_STYLE_PYTHON);
        addLang("py", SyntaxConstants.SYNTAX_STYLE_PYTHON);
        addLang("ruby", SyntaxConstants.SYNTAX_STYLE_RUBY);
        addLang("rb", SyntaxConstants.SYNTAX_STYLE_RUBY);
        addLang("lua", SyntaxConstants.SYNTAX_STYLE_LUA);
        addLang("perl", SyntaxConstants.SYNTAX_STYLE_PERL);
        addLang("pl", SyntaxConstants.SYNTAX_STYLE_PERL);
        addLang("tcl", SyntaxConstants.SYNTAX_STYLE_TCL);

        // Shell & Scripts
        addLang("sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        addLang("bash", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        addLang("zsh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        addLang("ksh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        addLang("shell", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL);
        addLang("batch", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);
        addLang("bat", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH);

        // Config & Data
        addLang("xml", SyntaxConstants.SYNTAX_STYLE_XML);
        addLang("yaml", SyntaxConstants.SYNTAX_STYLE_YAML);
        addLang("yml", SyntaxConstants.SYNTAX_STYLE_YAML);
        addLang("sql", SyntaxConstants.SYNTAX_STYLE_SQL);
        addLang("dockerfile", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE);
        addLang("docker", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE);
        addLang("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
        addLang("ini", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);

        // Others
        addLang("go", SyntaxConstants.SYNTAX_STYLE_GO);
        addLang("rust", SyntaxConstants.SYNTAX_STYLE_RUST);
        addLang("rs", SyntaxConstants.SYNTAX_STYLE_RUST);
        addLang("markdown", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        addLang("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN);
        addLang("makefile", SyntaxConstants.SYNTAX_STYLE_MAKEFILE);
        addLang("make", SyntaxConstants.SYNTAX_STYLE_MAKEFILE);
        addLang("dart", SyntaxConstants.SYNTAX_STYLE_DART);
        addLang("latex", SyntaxConstants.SYNTAX_STYLE_LATEX);
        addLang("tex", SyntaxConstants.SYNTAX_STYLE_LATEX);
        addLang("actionscript", SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT);
        addLang("as", SyntaxConstants.SYNTAX_STYLE_ACTIONSCRIPT);

        // Pre-compute suffix-based fallbacks for compound identifiers
        for (Map.Entry<String, String> e : LANGUAGE_MAP.entrySet()) {
            LANGUAGE_FALLBACK_MAP.put(e.getKey(), e.getValue());
        }
    }

    private static void addLang(String key, String style) {
        LANGUAGE_MAP.put(key, style);
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
                BorderFactory.createEmptyBorder(6, 8, 6, 3)
        ));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        headerLabel.setIconTextGap(8);
        headerLabel.setForeground(theme.codeHeaderForeground());

        // Copy button
        copyButton = UIUtils.createToolbarButton("copy.svg", 20, "Copy code", e -> copyCodeToClipboard());
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
        codeWrapper.setBorder(BorderFactory.createEmptyBorder(8, 40, 8, 12));
        codeWrapper.add(codeTextArea, BorderLayout.CENTER);

        contentPanel.add(codeWrapper, BorderLayout.CENTER);

        setBaseColor(theme.codeBackground());
        setBorderColor(theme.codeHeaderBorder());
        setShowBorder(true);
        setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
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
        String style = LANGUAGE_FALLBACK_MAP.get(lang);
        if (style == null) {
            for (Map.Entry<String, String> e : LANGUAGE_MAP.entrySet()) {
                if (lang.endsWith(e.getKey())) {
                    style = e.getValue();
                    LANGUAGE_FALLBACK_MAP.put(lang, style);
                    break;
                }
            }
        }
        codeTextArea.setSyntaxEditingStyle(style != null ? style : SyntaxConstants.SYNTAX_STYLE_NONE);
    }

    private void applySolarizedDarkTheme() {
        org.fife.ui.rsyntaxtextarea.Theme rTheme = loadCodeTheme();
        if (rTheme != null) {
            try {
                rTheme.apply(codeTextArea);
                ColorTheme theme = ThemeManager.getCurrentTheme();
                codeTextArea.setBackground(theme.codeBackground());
                codeTextArea.setSelectionColor(theme.codeSelection());
                return;
            } catch (Exception ioe) {
            }
        }
        manualSolarizedDark();
    }

    private void manualSolarizedDark() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        codeTextArea.setBackground(theme.codeBackground());
        codeTextArea.setForeground(theme.codeForeground());
        codeTextArea.setSelectionColor(theme.codeSelection());
    }
}
