package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ACPSettingsTest {

    @Test
    void testPreamblePersistence() {
        String original = ACPSettings.getPreamble();
        try {
            ACPSettings.setPreamble("Test Preamble");
            assertEquals("Test Preamble", ACPSettings.getPreamble());
        } finally {
            ACPSettings.setPreamble(original);
        }
    }
}
