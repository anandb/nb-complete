package ai.opencode.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ai.opencode.netbeans.ui.ThemeManager.Theme;

public class CollapsibleCodePane extends JPanel {
    private final String language;
    private final String code;
    private final JPanel contentPanel;
    private final JLabel headerLabel;
    private final JLabel toggleIcon;
    private final JEditorPane codePane;
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
        
        codePane = new JEditorPane();
        codePane.setEditable(false);
        codePane.setContentType("text/html");
        codePane.setOpaque(true);
        codePane.setBackground(new Color(0xe9e9d0)); // Matches theme pre background
        
        updateCodeContent();
        
        contentPanel.add(codePane, BorderLayout.CENTER);
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

    private void toggle() {
        expanded = !expanded;
        contentPanel.setVisible(expanded);
        headerLabel.setText(getLabelText());
        toggleIcon.setText(expanded ? "▼" : "▶");
        revalidate();
        repaint();
        // Force parent re-layout
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

    private void updateCodeContent() {
        Theme theme = ThemeManager.getCurrentTheme();
        
        // Escape HTML
        String escaped = code.replace("&", "&amp;")
                             .replace("<", "&lt;")
                             .replace(">", "&gt;")
                             .replace("\"", "&quot;")
                             .replace("\n", "<br>");

        // Alternate spaces to allow wrapping in Swing but preserve indentation
        StringBuilder spaceFixed = new StringBuilder();
        boolean lastWasSpace = false;
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == ' ') {
                if (lastWasSpace) {
                    spaceFixed.append("&nbsp;");
                    lastWasSpace = false;
                } else {
                    spaceFixed.append(" ");
                    lastWasSpace = true;
                }
            } else if (c == '\t') {
                spaceFixed.append(" &nbsp; &nbsp;");
                lastWasSpace = false;
            } else {
                spaceFixed.append(c);
                lastWasSpace = false;
            }
        }

        String html = "<html><body style=\"font-family: 'JetBrains Mono', 'Input Mono', monospace; font-size: 12px; color: #839496; background-color: #002B36; padding: 10px; margin: 0;\">"
                    + spaceFixed.toString()
                    + "</body></html>";
        codePane.setText(html);
        codePane.setBackground(Color.decode("#002B36"));
    }
}
