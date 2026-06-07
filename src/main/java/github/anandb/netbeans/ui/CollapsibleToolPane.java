package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Timer;

import java.util.Locale;

import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class CollapsibleToolPane extends BaseCollapsiblePane {
    private static final long serialVersionUID = 1L;
    private JTextArea textArea;
    /** Plain text of the current content (used by copy button). */
    private String combinedPlainText = "";
    private final JPanel titlePanel;
    private final JButton copyButton;
    /** Toggle button for collapsing/expanding the entire accordion group. */
    private final JButton groupToggleBtn;
    /**
     * The default accent color for the left border. Kept for restoring
     * when the pane is not part of an accordion group.
     */
    private final Color defaultAccent;
    private JLabel paramLabel;
    private boolean isThinking;
    private final AtomicBoolean copyHovered = new AtomicBoolean(false);

    public CollapsibleToolPane(String title, String content, boolean expandedAtStart) {
        super(12, "", getHeaderIcon(title), expandedAtStart);
        this.isThinking = title.toUpperCase().contains("THINKING");

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color yellowAccent = theme.yellow();
        this.defaultAccent = yellowAccent;

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
        // Wrap in fixed-size panel so header height never changes when button visibility toggles
        JPanel eastPlaceholder = new JPanel(new BorderLayout());
        eastPlaceholder.setOpaque(false);
        Dimension btnSize = copyButton.getPreferredSize();
        eastPlaceholder.setPreferredSize(btnSize);
        eastPlaceholder.setMinimumSize(btnSize);
        eastPlaceholder.setMaximumSize(btnSize);
        eastPlaceholder.add(copyButton, BorderLayout.CENTER);
        header.add(eastPlaceholder, BorderLayout.EAST);

        // Collapse/expand button for the accordion group
        groupToggleBtn = new JButton("+");
        groupToggleBtn.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, 14f));
        groupToggleBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        groupToggleBtn.setContentAreaFilled(false);
        groupToggleBtn.setFocusPainted(false);
        groupToggleBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        groupToggleBtn.setToolTipText("Collapse/expand all tool/thought panes");
        groupToggleBtn.setVisible(false);
        groupToggleBtn.addActionListener(e -> {
            AccordionGroup g = getAccordionGroup();
            if (g != null) g.toggleAll();
        });
        header.add(groupToggleBtn, BorderLayout.WEST);

        setupTitleLabels(title);
        updateAppearance();
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /**
     * Creates a tool pane without a text area. Use this for the combined
     * activity bubble where content will be supplied later via
     * {@link #setSegmentedContent(List)}.
     */
    public CollapsibleToolPane(String title, boolean expandedAtStart) {
        super(12, "", getHeaderIcon(title), expandedAtStart);
        this.isThinking = title.toUpperCase().contains("THINKING");

        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color yellowAccent = theme.yellow();
        this.defaultAccent = yellowAccent;

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

        // No textArea for combined bubble — content added via setSegmentedContent()

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
        // Wrap in fixed-size panel so header height never changes when button visibility toggles
        JPanel eastPlaceholder = new JPanel(new BorderLayout());
        eastPlaceholder.setOpaque(false);
        Dimension btnSize = copyButton.getPreferredSize();
        eastPlaceholder.setPreferredSize(btnSize);
        eastPlaceholder.setMinimumSize(btnSize);
        eastPlaceholder.setMaximumSize(btnSize);
        eastPlaceholder.add(copyButton, BorderLayout.CENTER);
        header.add(eastPlaceholder, BorderLayout.EAST);

        // Collapse/expand button for the accordion group
        groupToggleBtn = new JButton("+");
        groupToggleBtn.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, 14f));
        groupToggleBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        groupToggleBtn.setContentAreaFilled(false);
        groupToggleBtn.setFocusPainted(false);
        groupToggleBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        groupToggleBtn.setToolTipText("Collapse/expand all tool/thought panes");
        groupToggleBtn.setVisible(false);
        groupToggleBtn.addActionListener(e -> {
            AccordionGroup g = getAccordionGroup();
            if (g != null) g.toggleAll();
        });
        header.add(groupToggleBtn, BorderLayout.WEST);

        setupTitleLabels(title);
        updateAppearance();
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    @Override
    public void setAccordionGroup(AccordionGroup group) {
        super.setAccordionGroup(group);
        // Visually distinguish panes when part of an accordion group
        // by changing the left border from the default accent to a
        // shared "grouped" color.
        boolean isGrouped = group != null;
        Color borderColor = isGrouped
                ? ThemeManager.getCurrentTheme().base1()
                : defaultAccent;
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, borderColor),
                BorderFactory.createEmptyBorder(5, 4, 5, 10)));
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, borderColor));
        // Show/hide the group collapse/expand toggle button
        groupToggleBtn.setVisible(isGrouped);
        if (isGrouped) {
            updateGroupToggleIcon();
        }
    }

    /** Updates the group toggle button icon to reflect current group state. */
    private void updateGroupToggleIcon() {
        AccordionGroup g = getAccordionGroup();
        if (g == null) return;
        boolean allExpanded = g.allExpanded();
        groupToggleBtn.setText(allExpanded ? "-" : "+");
    }

    @Override
    protected void onToggle(boolean expanded) {
        if (isThinking) {
            String tp = NbBundle.getMessage(CollapsibleToolPane.class, "LBL_ThinkingProcess");
            headerLabel.setText(expanded ? tp : tp + "...");
        }
        updateAppearance();
        // Keep group toggle icon consistent when individual panes toggle
        updateGroupToggleIcon();
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
        if (textArea != null) {
            textArea.setForeground(expanded ? theme.thinkingHeaderForeground() : theme.foreground());
        }
        headerLabel.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
        if (paramLabel != null) {
            paramLabel.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
        }
        copyButton.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
    }

    private void copyContentToClipboard() {
        String content = combinedPlainText.isEmpty()
                ? (textArea != null ? textArea.getText() : "")
                : combinedPlainText;
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
        if (textArea == null) {
            return;
        }
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

    /** A segment of tool/thought content to render with an appropriate background. */
    public record ToolSegment(String text, boolean isThought) {}

    /**
     * Replaces the content area with a panel of colored segments, one per
     * consecutive same-type block. Each segment renders its text as markdown
     * via {@link FitEditorPane}.
     */
    public void setSegmentedContent(List<ToolSegment> blocks) {
        contentPanel.removeAll();

        // Build combined plain text for copy button
        StringBuilder plainText = new StringBuilder();
        JPanel multiPanel = new JPanel();
        multiPanel.setLayout(new BoxLayout(multiPanel, BoxLayout.Y_AXIS));
        multiPanel.setOpaque(false);
        multiPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        for (int i = 0; i < blocks.size(); i++) {
            ToolSegment block = blocks.get(i);
            if (i > 0) {
                multiPanel.add(Box.createVerticalStrut(6));
                plainText.append("\n\n");
            }
            multiPanel.add(createSegmentPane(block.text(), block.isThought(), theme));
            plainText.append(block.text());
        }

        combinedPlainText = plainText.toString();
        contentPanel.add(multiPanel, BorderLayout.CENTER);
        // Do NOT revalidate here — the pane may not yet be in a validated
        // container. Let the caller’s revalidate (e.g. messagesContainer)
        // lay everything out with proper widths.
    }

    private JPanel createSegmentPane(String text, boolean isThought, ColorTheme theme) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Same background for both; differentiate by left border accent only
        wrapper.setBackground(theme.thinkingHeaderBackground());
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0,
                isThought ? theme.yellow() : theme.accent()));

        String html = HtmlContentPreparer.prepareHtml(text, theme, "assistant", false);
        FitEditorPane pane = FitEditorPane.createHtmlPane(html, null, "assistant", false);
        wrapper.add(pane, BorderLayout.CENTER);

        return wrapper;
    }
}
