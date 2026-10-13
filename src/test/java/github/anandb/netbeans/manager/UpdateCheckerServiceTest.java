package github.anandb.netbeans.manager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.contract.UpdateCheckerControl.UpdateCheckResult;
import github.anandb.netbeans.support.PreferenceKeys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void manualCheckIsUnscheduledAndConcurrentRequestsShareOneCheck() throws Exception {
        var prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String oldSchedule = prefs.get(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, null);
        String oldEnabled = prefs.get(PreferenceKeys.CHECK_FOR_UPDATES, null);
        long scheduledTime = 1_900_000_000_000L;
        prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, scheduledTime);
        prefs.putBoolean(PreferenceKeys.CHECK_FOR_UPDATES, false);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger checkCount = new AtomicInteger();
        UpdateCheckerService service = new UpdateCheckerService(() -> {
            checkCount.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    return UpdateCheckResult.CHECK_FAILED;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return UpdateCheckResult.CHECK_FAILED;
            }
            return UpdateCheckResult.NO_UPDATE;
        });

        try {
            var first = service.checkNow();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = service.checkNow();
            assertSame(first, second);
            release.countDown();

            assertEquals(UpdateCheckResult.NO_UPDATE, first.get(5, TimeUnit.SECONDS));
            assertEquals(1, checkCount.get());
            assertEquals(scheduledTime, prefs.getLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, 0L));
        } finally {
            release.countDown();
            if (oldSchedule == null) {
                prefs.remove(PreferenceKeys.NEXT_UPDATE_CHECK_TIME);
            } else {
                prefs.put(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, oldSchedule);
            }
            if (oldEnabled == null) {
                prefs.remove(PreferenceKeys.CHECK_FOR_UPDATES);
            } else {
                prefs.put(PreferenceKeys.CHECK_FOR_UPDATES, oldEnabled);
            }
        }
    }
}
