package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Timer;

import java.util.Locale;

import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@NbBundle.Messages({
    "LBL_ThinkingProcess=Thinking Process",
    "LBL_ToolFallback=Tool",
    "LBL_TagRead=read",
    "LBL_TagExecute=shell",
    "LBL_TagWrite=write",
    "LBL_TagEdit=edit",
    "LBL_TagSearch=search",
    "LBL_TagSkill=skill",
    "LBL_TagContext=context",
    "LBL_TagOther=other",
    "LBL_TagThink=think",
    "LBL_TagMcp=mcp",
    "HINT_CopyContent=Copy Content"
})
public class CollapsibleToolPane extends BaseCollapsiblePane {
    private static final long serialVersionUID = 1L;
    private final JTextArea textArea;
    private final JPanel titlePanel;
    private final JButton copyButton;
    private JLabel paramLabel;
    private boolean isThinking;
    private final AtomicBoolean copyHovered = new AtomicBoolean(false);

    public CollapsibleToolPane(String title, String content, boolean expandedAtStart) {
        super(12, "", getHeaderIcon(title), expandedAtStart);
        this.isThinking = title.toUpperCase().contains("THINKING");

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color yellowAccent = theme.yellow();

        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, yellowAccent),
            BorderFactory.createEmptyBorder(5, 4, 5, 10)
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

        // Copy button — visible only on header hover
        String hint = NbBundle.getMessage(CollapsibleToolPane.class, "HINT_CopyContent");
        copyButton = UIUtils.createToolbarButton("copy.svg", 20, hint, e -> copyContentToClipboard());
        copyButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        copyButton.setForeground(theme.foreground());
        copyButton.setVisible(false);
        copyButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                copyHovered.set(true);
                copyButton.setVisible(true);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                copyHovered.set(false);
                copyButton.setVisible(false);
            }
        });
        header.add(copyButton, BorderLayout.EAST);

        setupTitleLabels(title);
        updateAppearance();
        setAlignmentX(Component.LEFT_ALIGNMENT);
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

        int pos = stripped.indexOf(' ');
        String tag = (pos > 1) ? translateTag(stripped.substring(0, pos)) : null;
        String param = (pos > 1) ? stripped.substring(pos + 1) : null;

        if (tag != null) {
            headerLabel.setText(tag);
        } else {
            headerLabel.setText(stripped.isEmpty() ? NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ToolFallback") : stripped);
        }

        if (isNotBlank(param)) {
            if (paramLabel == null) {
                paramLabel = new JLabel() {
                    @Override
                    public boolean contains(int x, int y) {
                        return false;
                    }
                };
                paramLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
                paramLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        toggle();
                    }
                });
            }
            paramLabel.setText(" " + param);
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
        copyButton.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
    }

    private void copyContentToClipboard() {
        String content = textArea.getText();
        if (content == null || content.isEmpty()) {
            return;
        }
        StringSelection selection = new StringSelection(content);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Icon originalIcon = copyButton.getIcon();
        Icon checkIcon = ThemeManager.getIcon("check.svg", 20);
        copyButton.setIcon(checkIcon);

        Timer timer = new Timer(2000, e -> {
            copyButton.setIcon(originalIcon);
        });
        timer.setRepeats(false);
        timer.start();
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
        copyButton.setVisible(hover || copyHovered.get());
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
            case "mcp" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagMcp");
            case "other" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagOther");
            case "think" -> NbBundle.getMessage(CollapsibleToolPane.class, "LBL_TagThink");
            default -> null;
        };
    }
}
