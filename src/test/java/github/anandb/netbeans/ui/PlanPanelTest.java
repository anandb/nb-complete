package github.anandb.netbeans.ui;

import java.awt.Container;
import java.util.List;

import javax.swing.JLabel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.ModelRecords.PlanEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // its children are the chevron, a strut, and the header label.
        Container header = (Container) panel.getComponent(0);
        JLabel label = (JLabel) header.getComponent(2);
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
