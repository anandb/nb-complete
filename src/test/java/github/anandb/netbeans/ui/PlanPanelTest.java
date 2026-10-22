package github.anandb.netbeans.ui;

import java.awt.Container;
import java.util.List;

import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.ModelRecords.PlanEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JScrollPane;

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

    private static JLabel headerLabel(PlanPanel panel) {
        // Structure: the panel's first child is the clickable header row;
        // its children are the chevron, a strut, the badge icon, a strut, and
        // the header label.
        Container header = (Container) panel.getComponent(0);
        JLabel label = (JLabel) header.getComponent(4);
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

        String text = headerLabel(panel).getText();
        assertTrue(text.contains("Verify extension functionality"),
                "the first unfinished entry is the header title, got " + text);
        assertTrue(text.contains("(1/3)"), "one completed of three, got " + text);
    }

    @Test
    void rowsCarryStatusIconsInsteadOfUnicodeGlyphs() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());

        // Expanded: rows live in the scrollpane's viewport.
        Container header = (Container) panel.getComponent(0);
        for (var listener : header.getMouseListeners()) {
            listener.mouseClicked(null);
        }
        JScrollPane scroll = (JScrollPane) panel.getComponent(1);
        assertTrue(scroll.isVisible());
        Container rows = (Container) scroll.getViewport().getView();
        // Rows alternate with vertical-gap struts: row, gap, row, gap, row.
        assertEquals(5, rows.getComponentCount());
        for (int i = 0; i < 5; i += 2) {
            JLabel row = (JLabel) ((Container) rows.getComponent(i)).getComponent(0);
            assertNotNull(row.getIcon(), "row " + i + " uses an icon, not a font glyph");
        }
        // Completed row is muted; the active row takes the accent color and
        // bold weight (bold only visibly differs when the base font is plain,
        // so the foreground colors carry the assertion).
        JLabel done = (JLabel) ((Container) rows.getComponent(2)).getComponent(0);
        JLabel active = (JLabel) ((Container) rows.getComponent(0)).getComponent(0);
        assertTrue(active.getFont().isBold(), "active row is bold");
        ColorTheme theme = ThemeManager.getCurrentTheme();
        assertEquals(theme.mutedForeground(), done.getForeground(),
                "completed row is muted");
        assertEquals(theme.accent(), active.getForeground(),
                "active row takes the accent color");
    }

    @Test
    void expandedListCapsAtEightRows() {
        List<PlanEntry> many = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            many.add(new PlanEntry("p" + i, "pending", "entry " + i));
        }
        PlanPanel panel = new PlanPanel();
        panel.setEntries(many);
        Container header = (Container) panel.getComponent(0);
        for (var listener : header.getMouseListeners()) {
            listener.mouseClicked(null);
        }
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
    void clearForgetsEntriesSoExpandDoesNotResurrect() {
        PlanPanel panel = new PlanPanel();
        panel.setEntries(sample());
        panel.clear();

        // Clicking the header after a clear must not resurrect the old plan.
        Container header = (Container) panel.getComponent(0);
        for (var listener : header.getMouseListeners()) {
            listener.mouseClicked(null);
        }
        assertFalse(panel.isVisible());
    }
}
