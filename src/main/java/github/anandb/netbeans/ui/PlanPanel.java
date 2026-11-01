package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import github.anandb.netbeans.model.ModelRecords.PlanEntry;
import github.anandb.netbeans.support.Logger;
import org.openide.util.NbBundle;

/**
 * Live plan slideout docked above the message thread. Takes its height from
 * the thread viewport: hidden when there is no plan, a one-row header when
 * collapsed (showing the next actionable entry plus the done/total count),
 * the full entry list when expanded. Every {@code plan} session update
 * replaces the entries wholesale.
 *
 * <p>Status is rendered with SVG icons ({@code plan-*.svg}, dark variants
 * selected by {@link ThemeManager}) instead of Unicode glyphs, which render
 * as tofu boxes when the active font lacks the code points. The expanded list
 * shows at most {@value #MAX_VISIBLE_ROWS} rows; the rest scroll in the
 * embedded scroll pane.</p>
 *
 * <p>EDT-only: {@link #setEntries} and {@link #clear} must be called on the
 * Event Dispatch Thread (the session-update pipeline marshals via
 * {@code invokeLater} before reaching this panel).</p>
 */
public class PlanPanel extends JPanel {
    private static final Logger LOG = Logger.from(PlanPanel.class);
    private static final long serialVersionUID = 1L;

    /** Cap on the expanded list height in rows; the scrollpane absorbs the rest. */
    private static final int MAX_VISIBLE_ROWS = 8;
    /** Size of the status icons, in pixels. */
    private static final int ICON_SIZE = 12;
    /** Vertical + horizontal padding inside the panel. */
    private static final int PAD = 6;
    /** Trailing padding that keeps the vertical scrollbar off the row text. */
    private static final int SCROLL_INSET_PX = 8;
    /** Vertical gap between rows in the expanded list. */
    static final int ROW_GAP = 6;

    private final JLabel chevron = new JLabel();
    private final JLabel badgeIcon = new JLabel();
    private final EllipsisLabel headerLabel = new EllipsisLabel();
    private final MiniProgressBar progressBar = new MiniProgressBar();
    private final JPanel rowsPanel = new JPanel();
    private final JScrollPane rowsScroll;
    private final JButton closeButton;
    /** Current entries, kept so expanding/collapsing re-renders from them. */
    private List<PlanEntry> entries = List.of();
    /**
     * Plans the user closed, keyed by session id. The value is the step signature
     * (contents and priorities, not statuses), so a reload replay or a status-only
     * update stays hidden while a genuinely new plan reopens the slideout.
     */
    private final Map<String, String> dismissedPlans = new HashMap<>();
    private String sessionId;
    private boolean expanded;

    public PlanPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setVisible(false);

        JPanel header = new JPanel();
        header.setOpaque(false);
        // BorderLayout lets the label take exactly the remaining width: its text
        // can then ellipsize without ever pushing the (n/n) bar out of the header.
        header.setLayout(new BorderLayout(4, 0));
        MouseAdapter toggle = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                expanded = !expanded;
                setEntries(entries);
            }
        };
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerLabel.setOpaque(false);
        badgeIcon.setOpaque(false);
        progressBar.setVisible(false);
        JPanel prependSlot = new JPanel();
        prependSlot.setOpaque(false);
        prependSlot.setLayout(new BoxLayout(prependSlot, BoxLayout.X_AXIS));
        prependSlot.add(chevron);
        prependSlot.add(Box.createHorizontalStrut(4));
        prependSlot.add(badgeIcon);
        header.add(prependSlot, BorderLayout.WEST);
        header.add(headerLabel, BorderLayout.CENTER);
        JPanel barSlot = new JPanel();
        barSlot.setOpaque(false);
        barSlot.setLayout(new BoxLayout(barSlot, BoxLayout.X_AXIS));
        barSlot.add(Box.createHorizontalStrut(8));
        barSlot.add(progressBar);
        header.add(barSlot, BorderLayout.EAST);
        add(header);
        // Mouse events go to the deepest component under the cursor, so the
        // header panel's own listener never sees clicks on its children
        // (label, icons, progress bar). Attach the same toggle to all of them;
        // containers are traversed recursively.
        wireToggleRecursively(header, toggle);

        // Added after the toggle wiring so a click on the close button hides the
        // slideout instead of also expanding/collapsing it.
        closeButton = UIUtils.createToolbarButton("plan-close.svg", ICON_SIZE,
                NbBundle.getMessage(PlanPanel.class, "TT_HidePlan"), e -> hidePlan());
        closeButton.setContentAreaFilled(false);
        closeButton.setBorderPainted(false);
        closeButton.getAccessibleContext().setAccessibleName(
                NbBundle.getMessage(PlanPanel.class, "ACSN_HidePlan"));
        barSlot.add(Box.createHorizontalStrut(2));
        barSlot.add(closeButton);

        rowsPanel.setOpaque(false);
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        // leading inset: visible gap between the panel's left edge and the row text/icons;
        // trailing inset: the vertical scrollbar rides on empty space, not on glyph ink
        rowsPanel.setBorder(BorderFactory.createEmptyBorder(0, PAD, 0, SCROLL_INSET_PX));
        rowsScroll = new JScrollPane(rowsPanel);
        rowsScroll.setBorder(BorderFactory.createEmptyBorder());
        rowsScroll.setOpaque(false);
        rowsScroll.getVerticalScrollBar().setUnitIncrement(12);
        add(rowsScroll);
    }

    /**
     * Replaces the panel content with the given, priority-sorted entries.
     * An empty list hides the slideout. Never call revalidate() from inside a
     * sizing method — this runs from the session-update pipeline.
     */
    public void setEntries(List<PlanEntry> entries) {
        setEntries(null, entries);
    }

    /**
     * As above, with the session the plan belongs to. A plan the user closed for
     * this session stays hidden when {@code session/load} replays the same steps;
     * a changed plan (different steps or priorities) reopens the slideout.
     */
    public void setEntries(String sessionId, List<PlanEntry> entries) {
        this.sessionId = sessionId;
        List<PlanEntry> previous = this.entries;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        if (this.entries.isEmpty()) {
            clear();
            return;
        }
        String signature = planSignature(this.entries);
        boolean dismissed = sessionId != null && signature.equals(dismissedPlans.get(sessionId));
        if (!dismissed && sessionId != null) {
            dismissedPlans.remove(sessionId);
        }
        // The slideout opens itself when a plan is created (first entries) or
        // populated further (more steps than before); a same-size refresh
        // preserves whatever the user last chose (toggle via the header).
        if (previous.isEmpty() || this.entries.size() > previous.size()) {
            expanded = true;
        }
        ColorTheme theme = ThemeManager.getCurrentTheme();
        Color headerBg = theme.tableHeaderBackground();
        setBackground(headerBg);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, theme.tableBorder()),
                BorderFactory.createEmptyBorder(PAD, PAD + 4, PAD, PAD)));

        Font baseFont = ThemeManager.getFont();
        Font font = baseFont;
        chevron.setFont(font);
        headerLabel.setFont(font);

        int done = 0;
        int activeIndex = -1;
        for (int i = 0; i < this.entries.size(); i++) {
            if (isCompleted(this.entries.get(i))) {
                done++;
            } else if (activeIndex < 0) {
                activeIndex = i;
            }
        }
        rowsPanel.removeAll();
        int rowCount = this.entries.size();
        for (int i = 0; i < rowCount; i++) {
            rowsPanel.add(buildRow(this.entries.get(i), i == activeIndex, font, theme));
            if (i < rowCount - 1) {
                rowsPanel.add(Box.createVerticalStrut(ROW_GAP));
            }
        }
        int total = this.entries.size();
        String count = " (" + done + "/" + total + ")";
        if (expanded) {
            setChevron("plan-chevron-down.svg", "\u2304"); // ⌄ down chevron fallback
            badgeIcon.setIcon(null);
            headerLabel.setFullText(NbBundle.getMessage(PlanPanel.class, "LBL_Plan") + count);
            rowsScroll.setVisible(true);
            // Size the viewport from the rows themselves, never from the scrollpane's
            // current preferred size: setPreferredSize() pins that value, so reading it
            // back would freeze the slideout at the height of the first (often
            // single-step) plan update. Inter-row struts count too.
            int rowHeight = rowsPanel.getComponent(0).getPreferredSize().height;
            int visibleRows = Math.min(rowCount, MAX_VISIBLE_ROWS);
            int listHeight = rowHeight * visibleRows + ROW_GAP * Math.max(0, visibleRows - 1);
            Dimension preferred = rowsScroll.getPreferredSize();
            preferred.height = listHeight;
            rowsScroll.setPreferredSize(preferred);
            // BoxLayout hands spare height to the scrollpane; pin the maximum
            // too so a stretched slideout still shows at most 8 rows.
            rowsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, listHeight));
        } else {
            setChevron("plan-chevron-right.svg", "\u203A"); // › right chevron fallback
            if (activeIndex >= 0) {
                PlanEntry active = this.entries.get(activeIndex);
                badgeIcon.setIcon(statusIcon(active));
                headerLabel.setFullText(active.content() + count);
            } else {
                badgeIcon.setIcon(null);
                headerLabel.setFullText(
                        NbBundle.getMessage(PlanPanel.class, "LBL_PlanCompleted") + count);
            }
            rowsScroll.setVisible(false);
        }
        headerLabel.setForeground(theme.foreground());
        chevron.setForeground(theme.mutedForeground());
        progressBar.setFraction(done, total,
                theme.accent(), theme.tableBorder());
        progressBar.getAccessibleContext().setAccessibleName(
                NbBundle.getMessage(PlanPanel.class, "ACSN_PlanProgress", done, total));
        setVisible(!dismissed);
        revalidate();
        repaint();
        LOG.fine("Plan slideout updated: {0} entries, {1} done, dismissed={2}", total, done, dismissed);
    }

    /**
     * Attaches the header's toggle mouse listener to every component in the
     * given container hierarchy (see the constructor call site for why).
     */
    private static void wireToggleRecursively(Component root, MouseAdapter toggle) {
        root.addMouseListener(toggle);
        if (root instanceof Container container) {
            for (int i = 0; i < container.getComponentCount(); i++) {
                wireToggleRecursively(container.getComponent(i), toggle);
            }
        }
    }

    /** Hides the slideout entirely and forgets the entries. */
    public void clear() {
        entries = List.of();
        setVisible(false);
        revalidate();
        repaint();
    }

    /**
     * Hides the slideout until the plan changes. The dismissal is remembered for the
     * current session and its step signature, so a {@code session/load} replay or a
     * status-only update does not reopen it. Entries are kept.
     */
    void hidePlan() {
        if (sessionId != null && !entries.isEmpty()) {
            dismissedPlans.put(sessionId, planSignature(entries));
        }
        setVisible(false);
        revalidate();
        repaint();
        LOG.fine("Plan slideout hidden by the user");
    }

    /** Step identity of a plan: contents and priorities, ignoring status changes. */
    private static String planSignature(List<PlanEntry> entries) {
        StringBuilder signature = new StringBuilder(entries.size() * 32);
        for (PlanEntry entry : entries) {
            signature.append(entry.priority()).append('\u0001')
                    .append(entry.content()).append('\u0002');
        }
        return signature.toString();
    }

    /**
     * One plan row: status icon + text. Completed rows mute; the active row
     * (the first unfinished one) takes the theme foreground at bold weight so
     * the eye lands on what the harness is working on right now — accent blue
     * read as a selection state rather than emphasis, and plain text keeps the
     * same contrast in both light and dark themes.
     */
    private JPanel buildRow(PlanEntry entry, boolean active, Font font, ColorTheme theme) {
        EllipsisLabel label = new EllipsisLabel();
        label.setIcon(statusIcon(entry));
        label.setFullText(entry.content());
        label.setHorizontalAlignment(JLabel.LEFT);
        if (active) {
            label.setFont(font.deriveFont(Font.BOLD));
            label.setForeground(theme.foreground());
        } else if (isCompleted(entry)) {
            // strike-through + muted colour: instantly reads as "done" without HTML
            label.setFont(font.deriveFont(Collections.<TextAttribute, Boolean>singletonMap(
                    TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON)));
            label.setForeground(theme.mutedForeground());
        } else {
            label.setFont(font);
            label.setForeground(theme.foreground());
        }
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        // CENTER stretches the label to the row width; the label's preferred width is
        // zero, so a long entry cannot widen the row (or the scrollpane) either.
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    /**
     * Draws the expand/collapse chevron as an SVG icon so it cannot turn into
     * a tofu box when the active font lacks the glyph; falls back to the old
     * Unicode character if the icon cannot be loaded.
     */
    private void setChevron(String iconName, String fallbackGlyph) {
        Icon icon = ThemeManager.getIcon(iconName, ICON_SIZE);
        if (icon != null) {
            chevron.setIcon(icon);
            chevron.setText(null);
        } else {
            chevron.setIcon(null);
            chevron.setText(fallbackGlyph);
        }
    }

    private static Icon statusIcon(PlanEntry entry) {
        String status = entry.status() == null ? "" : entry.status().toLowerCase(Locale.ROOT);
        String name = switch (status) {
            case "completed", "done" -> "plan-done.svg";
            case "in_progress", "in-progress", "inprogress", "active" -> "plan-active.svg";
            case "cancelled", "canceled", "failed" -> "plan-failed.svg";
            default -> "plan-open.svg";
        };
        return ThemeManager.getIcon(name, ICON_SIZE);
    }

    private static boolean isCompleted(PlanEntry entry) {
        String status = entry.status() == null ? "" : entry.status().toLowerCase(Locale.ROOT);
        return "completed".equals(status) || "done".equals(status);
    }

    /**
     * Single-line label that ellipsizes its text to the laid-out width instead of
     * clipping mid-glyph. The full entry stays the model text, so {@link #getText()},
     * the tooltip and accessibility always report it; the clipped rendering is a
     * paint-time decision only. The preferred width is deliberately zero: the parent
     * layout (BorderLayout CENTER) owns the width, so one long entry can neither widen
     * the slideout nor force a horizontal scrollbar, and widening the panel restores
     * the full title without any plan update.
     */
    private static final class EllipsisLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        private EllipsisLabel() {
            setOpaque(false);
        }

        void setFullText(String text) {
            String full = text == null ? "" : text;
            setToolTipText(full.isEmpty() ? null : full);
            super.setText(full);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            size.width = 0;
            return size;
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            java.awt.Insets insets = getInsets();
            Rectangle view = new Rectangle(
                    insets.left, insets.top,
                    Math.max(0, getWidth() - insets.left - insets.right),
                    Math.max(0, getHeight() - insets.top - insets.bottom));
            Rectangle iconRect = new Rectangle();
            Rectangle textRect = new Rectangle();
            // Computes the clipped text exactly as JLabel's own layout would, without
            // writing it back to the model (setText here would revalidate during paint).
            String displayed = SwingUtilities.layoutCompoundLabel(this,
                    getFontMetrics(getFont()), getText(), getIcon(),
                    getVerticalAlignment(), getHorizontalAlignment(),
                    getVerticalTextPosition(), getHorizontalTextPosition(),
                    view, iconRect, textRect, getIconTextGap());
            Icon icon = getIcon();
            if (icon != null) {
                icon.paintIcon(this, g, iconRect.x, iconRect.y);
            }
            if (displayed.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setFont(getFont());
                g2.setColor(getForeground());
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.drawString(displayed, textRect.x, textRect.y + g2.getFontMetrics().getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    /** Thin rounded bar showing done/total progress, drawn in the theme's colors. */
    private static final class MiniProgressBar extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int BAR_WIDTH = 64;
        private static final int BAR_HEIGHT = 4;
        private double fraction;
        private Color fill;
        private Color track;

        MiniProgressBar() {
            setOpaque(false);
        }

        /** Updates the fraction and colors from the current theme snapshot. */
        void setFraction(int done, int total, Color fill, Color track) {
            this.fraction = total > 0 ? (double) done / total : 0.0;
            this.fill = fill;
            this.track = track;
            setVisible(true);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            try {
                int w = getWidth();
                int h = getHeight();
                g2.setColor(track != null ? track : getBackground());
                g2.fillRoundRect(0, (h - BAR_HEIGHT) / 2, w, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
                int filled = (int) Math.round(w * fraction);
                if (filled > 0) {
                    g2.setColor(fill != null ? fill : g2.getColor());
                    g2.fillRoundRect(0, (h - BAR_HEIGHT) / 2, filled, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT);
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(BAR_WIDTH, BAR_HEIGHT + 2);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    }
}
