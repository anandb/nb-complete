package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
