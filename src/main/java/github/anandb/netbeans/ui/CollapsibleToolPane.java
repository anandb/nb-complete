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
        Color yellowAccent = theme.getYellow();

        header.setBackground(theme.getPanelHeader());
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent),
            BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 0));

        contentPanel.setOpaque(true);
        contentPanel.setBackground(theme.getBase2());
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent));
        
        textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        boolean isThinking = title.toUpperCase().contains("THINKING");
        textArea.setFont(isThinking
                ? ThemeManager.getFont().deriveFont(Font.PLAIN)
                : ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN));
        textArea.setForeground(theme.getForeground());
        textArea.setBorder(BorderFactory.createEmptyBorder(2, 25, 2, 10));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        
        contentPanel.add(textArea, BorderLayout.CENTER);
        
        refreshTheme();
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
            return ThemeManager.getIcon("brain.svg", 21);
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
    protected void onToggle(boolean expanded) {
        if (headerLabel.getText().toUpperCase().contains("THINKING")) {
            headerLabel.setText(expanded ? "Thinking Process" : "Thinking Process...");
        }
    }

    @Override
    public void refreshTheme() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color headerBg = theme.getPanelHeader();
        Color yellowAccent = theme.getYellow();

        header.setBackground(headerBg);
        contentPanel.setBackground(theme.getBase2());
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent));
        
        textArea.setForeground(theme.getForeground());
        Color headerFg = theme.getHeaderForeground();
        headerLabel.setForeground(headerFg);
        toggleIcon.setForeground(headerFg);
        
        revalidate();
        repaint();
    }
}