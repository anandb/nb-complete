package github.anandb.netbeans.ui.chat;

import java.awt.BorderLayout;
import java.awt.Container;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JButton;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.ModelRecords.PlanEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JScrollPane;
import github.anandb.netbeans.ui.TestUiUtils;
import github.anandb.netbeans.ui.rendering.ColorTheme;
import github.anandb.netbeans.ui.rendering.ThemeManager;

class PlanPanelTest {

    @BeforeAll
    static void setUp() {
        TestUiUtils.setupTestUIManager();
    }

    private static List<PlanEntry> sample() {
        return List.of(
                new PlanEntry("high", "pending", "Verify extension functionality"),
                new PlanEntry("medium", "completed", "Move completed tasks"),
                new PlanEntry("low", "in-progress", "Analyze config"));
    }

    @Test
    void paintingANarrowSlideoutNeverDestroysTheFullTitle() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());
        toggleHeader(panel); // a new plan auto-opens; this test covers the collapsed header
        JLabel label = headerLabel(panel);
        String full = label.getText();
        assertTrue(full.contains("Verify extension functionality"), "precondition: " + full);

        // Narrow enough that the header title must be ellipsized on screen.
        paintAt(panel, 260, 200);
        assertTrue(label.getWidth() > 0,
                "precondition: the label is laid out, width=" + label.getWidth());
        assertEquals(full, label.getText(),
                "ellipsizing is a paint-time decision; it must not overwrite the model text");
        assertEquals(full, label.getToolTipText(), "tooltip keeps the full entry");

        // Widening restores the whole title with no plan update in between.
        paintAt(panel, 900, 200);
        assertEquals(full, label.getText());

        // The width comes from the layout now (preferred width is zero), so verify the
        // expanded rows still span the viewport and their labels span the row.
        toggleHeader(panel);
        java.awt.image.BufferedImage painted = paintAt(panel, 260, 400);
        Container rows = (Container) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
        Container firstRow = (Container) rows.getComponent(0);
        assertTrue(rows.getWidth() > 0, "rows laid out, width=" + rows.getWidth());
        int contentWidth = rows.getWidth() - rows.getInsets().left - rows.getInsets().right;
        assertEquals(contentWidth, firstRow.getWidth(), "row spans the content width");
        JLabel firstRowLabel = (JLabel) firstRow.getComponent(0);
        assertEquals(firstRow.getWidth(), firstRowLabel.getWidth(), "label spans the row");
        assertEquals("Verify extension functionality", firstRowLabel.getText(),
                "painting a narrow row must keep the full entry text");
        // Custom paint path: the label must actually draw ink (icon + clipped text),
        // not just keep the model text while rendering nothing.
        java.awt.Rectangle labelBounds = javax.swing.SwingUtilities.convertRectangle(
                firstRowLabel.getParent(), firstRowLabel.getBounds(), panel);
        assertTrue(paintedInk(painted, labelBounds) > 0,
                "the ellipsized row label renders ink inside " + labelBounds);
    }

    private static int paintedInk(java.awt.image.BufferedImage image, java.awt.Rectangle area) {
        int ink = 0;
        for (int y = Math.max(0, area.y); y < Math.min(image.getHeight(), area.y + area.height); y++) {
            for (int x = Math.max(0, area.x); x < Math.min(image.getWidth(), area.x + area.width); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    ink++;
                }
            }
        }
        return ink;
    }

    private static java.awt.image.BufferedImage paintAt(PlanPanel panel, int width, int height) {
        panel.setSize(width, height);
        // Headless: no peer to validate the tree, so lay every container out top-down
        // (a single doLayout() would leave the header's children at width 0 and the
        // label would never reach its paint path).
        layoutTree(panel);
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layoutTree(nested);
            }
        }
    }

    /** Clicks the header once; the first non-empty plan auto-opens the slideout. */
    private static void toggleHeader(PlanPanel panel) {
        Container header = (Container) panel.getComponent(0);
        for (var listener : header.getMouseListeners()) {
            listener.mouseClicked(null);
        }
    }

    private static JLabel headerLabel(PlanPanel panel) {
        // The header row is the panel's first child and uses BorderLayout:
        // WEST = chevron + badge, CENTER = the title label, EAST = progress bar.
        // Ask the layout for CENTER instead of indexing children, so the header can
        // gain slots without silently breaking (or misreading) the assertion.
        Container header = (Container) panel.getComponent(0);
        JLabel label = (JLabel) ((BorderLayout) header.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        assertNotNull(label);
        return label;
    }

    @Test
    void emptyEntriesHideThePanel() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());
        assertTrue(panel.isVisible());

        panel.setEntries(List.of());
        assertFalse(panel.isVisible());
        panel.clear();
        assertFalse(panel.isVisible());
    }

    @Test
    void collapsedHeaderShowsTheFirstUnfinishedItemWithCount() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());
        toggleHeader(panel); // a new plan auto-opens; collapse to test the header text

        String text = headerLabel(panel).getText();
        assertTrue(text.contains("Verify extension functionality"),
                "the first unfinished entry is the header title, got " + text);
        assertTrue(text.contains("(1/3)"), "one completed of three, got " + text);
    }

    @Test
    void rowsCarryStatusIconsInsteadOfUnicodeGlyphs() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());

        // A new plan opens the slideout: rows live in the scrollpane's viewport.
        JScrollPane scroll = (JScrollPane) panel.getComponent(1);
        assertTrue(scroll.isVisible());
        Container rows = (Container) scroll.getViewport().getView();
        // Rows alternate with vertical-gap struts: row, gap, row, gap, row.
        assertEquals(5, rows.getComponentCount());
        for (int i = 0; i < 5; i += 2) {
            JLabel row = (JLabel) ((Container) rows.getComponent(i)).getComponent(0);
            assertNotNull(row.getIcon(), "row " + i + " uses an icon, not a font glyph");
        }
        // Completed row is muted; the active row takes the theme foreground and
        // bold weight (bold only visibly differs when the base font is plain,
        // so the foreground colors carry the assertion).
        JLabel done = (JLabel) ((Container) rows.getComponent(2)).getComponent(0);
        JLabel active = (JLabel) ((Container) rows.getComponent(0)).getComponent(0);
        assertTrue(active.getFont().isBold(), "active row is bold");
        ColorTheme theme = ThemeManager.getCurrentTheme();
        assertEquals(theme.mutedForeground(), done.getForeground(),
                "completed row is muted");
        assertEquals(theme.foreground(), active.getForeground(),
                "active row uses the theme foreground");
    }

    @Test
    void expandedListCapsAtEightRows() {
        List<PlanEntry> many = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add(new PlanEntry("p" + i, "pending", "entry " + i));
        }
        PlanPanel panel = new PlanPanel();
        panel.setEntries(many);
        JScrollPane scroll = (JScrollPane) panel.getComponent(1);
        Container rows = (Container) scroll.getViewport().getView();
        int rowHeight = rows.getComponent(0).getPreferredSize().height;
        int cap = rowHeight * 8 + PlanPanel.ROW_GAP * 7;
        assertTrue(scroll.getPreferredSize().height <= cap + 1,
                "capped at 8 rows incl. gaps, got " + scroll.getPreferredSize().height
                        + " vs " + cap);
        assertEquals(29, rows.getComponentCount(), "15 rows + 14 gaps, rest scrolls");
    }

    @Test
    void expandedListGrowsWhenThePlanArrivesInSteps() {
        // A harness may create the plan with one step and append the rest. The
        // slideout must take its height from the rows every update instead of
        // staying pinned at the height of the first one.
        PlanPanel panel = new PlanPanel();
        java.util.List<PlanEntry> growing = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            growing.add(new PlanEntry("p" + i, "pending", "entry " + i));
            panel.setEntries(growing);
        }
        JScrollPane scroll = (JScrollPane) panel.getComponent(1);
        Container rows = (Container) scroll.getViewport().getView();
        int rowHeight = rows.getComponent(0).getPreferredSize().height;
        assertEquals(6, growing.size());
        assertTrue(scroll.getPreferredSize().height >= rowHeight * 6 + PlanPanel.ROW_GAP * 5,
                "six steps need six visible rows, got " + scroll.getPreferredSize().height
                        + " with row height " + rowHeight);

        // Laid out inside a tall thread window the viewport really is six rows high,
        // not a sliver with a one-row scrollbar squeezed into it.
        panel.setSize(400, 600);
        layoutTree(panel);
        assertTrue(scroll.getHeight() >= rowHeight * 6 + PlanPanel.ROW_GAP * 5,
                "laid-out scrollpane height " + scroll.getHeight());

        // Growing past the cap stops at eight rows; the rest scrolls.
        for (int i = 6; i < 12; i++) {
            growing.add(new PlanEntry("p" + i, "pending", "entry " + i));
            panel.setEntries(growing);
        }
        assertEquals(rowHeight * 8 + PlanPanel.ROW_GAP * 7, scroll.getPreferredSize().height,
                "the cap still holds after the plan grows");
    }

    @Test
    void preferredSizeIsMeasuredInsteadOfRememberedFromTheFirstUpdate() {
        // setPreferredSize() pins whatever it was given, so the pinned height must
        // never make the rest of the preferred size a snapshot: a restyled
        // scrollpane still has to report its own width.
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());
        JScrollPane scroll = (JScrollPane) panel.getComponent(1);
        int measured = scroll.getPreferredSize().width;

        scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 40, 0, 40));

        assertEquals(measured + 80, scroll.getPreferredSize().width,
                "the width must be measured from the scrollpane, not kept from the first plan update");
    }

    @Test
    void clearForgetsEntriesSoExpandDoesNotResurrect() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());
        panel.clear();

        // Clicking the header after a clear must not resurrect the old plan.
        toggleHeader(panel);
        assertFalse(panel.isVisible());
    }

    @Test
    void closeButtonHidesThePlanUntilTheNextPlanArrives() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries("s1", sample());
        assertTrue(panel.isVisible(), "precondition: a plan opens the slideout");

        closeButton(panel).doClick();

        assertFalse(panel.isVisible(), "the close button must hide the slideout");
        panel.setEntries("s1", sample());
        assertFalse(panel.isVisible(), "a replayed identical plan must stay closed");
        panel.setEntries("s1", List.of(new PlanEntry("high", "pending", "Different step")));
        assertTrue(panel.isVisible(), "a changed plan re-opens it");
    }

    @Test
    void dismissedPlanSurvivesSessionReloadAndStatusOnlyUpdates() {
        PlanPanel panel = new PlanPanel();
        List<PlanEntry> plan = List.of(
                new PlanEntry("high", "in-progress", "Analyze config"),
                new PlanEntry("low", "pending", "Ship it"));
        panel.setEntries("s1", plan);
        closeButton(panel).doClick();
        assertFalse(panel.isVisible());

        // session/load clears the panel, then replays the plan updates.
        panel.clear();
        panel.setEntries("s1", plan);
        assertFalse(panel.isVisible(), "reload replay must not reopen a dismissed plan");

        panel.setEntries("s1", List.of(
                new PlanEntry("high", "completed", "Analyze config"),
                new PlanEntry("low", "completed", "Ship it")));
        assertFalse(panel.isVisible(), "status-only progress must not reopen it");

        panel.setEntries("s2", plan);
        assertTrue(panel.isVisible(), "the dismissal is scoped to its session");
    }

    private static JButton closeButton(PlanPanel panel) {
        JButton button = findButton((Container) panel.getComponent(0));
        assertNotNull(button, "the header must expose a close button");
        return button;
    }

    private static JButton findButton(Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof JButton button) {
                return button;
            }
            if (child instanceof Container nested) {
                JButton button = findButton(nested);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }
}
