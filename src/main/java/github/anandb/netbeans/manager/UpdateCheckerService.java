package github.anandb.netbeans.manager;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.io.StringReader;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import java.util.function.Supplier;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.contract.UpdateCheckerControl;
import github.anandb.netbeans.contract.UpdateCheckerControl.UpdateCheckResult;
import github.anandb.netbeans.support.AgentUtils;
import github.anandb.netbeans.support.AppLinks;
import github.anandb.netbeans.support.BrowserUtils;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;

@ServiceProvider(service = UpdateCheckerControl.class)
public class UpdateCheckerService implements UpdateCheckerControl {
    private static final Logger LOG = Logger.from(UpdateCheckerService.class);

    // HTTP_CLIENT removed to prevent capturing ProxySelector at class load time

    /** Delay before the first update check after IDE startup. */
    private static final long FIRST_CHECK_DELAY_MS = 60_000L;

    /** Daemon request processor for the update-check loop. */
    private static final RequestProcessor RP =
            new RequestProcessor("ACP-UpdateChecker", 1, true);

    /** Cached singleton — populated lazily from Lookup. */
    private static volatile UpdateCheckerService instance;

    private volatile boolean running;

    /**
     * Single sliding task for the check loop. It is rescheduled, never re-created,
     * so {@link #cancel()} always cancels the one pending run and a repeated
     * {@code scheduleNext} coalesces instead of queueing extra cycles.
     */
    private final RequestProcessor.Task checkCycleTask = RP.create(this::runCheckCycle);
    private final Supplier<UpdateCheckResult> updateCheck;
    private CompletableFuture<UpdateCheckResult> manualCheckInFlight;

    public UpdateCheckerService() {
        this(null);
    }

    UpdateCheckerService(Supplier<UpdateCheckResult> updateCheck) {
        this.updateCheck = updateCheck == null ? this::checkForUpdates : updateCheck;
    }

    /**
     * Returns the singleton registered via {@link ServiceProvider}.
     * Falls back to a fresh instance if Lookup is not yet populated (should
     * not happen under normal startup).
     */
    public static UpdateCheckerService getInstance() {
        UpdateCheckerService ucs = instance;
        if (ucs == null) {
            ucs = Lookup.getDefault().lookup(UpdateCheckerService.class);
            if (ucs == null) {
                LOG.severe("UpdateCheckerService not found in Lookup — ServiceProvider registration missing?");
                ucs = new UpdateCheckerService();
            }
            instance = ucs;
        }
        return ucs;
    }

    /**
     * Called when the plugin is installed or upgraded.
     * If a future check is already scheduled, keeps it. Otherwise reschedules
     * with a random 16-24h window and triggers an immediate update check.
     */
    @Override
    public void onInstallOrUpgrade() {
        long stored = prefs().getLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, 0L);
        if (stored > System.currentTimeMillis()) {
            LOG.info("Plugin installed/upgraded. Next update check already scheduled for: {0}", new Date(stored));
            return;
        }
        // Past-due or first install — check immediately; next time will be randomized
        prefs().putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, System.currentTimeMillis());
        LOG.info("Plugin installed/upgraded. Scheduling immediate update check.");
    }

    /**
     * Starts the background daemon task on the {@link RequestProcessor}.
     * Safe to call multiple times — subsequent calls are no-ops.
     * Does NOT run an immediate check — respects the stored schedule
     * from the previous cycle (or from {@link #onInstallOrUpgrade()}).
     */
    @Override
    public synchronized void start() {
        if (running && !checkCycleTask.isFinished()) {
            return;
        }
        running = true;
        checkCycleTask.schedule(0);
        LOG.info("UpdateCheckerService daemon task submitted.");
    }

    /** Cancels the running update-check loop, if any. */
    public synchronized void cancel() {
        running = false;
        checkCycleTask.cancel();
    }

    /** Runs a manual check independently of the background schedule and setting. */
    @Override
    public synchronized CompletableFuture<UpdateCheckResult> checkNow() {
        if (manualCheckInFlight != null && !manualCheckInFlight.isDone()) {
            return manualCheckInFlight;
        }
        CompletableFuture<UpdateCheckResult> future = new CompletableFuture<>();
        manualCheckInFlight = future;
        RP.post(() -> {
            try {
                future.complete(updateCheck.get());
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private static Preferences prefs() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
    }

    private void runCheckCycle() {
        if (!running) return;
        try {
            Preferences prefs = prefs();
            long now = System.currentTimeMillis();
            long timeOfExecution = prefs.getLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, 0L);

            // If not initialized, set first check 1 minute from now.
            if (timeOfExecution == 0L) {
                timeOfExecution = now + FIRST_CHECK_DELAY_MS;
                prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, timeOfExecution);
            }

            if (now >= timeOfExecution) {
                long interval = getRandomIntervalMillis();
                // Compute next time of execution BEFORE the update check
                long nextTime = System.currentTimeMillis() + interval;
                prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, nextTime);

                boolean checkEnabled = prefs.getBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true);

                if (checkEnabled) {
                    try {
                        LOG.info("Checking for updates...");
                        updateCheck.get();
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error checking for updates: {0}", ExceptionUtils.getMessage(e), e);
                    }
                } else {
                    LOG.fine("Update check is disabled in settings. Skipping update check.");
                }

                scheduleNext(interval);
            } else {
                long delay = timeOfExecution - now;
                scheduleNext(delay > 0 ? delay : 1000L);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Unexpected error in update checker cycle: {0}", e);
            scheduleNext(60000L);
        }
    }

    private synchronized void scheduleNext(long delayMillis) {
        if (running) {
            checkCycleTask.schedule((int) Math.min(delayMillis, (long) Integer.MAX_VALUE));
        }
    }

    private UpdateCheckResult checkForUpdates() {
        String updateUrl = AppLinks.updateUrl();
        LOG.fine("Checking for updates from {0}", updateUrl);

        try {
            String ua = String.format(
                "Mozilla/5.0 (compatible; %s %s; NetBeans %s; Java %s) BeanBot/%s",
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                System.getProperty("netbeans.version", "unknown"),
                System.getProperty("java.version", "unknown"),
                AgentUtils.getVersion()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(updateUrl))
                    .header("User-Agent", ua)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .proxy(ProxySelector.getDefault())
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // A missing manifest is not an error — a fork or a self-hosted
                // deployment may not publish one at all. Stay silent.
                LOG.fine("No update manifest at {0} (HTTP {1})", updateUrl, response.statusCode());
                return UpdateCheckResult.CHECK_FAILED;
            }

            String body = response.body();
            String latestVersion = parseLatestVersion(body);
            if (latestVersion == null) {
                LOG.fine("Update metadata at {0} carries no release or latest version", updateUrl);
                return UpdateCheckResult.CHECK_FAILED;
            }

            String currentVersionStr = AgentUtils.getVersion();

            if (isNewerVersion(latestVersion, currentVersionStr)) {
                String skipped = prefs().get(PreferenceKeys.SKIPPED_UPDATE_VERSION, "");
                if (latestVersion.equals(skipped)) {
                    LOG.info("Update v{0} skipped by user preference, not prompting.", latestVersion);
                    return UpdateCheckResult.UPDATE_SKIPPED;
                } else {
                    LOG.info("New update found: {0} (current: {1}).",
                            new Object[]{latestVersion, currentVersionStr});

                    showNotification(latestVersion,
                            AppLinks.nbmDownloadUrl(latestVersion),
                            AppLinks.changelogUrl());
                    return UpdateCheckResult.UPDATE_AVAILABLE;
                }
            } else {
                LOG.info("Already on the latest version: {0}", currentVersionStr);
                return UpdateCheckResult.NO_UPDATE;
            }
        } catch (Exception e) {
            // Best effort: an offline machine, an unreachable host or a malformed
            // manifest must not surface anywhere.
            LOG.fine("Update check skipped: {0}", ExceptionUtils.getMessage(e));
            return UpdateCheckResult.CHECK_FAILED;
        }
    }

    /**
     * Extracts the newest published version from a Maven {@code maven-metadata.xml}.
     * A non-empty {@code <release>} wins; {@code <latest>} is the fallback. DTDs and
     * external entities are disabled, so a malformed or hostile document can neither
     * expand entities nor make the parser fetch anything.
     *
     * @param xml metadata document
     * @return the version, or {@code null} when the document is blank, malformed, or
     *         carries neither element
     */
    static String parseLatestVersion(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                String release = null;
                String latest = null;
                String element = null;
                StringBuilder text = new StringBuilder();
                while (reader.hasNext()) {
                    switch (reader.next()) {
                        case XMLStreamConstants.START_ELEMENT -> {
                            element = reader.getLocalName();
                            text.setLength(0);
                        }
                        case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                                text.append(reader.getText());
                        case XMLStreamConstants.END_ELEMENT -> {
                            String value = text.toString().trim();
                            if ("release".equals(element)) {
                                release = value;
                            } else if ("latest".equals(element)) {
                                latest = value;
                            }
                            element = null;
                        }
                        default -> {
                            // comments, processing instructions, whitespace — ignored
                        }
                    }
                }
                if (release != null && !release.isEmpty()) {
                    return release;
                }
                return latest == null || latest.isEmpty() ? null : latest;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isNewerVersion(String latestVersionStr, String currentVersionStr) {
        if (latestVersionStr == null || currentVersionStr == null) {
            return false;
        }
        try {
            SpecificationVersion current = new SpecificationVersion(currentVersionStr);
            SpecificationVersion latest = new SpecificationVersion(latestVersionStr);
            return latest.compareTo(current) > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void showNotification(String latestVersion, String downloadUrl, String notesUrl) {
        // Only show the message box once the IDE is fully loaded — showing it
        // during startup would cancel/close the dialog. The scheduling of the
        // check cycle can happen anytime; it's the dialog that must wait.
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            String title = "Coding Assistant Update Available";
            String body = "A new version of Coding Assistant (" + latestVersion
                    + ") is available.\n\nCurrent: " + AgentUtils.getVersion()
                    + "\nLatest: " + latestVersion
                    + "\n\nAfter downloading the .nbm file, go to "
                    + "Tools > Plugins > Downloaded and click \"Add Plugins\" "
                    + "to select the downloaded file, then click \"Install\".";

            List<String> options = new ArrayList<>(List.of(
                    "Download Now", "Remind me Later", "Skip this Version"));
            // The notes URL is derived from the version, so it is always available.
            options.add(0, "View Release Notes");
            NotifyDescriptor nd = new NotifyDescriptor(
                    body,
                    title,
                    NotifyDescriptor.DEFAULT_OPTION,
                    NotifyDescriptor.INFORMATION_MESSAGE,
                    options.toArray(new Object[0]),
                    "Remind me Later"
            );
            Object result = DialogDisplayer.getDefault().notify(nd);
            if ("Download Now".equals(result)) {
                BrowserUtils.openOrCopyUrl(downloadUrl, null, null);
            } else if ("Skip this Version".equals(result)) {
                prefs().put(PreferenceKeys.SKIPPED_UPDATE_VERSION, latestVersion);
            } else if ("View Release Notes".equals(result)) {
                BrowserUtils.openOrCopyUrl(notesUrl, null, null);
            }
            // "Remind me Later" — just dismiss, next cycle prompts again
        });
    }

    long getRandomIntervalMillis() {
        long min = 16L * 60 * 60 * 1000;
        long max = 24L * 60 * 60 * 1000;
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
