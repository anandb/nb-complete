package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

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
}
