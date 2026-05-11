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

import java.util.Locale;
import java.util.regex.Pattern;
import org.openide.util.NbBundle;

@NbBundle.Messages({
    "LBL_ThinkingProcess=Thinking Process",
    "LBL_ToolFallback=Tool",
    "LBL_TagRead=read",
    "LBL_TagExecute=execute",
    "LBL_TagWrite=write",
    "LBL_TagEdit=edit",
    "LBL_TagSearch=search",
    "LBL_TagSkill=skill",
    "LBL_TagContext=context",
    "LBL_TagOther=other",
    "LBL_TagThink=think"
})
public class CollapsibleToolPane extends BaseCollapsiblePane {
    private static final long serialVersionUID = 1L;
    private static final Pattern WHITESPACE_SPLIT = Pattern.compile("\\s+", 2);
    private final JTextArea textArea;
    private final JPanel titlePanel;
    private JLabel paramLabel;
    private boolean isThinking;

    public CollapsibleToolPane(String title, String content, boolean expandedAtStart) {
        super(12, "", getHeaderIcon(title), expandedAtStart);
        this.isThinking = title.toUpperCase().contains("THINKING");

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color yellowAccent = theme.yellow();

        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent),
            BorderFactory.createEmptyBorder(2, 4, 2, 10)
        ));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

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
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 6));
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

        headerLabel.setIcon(icon);
        headerLabel.setIconTextGap(8);
        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        titlePanel.add(headerLabel);

        if (isThinking) {
            String tp = NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ThinkingProcess");
            headerLabel.setText(expanded ? tp : tp + "...");
            return;
        }

        String stripped = rawTitle.replaceFirst("(?i)TOOL:?\\s*", "").trim();

        if (stripped.isEmpty() || "Tool".equalsIgnoreCase(stripped) || "Tool Call".equalsIgnoreCase(stripped)) {
            headerLabel.setText(NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ToolFallback"));
            return;
        }

        String[] parts = WHITESPACE_SPLIT.split(stripped);
        String tag = translateTag(parts[0]);
        headerLabel.setText(tag != null ? tag : parts[0]);

        if (parts.length > 1) {
            if (paramLabel == null) {
                paramLabel = new JLabel() {
                    @Override
                    public boolean contains(int x, int y) {
                        return false;
                    }
                };
                paramLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
                paramLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        toggle();
                    }
                });
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
        if (content == null) {
            textArea.setText("");
            return;
        }
        if (content.equals(textArea.getText())) {
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
            String tp = NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ThinkingProcess");
            headerLabel.setText(expanded ? tp : tp + "...");
        }
        updateAppearance();
    }

    @Override
    protected Color getDefaultHeaderBackground() {
        return expanded ? ThemeManager.getCurrentTheme().base2() : ThemeManager.getCurrentTheme().thinkingHeaderBackground();
    }

    private static Icon getHeaderIcon(String title) {
        if (title.toUpperCase().contains("THINKING")) {
            return ThemeManager.getIcon("brain.svg", 24);
        }
        return ThemeManager.getIcon("tool.svg", 24);
    }

    private static String translateTag(String tag) {
        if (tag == null) return null;
        return switch (tag.toLowerCase(Locale.ROOT)) {
            case "read" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagRead");
            case "execute" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagExecute");
            case "write" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagWrite");
            case "edit" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagEdit");
            case "search" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagSearch");
            case "skill" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagSkill");
            case "context" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagContext");
            case "other" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagOther");
            case "think" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagThink");
            default -> null;
        };
    }
}
