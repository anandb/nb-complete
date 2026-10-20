package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.model.HarnessCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSettingsTest {

    @Test
    void testMiniAssistantPreferenceKey() {
        assertEquals("miniAssistant.enabled", PreferenceKeys.MINI_ASSISTANT_ENABLED);
    }

    @Test
    void testMiniAssistantToggleRoundTrip() {
        java.util.prefs.Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        PluginSettings.setMiniAssistantEnabled(false);
        assertFalse(prefs.getBoolean(PreferenceKeys.MINI_ASSISTANT_ENABLED, true));
        PluginSettings.setMiniAssistantEnabled(true);
        assertTrue(prefs.getBoolean(PreferenceKeys.MINI_ASSISTANT_ENABLED, false));
    }

    @Test
    void testPreambleToggleRoundTrip() {
        // Assert the persisted values only: the cached accessor is updated
        // asynchronously by the PreferenceChangeListener and can briefly lag.
        java.util.prefs.Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        PluginSettings.setPreambleEnabled(false);
        assertFalse(prefs.getBoolean(PreferenceKeys.PREAMBLE_ENABLED, true));
        PluginSettings.setPreambleEnabled(true);
        assertTrue(prefs.getBoolean(PreferenceKeys.PREAMBLE_ENABLED, false));
    }

    @Test
    void testHarnessOverlayAppendedForKnownHarness() {
        java.util.prefs.Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String previous = prefs.get(PreferenceKeys.ACP_HARNESS_ID, null);
        try {
            // A harness with an overlay gets the universal rules plus its mechanics.
            prefs.put(PreferenceKeys.ACP_HARNESS_ID, "claude");
            String rules = PluginSettings.getCriticalRules();
            assertTrue(rules.contains("NEVER automatically commit or push"),
                    "universal base rules must always be present");
            assertTrue(rules.contains("sub-agent"),
                    "the claude overlay's tool mechanics must be appended");

            // A harness without an overlay (or unknown) gets the base only.
            prefs.put(PreferenceKeys.ACP_HARNESS_ID, "goose");
            assertFalse(PluginSettings.getCriticalRules().contains("sub-agent"),
                    "harnesses without an overlay must not see another harness's mechanics");
            prefs.put(PreferenceKeys.ACP_HARNESS_ID, "unknown");
            assertFalse(PluginSettings.getCriticalRules().contains("sub-agent"));
        } finally {
            if (previous == null) {
                prefs.remove(PreferenceKeys.ACP_HARNESS_ID);
            } else {
                prefs.put(PreferenceKeys.ACP_HARNESS_ID, previous);
            }
        }
    }

    @Test
    void testEveryCatalogHarnessHasAnOverlayResource() {
        // Guards id/file drift: adding a harness without critical_rules/<id>.md
        // (or renaming an id) must fail here, not silently drop the overlay.
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            String overlay = AgentUtils.readResource("critical_rules/" + harness.id() + ".md");
            assertTrue(overlay != null && !overlay.isBlank(),
                    "missing critical_rules/" + harness.id() + ".md for harness '" + harness.id() + "'");
        }
    }
}
