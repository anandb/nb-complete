package github.anandb.netbeans.ui;

import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JToolBar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests for the Show History refresh-button injection. The IDE git
 * module's SearchHistoryPanel is absent from the test classpath, so a
 * stand-in panel tree with the same layout is used (toolbar in the panel,
 * Show All Files checkbox + Search button in the toolbar's position).
 */
class ShowHistoryRefreshTest {

    /** Mirror of SearchHistoryPanel: toolbar with summary/diff/check/search control order. */
    private static final class FakeHistoryPanel extends JPanel {
        final JToolBar toolbar = new JToolBar();
        final JCheckBox showAllFiles = new JCheckBox("Show All Files");
        final JButton searchButton = new JButton("Search");
        int searchClicks;

        FakeHistoryPanel() {
            toolbar.add(new JButton("Summary"));
            toolbar.add(new JButton("Diff"));
            toolbar.add(showAllFiles);
            // bSearch sits on the panel itself, below the toolbar, exactly
            // like SearchHistoryPanel's GroupLayout puts it.
            add(toolbar);
            add(searchButton);
        }

        void countSearchClick() {
            // AbstractButton.doClick() fires even when the button is hidden.
            searchButton.addActionListener(e -> searchClicks++);
        }
    }

    @Test
    void insertAddsGapAndIconOnlyButtonAfterCheckbox() {
        FakeHistoryPanel panel = new FakeHistoryPanel();
        int before = countButtons(panel.toolbar);
        ShowHistoryRefresh.injectToolbar(panel, panel.toolbar);
        int checkboxIndex = panel.toolbar.getComponentIndex(panel.showAllFiles);
        Component gap = panel.toolbar.getComponent(checkboxIndex + 1);
        Component injected = panel.toolbar.getComponent(checkboxIndex + 2);
        assertTrue(!(gap instanceof JButton), "a gap separates the checkbox from the button");
        assertEquals(20, gap.getPreferredSize().width, "gap is 20px");
        assertTrue(injected instanceof JButton, "refresh button follows the gap");
        JButton refresh = (JButton) injected;
        assertNotNull(refresh.getIcon(), "refresh button keeps its icon");
        assertTrue(refresh.getText() == null || refresh.getText().isEmpty(),
                "button is icon-only: no visible text to truncate in the toolbar");
        assertEquals("Show All Files", panel.showAllFiles.getText(), "checkbox text preserved");
        assertEquals(before + 1, countButtons(panel.toolbar), "exactly one button added");
    }

    @Test
    void injectionIsIdempotentPerToolbar() {
        FakeHistoryPanel panel = new FakeHistoryPanel();
        ShowHistoryRefresh.injectToolbar(panel, panel.toolbar);
        int after = panel.toolbar.getComponentCount();
        JButton first = ShowHistoryRefresh.findRefreshButton(panel.toolbar);
        assertNotNull(first, "refresh button injected once");
        ShowHistoryRefresh.injectToolbar(panel, panel.toolbar);
        assertEquals(after, panel.toolbar.getComponentCount(), "no duplicate injection");
        assertSame(first, ShowHistoryRefresh.findRefreshButton(panel.toolbar),
                "existing refresh button kept");
    }

    @Test
    void refreshClickRunsFallbackSearch() {
        FakeHistoryPanel panel = new FakeHistoryPanel();
        panel.countSearchClick();
        ShowHistoryRefresh.injectToolbar(panel, panel.toolbar);
        JButton refresh = ShowHistoryRefresh.findRefreshButton(panel.toolbar);
        assertNotNull(refresh, "refresh button injected");

        // FakeHistoryPanel is not the git module's class, so the reflection
        // path cannot match it and the click lands on the Search button
        // fallback — the same behaviour a JPMS-blocked reflection hits.
        for (ActionListener l : refresh.getActionListeners()) {
            l.actionPerformed(new ActionEvent(refresh, 0, "refresh"));
        }
        assertEquals(1, panel.searchClicks, "refresh runs a search");
    }

    @Test
    void toolbarButtonsAreNotMistakenForSearchButton() {
        FakeHistoryPanel fresh = new FakeHistoryPanel();
        fresh.remove(fresh.searchButton); // tree holds only the toolbar subtree
        assertNull(ShowHistoryRefresh.findSearchButton(fresh),
                "toolbar jump buttons are not treated as the Search button");
    }

    @Test
    void panelFinderMatchesDeeplyNestedPanel() {
        FakeHistoryPanel panel = new FakeHistoryPanel();
        JPanel root = new JPanel();
        JPanel mid = new JPanel();
        mid.add(panel);
        root.add(mid);
        Component found = ShowHistoryRefresh.findByName(root, FakeHistoryPanel.class.getName());
        assertNotNull(found, "matched panel is located through the nesting");
        assertSame(panel, found);
        assertNull(ShowHistoryRefresh.findByName(root, "no.such.Class"), "no false match");
    }

    @Test
    void injectWithoutCheckboxAppendsAtEnd() {
        FakeHistoryPanel panel = new FakeHistoryPanel();
        Container tb = panel.toolbar;
        // Remove the checkbox to exercise the positional fallback.
        tb.remove(panel.showAllFiles);
        ShowHistoryRefresh.injectToolbar(panel, panel.toolbar);
        Component last = tb.getComponent(tb.getComponentCount() - 1);
        assertTrue(last instanceof JButton, "button appended when checkbox is absent");
        assertSame(last, ShowHistoryRefresh.findRefreshButton(panel.toolbar),
                "the appended button is the refresh button");
    }

    private static int countButtons(Container toolbar) {
        int n = 0;
        for (Component c : toolbar.getComponents()) {
            if (c instanceof JButton) {
                n++;
            }
        }
        return n;
    }
}
