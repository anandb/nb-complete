package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
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
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.text.View;

import org.openide.util.NbBundle;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * A collapsible pane for displaying AI activity (tool calls, thoughts, etc.)
 * using a {@link JTextArea} with word wrapping instead of a complex HTML
 * renderer. Provides reliable text wrapping for streamed tool/thought
 * content in the activity pane.
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
    private boolean isSegmented;
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
        Color blueAccent = Color.decode(theme.isDark() ? "#589DF6" : "#268BD2");
        this.defaultAccent = blueAccent;

        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, blueAccent),
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
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, blueAccent));

        // Show initial content as plain text with wrapping
        combinedPlainText = content != null ? content : "";
        JTextArea textArea = createActivityTextArea(combinedPlainText);
        contentPanel.add(textArea, BorderLayout.CENTER);

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
        if (isSegmented) {
            // Hide main header when expanded — segment headers act as toggle targets
            header.setVisible(!expanded);
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
                    paramLabel.setFont(isSegmented
                            ? ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN)
                            : ThemeManager.getFont().deriveFont(Font.PLAIN));
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
     * Sets plain-text content, rendered with word-wrapping via
     * {@link JTextArea}. Replaces any existing content
     * (including segmented content).
     */
    public void setContent(String content) {
        combinedPlainText = content != null ? content : "";

        // Reuse existing text area to avoid flicker from removeAll/recreate cycle
        if (contentPanel.getComponentCount() > 0
                && contentPanel.getComponent(0) instanceof JTextArea existing) {
            existing.setText(combinedPlainText);
            return;
        }

        contentPanel.removeAll();
        JTextArea textArea = createActivityTextArea(combinedPlainText);
        contentPanel.add(textArea, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Appends text to the existing content without replacing the entire text.
     * Used for streaming to avoid flicker from full text replacement.
     */
    public void appendContent(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        combinedPlainText += text;
        if (contentPanel.getComponentCount() > 0
                && contentPanel.getComponent(0) instanceof JTextArea existing) {
            existing.append(text);
            return;
        }
        contentPanel.removeAll();
        JTextArea textArea = createActivityTextArea(combinedPlainText);
        contentPanel.add(textArea, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Replaces the content area with a panel of colored segments, one per
     * consecutive same-type block. Each segment renders its text with
     * word-wrapping via {@link JTextArea}.
     *
     * @param blocks the segmented content blocks
     */
    public void setSegmentedContent(List<CollapsibleToolPane.ToolSegment> blocks) {
        isSegmented = true;
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
                plainText.append("\n\n");
            }
            multiPanel.add(createSegmentPane(block.text(), block.isThought(), block.title(), theme, i, this::toggle));
            plainText.append(block.text());
        }
        combinedPlainText = plainText.toString();
        contentPanel.add(multiPanel, BorderLayout.CENTER);

        // When expanded and segmented, hide the main header so segment headers
        // act as the toggle surface. Restored on collapse.
        header.setVisible(!(expanded && isSegmented));
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
        if (title.toUpperCase(Locale.ROOT).contains("THINKING")) {
            return ThemeManager.getIcon("brain.svg", 24);
        }
        return ThemeManager.getIcon("go.svg", 24);
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

    private static JPanel createSegmentPane(String text, boolean isThought, String title, ColorTheme theme, int index, Runnable toggleCallback) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(theme.thinkingHeaderBackground());
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Title bar sits directly on wrapper so it spans full width (no left border indent)
        if (title != null && !title.isEmpty()) {
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setOpaque(true);
            headerPanel.setBackground(theme.base2());
            int leftPad = isThought ? 0 : 4;
            headerPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, theme.isDark() ? theme.base1() : theme.bubbleBorder()),
                    BorderFactory.createEmptyBorder(5, leftPad, 5, 0)));

            Icon icon = isThought
                    ? ThemeManager.getIcon("brain.svg", 16)
                    : ThemeManager.getIcon("tool.svg", 16);

            String displayTitle = formatInnerTitle(title);
            JLabel titleLabel = new JLabel(displayTitle, icon, JLabel.LEFT);
            titleLabel.setIconTextGap(8);
            titleLabel.setFont(ThemeManager.getFont().deriveFont(Font.BOLD));
            titleLabel.setForeground(theme.foreground());
            // Make segment header clickable to toggle the entire activity pane
            if (toggleCallback != null) {
                titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                titleLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        toggleCallback.run();
                    }
                });
            }
            headerPanel.add(titleLabel, BorderLayout.CENTER);
            wrapper.add(headerPanel, BorderLayout.NORTH);
        }

        // Body panel wraps the rendered content with a left accent border.
        // Skip entirely for empty text (e.g. "read" segments with content suppressed).
        if (text != null && !text.trim().isEmpty()) {
            JPanel bodyPanel = new JPanel(new BorderLayout());
            bodyPanel.setOpaque(false);
            bodyPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0,
                    isThought ? theme.yellow() : theme.accent()));

            JEditorPane editorPane = createSegmentHtmlPane(text, theme);
            bodyPanel.add(editorPane, BorderLayout.CENTER);
            wrapper.add(bodyPanel, BorderLayout.CENTER);
        }
        return wrapper;
    }

    /**
     * Creates a JEditorPane that renders markdown as styled HTML for inner
     * segments in the combined view. Uses Flexmark directly for conversion
     * and places CSS in the &lt;head&gt;. Overrides getPreferredSize() to fix the
     * height-clipping bug by computing height from the root view at the
     * constrained width.
     */
    private static JEditorPane createSegmentHtmlPane(String markdown, ColorTheme theme) {
        MutableDataSet options = new MutableDataSet();
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        String bodyHtml = renderer.render(parser.parse(markdown));
        // Insert zero-width spaces after common break characters to enable
        // wrapping of long words (URLs, paths, identifiers) that Swing's
        // HTMLEditorKit would otherwise overflow. Skip HTML tags.
        bodyHtml = bodyHtml.replaceAll("(?<=[/_.])(?![^<]*>)", "&#8203;");

        String fontFamily = ThemeManager.getFont().getFamily();
        int fontSize = ThemeManager.getFont().getSize() + 1;
        String fg = theme.toHtmlHex(theme.foreground());
        String tableBg = theme.toHtmlHex(theme.tableBackground());
        String headerBg = theme.toHtmlHex(theme.tableHeaderBackground());
        String borderColor = theme.toHtmlHex(theme.tableBorder());

        String html = "<html><head><style>"
                + "body{margin:0;padding:0;text-align:left;width:100%;"
                + "font-family:'Segoe UI','Noto Sans','Ubuntu','Helvetica Neue','Arial',"
                + "'Apple Color Emoji','Segoe UI Emoji','Segoe UI Symbol','Noto Color Emoji',sans-serif;"
                + "font-size:" + fontSize + "pt;color:" + fg + ";}"
                + "pre{white-space:pre-wrap;font-family:monospace;font-size:100%;"
                + "background:rgba(0,0,0,0.05);padding:8px;border-radius:3px;}"
                + "code{font-family:monospace;font-size:100%;}"
                + "table{border-collapse:collapse;table-layout:fixed;width:100%;margin:8px 0;background:" + tableBg + ";}"
                + "th{padding:8px;border:1px solid " + borderColor + ";text-align:left;font-weight:bold;"
                + "background:" + headerBg + ";}"
                + "td{padding:8px;border:1px solid " + borderColor + ";vertical-align:top;overflow-wrap:break-word;}"
                + "p{margin:4px 0;text-align:left !important;}"
                + "h1,h2,h3,h4,h5,h6{margin:8px 0;font-weight:bold;text-align:left;}"
                + "h1{font-size:140%;}h2{font-size:130%;}h3{font-size:120%;}"
                + "strong{font-weight:bold;}"
                + "</style></head><body>"
                + bodyHtml
                + "</body></html>";

        JEditorPane pane = new JEditorPane() {
            private int lastW = 0;

            @Override
            public java.awt.Dimension getPreferredSize() {
                java.awt.Insets ins = getInsets();
                int w = getWidth();
                if (w <= 0) {
                    java.awt.Component p = getParent();
                    while (p != null) {
                        if (p.getWidth() > 0) {
                            w = p.getWidth();
                            break;
                        }
                        p = p.getParent();
                    }
                }
                if (w <= 0) {
                    w = 500;
                }
                View root = getUI().getRootView(this);
                if (root != null) {
                    int cw = Math.max(1, w - ins.left - ins.right);
                    root.setSize(cw, Integer.MAX_VALUE);
                    float h = root.getPreferredSpan(View.Y_AXIS);
                    return new java.awt.Dimension(w, (int) Math.ceil(h) + ins.top + ins.bottom + 20);
                }
                return super.getPreferredSize();
            }

            @Override
            public void setBounds(int x, int y, int w, int h) {
                boolean changed = w > 0 && w != lastW;
                super.setBounds(x, y, w, h);
                if (changed) {
                    lastW = w;
                    View root = getUI().getRootView(this);
                    if (root != null) {
                        java.awt.Insets ins = getInsets();
                        int cw = Math.max(1, w - ins.left - ins.right);
                        root.setSize(cw, Integer.MAX_VALUE);
                    }
                    javax.swing.SwingUtilities.invokeLater(this::revalidate);
                }
            }

            @Override
            public java.awt.Dimension getMaximumSize() {
                java.awt.Dimension pref = getPreferredSize();
                return new java.awt.Dimension(Short.MAX_VALUE, pref.height);
            }
        };
        pane.setContentType("text/html");
        pane.setText(html);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBackground(null);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 6));
        pane.setFont(ThemeManager.getFont());
        return pane;
    }

    private static String formatInnerTitle(String rawTitle) {
        if (rawTitle.toUpperCase().contains("THINKING")) {
            return NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_ThinkingProcess");
        }

        String stripped = rawTitle.replaceFirst("(?i)TOOL:?\\s*", "").trim();

        if (stripped.isEmpty() || "Tool".equalsIgnoreCase(stripped) || "Tool Call".equalsIgnoreCase(stripped)) {
            return NbBundle.getMessage(CollapsibleActivityPane.class, "LBL_ToolFallback");
        }

        int pos = stripped.indexOf(' ');
        String firstWord = (pos > 1) ? stripped.substring(0, pos) : null;
        String tag = firstWord != null ? translateTag(firstWord) : null;
        String param = (pos > 1) ? stripped.substring(pos + 1) : null;

        if (tag != null && isNotBlank(param)) {
            return tag + " " + param;
        } else if (tag != null) {
            return tag;
        } else {
            return stripped;
        }
    }

    private static JTextArea createActivityTextArea(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setBackground(null);
        textArea.setFont(ThemeManager.getFont());
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 6));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        return textArea;
    }
}
