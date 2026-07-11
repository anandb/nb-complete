package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.openide.util.NbBundle;
import org.apache.commons.lang3.StringUtils;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ToolDataExtractor;

/**
 * Abstract base class for collapsible UI panes (such as tool, activity, and code panes).
 * Centralizes common GUI layout elements including headers, titles, accordion toggles,
 * copy buttons, and segmented panel builders to eliminate code duplication and maintain visual consistency.
 */
// DSL-LEAF: keep imperative, wrap via UI.of(...) — extends JPanel with custom
// expand/collapse animation, title row, param/output sub-panels. Migration
// target: CollapsibleHeaderSpec + CollapsibleBodySpec; the toggle timer stays imperative.
public abstract class BaseCollapsiblePane extends RoundedPanel {
    private static final Pattern TOOL_PREFIX = Pattern.compile("(?i)TOOL:?\\s*");
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
    protected boolean isThinking;
    protected boolean isSegmented;
    protected String combinedPlainText = "";
    /** Cached appearance state to avoid re-setting colors on every hover. */
    private Color lastHeaderBg;
    private Color lastFg;
    private boolean lastExpanded;
    private boolean lastHadParamLabel;
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

        titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override
            public boolean contains(int x, int y) {
                return false;
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
        String hint = NbBundle.getMessage(BaseCollapsiblePane.class, "HINT_CopyContent");
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
        groupToggleBtn.setToolTipText(NbBundle.getMessage(BaseCollapsiblePane.class, "HINT_CollapseExpandAll"));
        groupToggleBtn.getAccessibleContext().setAccessibleName("Collapse or expand all panes");
        groupToggleBtn.getAccessibleContext().setAccessibleDescription(
                NbBundle.getMessage(BaseCollapsiblePane.class, "HINT_CollapseExpandAll"));
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

    public static boolean isBatchMode() {
        return inBatch;
    }

    private Dimension cachedMaxSize;

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        if (cachedMaxSize == null || cachedMaxSize.height != pref.height) {
            cachedMaxSize = new Dimension(Integer.MAX_VALUE, pref.height);
        }
        return cachedMaxSize;
    }

    protected void updateParentLayout() {
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
        Icon icon = getHeaderIcon(rawTitle);

        headerLabel.setIcon(icon);
        headerLabel.setIconTextGap(8);
        headerLabel.setFont(ThemeManager.getFont().deriveFont(Font.PLAIN, ThemeManager.getFont().getSize() - 1f));
        titlePanel.add(headerLabel);

        if (isThinking) {
            headerLabel.setText(CollapsibleHeaderRenderer.thinkingLabel(expanded));
            return;
        }

        String stripped = TOOL_PREFIX.matcher(rawTitle).replaceFirst("").trim();

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
                    paramLabel.setFont(isSegmented
                            ? ThemeManager.getMonospaceFont().deriveFont(Font.PLAIN)
                            : ThemeManager.getFont().deriveFont(Font.PLAIN));
                    paramLabelToggleListener = new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            toggle();
                        }
                    };
                    paramLabel.addMouseListener(paramLabelToggleListener);
                }
                paramLabel.setText(" " + param);
                titlePanel.add(paramLabel);
            }
        } else {
            headerLabel.setText(stripped);
        }
    }

    public void setTitle(String title) {
        this.isThinking = title.toUpperCase().contains("THINKING");
        setupTitleLabels(title);
        updateMaxTitleLength();
    }

    protected void updateMaxTitleLength() {
        int w = getWidth();
        if (w <= 0) return;
        FontMetrics fm = getFontMetrics(ThemeManager.getFont());
        int avgCharWidth = fm.stringWidth("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz") / 52;
        if (avgCharWidth <= 0) avgCharWidth = fm.stringWidth("M");
        int usable = Math.max(50, w - 90);
        int chars = usable / avgCharWidth;
        ToolDataExtractor.setMaxTitleLength(chars);
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
        Color newBg = expanded ? theme.base2() : getDefaultHeaderBackground();
        Color newFg = getHeaderForeground(theme);
        boolean hasParamLabel = paramLabel != null;

        if (java.util.Objects.equals(newBg, lastHeaderBg) && java.util.Objects.equals(newFg, lastFg)
                && expanded == lastExpanded && hasParamLabel == lastHadParamLabel) {
            return;
        }

        lastHeaderBg = newBg;
        lastFg = newFg;
        lastExpanded = expanded;
        lastHadParamLabel = hasParamLabel;

        header.setBackground(newBg);
        contentPanel.setBackground(null);
        headerLabel.setForeground(newFg);
        if (paramLabel != null) {
            paramLabel.setForeground(newFg);
        }
        copyButton.setForeground(newFg);
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

    protected static String formatInnerTitle(String rawTitle) {
        return CollapsibleHeaderRenderer.formatTitle(rawTitle);
    }

    protected static JTextArea createActivityTextArea(String text) {
        JTextArea textArea = new JTextArea(text) {
            @Override
            public JPopupMenu getComponentPopupMenu() {
                JPopupMenu menu = new JPopupMenu();
                JMenuItem copyItem = new JMenuItem(NbBundle.getMessage(BaseCollapsiblePane.class, "LBL_Copy"));
                copyItem.setEnabled(getSelectedText() != null);
                copyItem.addActionListener(e -> {
                    String sel = getSelectedText();
                    if (sel != null && !sel.isEmpty()) {
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
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
        textArea.setFont(ThemeManager.getFont().deriveFont(ThemeManager.getFont().getSize() - 1f));
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 6));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        return textArea;
    }

    protected static JPanel createSegmentPane(String text, boolean isThought, String title,
            ColorTheme theme, Component bodyComponent, Runnable toggleCallback) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBackground(null);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setAlignmentY(Component.CENTER_ALIGNMENT);

        if (title != null && !title.isEmpty()) {
            // Title bar sits directly on wrapper so it spans full width (no left border indent)
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
            if (toggleCallback != null) {
                // Make segment header clickable to toggle the entire activity pane
                titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                titleLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        toggleCallback.run();
                    }
                });
            }
            headerPanel.add(titleLabel, BorderLayout.CENTER);

            // Copy button for this segment — visible on header hover.
            final String segmentText = text != null ? text : "";
            final JButton[] segCopyBtnRef = new JButton[1];
            Timer[] segRevertTimer = new Timer[1];
            segCopyBtnRef[0] = UIUtils.createToolbarButton("copy.svg", 20,
                    NbBundle.getMessage(BaseCollapsiblePane.class, "HINT_CopyContent"),
                    e -> {
                        java.awt.datatransfer.StringSelection sel =
                                new java.awt.datatransfer.StringSelection(segmentText);
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(sel, sel);
                        JButton btn = segCopyBtnRef[0];
                        Icon orig = btn.getIcon();
                        btn.setIcon(ThemeManager.getIcon("check.svg", 14));
                        if (segRevertTimer[0] != null) {
                            segRevertTimer[0].stop();
                        }
                        segRevertTimer[0] = new Timer(2000, ev -> btn.setIcon(orig));
                        segRevertTimer[0].setRepeats(false);
                        segRevertTimer[0].start();
                    });
            JButton segCopyBtn = segCopyBtnRef[0];
            segCopyBtn.setBorder(BorderFactory.createEmptyBorder());
            segCopyBtn.setContentAreaFilled(false);
            segCopyBtn.setOpaque(false);
            segCopyBtn.setForeground(theme.foreground());
            segCopyBtn.setVisible(false);

            // Reserve space so layout doesn't shift.
            JPanel segCopyPlaceholder = new JPanel(new BorderLayout());
            segCopyPlaceholder.setOpaque(false);
            Dimension btnSz = segCopyBtn.getPreferredSize();
            segCopyPlaceholder.setPreferredSize(btnSz);
            segCopyPlaceholder.setMinimumSize(btnSz);
            segCopyPlaceholder.setMaximumSize(btnSz);
            segCopyPlaceholder.add(segCopyBtn, BorderLayout.CENTER);

            headerPanel.add(segCopyPlaceholder, BorderLayout.EAST);
            headerPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    segCopyBtn.setVisible(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    // Only hide if the mouse actually left the header bounds,
                    // not when moving between header children (e.g. title → button).
                    if (!headerPanel.contains(e.getPoint())) {
                        segCopyBtn.setVisible(false);
                    }
                }
            });

            wrapper.add(headerPanel, BorderLayout.NORTH);
        }

        if (bodyComponent != null) {
            // Body panel wraps the rendered content with a left accent border.
            // Skip entirely for empty text (e.g. "read" segments with content suppressed).
            JPanel bodyPanel = new JPanel(new BorderLayout());
            bodyPanel.setOpaque(false);
            bodyPanel.setBorder(BorderFactory.createMatteBorder(0, 4, 0, 0,
                    isThought ? theme.yellow() : theme.accent()));
            bodyPanel.add(bodyComponent, BorderLayout.CENTER);
            wrapper.add(bodyPanel, BorderLayout.CENTER);
        }
        return wrapper;
    }

    @Override
    public void removeNotify() {
        // Stop timer before super.removeNotify() to prevent callbacks
        // from firing on a partially-dismantled component tree.
        if (copyFeedbackTimer != null && copyFeedbackTimer.isRunning()) {
            copyFeedbackTimer.stop();
        }
        super.removeNotify();
    }
}
