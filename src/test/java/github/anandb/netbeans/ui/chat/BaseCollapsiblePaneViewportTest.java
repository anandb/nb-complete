package github.anandb.netbeans.ui.chat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertTrue;
import github.anandb.netbeans.ui.TestUiUtils;

/**
 * Expanding a pane inside a scroll pane changes the view's height. A repaint of
 * the old bounds leaves the newly exposed strip blank until a scroll repaints
 * the viewport, so the pane must repaint the viewport after the pending layout.
 */
class BaseCollapsiblePaneViewportTest {

    @BeforeAll
    static void setUp() {
        TestUiUtils.setupTestUIManager();
    }

    @Test
    void togglingAViewportHostedPaneRepaintsTheViewportAfterLayout() throws Exception {
        CollapsibleActivityPane pane = new CollapsibleActivityPane("Execution Steps", "", false);
        CountingViewport viewport = new CountingViewport();
        JPanel view = new JPanel(new BorderLayout());
        view.add(pane);
        viewport.setView(view);
        new JScrollPane(viewport);

        int before = viewport.repaints.get();
        expand(pane, true);
        assertTrue(viewport.repaints.get() > before,
                "expanding must repaint the viewport after the layout pass");

        int afterExpand = viewport.repaints.get();
        expand(pane, false);
        assertTrue(viewport.repaints.get() > afterExpand,
                "collapsing must repaint the viewport too");
    }

    private static void expand(CollapsibleActivityPane pane, boolean expanded) throws Exception {
        SwingUtilities.invokeAndWait(() -> pane.setExpanded(expanded));
        // Drain the queued validate so the deferred repaint runs after it.
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static final class CountingViewport extends JViewport {
        private static final long serialVersionUID = 1L;
        /** Null during the superclass constructor, which repaints before fields exist. */
        private AtomicInteger repaints;

        CountingViewport() {
            repaints = new AtomicInteger();
        }

        @Override
        public void repaint() {
            AtomicInteger counter = repaints;
            if (counter != null) {
                counter.incrementAndGet();
            }
            super.repaint();
        }
    }
}
