package github.anandb.netbeans.manager;

import github.anandb.netbeans.support.PluginSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ACPSettingsTest {

    @Test
    void testPreamblePersistence() {
        String original = PluginSettings.getPreamble();
        try {
            PluginSettings.setPreamble("Test Preamble");
            assertEquals("Test Preamble", PluginSettings.getPreamble());
        } finally {
            PluginSettings.setPreamble(original);
        }
    }

    @Test
    void preambleBeyondOnePreferenceValueIsRejectedWithoutTouchingTheStoredOne() {
        String original = PluginSettings.getPreamble();
        try {
            String atLimit = "x".repeat(PluginSettings.MAX_PREAMBLE_LENGTH);
            String tooLong = atLimit + "x";
            assertFalse(PluginSettings.isPreambleTooLong(atLimit));
            assertTrue(PluginSettings.isPreambleTooLong(tooLong));

            assertThrows(IllegalArgumentException.class, () -> PluginSettings.setPreamble(tooLong));
            assertEquals(original, PluginSettings.getPreamble(), "the stored preamble is untouched");
        } finally {
            PluginSettings.setPreamble(original);
        }
    }
}
