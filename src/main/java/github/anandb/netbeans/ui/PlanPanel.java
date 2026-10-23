package github.anandb.netbeans.ui;

import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
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
    /** Current entries, kept so expanding/collapsing re-renders from them. */
    private List<PlanEntry> entries = List.of();
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
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                expanded = !expanded;
                setEntries(entries);
            }
        });
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

        rowsPanel.setOpaque(false);
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        // trailing inset: the vertical scrollbar rides on empty space, not on glyph ink
        rowsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, SCROLL_INSET_PX));
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
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        if (this.entries.isEmpty()) {
            clear();
            return;
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
            // Cap the expanded list height in rows; the scrollpane absorbs the rest.
            // Inter-row struts count too: 8 rows + the 7 gaps between them.
            Dimension preferred = rowsScroll.getPreferredSize();
            int cap = rowsPanel.getComponentCount() > 0
                    ? rowsPanel.getComponent(0).getPreferredSize().height * MAX_VISIBLE_ROWS
                            + ROW_GAP * (MAX_VISIBLE_ROWS - 1)
                    : preferred.height;
            preferred.height = Math.min(preferred.height, cap);
            rowsScroll.setPreferredSize(preferred);
            // BoxLayout hands spare height to the scrollpane; pin the maximum
            // too so a stretched slideout still shows at most 8 rows.
            rowsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
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
        setVisible(true);
        revalidate();
        repaint();
        LOG.fine("Plan slideout updated: {0} entries, {1} done", total, done);
    }

    /** Hides the slideout entirely and forgets the entries. */
    public void clear() {
        entries = List.of();
        setVisible(false);
        revalidate();
        repaint();
    }

    /**
     * One plan row: status icon + text. Completed rows mute; the active row
     * (the first unfinished one) takes the accent color and bold weight so
     * the eye lands on what the harness is working on right now.
     */
    private JPanel buildRow(PlanEntry entry, boolean active, Font font, ColorTheme theme) {
        EllipsisLabel label = new EllipsisLabel();
        label.setIcon(statusIcon(entry));
        label.setFullText(entry.content());
        label.setHorizontalAlignment(JLabel.LEFT);
        if (active) {
            label.setFont(font.deriveFont(Font.BOLD));
            label.setForeground(theme.accent());
        } else if (isCompleted(entry)) {
            // strike-through + muted colour: instantly reads as "done" without HTML
            label.setFont(font.deriveFont(Collections.<TextAttribute, Boolean>singletonMap(
                    TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON)));
            label.setForeground(theme.mutedForeground());
        } else {
            label.setFont(font);
            label.setForeground(theme.foreground());
        }
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(label);
        row.add(Box.createHorizontalGlue());
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
     * Single-line label that clips its text with a trailing ellipsis instead
     * of a hard mid-glyph edge; the full text is always available as the
     * tooltip. Uses {@link SwingUtilities#layoutCompoundLabel} so placement
     * and clipping match exactly how the label paints. No wrapping, no
     * {@code revalidate()} from a sizing path (only a setText equality guard).
     */
    private static final class EllipsisLabel extends JLabel {
        private static final long serialVersionUID = 1L;

        private EllipsisLabel() {
            setOpaque(false);
        }

        void setFullText(String text) {
            setToolTipText(text);
            super.setText(text);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Rectangle view = new Rectangle(0, 0, Math.max(0, getWidth()), getHeight());
            String displayed = SwingUtilities.layoutCompoundLabel(this,
                    getFontMetrics(getFont()), getText(), getIcon(),
                    getVerticalAlignment(), getHorizontalAlignment(),
                    getVerticalTextPosition(), getHorizontalTextPosition(),
                    view, new Rectangle(), new Rectangle(), 0);
            // equality guard: setText changes preferred size → relayout, converges
            if (!displayed.equals(getText())) {
                super.setText(displayed);
            }
            super.paintComponent(g);
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
