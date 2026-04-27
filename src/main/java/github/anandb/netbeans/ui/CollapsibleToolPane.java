package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class CollapsibleToolPane extends BaseCollapsiblePane {
    private static final long serialVersionUID = 1L;
    private final JTextArea textArea;
    private final JPanel titlePanel;
    private JLabel paramLabel;
    private boolean isThinking;

    public CollapsibleToolPane(String title, String content, boolean expandedByDefault) {
        super(12, "", getHeaderIcon(title), expandedByDefault);
        this.isThinking = title.toUpperCase().contains("THINKING");

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color yellowAccent = theme.yellow();

        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent),
            BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 0));

        titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }
        };
        titlePanel.setOpaque(false);
        header.remove(headerLabel);
        header.add(titlePanel, BorderLayout.CENTER);

        contentPanel.setOpaque(true);
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent));

        textArea = new JTextArea(content);
        textArea.setBackground(null);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setFont(isThinking
                ? ThemeManager.getFont().deriveFont(Font.PLAIN)
                : ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN));
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 25, 8, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        contentPanel.add(textArea, BorderLayout.CENTER);

        setupTitleLabels(title);
        updateAppearance();
        setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
    }

    private void setupTitleLabels(String rawTitle) {
        titlePanel.removeAll();

        Icon icon = getHeaderIcon(rawTitle);
        if (icon != null) {
            JLabel iconLabel = new JLabel(icon) {
                @Override
                public boolean contains(int x, int y) {
                    return false;
                }
            };
            iconLabel.setVerticalAlignment(JLabel.CENTER);
            titlePanel.add(iconLabel);
        }

        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        headerLabel.setIcon(null);
        titlePanel.add(headerLabel);

        if (isThinking) {
            headerLabel.setText(expanded ? "Thinking Process" : "Thinking Process...");
            return;
        }

        String stripped = rawTitle.replaceFirst("(?i)TOOL:?\\s*", "").trim();

        if (stripped.isEmpty() || stripped.equalsIgnoreCase("Tool Call")) {
            headerLabel.setText("Tool");
            return;
        }

        String[] parts = stripped.split("\\s+", 2);
        headerLabel.setText(parts[0]);

        if (parts.length > 1) {
            if (paramLabel == null) {
                paramLabel = new JLabel() {
                    @Override
                    public boolean contains(int x, int y) {
                        return false;
                    }
                };
                paramLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
            }
            paramLabel.setText(" " + parts[1]);
            titlePanel.add(paramLabel);
        }
    }

    private void updateAppearance() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(expanded ? theme.base2() : theme.thinkingHeaderBackground());
        contentPanel.setBackground(expanded ? theme.thinkingHeaderBackground() : theme.base2());
        textArea.setForeground(expanded ? theme.thinkingHeaderForeground() : theme.foreground());
        headerLabel.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
        if (paramLabel != null) {
            paramLabel.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
        }
    }

    public void setTitle(String title) {
        this.isThinking = title.toUpperCase().contains("THINKING");
        setupTitleLabels(title);
    }

    public void setContent(String content) {
        if (content != null && content.equals(textArea.getText())) {
            return;
        }
        textArea.setText(content);
    }

    @Override
    protected void onHeaderHover(boolean hover) {
        updateAppearance();
    }

    @Override
    protected void onToggle(boolean expanded) {
        if (isThinking) {
            headerLabel.setText(expanded ? "Thinking Process" : "Thinking Process...");
        }
        updateAppearance();
    }

    @Override
    protected Color getDefaultHeaderBackground() {
        return expanded ? ThemeManager.getCurrentTheme().base2() : ThemeManager.getCurrentTheme().thinkingHeaderBackground();
    }

    private static Icon getHeaderIcon(String title) {
        if (title.toUpperCase().contains("THINKING")) {
            return ThemeManager.getIcon("brain.svg", 32);
        }
        return ThemeManager.getIcon("tool.svg", 24);
    }
}
