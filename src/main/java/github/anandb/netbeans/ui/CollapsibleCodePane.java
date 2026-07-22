package github.anandb.netbeans.ui;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.openide.util.NbBundle;
import github.anandb.netbeans.support.Logger;

// DSL-LEAF: keep imperative, wrap via UI.of(...) — extends BaseCollapsiblePane,
// builds RSyntaxTextArea code block + copy popup. Migration target: CodePaneToolbarSpec.
public class CollapsibleCodePane extends BaseCollapsiblePane {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.from(CollapsibleCodePane.class);
    private static volatile Theme cachedRTheme;

    private String language;
    private String code;
    private RSyntaxTextArea codeTextArea;
    private boolean codeAreaInitialized;

    private static synchronized Theme loadCodeTheme() {
        Theme theme = cachedRTheme;
        if (theme != null) {
            return theme;
        }
        try (InputStream in = CollapsibleCodePane.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml")) {
            if (in != null) {
                theme = Theme.load(in);
                cachedRTheme = theme;
                return theme;
            }
        } catch (Exception e) {
            LOG.warn("Failed to load code theme: {0}", ExceptionUtils.getMessage(e));
        }
        return null;
    }

    static final Map<String, String> LANGUAGE_MAP = new ConcurrentHashMap<>();
    static final Map<String, String> LANGUAGE_FALLBACK_MAP = new ConcurrentHashMap<>();

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
        super(12, (language != null && !language.isEmpty() ? language
                : NbBundle.getMessage(CollapsibleCodePane.class, "LBL_CodeFallback")).toUpperCase(),
                ThemeManager.getCurrentTheme().codeHeaderBorder(), expandedByDefault);
        this.language = language != null && !language.isEmpty() ? language : NbBundle.getMessage(CollapsibleCodePane.class, "LBL_CodeFallback");
        this.code = code;

        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(theme.codeHeaderBackground());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.codeHeaderBorder()),
                BorderFactory.createEmptyBorder(6, 8, 6, 3)
        ));
        headerLabel.setBorder(UIUtils.EMPTY_BORDER);
        headerLabel.setIconTextGap(8);
        headerLabel.setForeground(theme.codeHeaderForeground());

        // Remove only the base class hover listener — not all MouseListeners,
        // which would also strip L&F-installed ones (e.g. BasicButtonListener).
        copyButton.removeMouseListener(copyButtonHoverListener);
        copyButton.setToolTipText(NbBundle.getMessage(CollapsibleCodePane.class, "HINT_CopyCode"));
        copyButton.setBorder(UIUtils.COPY_BUTTON_BORDER);
        copyButton.setForeground(theme.codeHeaderForeground());
        copyButton.setVisible(true);

        if (expandedByDefault) {
            initCodeTextArea();
        } else {
            JLabel placeholder = new JLabel(NbBundle.getMessage(CollapsibleCodePane.class, "LBL_CodeBlockCollapsed"));
            placeholder.setForeground(theme.codeHeaderForeground());
            placeholder.setFont(ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN, 11f));
            contentPanel.add(placeholder, BorderLayout.CENTER);
        }

        setBaseColor(theme.codeBackground());
        setBorderColor(theme.codeHeaderBorder());
        setShowBorder(true);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void ensureCodeTextArea() {
        if (!codeAreaInitialized) {
            contentPanel.removeAll();
            initCodeTextArea();
            contentPanel.revalidate();
            contentPanel.repaint();
        }
    }

    private void initCodeTextArea() {
        codeAreaInitialized = true;
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

        // Right-click context menu on code area
        codeTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showContextMenu(e);
            }

            private void showContextMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                JPopupMenu menu = new JPopupMenu();

                JMenuItem copyItem = new JMenuItem("Copy");
                copyItem.addActionListener(ev -> copySelectedOrAll());
                menu.add(copyItem);

                JMenuItem copyAllItem = new JMenuItem("Copy All");
                copyAllItem.addActionListener(ev -> copyAll());
                menu.add(copyAllItem);

                JMenuItem copyMdItem = new JMenuItem("Copy as Markdown");
                copyMdItem.addActionListener(ev -> copyAsMarkdown());
                menu.add(copyMdItem);

                menu.show(codeTextArea, e.getX(), e.getY());
            }
        });

        JPanel codeWrapper = new JPanel(new BorderLayout());
        codeWrapper.setBackground(codeTextArea.getBackground());
        codeWrapper.setBorder(UIUtils.CODE_WRAPPER_BORDER);
        codeWrapper.add(codeTextArea, BorderLayout.CENTER);

        contentPanel.add(codeWrapper, BorderLayout.CENTER);
    }

    @Override
    protected void onToggle(boolean expanded) {
        if (expanded) {
            ensureCodeTextArea();
        }
    }

    public void updateContent(String language, String code) {
        if (code == null) code = "";
        if (!code.equals(this.code)) {
            String prevLanguage = this.language;
            this.code = code;
            this.language = (language != null && !language.isEmpty()) ? language : NbBundle.getMessage(CollapsibleCodePane.class, "LBL_CodeFallback");
            boolean languageChanged = !Objects.equals(prevLanguage, this.language);
            if (codeAreaInitialized) {
                codeTextArea.setText(code);
                codeTextArea.setCaretPosition(0);
                if (languageChanged) {
                    headerLabel.setText(this.language.toUpperCase());
                    // applySyntaxStyle forces a full re-tokenize via
                    // RSyntaxTextArea.setSyntaxEditingStyle. Only run it when
                    // the language actually changes — pure code-body updates
                    // are re-tokenized incrementally inside RSyntaxTextArea.
                    applySyntaxStyle();
                }
                SwingUtilities.invokeLater(() -> {
                    revalidate();
                    repaint();
                });
            }
            if (languageChanged) {
                headerLabel.setText(this.language.toUpperCase());
            }
        }
    }

    @Override
    protected void onHeaderHover(boolean hover) {
        // No color change for code block headers
        header.setBackground(getDefaultHeaderBackground());
    }

    @Override
    protected Color getDefaultHeaderBackground() {
        return ThemeManager.getCurrentTheme().codeHeaderBackground();
    }

    private void applySyntaxStyle() {
        if (codeTextArea == null) return;
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
        if (codeTextArea == null) return;
        Theme rTheme = loadCodeTheme();
        if (rTheme != null) {
            try {
                rTheme.apply(codeTextArea);
                ColorTheme theme = ThemeManager.getCurrentTheme();
                codeTextArea.setBackground(theme.codeBackground());
                codeTextArea.setSelectionColor(theme.codeSelection());
                return;
            } catch (Exception ioe) {
                // theme apply error, fall through to manual style
            }
        }
        manualSolarizedDark();
    }

    private void manualSolarizedDark() {
        if (codeTextArea == null) return;
        ColorTheme theme = ThemeManager.getCurrentTheme();
        codeTextArea.setBackground(theme.codeBackground());
        codeTextArea.setForeground(theme.codeForeground());
        codeTextArea.setSelectionColor(theme.codeSelection());
    }

    // ────────────────────────────────────────────────────────────────
    // BaseCollapsiblePane abstract method implementations
    // (not typically used for code panes, but required by the base class)
    // ────────────────────────────────────────────────────────────────

    @Override
    public void setTitle(String title) {
        headerLabel.setText(title);
    }

    @Override
    public void setContent(String content) {
        if (codeTextArea != null) {
            codeTextArea.setText(content);
            codeTextArea.setCaretPosition(0);
        }
    }

    @Override
    public void appendContent(String text) {
        if (codeTextArea != null) {
            codeTextArea.append(text);
        }
    }

    @Override
    protected Icon getDefaultIcon() {
        return ThemeManager.getIcon("file.svg", 24);
    }

    @Override
    protected String getContentToCopy() {
        return code;
    }

    /** Copy selected text if any, otherwise copy all code. */
    private void copySelectedOrAll() {
        String selected = (codeTextArea != null) ? codeTextArea.getSelectedText() : null;
        String toCopy = (selected != null && !selected.isEmpty()) ? selected : code;
        if (toCopy != null && !toCopy.isEmpty()) {
            StringSelection sel = new StringSelection(toCopy);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        }
    }

    /** Copy all code text. */
    private void copyAll() {
        if (code != null && !code.isEmpty()) {
            StringSelection sel = new StringSelection(code);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        }
    }

    /** Copy code wrapped in a markdown fenced code block. */
    private void copyAsMarkdown() {
        if (code == null || code.isEmpty()) return;
        String lang = (language != null) ? language : "";
        String md = "```" + lang + "\n" + code + "\n```";
        StringSelection sel = new StringSelection(md);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
    }

    @Override
    protected void updateAppearance() {
        super.updateBaseAppearance();
    }
}
