package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import javax.swing.JCheckBox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.UiTestSupport;

/**
 * The diff-font switch replaced a system property, so the panel must show and persist it like any
 * other option rather than letting the checkbox drift from the stored value.
 */
class ACPOptionsPanelDiffFontTest {

    private boolean original;

    @BeforeEach
    void setUp() {
        UiTestSupport.assumeGraphicsStack();
        original = PluginSettings.isDiffIdeFontEnabled();
    }

    @AfterEach
    void tearDown() {
        PluginSettings.setDiffIdeFontEnabled(original);
    }

    @Test
    void checkboxRoundTripsThroughThePanel() throws Exception {
        PluginSettings.setDiffIdeFontEnabled(true);
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        JCheckBox box = checkbox(panel);
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(box, panel),
                "the switch must be part of the panel");
        assertTrue(box.isSelected(), "load() must reflect the stored setting");

        box.setSelected(false);
        panel.store();
        assertFalse(PluginSettings.isDiffIdeFontEnabled(), "store() must persist the checkbox");

        panel.load();
        assertFalse(box.isSelected());
    }

    @Test
    void labelCarriesTheRestartMarkerAndDescribesTheFont() throws Exception {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        JCheckBox box = checkbox(panel);

        assertEquals(NbBundle.getMessage(UIBundle.class, "LBL_DiffIdeFont") + " \u00b9", box.getText(),
                "the hook reads the setting at startup, so the label carries the restart marker");
        assertTrue(box.getToolTipText().contains("diff font"), box.getToolTipText());
    }

    private static JCheckBox checkbox(ACPOptionsPanel panel) throws Exception {
        Field field = ACPOptionsPanel.class.getDeclaredField("diffIdeFontCheckbox");
        field.setAccessible(true);
        return (JCheckBox) field.get(panel);
    }
}
