package github.anandb.netbeans.ui.rendering;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import github.anandb.netbeans.ui.TestUiUtils;

/**
 * A bubble must follow the width of its container. A monitor-derived width cap used to be
 * applied here, which clipped a table (its columns cannot shrink past their minimum) and
 * made resizing the sidebar do nothing.
 */
class FitEditorPaneSizingTest {

    private static final String TABLE_HTML = "<html><body>"
            + "<table width='100%'><tr><th>uri</th><th>environments</th><th>note</th></tr>"
            + "<tr><td>hub://acme/refunds</td><td>staging, then production</td><td>config</td></tr>"
            + "<tr><td>hub://acme/refunds:production</td><td>overridden to staging</td><td>patch</td></tr>"
            + "<tr><td>hub://acme/refunds:7ca95573</td><td>honoured verbatim</td><td>pin</td></tr>"
            + "</table></body></html>";

    private FitEditorPane pane;

    @BeforeEach
    void setUp() {
        TestUiUtils.setupTestUIManager();
        pane = new FitEditorPane();
        pane.setContentType("text/html");
        pane.setText(TABLE_HTML);
    }

    @Test
    void paneIsNotCappedToAnArtificialWidth() {
        assertTrue(pane.getMaximumSize().width > 10_000,
                "the pane must be allowed to fill a wide sidebar");
    }

    @Test
    void paneFollowsItsContainerWidth() {
        assertEquals(300, layoutAt(300));
        assertEquals(900, layoutAt(900));
    }

    @Test
    void preferredHeightIsComputedForTheWidthActuallyAssigned() {
        int heightAt300 = preferredHeightAt(300);
        int heightAt900 = preferredHeightAt(900);

        assertTrue(heightAt300 > 0 && heightAt900 > 0, "both widths must yield a height");
        assertTrue(heightAt900 <= heightAt300,
                "a wider pane must not need more height than a narrow one");
    }

    /** Lays the pane out in a parent of the given width and returns the width it was given. */
    private int layoutAt(int width) {
        JPanel parent = new JPanel(new BorderLayout());
        parent.add(pane, BorderLayout.CENTER);
        parent.setSize(width, 400);
        parent.doLayout();
        pane.doLayout();
        return pane.getWidth();
    }

    /** Preferred height after the pane was assigned the given width. */
    private int preferredHeightAt(int width) {
        layoutAt(width);
        return pane.getPreferredSize().height;
    }
}
