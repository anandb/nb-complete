package github.anandb.netbeans.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JComboBox;
import javax.swing.JLabel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.support.HarnessSelection;
import github.anandb.netbeans.support.PreferenceKeys;

/**
 * Guards the harness selector's prepopulation and store rules: a stored id the path
 * cannot explain (custom binary, container launcher) survives, an explanation by the
 * binary name is reported as Auto-detect, and store() persists the visible choice.
 */
class ACPOptionsPanelHarnessTest {

    private Preferences prefs;
    private final Map<String, String> saved = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        String display = System.getenv("DISPLAY");
        assumeTrue(display != null && !display.isEmpty(),
                "Skipping UI test: DISPLAY not set (headless environment)");
        prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        for (String key : prefs.keys()) {
            saved.put(key, prefs.get(key, null));
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (prefs == null) {
            return;
        }
        for (String key : prefs.keys()) {
            if (!saved.containsKey(key)) {
                prefs.remove(key);
            }
        }
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            if (entry.getValue() != null) {
                prefs.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Test
    void catalogPathPrepopulatesAsAutoDetect() throws Exception {
        seed("/usr/bin/copilot", "", "acp");
        ACPOptionsPanel panel = loadPanel();

        HarnessSelection.Selection selection = selectedChoice(panel);
        assertTrue(selection.auto());
        assertEquals(NbBundle.getMessage(ACPOptionsPanel.class, "HLP_HarnessDetected"), hintText(panel));

        panel.store();
        assertEquals("copilot", prefs.get(PreferenceKeys.ACP_HARNESS_ID, null));
        assertEquals("Copilot", prefs.get(PreferenceKeys.ACP_HARNESS_DISPLAY_NAME, null));
    }

    @Test
    void containerLauncherKeepsTheStoredHarnessAndItsArguments() throws Exception {
        String containerArgs = "run --rm -i harness-image acp";
        seed("/usr/bin/podman", "goose", containerArgs);
        ACPOptionsPanel panel = loadPanel();

        HarnessSelection.Selection selection = selectedChoice(panel);
        assertFalse(selection.auto());
        assertEquals("goose", selection.harness().id());
        assertEquals(NbBundle.getMessage(ACPOptionsPanel.class, "HLP_HarnessManual"), hintText(panel));

        panel.store();
        assertEquals("goose", prefs.get(PreferenceKeys.ACP_HARNESS_ID, null));
        assertEquals("Goose", prefs.get(PreferenceKeys.ACP_HARNESS_DISPLAY_NAME, null));
        assertEquals(containerArgs, prefs.get(PreferenceKeys.PROCESS_ARGUMENTS, null),
                "the harness choice must not rewrite the container command");
    }

    @Test
    void containerLauncherWithoutStoredHarnessAsksForOne() throws Exception {
        seed("/usr/bin/docker", "", "run --rm -i harness-image acp");
        ACPOptionsPanel panel = loadPanel();

        assertTrue(selectedChoice(panel).auto());
        assertEquals(NbBundle.getMessage(ACPOptionsPanel.class, "HLP_HarnessContainer"), hintText(panel));
    }

    private void seed(String path, String harnessId, String args) {
        prefs.put(PreferenceKeys.ACP_EXECUTABLE_PATH, path);
        prefs.put(PreferenceKeys.ACP_HARNESS_ID, harnessId);
        prefs.put(PreferenceKeys.ACP_HARNESS_DISPLAY_NAME, "");
        prefs.put(PreferenceKeys.PROCESS_ARGUMENTS, args);
    }

    private static ACPOptionsPanel loadPanel() {
        ACPOptionsPanel panel = new ACPOptionsPanel(new ACPOptionsPanelController());
        panel.load();
        return panel;
    }

    private static HarnessSelection.Selection selectedChoice(ACPOptionsPanel panel) throws Exception {
        JComboBox<?> combo = (JComboBox<?>) field(panel, "harnessCombo");
        assertNotNull(combo.getSelectedItem(), "the selector must never be empty");
        return (HarnessSelection.Selection) combo.getSelectedItem();
    }

    private static String hintText(ACPOptionsPanel panel) throws Exception {
        return ((JLabel) field(panel, "harnessHintLabel")).getText();
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f = ACPOptionsPanel.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }
}
