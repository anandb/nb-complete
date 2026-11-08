package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import github.anandb.netbeans.ui.UiTestSupport;

/**
 * Guards the Assistant row rhythm: the binary, arguments and harness rows are
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
        Component box = field(panel, "iconPreviewBox");
        Container appearance = box.getParent().getParent();
        // The width follows the height the box gives the preview, so let that deferred
        // adjustment land before measuring; the section's two columns size the box.
        for (int i = 0; i < 4; i++) {
            layout(appearance);
            SwingUtilities.invokeAndWait(() -> { });
            if (preview.getWidth() == preview.getHeight()) {
                break;
            }
        }
        layout(appearance);

        assertEquals(preview.getWidth(), preview.getHeight(), "the preview area must be square");
        assertTrue(preview.getWidth() >= 80, "the preview needs room, was " + preview.getWidth());
        JPanel previewBox = (JPanel) box;
        int titleWidth = ((TitledBorder) previewBox.getBorder()).getMinimumSize(previewBox).width;
        assertTrue(previewBox.getPreferredSize().width >= titleWidth,
                "the box must be wide enough for the whole title, was "
                        + previewBox.getPreferredSize().width + " vs " + titleWidth);
        Rectangle boxBounds = SwingUtilities.convertRectangle(
                box.getParent(), box.getBounds(), appearance);
        for (String name : new String[]{"toolbarIconCombo", "chatFontCombo"}) {
            Component control = field(panel, name);
            Rectangle bounds = SwingUtilities.convertRectangle(
                    control.getParent(), control.getBounds(), appearance);
            assertTrue(bounds.x + bounds.width <= boxBounds.x,
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

    /** Sizes a section to its preferred height, lays it out, and lays out its nested containers. */
    private static void layout(Container section) {
        Dimension preferred = section.getPreferredSize();
        section.setSize(preferred.width, preferred.height);
        section.doLayout();
        for (Component child : section.getComponents()) {
            if (child instanceof Container nested) {
                layoutChildren(nested);
            }
        }
    }

    /** Lays out a container within the bounds its parent assigned, all the way down. */
    private static void layoutChildren(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layoutChildren(nested);
            }
        }
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
