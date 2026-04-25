package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTextArea;

public class CollapsibleToolPane extends BaseCollapsiblePane {
    private final JTextArea textArea;

    public CollapsibleToolPane(String title, String content, boolean expandedByDefault) {
        super(12, getDisplayTitle(title, expandedByDefault), getHeaderIcon(title), expandedByDefault);

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color yellowAccent = theme.yellow();

        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent),
            BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 0));

        contentPanel.setOpaque(true);
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent));

        textArea = new JTextArea(content);
        // Let contentPanel handle the background color for textArea to ensure consistency across states.
        textArea.setBackground(null); // Null allows it to inherit from parent (contentPanel)
        textArea.setEditable(false);
        textArea.setOpaque(false);
        
        boolean isThinking = title.toUpperCase().contains("THINKING");
        textArea.setFont(isThinking
                ? ThemeManager.getFont().deriveFont(Font.PLAIN)
                : ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN));

        textArea.setBorder(BorderFactory.createEmptyBorder(8, 25, 8, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        contentPanel.add(textArea, BorderLayout.CENTER);
        
        updateAppearance();
    }

    private void updateAppearance() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(expanded ? theme.base2() : theme.thinkingHeaderBackground());
        contentPanel.setBackground(expanded ? theme.thinkingHeaderBackground() : theme.base2());
        textArea.setForeground(expanded ? theme.thinkingHeaderForeground() : theme.foreground());
        headerLabel.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
    }

    private static String getDisplayTitle(String title, boolean expanded) {
        if (title.toUpperCase().contains("THINKING")) {
            return expanded ? "Thinking Process" : "Thinking Process...";
        } else if (title.toUpperCase().contains("TOOL")) {
            String stripped = title.replaceFirst("(?i)TOOL:?\\s*", "").trim();
            if (stripped.isEmpty() || stripped.equalsIgnoreCase("Tool Call")) {
                return "Tool";
            } else {
                return "Tool: " + stripped;
            }
        }
        return title;
    }

    private static Icon getHeaderIcon(String title) {
        if (title.toUpperCase().contains("THINKING")) {
            return ThemeManager.getIcon("brain.svg", 32);
        } else if (title.toUpperCase().contains("TOOL")) {
            return ThemeManager.getIcon("tool.svg", 21);
        }
        return null;
    }

    public void setTitle(String title) {
        headerLabel.setText(getDisplayTitle(title, expanded));
        headerLabel.setIcon(getHeaderIcon(title));
    }

    public void setContent(String content) {
        if (content != null && content.equals(textArea.getText())) {
            return;
        }
        textArea.setText(content);
    }

    @Override
    protected void onHeaderHover(boolean hover) {
        // Ensure colors are consistent even during hover events
        updateAppearance();
    }

    @Override
    protected void onToggle(boolean expanded) {
        if (headerLabel.getText().toUpperCase().contains("THINKING") || 
            headerLabel.getText().toUpperCase().contains("PROCESS")) {
            headerLabel.setText(expanded ? "Thinking Process" : "Thinking Process...");
        }
        updateAppearance();
    }

    @Override
    protected Color getDefaultHeaderBackground() {
        return expanded ? ThemeManager.getCurrentTheme().base2() : ThemeManager.getCurrentTheme().thinkingHeaderBackground();
    }

}