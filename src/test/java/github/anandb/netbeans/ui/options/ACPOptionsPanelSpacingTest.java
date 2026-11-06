package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import github.anandb.netbeans.ui.UiTestSupport;

/**
 * Guards the Assistant Service row rhythm: the binary, arguments and harness rows are
 * separated by one gap, so no row runs closer to its neighbour than the rest of the
 * section does.
 */
class ACPOptionsPanelSpacingTest {

    @BeforeEach
    void setUp() {
        UiTestSupport.assumeGraphicsStack();
    }

    @Test
    void serviceSectionRowsAreEvenlySpaced() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();

        List<Integer> gaps = chain(panel, "pathCombo", "argsField", "harnessCombo", "harnessHintRow");
        int min = gaps.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = gaps.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue(max - min <= 2, "the service rows must be evenly spaced, got " + gaps);
        assertTrue(min >= 4, "the service rows must not run together, got " + gaps);
    }

    @Test
    void emptyPathErrorDoesNotOpenARow() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        // The row's height is look-and-feel dependent, so an empty message would change
        // the gap above Process Arguments on some look and feels but not on others.
        assertFalse(field(panel, "pathErrorLabel").isVisible());
    }

    @Test
    void iconPreviewKeepsASquareOfItsOwn() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        Component preview = field(panel, "iconPreviewLabel");
        // The width follows the height the grid gives the preview, so let that deferred
        // adjustment land before measuring.
        for (int i = 0; i < 4; i++) {
            layout(preview.getParent());
            SwingUtilities.invokeAndWait(() -> { });
            if (preview.getWidth() == preview.getHeight()) {
                break;
            }
        }
        layout(preview.getParent());

        assertEquals(preview.getWidth(), preview.getHeight(), "the preview area must be square");
        assertTrue(preview.getWidth() >= 80, "the preview needs room, was " + preview.getWidth());
        for (String name : new String[]{"toolbarIconCombo", "chatFontCombo"}) {
            Component control = field(panel, name);
            assertTrue(control.getX() + control.getWidth() <= preview.getX(),
                    name + " must not reach into the preview column");
        }
        List<Component> rightColumn = List.of(field(panel, "iconBrowseButton"),
                field(panel, "toolbarIconCombo"), field(panel, "chatFontCombo"));
        int rightEdge = rightColumn.get(0).getX() + rightColumn.get(0).getWidth();
        for (Component control : rightColumn) {
            assertEquals(rightEdge, control.getX() + control.getWidth(), 1,
                    "the Browse button and the two size controls must share one right edge");
        }

        Component browse = field(panel, "iconBrowseButton");
        Component pathField = field(panel, "iconPathField");
        assertTrue(browse.isVisible(), "the Browse button must stay visible");
        assertTrue(browse.getWidth() > 0 && browse.getHeight() > 0,
                "the Browse button must be sized, was " + browse.getBounds());
        assertTrue(browse.getX() >= pathField.getX() + pathField.getWidth(),
                "the Browse button must not share the path field's cell");
    }

    /** Sizes a section to its preferred height and lays it out. */
    private static void layout(Container section) {
        Dimension preferred = section.getPreferredSize();
        section.setSize(preferred.width, preferred.height);
        section.doLayout();
    }

    /** Lays out the section and returns the gaps between the consecutive rows. */
    private static List<Integer> chain(ACPOptionsPanel panel, String... names) throws Exception {
        Component[] components = new Component[names.length];
        for (int i = 0; i < names.length; i++) {
            components[i] = field(panel, names[i]);
            layout(components[i].getParent());
        }
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < components.length; i++) {
            gaps.add(components[i].getY()
                    - (components[i - 1].getY() + components[i - 1].getHeight()));
        }
        return gaps;
    }

    private static Component field(Object owner, String name) throws Exception {
        Field f = ACPOptionsPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        return (Component) f.get(owner);
    }
}
