package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerServiceTest {

    @Test
    void testIsNewerVersion() {
        // Higher version available
        assertTrue(UpdateCheckerService.isNewerVersion("1.7.3", "1.7.2"));
        assertTrue(UpdateCheckerService.isNewerVersion("2.0.0", "1.7.2"));
        assertTrue(UpdateCheckerService.isNewerVersion("1.8", "1.7.2"));

        // Same or lower version
        assertFalse(UpdateCheckerService.isNewerVersion("1.7.2", "1.7.2"));
        assertFalse(UpdateCheckerService.isNewerVersion("1.7.1", "1.7.2"));
        assertFalse(UpdateCheckerService.isNewerVersion("1.0.0", "1.7.2"));

        // Invalid version formats
        assertFalse(UpdateCheckerService.isNewerVersion("invalid", "1.7.2"));
        assertFalse(UpdateCheckerService.isNewerVersion("1.7.3", "invalid"));
        assertFalse(UpdateCheckerService.isNewerVersion(null, "1.7.2"));
        assertFalse(UpdateCheckerService.isNewerVersion("1.7.3", null));
    }

    @Test
    void testGetRandomIntervalMillis() {
        UpdateCheckerService service = new UpdateCheckerService();
        long min = 16L * 60 * 60 * 1000;
        long max = 24L * 60 * 60 * 1000;

        for (int i = 0; i < 100; i++) {
            long interval = service.getRandomIntervalMillis();
            assertTrue(interval >= min, "Interval " + interval + " should be >= min " + min);
            assertTrue(interval <= max, "Interval " + interval + " should be <= max " + max);
        }
    }
}
