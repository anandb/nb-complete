package github.anandb.netbeans.ui.chat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.openide.util.NbBundle;
import org.apache.commons.lang3.StringUtils;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.pattern.ToolPrefixPattern;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.components.RoundedPanel;
import github.anandb.netbeans.ui.components.AccordionGroup;
import github.anandb.netbeans.ui.components.UIUtils;

/**
 * Abstract base class for collapsible UI panes (such as tool, activity, and code panes).
 * Centralizes common GUI layout elements including headers, titles, accordion toggles,
 * copy buttons, and segmented panel builders to eliminate code duplication and maintain visual consistency.
 */
// DSL-LEAF: keep imperative, wrap via UI.of(...) — extends JPanel with custom
// expand/collapse animation, title row, param/output sub-panels. Migration
// target: CollapsibleHeaderSpec + CollapsibleBodySpec; the toggle timer stays imperative.
public abstract class BaseCollapsiblePane extends RoundedPanel {
    private static final Logger LOG = Logger.from(BaseCollapsiblePane.class);
    private static final long serialVersionUID = 1L;

    protected final JPanel header;
    protected final JLabel headerLabel;
    protected final JPanel contentPanel;
    protected boolean expanded;

    protected final JPanel titlePanel;
    protected final JButton copyButton;
    protected final JButton groupToggleBtn;
    protected final Color defaultAccent;
    protected JLabel paramLabel;
    /**
     * The title's parameter as given, before any width elision. The header re-elides this whenever
     * the pane resizes, so a wider sidebar shows more of the path instead of keeping the
     * abbreviation it was built with.
     */
    private String fullParam;
    /** The whole header text for a pane whose title carries no tag. */
    private String fullHeaderText;
    protected boolean isThinking;
    /** Cached appearance state to avoid re-setting colors on every hover. */
    private Color lastHeaderBg;
    private Color lastFg;
    private Color lastBorderColor;
    private boolean lastExpanded;
    private boolean lastHadParamLabel;

    /** Outline shift while expanded: the theme border washes out against the expanded surfaces. */
    private static final double EXPANDED_BORDER_SHIFT = 0.30;
    private transient Timer copyFeedbackTimer;
    private transient AccordionGroup accordionGroup;
    protected final AtomicBoolean copyHovered = new AtomicBoolean(false);
    /** Listener references for clean removal in removeNotify(). */
    private final transient MouseAdapter toggleListener;
    protected final transient MouseAdapter copyButtonHoverListener;
    private transient MouseAdapter paramLabelToggleListener;

    public BaseCollapsiblePane(int radius, String title, Color defaultAccent, boolean expandedByDefault) {
        super(radius);
        this.expanded = expandedByDefault;
        this.defaultAccent = defaultAccent;
        this.isThinking = title.toUpperCase().contains("THINKING");

        setLayout(new BorderLayout());
        setOpaque(false);
        setDoubleBuffered(true);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setAlignmentY(Component.CENTER_ALIGNMENT);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        // Header setup
        header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, defaultAccent),
            BorderFactory.createEmptyBorder(5, 4, 5, 10)
        ));
        header.getAccessibleContext().setAccessibleName("Collapsible pane header");
        header.getAccessibleContext().setAccessibleDescription("Click to expand or collapse");

        headerLabel = new JLabel("");
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        headerLabel.getAccessibleContext().setAccessibleName("Pane title");
        headerLabel.getAccessibleContext().setAccessibleDescription("Click to expand or collapse this pane");

        // BorderLayout, not FlowLayout: FlowLayout wraps the parameter onto a second row when it
        // does not fit, and this panel keeps one row's height, so that row painted as a clipped
        // sliver above the card border. CENTER just narrows the parameter instead.
        titlePanel = new JPanel(new BorderLayout()) {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }

            @Override
            public void doLayout() {
                super.doLayout();
                applyTitleElision();
            }
        };
        titlePanel.setOpaque(false);
        header.add(titlePanel, BorderLayout.CENTER);

        // Content setup
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(true);
        contentPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, defaultAccent));
        contentPanel.setVisible(expanded);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);

        // Toggle listener
        toggleListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!expanded && accordionGroup != null) {
                    LOG.info("Collapsible: click on collapsed pane with accordion group, delegating to accordion");
                    accordionGroup.expandPane(BaseCollapsiblePane.this);
                } else {
                    if (accordionGroup == null) {
                        LOG.fine("Collapsible: click on pane (expanded={0}), accordionGroup=null, toggling directly", expanded);
                    } else {
                        LOG.fine("Collapsible: click on expanded pane with accordionGroup, toggling to collapse");
                    }
                    toggle();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                onHeaderHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                onHeaderHover(false);
            }
        };
        header.addMouseListener(toggleListener);

        // Copy button — visible only on header hover
        String hint = NbBundle.getMessage(UIBundle.class, "HINT_CopyContent");
        copyButton = UIUtils.createToolbarButton("copy.svg", 20, hint, e -> copyContentToClipboard());
        copyButton.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        copyButton.setForeground(theme.foreground());
        copyButton.setVisible(false);
        copyButtonHoverListener = new MouseAdapter() {
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
        };
        copyButton.addMouseListener(copyButtonHoverListener);

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
        groupToggleBtn = new JButton(ThemeManager.getIcon("expand.svg", 14));
        groupToggleBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        groupToggleBtn.setContentAreaFilled(false);
        groupToggleBtn.setFocusPainted(false);
        groupToggleBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        groupToggleBtn.setToolTipText(NbBundle.getMessage(UIBundle.class, "HINT_CollapseExpandAll"));
        groupToggleBtn.getAccessibleContext().setAccessibleName("Collapse or expand all panes");
        groupToggleBtn.getAccessibleContext().setAccessibleDescription(
                NbBundle.getMessage(UIBundle.class, "HINT_CollapseExpandAll"));
        groupToggleBtn.setVisible(false);
        groupToggleBtn.addActionListener(e -> {
            AccordionGroup g = getAccordionGroup();
            if (g != null) g.toggleAll();
        });
        header.add(groupToggleBtn, BorderLayout.WEST);

        setupTitleLabels(title);
    }

    public AccordionGroup getAccordionGroup() {
        return accordionGroup;
    }

    public void setAccordionGroup(AccordionGroup group) {
        this.accordionGroup = group;
        if (group != null) {
            group.register(this);
        }
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
        groupToggleBtn.setIcon(ThemeManager.getIcon(allExpanded ? "collapse.svg" : "expand.svg", 14));
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            contentPanel.setVisible(expanded);
            onToggle(expanded);
            revalidate();
            repaint();
            if (!inBatch) {
                updateParentLayout();
            }
        }
    }

    private static volatile boolean inBatch = false;

    public static void setBatchMode(boolean batch) {
        inBatch = batch;
    }

    private Dimension cachedPreferredSize;
    private Dimension cachedMaxSize;

    /**
     * A layout pass asks for the preferred AND maximum size of every child, and
     * a wrapped text body recomputes its height from scratch on each query. The
     * cached values are discarded by {@link #invalidate()}, which AWT also
     * propagates from any child whose content or width changes.
     */
    @Override
    public void invalidate() {
        cachedPreferredSize = null;
        cachedMaxSize = null;
        super.invalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension cached = cachedPreferredSize;
        if (cached == null) {
            cached = super.getPreferredSize();
            cachedPreferredSize = cached;
        }
        return cached;
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension cached = cachedMaxSize;
        if (cached == null) {
            cached = new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            cachedMaxSize = cached;
        }
        return cached;
    }

    public void updateParentLayout() {
        // Walk to the topmost relevant ancestor and revalidate only that.
        // Revalidating every ancestor causes redundant layout passes.
        Component parent = getParent();
        Component top = this;
        while (parent != null) {
            top = parent;
            if (parent instanceof ChatThreadPanel) {
                break;
            }
            parent = parent.getParent();
        }
        top.revalidate();
        top.repaint();

        // The pane changes height inside a scroll pane. The repaint above still
        // targets the pre-layout bounds, and a BLIT viewport then leaves the
        // newly exposed strip blank until a scroll forces a full repaint.
        // Repaint the viewport after the queued validate has resized the view.
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (viewport != null) {
            SwingUtilities.invokeLater(viewport::repaint);
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    protected void toggle() {
        setExpanded(!expanded);
    }

    protected void onToggle(boolean expanded) {
        if (isThinking) {
            headerLabel.setText(CollapsibleHeaderRenderer.thinkingLabel(expanded));
        }
        updateAppearance();
        updateGroupToggleIcon();
    }

    protected void onHeaderHover(boolean hover) {
        updateAppearance();
        copyButton.setVisible(hover || copyHovered.get());
    }

    protected Color getDefaultHeaderBackground() {
        return ThemeManager.getCurrentTheme().panelHeader();
    }

    protected final void setupTitleLabels(String rawTitle) {
        titlePanel.removeAll();
        fullParam = null;
        fullHeaderText = null;
        Icon icon = getHeaderIcon(rawTitle);

        headerLabel.setIcon(icon);
        headerLabel.setIconTextGap(8);
        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f));
        titlePanel.add(headerLabel, BorderLayout.WEST);

        if (isThinking) {
            headerLabel.setText(CollapsibleHeaderRenderer.thinkingLabel(expanded));
            return;
        }

        String stripped = ToolPrefixPattern.stripPrefix(rawTitle);

        if (stripped.isEmpty() || "Tool".equalsIgnoreCase(stripped) || "Tool Call".equalsIgnoreCase(stripped)) {
            headerLabel.setText(CollapsibleHeaderRenderer.formatTitle(rawTitle));
            return;
        }

        int pos = stripped.indexOf(' ');
        String firstWord = (pos > 1) ? stripped.substring(0, pos) : null;
        String tag = firstWord != null ? CollapsibleHeaderRenderer.translateTag(firstWord) : null;

        if (tag != null) {
            headerLabel.setText(tag);
            String param = stripped.substring(pos + 1);
            if (StringUtils.isNotBlank(param)) {
                if (paramLabel == null) {
                    paramLabel = new JLabel() {
                        @Override
                        public boolean contains(int x, int y) {
                            return false;
                        }
                    };
                    paramLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN));
                    paramLabelToggleListener = new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            toggle();
                        }
                    };
                    paramLabel.addMouseListener(paramLabelToggleListener);
                }
                fullParam = param;
                paramLabel.setText(" " + param);
                paramLabel.setToolTipText(null);
                titlePanel.add(paramLabel, BorderLayout.CENTER);
            }
        } else {
            fullHeaderText = stripped;
            headerLabel.setText(stripped);
        }
    }

    public void setTitle(String title) {
        this.isThinking = title.toUpperCase().contains("THINKING");
        setupTitleLabels(title);
        applyTitleElision();
    }

    /**
     * Fits the title to the width the header actually got. {@code setupTitleLabels} runs before the
     * pane is laid out, so the first pass keeps the title whole and a resize re-runs this with the
     * real width — a wide sidebar shows the full path instead of the abbreviation it was built with.
     */
    private void applyTitleElision() {
        if (isThinking || titlePanel == null || titlePanel.getWidth() <= 0) {
            return;
        }
        if (paramLabel != null && fullParam != null) {
            FontMetrics fm = paramLabel.getFontMetrics(paramLabel.getFont());
            int budget = titlePanel.getWidth() - headerLabel.getPreferredSize().width - 8;
            String shown = CollapsibleHeaderRenderer.elideMiddle(fullParam, fm, budget);
            if (!shown.equals(fullParam)) {
                paramLabel.setToolTipText(fullParam);
            }
            if (!(" " + shown).equals(paramLabel.getText())) {
                paramLabel.setText(" " + shown);
            }
        } else if (fullHeaderText != null) {
            FontMetrics fm = headerLabel.getFontMetrics(headerLabel.getFont());
            String shown = CollapsibleHeaderRenderer.elideMiddle(
                    fullHeaderText, fm, titlePanel.getWidth() - 8);
            if (!shown.equals(fullHeaderText)) {
                headerLabel.setToolTipText(fullHeaderText);
            }
            if (!shown.equals(headerLabel.getText())) {
                headerLabel.setText(shown);
            }
        }
    }


    protected void copyContentToClipboard() {
        String content = getContentToCopy();
        if (content == null || content.isEmpty()) {
            return;
        }
        StringSelection selection = new StringSelection(content);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

        Icon originalIcon = copyButton.getIcon();
        Icon checkIcon = ThemeManager.getIcon("check.svg", 20);
        copyButton.setIcon(checkIcon);

        if (copyFeedbackTimer != null && copyFeedbackTimer.isRunning()) {
            copyFeedbackTimer.stop();
        }
        copyFeedbackTimer = new Timer(2000, e -> {
            copyButton.setIcon(originalIcon);
        });
        copyFeedbackTimer.setRepeats(false);
        copyFeedbackTimer.start();
    }

    protected final void updateBaseAppearance() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        applyPaneBorder(theme);
        Color newBg = expanded ? theme.base2() : getDefaultHeaderBackground();
        Color newFg = getHeaderForeground(theme);
        boolean hasParamLabel = paramLabel != null;

        if (Objects.equals(newBg, lastHeaderBg) && Objects.equals(newFg, lastFg)
                && expanded == lastExpanded && hasParamLabel == lastHadParamLabel) {
            return;
        }

        lastHeaderBg = newBg;
        lastFg = newFg;
        lastExpanded = expanded;
        lastHadParamLabel = hasParamLabel;

        header.setBackground(newBg);
        contentPanel.setBackground(theme.sunkenBackground());
        headerLabel.setForeground(newFg);
        if (paramLabel != null) {
            paramLabel.setForeground(newFg);
        }
        copyButton.setForeground(newFg);
    }

    /**
     * Applies the pane's own rounded outline. The theme border is tuned for the collapsed one-line
     * header; once expanded the card spans the header and the body, where a 1px {@code bubbleBorder}
     * all but disappears, so the expanded outline is shifted 30% toward black (light themes) or
     * white (dark themes) to keep all four sides reading as a border.
     */
    private void applyPaneBorder(ColorTheme theme) {
        Color border = resolvePaneBorder(theme);
        if (Objects.equals(border, lastBorderColor)) {
            return;
        }
        lastBorderColor = border;
        setBorderColor(border);
        repaint();
    }

    /**
     * The pane outline for the current state; {@code null} keeps the theme's default border.
     * Darker themes lighten the border, light themes darken it.
     */
    protected Color resolvePaneBorder(ColorTheme theme) {
        Color base = theme.bubbleBorder();
        if (!expanded || base == null) {
            return base;
        }
        return blend(base, theme.isDark() ? Color.WHITE : Color.BLACK, EXPANDED_BORDER_SHIFT);
    }

    private static Color blend(Color from, Color to, double ratio) {
        double r = Math.max(0, Math.min(1, ratio));
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * r),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * r),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * r));
    }

    protected abstract void updateAppearance();

    protected Color getHeaderForeground(ColorTheme theme) {
        return expanded ? theme.foreground() : theme.collapsedHeaderForeground();
    }

    protected Icon getHeaderIcon(String title) {
        return CollapsibleHeaderRenderer.getHeaderIcon(title);
    }

    protected abstract Icon getDefaultIcon();

    protected abstract String getContentToCopy();

    public abstract void setContent(String content);

    public abstract void appendContent(String text);

    protected static String translateTag(String tag) {
        return CollapsibleHeaderRenderer.translateTag(tag);
    }



    /**
     * Header label that keeps its full text and fits it to the width it is given. The elision runs
     * from the containers' layout passes ({@link CollapsibleHeaderRenderer#elideMiddle}), never
     * from a resize listener: a listener fires before the label has a width, so the first guess
     * sticks and the tail is clipped.
     */
    static final class ElidingHeaderLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        private final String fullText;

        ElidingHeaderLabel(String text, Icon icon) {
            super(text, icon, LEFT);
            this.fullText = text;
        }

        /** Fits the text to the current width; no-op until the label has one. */
        void fitText() {
            int iconRoom = getIcon() == null ? 0 : getIcon().getIconWidth() + getIconTextGap();
            String shown = CollapsibleHeaderRenderer.elideMiddle(
                    fullText, getFontMetrics(getFont()), getWidth() - iconRoom);
            if (!shown.equals(getText())) {
                setText(shown);
                setToolTipText(shown.equals(fullText) ? null : fullText);
            }
        }
    }

    protected static JTextArea createActivityTextArea(String text) {
        JTextArea textArea = new JTextArea(text) {
            @Override
            public JPopupMenu getComponentPopupMenu() {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem copyItem = new JMenuItem(NbBundle.getMessage(UIBundle.class, "LBL_Copy"));
                copyItem.setEnabled(getSelectedText() != null);
                copyItem.addActionListener(e -> {
                    String sel = getSelectedText();
                    if (sel != null && !sel.isEmpty()) {
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(sel), null);
                    }
                });
                menu.add(copyItem);
                return menu;
            }
        };
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setBackground(null);
        textArea.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f));
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 6));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        return textArea;
    }

    /**
     * Replaces the content area with one segment pane per block and returns the plain text
     * joined by blank lines. The caller supplies the body component for a segment.
     *
     * @param toggleSegments          make each segment header toggle this pane
     * @param hideHeaderWhenExpanded  hide the pane header while it is expanded
     */




    @Override
    public void addNotify() {
        super.addNotify();
        // removeNotify drops the listener when a pane is reparented into a
        // composite bubble; re-arm it or the parameter stops toggling.
        if (paramLabelToggleListener != null && paramLabel != null) {
            paramLabel.removeMouseListener(paramLabelToggleListener);
            paramLabel.addMouseListener(paramLabelToggleListener);
        }
    }

    @Override
    public void removeNotify() {
        // Stop all timers before super.removeNotify() to prevent callbacks
        // from firing on a partially-dismantled component tree.
        if (copyFeedbackTimer != null && copyFeedbackTimer.isRunning()) {
            copyFeedbackTimer.stop();
        }
        if (paramLabelToggleListener != null && paramLabel != null) {
            paramLabel.removeMouseListener(paramLabelToggleListener);
        }
        super.removeNotify();
    }
}
