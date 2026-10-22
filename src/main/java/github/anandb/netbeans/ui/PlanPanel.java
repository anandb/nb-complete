package github.anandb.netbeans.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

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
 * <p>EDT-only: {@link #setEntries} and {@link #clear} must be called on the
 * Event Dispatch Thread (the session-update pipeline marshals via
 * {@code invokeLater} before reaching this panel).</p>
 */
public class PlanPanel extends JPanel {
    private static final Logger LOG = Logger.from(PlanPanel.class);
    private static final long serialVersionUID = 1L;

    /** Cap on the expanded list height, so a long plan cannot eat the thread. */
    private static final int MAX_ROWS_HEIGHT = 170;
    /** Vertical + horizontal padding inside the panel. */
    private static final int PAD = 6;

    private final JLabel chevron = new JLabel();
    private final JLabel headerLabel = new JLabel();
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
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                expanded = !expanded;
                setEntries(entries);
            }
        });
        headerLabel.setOpaque(false);
        header.add(chevron);
        header.add(Box.createHorizontalStrut(4));
        header.add(headerLabel);
        add(header);

        rowsPanel.setOpaque(false);
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
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

        Font font = ThemeManager.getFont();
        chevron.setFont(font);
        headerLabel.setFont(font);

        int done = 0;
        PlanEntry active = null;
        rowsPanel.removeAll();
        for (PlanEntry entry : entries) {
            if (isCompleted(entry)) {
                done++;
            }
            if (active == null && !isCompleted(entry)) {
                active = entry;
            }
            rowsPanel.add(buildRow(entry, font, theme));
        }
        int total = entries.size();
        String count = " (" + done + "/" + total + ")";
        if (expanded) {
            chevron.setText("\u2304"); // ⌄ down chevron
            headerLabel.setText(NbBundle.getMessage(PlanPanel.class, "LBL_Plan") + count);
            rowsScroll.setVisible(true);            // Cap the expanded list height; the scrollpane absorbs the rest.
            Dimension preferred = rowsScroll.getPreferredSize();
            preferred.height = Math.min(preferred.height, MAX_ROWS_HEIGHT);
            rowsScroll.setPreferredSize(preferred);
        } else {
            chevron.setText("\u203A"); // › right chevron
            String title = active != null ? escapeHtml(active.content())
                    : NbBundle.getMessage(PlanPanel.class, "LBL_PlanCompleted");
            String badge = active != null ? statusGlyph(active, theme) : "";
            headerLabel.setText("<html>" + badge + " " + title
                    + escapeHtml(count) + "</html>");
            rowsScroll.setVisible(false);
        }
        headerLabel.setForeground(theme.foreground());
        chevron.setForeground(theme.mutedForeground());
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

    private JPanel buildRow(PlanEntry entry, Font font, ColorTheme theme) {
        JLabel label = new JLabel("<html>" + statusGlyph(entry, theme) + " "
                + escapeHtml(entry.content()) + "</html>");
        label.setFont(font);
        label.setForeground(theme.foreground());
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(label);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private String statusGlyph(PlanEntry entry, ColorTheme theme) {
        String status = entry.status() == null ? "" : entry.status().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "completed" -> "<span style='color:"
                    + theme.toHtmlHex(permissionGreen(theme)) + "'>\u2714</span>";
            case "in_progress", "in-progress", "inprogress", "active" ->
                    "<span style='color:" + theme.toHtmlHex(theme.accent())
                            + "'>\u25D4</span>";
            case "cancelled", "canceled", "failed" ->
                    "<span style='color:" + theme.toHtmlHex(theme.mutedForeground())
                            + "'>\u2715</span>";
            default -> "<span style='color:" + theme.toHtmlHex(theme.mutedForeground())
                    + "'>\u25CB</span>";
        };
    }

    private Color permissionGreen(ColorTheme theme) {
        // The green used for the permission dialog's grant action reads as
        // "succeeded" in this theme pair; fall back to the accent when unset.
        Color green = theme.permissionGrantFg();
        return green != null ? green : theme.accent();
    }

    private static boolean isCompleted(PlanEntry entry) {
        String status = entry.status() == null ? "" : entry.status().toLowerCase(Locale.ROOT);
        return "completed".equals(status) || "done".equals(status);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
