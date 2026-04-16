package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.openide.util.ImageUtilities;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import github.anandb.netbeans.ui.ColorTheme;

public class CollapsibleCodePane extends RoundedPanel {

    private static final long serialVersionUID = 1L;
    private String language;
    private String code;
    private final JLabel headerLabel;
    private final JLabel toggleIcon;
    private final RSyntaxTextArea codeTextArea;
    private final JPanel contentPanel;
    private final JButton copyButton;
    private boolean expanded;

    public CollapsibleCodePane(String language, String code, boolean expandedByDefault) {
        super(12);
        this.language = language != null && !language.isEmpty() ? language : "Code";
        this.code = code;
        this.expanded = expandedByDefault;

        setLayout(new BorderLayout());
        setOpaque(false);
        setDoubleBuffered(true);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color headerBg = theme.getBase2();
        Color borderCol = theme.getBubbleBorder();

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(headerBg);
        header.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 3));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Icon fileIcon = ThemeManager.getIcon("file.svg");
        headerLabel = new JLabel(getLabelText(), fileIcon, JLabel.LEFT);
        headerLabel.setIconTextGap(8);
        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        headerLabel.setForeground(theme.isDark() ? Color.decode("#BBBBBB") : Color.decode("#555555"));
        header.add(headerLabel, BorderLayout.CENTER);
        toggleIcon = new JLabel(expanded ? "▼" : "▶");
        toggleIcon.setFont(ThemeManager.getMonospaceFont().deriveFont(Font.BOLD));
        toggleIcon.setForeground(theme.isDark() ? Color.decode("#BBBBBB") : Color.decode("#555555"));
        header.add(toggleIcon, BorderLayout.WEST);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        // Copy button
        Icon copyIcon = ThemeManager.getIcon("copy.svg", 20);
        copyButton = new JButton(copyIcon);
        copyButton.setToolTipText("Copy code");
        copyButton.setFont(ThemeManager.getFont().deriveFont(12f));
        copyButton.setFocusPainted(false);
        copyButton.setContentAreaFilled(false);
        copyButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        copyButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        copyButton.setForeground(theme.isDark() ? Color.decode("#BBBBBB") : Color.decode("#555555"));
        copyButton.addActionListener(e -> copyCodeToClipboard());

        header.add(copyButton, BorderLayout.EAST);

        // Content
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        codeTextArea = new RSyntaxTextArea();
        codeTextArea.setEditable(false);
        codeTextArea.setHighlightCurrentLine(false);
        codeTextArea.setAnimateBracketMatching(false);
        codeTextArea.setLineWrap(true);

        // Always use Solarized Dark for code blocks per user preference
        Color bg = Color.decode("#002B36");
        Color fg = Color.decode("#839496");

        codeTextArea.setBackground(bg);
        codeTextArea.setForeground(fg);
        codeTextArea.setCaretColor(fg);
        codeTextArea.setSelectionColor(new Color(7, 54, 66));

        applySyntaxStyle();
        applySolarizedDarkTheme();

        // Ensure font is set AFTER theme application to avoid being overwritten
        codeTextArea.setFont(ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN));

        codeTextArea.setText(code);
        codeTextArea.setCaretPosition(0);

        // Wrap in a panel with padding
        JPanel codeWrapper = new JPanel(new BorderLayout());
        codeWrapper.setBackground(bg);
        codeWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        codeWrapper.add(codeTextArea, BorderLayout.CENTER);

        contentPanel.add(codeWrapper, BorderLayout.CENTER);
        contentPanel.setVisible(expanded);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        MouseAdapter toggleListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                toggle();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                header.setBackground(new Color(0, 0, 0, 25));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                header.setBackground(headerBg);
            }
        };
        header.addMouseListener(toggleListener);
        headerLabel.addMouseListener(toggleListener);
        toggleIcon.addMouseListener(toggleListener);
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            contentPanel.setVisible(expanded);
            headerLabel.setText(getLabelText());
            toggleIcon.setText(expanded ? "▼" : "▶");
            revalidate();
            repaint();
            // Force parent re-layout
            updateParentLayout();
        }
    }

    public void updateContent(String language, String code) {
        if (code == null) code = "";
        if (!code.equals(this.code)) {
            this.code = code;
            this.language = (language != null && !language.isEmpty()) ? language : "Code";
            codeTextArea.setText(code);
            codeTextArea.setCaretPosition(0);
            headerLabel.setText(getLabelText());
            applySyntaxStyle();
            revalidate();
            repaint();
        }
    }

    private void copyCodeToClipboard() {
        StringSelection selection = new StringSelection(code);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        // Visual feedback
        Icon originalIcon = copyButton.getIcon();
        Icon checkIcon = ThemeManager.getIcon("check.svg", 20);
        copyButton.setIcon(checkIcon);
        
        javax.swing.Timer timer = new javax.swing.Timer(2000, e -> {
            copyButton.setIcon(originalIcon);
        });
        timer.setRepeats(false);
        timer.start();
    }

    public void refreshTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        codeTextArea.setBackground(theme.isDark() ? Color.decode("#002B36") : Color.WHITE);
        codeTextArea.setForeground(theme.isDark() ? Color.decode("#839496") : Color.BLACK);

        // Recolor wrapper
        if (codeTextArea.getParent() != null) {
            codeTextArea.getParent().setBackground(codeTextArea.getBackground());
        }

        Color headerFg = theme.isDark() ? Color.decode("#BBBBBB") : Color.decode("#555555");
        headerLabel.setForeground(headerFg);
        toggleIcon.setForeground(headerFg);
        copyButton.setForeground(headerFg);

        applySolarizedDarkTheme();
        revalidate();
        repaint();
    }

    private void toggle() {
        setExpanded(!expanded);
    }

    private void updateParentLayout() {
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
            if (getParent().getParent() != null) {
                getParent().getParent().revalidate();
                getParent().getParent().repaint();
            }
        }
    }

    private String getLabelText() {
        return language.toUpperCase();
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
            // Load the dark theme from RSyntaxTextArea resources
            java.io.InputStream in = getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml");
            if (in == null) {
                // Fallback to manual coloring if XML loading fails
                manualSolarizedDark();
                return;
            }
            org.fife.ui.rsyntaxtextarea.Theme rTheme = org.fife.ui.rsyntaxtextarea.Theme.load(in);
            rTheme.apply(codeTextArea);

            // Override background to strict Solarized Dark
            codeTextArea.setBackground(Color.decode("#002B36"));
            codeTextArea.setSelectionColor(new Color(7, 54, 66));
        } catch (Exception ioe) {
            manualSolarizedDark();
        }
    }

    private void manualSolarizedDark() {
        codeTextArea.setBackground(Color.decode("#002B36"));
        codeTextArea.setForeground(Color.decode("#839496"));
        codeTextArea.setSelectionColor(new Color(7, 54, 66));
        codeTextArea.setCurrentLineHighlightColor(new Color(0, 43, 54));
    }

    private void applySolarizedLightTheme() {
        try {
            java.io.InputStream in = getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/default.xml");
            if (in == null) {
                manualSolarizedLight();
                return;
            }
            org.fife.ui.rsyntaxtextarea.Theme rTheme = org.fife.ui.rsyntaxtextarea.Theme.load(in);
            rTheme.apply(codeTextArea);
            codeTextArea.setBackground(Color.decode("#FDF6E3"));
            codeTextArea.setSelectionColor(new Color(238, 232, 213));
        } catch (Exception ioe) {
            manualSolarizedLight();
        }
    }

    private void manualSolarizedLight() {
        codeTextArea.setBackground(Color.decode("#FDF6E3"));
        codeTextArea.setForeground(Color.decode("#657B83"));
        codeTextArea.setSelectionColor(new Color(238, 232, 213));
        codeTextArea.setCurrentLineHighlightColor(new Color(253, 246, 227));
    }
}
