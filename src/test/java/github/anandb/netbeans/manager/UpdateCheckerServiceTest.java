package github.anandb.netbeans.manager;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.openide.NotifyDescriptor;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.contract.UpdateCheckerControl.UpdateCheckResult;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerServiceTest {

    private static final String UPDATE_VERSION = "9999.0.0";

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
    void finalReleaseIsNewerThanItsMilestones() {
        assertTrue(UpdateCheckerService.isNewerVersion("1.29.0", "1.29.0-M1"));
        assertTrue(UpdateCheckerService.isNewerVersion("1.29.0-RELEASE", "1.29.0-M2"));
        assertFalse(UpdateCheckerService.isNewerVersion("1.29.0-M2", "1.29.0"));
        assertFalse(UpdateCheckerService.isNewerVersion("1.29.0-M1", "1.29.0-M1"));
        assertTrue(UpdateCheckerService.isNewerVersion("1.30.0", "1.29.0-M1"));
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
    void installOrUpgradeKeepsFutureScheduleAndResetsExpiredSchedule() {
        var prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String previous = prefs.get(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, null);
        try {
            long future = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
            prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, future);
            new UpdateCheckerService(() -> UpdateCheckResult.NO_UPDATE).onInstallOrUpgrade();
            assertEquals(future, prefs.getLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, 0L));

            prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, 1L);
            long before = System.currentTimeMillis();
            new UpdateCheckerService(() -> UpdateCheckResult.NO_UPDATE).onInstallOrUpgrade();
            long scheduled = prefs.getLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, 0L);
            assertTrue(scheduled >= before);
            assertTrue(scheduled <= System.currentTimeMillis());
        } finally {
            restore(prefs, PreferenceKeys.NEXT_UPDATE_CHECK_TIME, previous);
        }
    }

    @Test
    void checkForUpdatesHandlesCurrentSkippedFailedAndAvailableResults() throws Exception {
        var prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String previousUrl = System.getProperty(PreferenceKeys.UPDATE_URL_PROP);
        String previousSkipped = prefs.get(PreferenceKeys.SKIPPED_UPDATE_VERSION, null);
        String manifest = "<metadata><versioning><release>" + UPDATE_VERSION
                + "</release></versioning></metadata>";
        RecordingUpdateUi ui = new RecordingUpdateUi("Remind me Later");
        RecordingHttpClient client = new RecordingHttpClient();
        UpdateCheckerService service = new UpdateCheckerService(null, ui, client, () -> "0.9.0");
        try {
            System.setProperty(PreferenceKeys.UPDATE_URL_PROP, "https://example.test/metadata");
            client.respond(404, "");
            assertEquals(UpdateCheckResult.CHECK_FAILED, service.checkForUpdates());

            client.respond(200, "not xml");
            assertEquals(UpdateCheckResult.CHECK_FAILED, service.checkForUpdates());

            client.respond(200, "<metadata><versioning><release>0.0.0</release></versioning></metadata>");
            assertEquals(UpdateCheckResult.NO_UPDATE, service.checkForUpdates());

            System.setProperty(PreferenceKeys.UPDATE_URL_PROP, "not a valid URI");
            assertEquals(UpdateCheckResult.CHECK_FAILED, service.checkForUpdates());

            System.setProperty(PreferenceKeys.UPDATE_URL_PROP, "https://example.test/metadata");
            client.respond(200, manifest);
            prefs.put(PreferenceKeys.SKIPPED_UPDATE_VERSION, UPDATE_VERSION);
            assertEquals(UpdateCheckResult.UPDATE_SKIPPED, service.checkForUpdates());

            prefs.remove(PreferenceKeys.SKIPPED_UPDATE_VERSION);
            assertEquals(UpdateCheckResult.UPDATE_AVAILABLE, service.checkForUpdates());
            assertEquals(1, ui.notifications.size());
        } finally {
            restoreSystemProperty(PreferenceKeys.UPDATE_URL_PROP, previousUrl);
            restore(prefs, PreferenceKeys.SKIPPED_UPDATE_VERSION, previousSkipped);
        }
    }

    @Test
    void notificationActionsOpenLinksStoreSkippedVersionAndAllowDismissal() throws Exception {
        var prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String previousUrl = System.getProperty(PreferenceKeys.UPDATE_URL_PROP);
        String previousSkipped = prefs.get(PreferenceKeys.SKIPPED_UPDATE_VERSION, null);
        String manifest = "<metadata><versioning><release>" + UPDATE_VERSION
                + "</release></versioning></metadata>";
        System.setProperty(PreferenceKeys.UPDATE_URL_PROP, "https://example.test/metadata");
        try {
            for (String result : List.of("Download Now", "Skip this Version",
                    "View Release Notes", "Remind me Later")) {
                RecordingUpdateUi ui = new RecordingUpdateUi(result);
                RecordingHttpClient client = new RecordingHttpClient();
                client.respond(200, manifest);
                UpdateCheckerService service = new UpdateCheckerService(
                        null, ui, client, () -> "0.9.0");
                assertEquals(UpdateCheckResult.UPDATE_AVAILABLE, service.checkForUpdates());
                assertEquals(1, ui.notifications.size());
                assertEquals(4, ui.lastDescriptor.getOptions().length);
                if ("Download Now".equals(result)) {
                    assertEquals(List.of("https://repo1.maven.org/maven2/io/github/anandb/beanbot/"
                            + UPDATE_VERSION + "/beanbot-" + UPDATE_VERSION
                            + ".nbm"), ui.openedUrls);
                } else if ("View Release Notes".equals(result)) {
                    assertEquals(List.of("https://github.com/anandb/nb-complete/blob/main/CHANGELOG.md"),
                            ui.openedUrls);
                } else if ("Skip this Version".equals(result)) {
                    assertEquals(UPDATE_VERSION, prefs.get(PreferenceKeys.SKIPPED_UPDATE_VERSION, null));
                } else {
                    assertTrue(ui.openedUrls.isEmpty());
                }
                prefs.remove(PreferenceKeys.SKIPPED_UPDATE_VERSION);
            }
        } finally {
            restoreSystemProperty(PreferenceKeys.UPDATE_URL_PROP, previousUrl);
            restore(prefs, PreferenceKeys.SKIPPED_UPDATE_VERSION, previousSkipped);
        }
    }

    private static void restore(java.util.prefs.Preferences prefs, String key, String value) {
        if (value == null) {
            prefs.remove(key);
        } else {
            prefs.put(key, value);
        }
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static final class RecordingUpdateUi implements UpdateCheckerService.UpdateUi {
        private final String result;
        private final List<NotifyDescriptor> notifications = new ArrayList<>();
        private final List<String> openedUrls = new ArrayList<>();
        private NotifyDescriptor lastDescriptor;

        private RecordingUpdateUi(String result) {
            this.result = result;
        }

        @Override
        public void whenReady(Runnable action) {
            action.run();
        }

        @Override
        public Object notify(NotifyDescriptor descriptor) {
            lastDescriptor = descriptor;
            notifications.add(descriptor);
            return result;
        }

        @Override
        public void openUrl(String url) {
            openedUrls.add(url);
        }
    }

    private static final class RecordingHttpClient extends HttpClient {
        private int statusCode;
        private String body;

        private void respond(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException {
            return (HttpResponse<T>) new RecordingHttpResponse(request, statusCode, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }

    private record RecordingHttpResponse(HttpRequest request, int statusCode, String body)
            implements HttpResponse<String> {
        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            BiPredicate<String, String> acceptAll = (name, value) -> true;
            return HttpHeaders.of(Map.of(), acceptAll);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
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
        boolean oldEnabled = PluginSettings.isCheckForUpdates();
        long scheduledTime = 1_900_000_000_000L;
        prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, scheduledTime);
        PluginSettings.setCheckForUpdates(false);

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
            PluginSettings.setCheckForUpdates(oldEnabled);
        }
    }

    @Test
    void cancelStopsPendingCycleAndStartResumesLoop() {
        var prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String oldSchedule = prefs.get(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, null);
        boolean oldEnabled = PluginSettings.isCheckForUpdates();
        try {
            // An hour out, so the first cycle can never race into a check.
            prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME,
                    System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
            PluginSettings.setCheckForUpdates(true);

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
            PluginSettings.setCheckForUpdates(oldEnabled);
        }
    }
}
