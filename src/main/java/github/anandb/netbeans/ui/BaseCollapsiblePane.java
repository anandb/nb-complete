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
    protected boolean isThinking;
    protected boolean isSegmented;
    protected String combinedPlainText = "";
    private transient Timer copyFeedbackTimer;
    private transient AccordionGroup accordionGroup;
    protected final AtomicBoolean copyHovered = new AtomicBoolean(false);

    public BaseCollapsiblePane(int radius, String title, Color defaultAccent, boolean expandedByDefault) {
        super(radius);
        this.expanded = expandedByDefault;
        this.defaultAccent = defaultAccent;
        this.isThinking = title.toUpperCase().contains("THINKING");

        setLayout(new BorderLayout());
        setOpaque(false);
        setDoubleBuffered(true);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        ColorTheme theme = ThemeManager.getCurrentTheme();

        // Header setup
        header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, defaultAccent),
            BorderFactory.createEmptyBorder(5, 4, 5, 10)
        ));

        headerLabel = new JLabel("");
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

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
        MouseAdapter toggleListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!expanded && accordionGroup != null) {
                    LOG.info("Collapsible: click on collapsed pane with accordion group, delegating to accordion");
                    accordionGroup.expandPane(BaseCollapsiblePane.this);
                } else {
                    if (accordionGroup == null) {
                        LOG.info("Collapsible: click on pane (expanded={0}), accordionGroup=null, toggling directly", expanded);
                    } else {
                        LOG.info("Collapsible: click on expanded pane with accordionGroup, toggling to collapse");
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
        groupToggleBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        groupToggleBtn.setToolTipText(NbBundle.getMessage(BaseCollapsiblePane.class, "HINT_CollapseExpandAll"));
        groupToggleBtn.setVisible(false);
        groupToggleBtn.addActionListener(e -> {
            AccordionGroup g = getAccordionGroup();
            if (g != null) g.toggleAll();
        });
        header.add(groupToggleBtn, BorderLayout.WEST);

        setupTitleLabels(title);
        updateAppearance();
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
        groupToggleBtn.setText(allExpanded ? "-" : "+");
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

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }

    protected void updateParentLayout() {
        Component parent = getParent();
        while (parent != null) {
            parent.revalidate();
            parent.repaint();
            if (parent instanceof ChatThreadPanel) {
                break;
            }
            parent = parent.getParent();
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

    protected void setupTitleLabels(String rawTitle) {
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

        String stripped = rawTitle.replaceFirst("(?i)TOOL:?\\s*", "").trim();

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
        if (avgCharWidth <= 0) avgCharWidth = fm.stringWidth("W");
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

    protected void updateAppearance() {
        ColorTheme theme = ThemeManager.getCurrentTheme();
        header.setBackground(expanded ? theme.base2() : getDefaultHeaderBackground());
        contentPanel.setBackground(null);
        Color fg = getHeaderForeground(theme);
        headerLabel.setForeground(fg);
        if (paramLabel != null) {
            paramLabel.setForeground(fg);
        }
        copyButton.setForeground(fg);
    }

    protected Color getHeaderForeground(ColorTheme theme) {
        return expanded ? theme.foreground() : new Color(96, 96, 96);
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
        super.removeNotify();
        if (copyFeedbackTimer != null && copyFeedbackTimer.isRunning()) {
            copyFeedbackTimer.stop();
        }
    }
}
