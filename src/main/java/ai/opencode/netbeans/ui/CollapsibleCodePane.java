package ai.opencode.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import ai.opencode.netbeans.ui.ThemeManager.Theme;

public class CollapsibleCodePane extends JPanel {
    private final String language;
    private final String code;
    private final JLabel headerLabel;
    private final JLabel toggleIcon;
    private final RSyntaxTextArea codeTextArea;
    private final JPanel contentPanel;
    private boolean expanded;

    public CollapsibleCodePane(String language, String code, boolean expandedByDefault) {
        this.language = language != null && !language.isEmpty() ? language : "Code";
        this.code = code;
        this.expanded = expandedByDefault;

        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        Theme theme = ThemeManager.getCurrentTheme();
        Color headerBg = new Color(0, 0, 0, 15);
        Color borderCol = new Color(0, 0, 0, 30);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(headerBg);
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderCol, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));

        headerLabel = new JLabel(getLabelText());
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        headerLabel.setForeground(Color.GRAY);
        header.add(headerLabel, BorderLayout.CENTER);

        toggleIcon = new JLabel(expanded ? "▼" : "▶");
        toggleIcon.setFont(new Font("Monospaced", Font.BOLD, 12));
        toggleIcon.setForeground(Color.GRAY);
        header.add(toggleIcon, BorderLayout.WEST);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        // Content
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        
        codeTextArea = new RSyntaxTextArea();
        codeTextArea.setEditable(false);
        codeTextArea.setHighlightCurrentLine(false);
        codeTextArea.setAnimateBracketMatching(false);
        codeTextArea.setLineWrap(true);
        
        // Solarized Dark Theme Colors
        Color bg = Color.decode("#002B36");
        Color fg = Color.decode("#839496");
        
        codeTextArea.setBackground(bg);
        codeTextArea.setForeground(fg);
        codeTextArea.setCaretColor(fg);
        codeTextArea.setSelectionColor(new Color(7, 54, 66)); // base02
        
        applySyntaxStyle();
        applySolarizedDarkTheme();
        
        // Ensure font is set AFTER theme application to avoid being overwritten
        codeTextArea.setFont(new Font(getBestMonospaceFont(), Font.PLAIN, 13));
        
        codeTextArea.setText(code);
        codeTextArea.setCaretPosition(0);
        
        // Wrap in a panel with padding instead of a scrollpane since we are in a chat bubble
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
        int lineCount = code.split("\n", -1).length;
        return "∨ CODE BLOCK (" + language.toUpperCase() + ", " + lineCount + " lines)";
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

    private String getBestMonospaceFont() {
        String[] preferredFonts = {
            "AtkynsonMono NF Medium", 
            "JetBrains Mono", 
            "Fira Code", 
            "Monaco", 
            "Droid Sans Mono", 
            "monospace"
        };
        
        try {
            Set<String> availableFonts = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
            ));
            
            for (String font : preferredFonts) {
                if (availableFonts.contains(font)) {
                    return font;
                }
            }
        } catch (Exception e) {
            // Fallback to generic monospace if something goes wrong
        }
        
        return "Monospaced";
    }
}
