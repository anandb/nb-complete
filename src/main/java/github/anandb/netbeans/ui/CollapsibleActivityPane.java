package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.openide.util.NbBundle;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * A collapsible pane for displaying AI activity (tool calls, thoughts, etc.)
 * using a {@link FitEditorPane} for HTML-rendered content instead of a plain
 * {@code JTextArea}. This avoids {@code NullPointerException} when combining
 * thought and tool content into a single pane with segmented
 * (markdown-rendered) blocks.
 * <p>
 * Usage:
 * <ul>
 *   <li>For a single text block: use
 *       {@link #CollapsibleActivityPane(String, String, boolean)} or
 *       {@link #setContent(String)}.</li>
 *   <li>For segmented (combined thought + tool) content: use
 *       {@link #setSegmentedContent(List)}.</li>
 * </ul>
 */
public class CollapsibleActivityPane extends BaseCollapsiblePane {

    private static final long serialVersionUID = 1L;

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

    // ────────────────────────────────────────────────────────
    // Constructors
    // ────────────────────────────────────────────────────────

    /**
     * Creates an activity pane with initial plain-text content.
     * The text is rendered as markdown HTML via {@link FitEditorPane}.
     */
    public CollapsibleActivityPane(String title, String content, boolean expandedAtStart) {
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

        // Show initial content as HTML-rendered markdown
        combinedPlainText = content != null ? content : "";
        String html = HtmlContentPreparer.prepareHtml(combinedPlainText, theme, "assistant", false);
        FitEditorPane pane = FitEditorPane.createHtmlPane(html, null, "assistant", false);
        contentPanel.add(pane, BorderLayout.CENTER);

        // Copy button — visible only on header hover
        String hint = NbBundle.getMessage(CollapsibleActivityPane.class, "HINT_CopyContent");
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
        // Wrap in fixed-size panel so header height never changes when copy button visibility toggles
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
        groupToggleBtn.setToolTipText("Collapse/expand all activity panes");
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
     * Creates an activity pane without initial content.
     * Call {@link #setContent(String)} or {@link #setSegmentedContent(List)}
     * later to populate the content area.
     */
    public CollapsibleActivityPane(String title, boolean expandedAtStart) {
        this(title, "", expandedAtStart);
    }

    // ────────────────────────────────────────────────────────
    // Accordion group
    // ────────────────────────────────────────────────────────

    @Override
    public void setAccordionGroup(AccordionGroup group) {
        super.setAccordionGroup(group);
        boolean isGrouped = group != null;
        Color borderColor = isGrouped
                ? ThemeManager.getCurrentTheme().base1()
                : defaultAccent;
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, borderColor),
                BorderFactory.createEmptyBorder(5, 4, 5, 10)));
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, borderColor));
        groupToggleBtn.setVisible(isGrouped);
        if (isGrouped) {
            updateGroupToggleIcon();
        }
    }

    private void updateGroupToggleIcon() {
        AccordionGroup g = getAccordionGroup();
        if (g == null) return;
        boolean allExpanded = g.allExpanded();
        groupToggleBtn.setText(allExpanded ? "-" : "+");
    }

    // ────────────────────────────────────────────────────────
    // Toggle behavior
    // ────────────────────────────────────────────────────────

    @Override
    protected void onToggle(boolean expanded) {
        if (isThinking) {
            String tp = NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_ThinkingProcess");
            headerLabel.setText(expanded ? tp : tp + "...");
        }
        updateAppearance();
        updateGroupToggleIcon();
    }

    // ────────────────────────────────────────────────────────
    // Title / header
    // ────────────────────────────────────────────────────────

    private void setupTitleLabels(String rawTitle) {
        titlePanel.removeAll();
        Icon icon = getHeaderIcon(rawTitle);

        headerLabel.setIcon(icon);
        headerLabel.setIconTextGap(8);
        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
        titlePanel.add(headerLabel);

        if (isThinking) {
            String tp = NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_ThinkingProcess");
            headerLabel.setText(expanded ? tp : tp + "...");
            return;
        }

        String stripped = rawTitle.replaceFirst("(?i)TOOL:?\\s*", "").trim();

        if (stripped.isEmpty() || "Tool".equalsIgnoreCase(stripped) || "Tool Call".equalsIgnoreCase(stripped)) {
            headerLabel.setText(NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_ToolFallback"));
            return;
        }

        // For titles like "Execution Steps (3)", don't split into tag+param.
        // Only do tag/param splitting for recognized tool call tags.
        int pos = stripped.indexOf(' ');
        String firstWord = (pos > 1) ? stripped.substring(0, pos) : null;
        String tag = firstWord != null ? translateTag(firstWord) : null;

        if (tag != null) {
            // Recognized tool call tag — show translated tag + param as subtitle
            headerLabel.setText(tag);
            String param = stripped.substring(pos + 1);
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
        } else {
            // Not a recognized tool call tag — show the full title as-is
            headerLabel.setText(stripped);
        }
    }

    /** Re-builds the title headers without resetting paramLabel. */
    public void setTitle(String title) {
        this.isThinking = title.toUpperCase().contains("THINKING");
        setupTitleLabels(title);
    }

    // ────────────────────────────────────────────────────────
    // Content
    // ────────────────────────────────────────────────────────

    /**
     * Sets plain-text content, rendered as markdown HTML via
     * {@link FitEditorPane}. Replaces any existing content
     * (including segmented content).
     */
    public void setContent(String content) {
        contentPanel.removeAll();
        combinedPlainText = content != null ? content : "";

        ColorTheme theme = ThemeManager.getCurrentTheme();
        String html = HtmlContentPreparer.prepareHtml(combinedPlainText, theme, "assistant", false);
        FitEditorPane pane = FitEditorPane.createHtmlPane(html, null, "assistant", false);
        contentPanel.add(pane, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Replaces the content area with a panel of colored segments, one per
     * consecutive same-type block. Each segment renders its text as markdown
     * via {@link FitEditorPane}.
     *
     * @param blocks the segmented content blocks
     */
    public void setSegmentedContent(List<CollapsibleToolPane.ToolSegment> blocks) {
        contentPanel.removeAll();

        StringBuilder plainText = new StringBuilder();
        JPanel multiPanel = new JPanel();
        multiPanel.setLayout(new BoxLayout(multiPanel, BoxLayout.Y_AXIS));
        multiPanel.setOpaque(false);
        multiPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        for (int i = 0; i < blocks.size(); i++) {
            CollapsibleToolPane.ToolSegment block = blocks.get(i);
            if (i > 0) {
                multiPanel.add(Box.createVerticalStrut(6));
                plainText.append("\n\n");
            }
            multiPanel.add(createSegmentPane(block.text(), block.isThought(), theme));
            plainText.append(block.text());
        }

        combinedPlainText = plainText.toString();
        contentPanel.add(multiPanel, BorderLayout.CENTER);
    }

    // ────────────────────────────────────────────────────────
    // Appearance
    // ────────────────────────────────────────────────────────

    private void updateAppearance() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(expanded ? theme.base2() : theme.thinkingHeaderBackground());
        contentPanel.setBackground(expanded ? theme.thinkingHeaderBackground() : theme.base2());
        headerLabel.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
        if (paramLabel != null) {
            paramLabel.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
        }
        copyButton.setForeground(expanded ? theme.foreground() : theme.thinkingHeaderForeground());
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

    // ────────────────────────────────────────────────────────
    // Copy to clipboard
    // ────────────────────────────────────────────────────────

    private void copyContentToClipboard() {
        String content = combinedPlainText;
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

    // ────────────────────────────────────────────────────────
    // Static helpers
    // ────────────────────────────────────────────────────────

    private static Icon getHeaderIcon(String title) {
        if (title.toUpperCase().contains("THINKING")) {
            return ThemeManager.getIcon("brain.svg", 24);
        }
        return ThemeManager.getIcon("tool.svg", 24);
    }

    private static String translateTag(String tag) {
        if (tag == null) return null;
        return switch (tag.toLowerCase(Locale.ROOT)) {
            case "read" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagRead");
            case "execute" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagExecute");
            case "write" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagWrite");
            case "edit" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagEdit");
            case "search" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagSearch");
            case "skill" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagSkill");
            case "context" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagContext");
            case "mcp" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagMcp");
            case "other" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagOther");
            case "think" -> NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_TagThink");
            default -> null;
        };
    }

    private static JPanel createSegmentPane(String text, boolean isThought, ColorTheme theme) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBackground(theme.thinkingHeaderBackground());
        wrapper.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0,
                isThought ? theme.yellow() : theme.accent()));

        String html = HtmlContentPreparer.prepareHtml(text, theme, "assistant", false);
        FitEditorPane pane = FitEditorPane.createHtmlPane(html, null, "assistant", false);
        wrapper.add(pane, BorderLayout.CENTER);

        // Allow horizontal stretching to fill the container width so FitEditorPane
        // inside can wrap text at the full width. Restrict vertical stretching so
        // BoxLayout does not grow the wrapper beyond its content height.
        Dimension pref = wrapper.getPreferredSize();
        wrapper.setMaximumSize(new Dimension(Short.MAX_VALUE, pref.height));

        return wrapper;
    }
}
