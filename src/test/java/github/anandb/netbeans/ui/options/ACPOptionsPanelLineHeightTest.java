package github.anandb.netbeans.ui.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.lang.reflect.Field;
import java.util.prefs.Preferences;

import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import github.anandb.netbeans.ui.UiTestSupport;

/**
 * Verifies that the Editor Line Height Correction preference persists across
 * store/load cycles. Requires a graphical display — skipped in headless CI.
 */
class ACPOptionsPanelLineHeightTest {

    private Preferences editorPrefs;
    private float originalValue;

    @BeforeEach
    void setUp() {
        UiTestSupport.assumeGraphicsStack();
        editorPrefs = MimeLookup.getLookup(MimePath.EMPTY).lookup(Preferences.class);
        originalValue = editorPrefs.getFloat(SimpleValueNames.LINE_HEIGHT_CORRECTION, 1.0f);
    }

    @AfterEach
    void tearDown() {
        if (editorPrefs != null) {
            editorPrefs.putFloat(SimpleValueNames.LINE_HEIGHT_CORRECTION, originalValue);
        }
    }

    private static JSpinner getLineHeightSpinner(ACPOptionsPanel panel) throws Exception {
        Field f = ACPOptionsPanel.class.getDeclaredField("lineHeightCorrectionSpinner");
        f.setAccessible(true);
        return (JSpinner) f.get(panel);
    }

    private static JCheckBox getCheckbox(ACPOptionsPanel panel, String field) throws Exception {
        Field f = ACPOptionsPanel.class.getDeclaredField(field);
        f.setAccessible(true);
        return (JCheckBox) f.get(panel);
    }

    @Test
    void storeAndLoadPersistsFractionalValue() throws Exception {
        ACPOptionsPanelController controller = new ACPOptionsPanelController();
        ACPOptionsPanel panel = new ACPOptionsPanel(controller);

        panel.load();
        JSpinner spinner = getLineHeightSpinner(panel);
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();

        // Set a fractional value and store it.
        model.setValue(1.7d);
        panel.store();

        // Reset spinner to default to prove load() actually reads the pref.
        model.setValue(1.0d);
        assertEquals(1.0d, model.getNumber().doubleValue());

        panel.load();
        double loaded = model.getNumber().doubleValue();
        assertEquals(1.7d, loaded, 0.01d,
                "load() must restore the fractional value written by store()");
    }

    @Test
    void experimentalSettingRowDoesNotAbsorbExtraVerticalSpace() throws Exception {
        ACPOptionsPanelController controller = new ACPOptionsPanelController();
        ACPOptionsPanel panel = new ACPOptionsPanel(controller);

        JCheckBox setting = getCheckbox(panel, "projectSwitchRestartCheckbox");
        Container system = setting.getParent();
        Field hintField = ACPOptionsPanel.class.getDeclaredField("projectSwitchHint");
        hintField.setAccessible(true);
        Component hint = (Component) hintField.get(panel);
        assertSame(system, hint.getParent(), "both rows must live in the System grid");

        // The Options dialog stretches sections beyond their preferred height.
        // Only a row with weighty > 0 grows; the last row must stay flush with
        // the previous one instead of centring itself in the spare space.
        Dimension preferred = system.getPreferredSize();
        system.setSize(preferred.width, preferred.height);
        system.doLayout();
        int gapBefore = hint.getY() - (setting.getY() + setting.getHeight());
        system.setSize(preferred.width, preferred.height + 80);
        system.doLayout();

        int gapAfter = hint.getY() - (setting.getY() + setting.getHeight());
        assertTrue(gapAfter - gapBefore <= 1,
                "last row must not absorb extra vertical space, gap grew from "
                        + gapBefore + "px to " + gapAfter + "px");
    }

    @Test
    void loadDefaultsWhenPrefIsOne() throws Exception {
        // Store the canonical default so load() exercises the default-value path.
        editorPrefs.putFloat(SimpleValueNames.LINE_HEIGHT_CORRECTION, 1.0f);

        ACPOptionsPanelController controller = new ACPOptionsPanelController();
        ACPOptionsPanel panel = new ACPOptionsPanel(controller);
        panel.load();

        JSpinner spinner = getLineHeightSpinner(panel);
        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
        assertEquals(1.0d, model.getNumber().doubleValue(), 0.01d,
                "spinner must show 1.0 when the stored preference is 1.0");
    }
}
