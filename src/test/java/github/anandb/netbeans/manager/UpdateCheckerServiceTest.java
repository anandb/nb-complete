package github.anandb.netbeans.manager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.contract.UpdateCheckerControl.UpdateCheckResult;
import github.anandb.netbeans.support.PreferenceKeys;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void parseLatestVersionPrefersRelease() {
        String metadata = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>io.github.anandb</groupId>
                  <artifactId>beanbot</artifactId>
                  <versioning>
                    <latest>1.26.1</latest>
                    <release>1.26.0</release>
                    <versions>
                      <version>1.25.0</version>
                      <version>1.26.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        assertEquals("1.26.0", UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void parseLatestVersionFallsBackToLatest() {
        String metadata = """
                <metadata><versioning><latest>2.0.0</latest></versioning></metadata>
                """;

        assertEquals("2.0.0", UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void parseLatestVersionRejectsBlankAndMalformedInput() {
        assertNull(UpdateCheckerService.parseLatestVersion(null));
        assertNull(UpdateCheckerService.parseLatestVersion("   "));
        assertNull(UpdateCheckerService.parseLatestVersion("<metadata><versioning>"));
    }

    @Test
    void parseLatestVersionFallsBackToTheVersionList() {
        String metadata = """
                <metadata><versioning><versions>
                  <version>1.0.0</version>
                  <version>1.2.0</version>
                  <version>1.1.0</version>
                </versions></versioning></metadata>
                """;

        assertEquals("1.2.0", UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void milestoneReleaseIsSkippedForTheNewestStableVersion() {
        String metadata = """
                <metadata><versioning>
                  <latest>1.30.0-M2</latest>
                  <release>1.30.0-M2</release>
                  <versions>
                    <version>1.28.0</version>
                    <version>1.29.0</version>
                    <version>1.30.0-M1</version>
                    <version>1.30.0-M2</version>
                  </versions>
                </versioning></metadata>
                """;

        assertEquals("1.29.0", UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void milestoneLatestAloneIsNotOffered() {
        String metadata = """
                <metadata><versioning><latest>1.30.0-M1</latest></versioning></metadata>
                """;

        assertNull(UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void milestoneOnlyVersionListIsNotOffered() {
        String metadata = """
                <metadata><versioning><versions>
                  <version>1.30.0-M1</version>
                  <version>1.30.0-M12</version>
                </versions></versioning></metadata>
                """;

        assertNull(UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void stableReleaseStillWinsOverANewerMilestoneInTheVersionList() {
        String metadata = """
                <metadata><versioning>
                  <release>1.29.0</release>
                  <versions>
                    <version>1.29.0</version>
                    <version>1.30.0-M1</version>
                  </versions>
                </versioning></metadata>
                """;

        assertEquals("1.29.0", UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void offerableVersionsArePlainOrRelease() {
        assertTrue(UpdateCheckerService.isOfferableVersion("1.30.0"));
        assertTrue(UpdateCheckerService.isOfferableVersion("1.30"));
        assertTrue(UpdateCheckerService.isOfferableVersion("  1.30.0  "));
        assertTrue(UpdateCheckerService.isOfferableVersion("1.30.0-RELEASE"));
        assertTrue(UpdateCheckerService.isOfferableVersion("1.30.0.RELEASE"));
        assertTrue(UpdateCheckerService.isOfferableVersion("1.30.0-release"));
        assertFalse(UpdateCheckerService.isOfferableVersion("1.30.0-M1"));
        assertFalse(UpdateCheckerService.isOfferableVersion("1.30.0-M12"));
        assertFalse(UpdateCheckerService.isOfferableVersion("1.30.0-RC1"));
        assertFalse(UpdateCheckerService.isOfferableVersion("1.30.0-SNAPSHOT"));
        assertFalse(UpdateCheckerService.isOfferableVersion("release"));
        assertFalse(UpdateCheckerService.isOfferableVersion(null));
    }

    @Test
    void releaseQualifierDoesNotChangeOrdering() {
        assertFalse(UpdateCheckerService.isNewerVersion("1.30.0-RELEASE", "1.30.0"));
        assertTrue(UpdateCheckerService.isNewerVersion("1.30.0-RELEASE", "1.29.0"));
        assertTrue(UpdateCheckerService.isNewerVersion("1.30.0", "1.29.0-RELEASE"));
    }

    @Test
    void releaseQualifiedVersionIsOffered() {
        String metadata = """
                <metadata><versioning><versions>
                  <version>1.29.0</version>
                  <version>1.30.0-RELEASE</version>
                </versions></versioning></metadata>
                """;

        assertEquals("1.30.0-RELEASE", UpdateCheckerService.parseLatestVersion(metadata));
    }

    @Test
    void parseLatestVersionRefusesDoctype() {
        String hostile = """
                <!DOCTYPE metadata [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <metadata><versioning><release>&xxe;</release></versioning></metadata>
                """;

        assertNull(UpdateCheckerService.parseLatestVersion(hostile));
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

    @Test
    void cancelStopsPendingCycleAndStartResumesLoop() {
        var prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String oldSchedule = prefs.get(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, null);
        String oldEnabled = prefs.get(PreferenceKeys.CHECK_FOR_UPDATES, null);
        try {
            // An hour out, so the first cycle can never race into a check.
            prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME,
                    System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
            prefs.putBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true);

            AtomicInteger checks = new AtomicInteger();
            UpdateCheckerService service = new UpdateCheckerService(() -> {
                checks.incrementAndGet();
                return UpdateCheckResult.NO_UPDATE;
            });

            // First cycle runs at once, sees the check is not due, reschedules itself.
            service.start();
            await().during(500, TimeUnit.MILLISECONDS).atMost(1500, TimeUnit.MILLISECONDS)
                    .until(() -> checks.get() == 0);

            // cancel() must take that pending run off the single task, even once due.
            service.cancel();
            prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, System.currentTimeMillis() - 1);
            await().during(500, TimeUnit.MILLISECONDS).atMost(1500, TimeUnit.MILLISECONDS)
                    .until(() -> checks.get() == 0);

            // start() resumes on the same sliding task.
            service.start();
            await().atMost(5, TimeUnit.SECONDS).until(() -> checks.get() == 1);

            // A second start() while the loop is pending must not queue a cycle.
            service.start();
            await().during(500, TimeUnit.MILLISECONDS).atMost(1500, TimeUnit.MILLISECONDS)
                    .until(() -> checks.get() == 1);
            service.cancel();
        } finally {
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
