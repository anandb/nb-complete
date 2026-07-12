package github.anandb.netbeans.manager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import org.openide.awt.NotificationDisplayer;
import org.openide.modules.SpecificationVersion;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import github.anandb.netbeans.contract.UpdateCheckerControl;
import github.anandb.netbeans.support.AgentUtils;
import github.anandb.netbeans.support.BrowserUtils;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PreferenceKeys;

@ServiceProvider(service = UpdateCheckerControl.class)
public class UpdateCheckerService implements UpdateCheckerControl {

    private static final Logger LOG = Logger.from(UpdateCheckerService.class);
    private static final String UPDATE_URL = "https://anandb.github.io/beanbot.json";

    /** Shared HTTP client — thread-safe, reused across checks. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** Delay before the first update check after IDE startup. */
    private static final long FIRST_CHECK_DELAY_MS = 60_000L;

    /** Daemon request processor for the update-check loop. */
    private static final RequestProcessor RP =
            new RequestProcessor("ACP-UpdateChecker", 1, true);

    /** Cached singleton — populated lazily from Lookup. */
    private static volatile UpdateCheckerService instance;

    private volatile boolean running;
    private volatile RequestProcessor.Task rpTask;

    public UpdateCheckerService() {
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
     * Computes a random time interval between 16 to 24 hours, adds that to current time
     * to get time of execution, and saves it in preferences.
     */
    @Override
    public void onInstallOrUpgrade() {
        long stored = prefs().getLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, 0L);
        if (stored > System.currentTimeMillis()) {
            LOG.info("Plugin installed/upgraded. Next update check already scheduled for: {0}", new java.util.Date(stored));
            return;
        }
        long timeOfExecution = System.currentTimeMillis() + FIRST_CHECK_DELAY_MS;
        prefs().putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, timeOfExecution);
        LOG.info("Plugin installed/upgraded. Scheduled first update check for: {0}", new java.util.Date(timeOfExecution));
    }

    /**
     * Starts the background daemon task on the {@link RequestProcessor}.
     * Safe to call multiple times — subsequent calls are no-ops.
     * Does NOT run an immediate check — respects the stored schedule
     * from the previous cycle (or from {@link #onInstallOrUpgrade()}).
     */
    @Override
    public synchronized void start() {
        if (rpTask != null && !rpTask.isFinished()) {
            return;
        }
        running = true;
        rpTask = RP.create(this::runCheckCycle);
        rpTask.schedule(0);
        LOG.info("UpdateCheckerService daemon task submitted.");
    }

    /** Cancels the running update-check loop, if any. */
    public synchronized void cancel() {
        running = false;
        if (rpTask != null) {
            rpTask.cancel();
            rpTask = null;
        }
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
                // Compute next time of execution BEFORE the update check
                long nextTime = System.currentTimeMillis() + getRandomIntervalMillis();
                prefs.putLong(PreferenceKeys.NEXT_UPDATE_CHECK_TIME, nextTime);

                boolean checkEnabled = prefs.getBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true);

                if (checkEnabled) {
                    try {
                        LOG.info("Checking for updates...");
                        checkForUpdates();
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error checking for updates: {0}", e.getMessage(), e);
                    }
                } else {
                    LOG.fine("Update check is disabled in settings. Skipping update check.");
                }

                scheduleNext(getRandomIntervalMillis());
            } else {
                long delay = timeOfExecution - now;
                scheduleNext(delay > 0 ? delay : 1000L);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Unexpected error in update checker cycle: {0}", e);
            scheduleNext(60000L);
        }
    }

    private void scheduleNext(long delayMillis) {
        if (running) {
            rpTask = RP.create(this::runCheckCycle);
            rpTask.schedule((int) Math.min(delayMillis, (long) Integer.MAX_VALUE));
        }
    }

    private void checkForUpdates() throws Exception {
        LOG.info("Checking for updates from {0}", UPDATE_URL);

        try {
            String userAgent = "CodingAssistant/" + AgentUtils.getVersion();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(UPDATE_URL))
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP update check failed with status code: " + response.statusCode());
            }

            String body = response.body();
            ObjectMapper mapper = MapperSupplier.get();
            UpdateInfo info = mapper.readValue(body, UpdateInfo.class);

            if (info == null || info.latestVersion == null) {
                throw new IOException("Invalid or empty update JSON response");
            }

            String currentVersionStr = AgentUtils.getVersion();

            if (isNewerVersion(info.latestVersion, currentVersionStr)) {
                LOG.info("New update found: {0} (current: {1}). Download URL: {2}",
                        new Object[]{info.latestVersion, currentVersionStr, info.downloadUrl});

                showNotification(info.latestVersion, info.downloadUrl);
            } else {
                LOG.info("Already on the latest version: {0}", currentVersionStr);
            }

            LOG.info("Update check completed successfully.");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Update check failed: {0}", e.getMessage());
            throw e;
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
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void showNotification(String latestVersion, String downloadUrl) {
        SwingUtilities.invokeLater(() -> {
            String title = "Coding Assistant Update Available";
            String message = "A new version of Coding Assistant (" + latestVersion
                    + ") is available. Click to download.";

            ActionListener action = (ActionEvent e) -> {
                BrowserUtils.openOrCopyUrl(downloadUrl, null, null);
            };

            NotificationDisplayer.getDefault().notify(
                    title,
                    NotificationDisplayer.Priority.NORMAL.getIcon(),
                    message,
                    action,
                    NotificationDisplayer.Priority.NORMAL
            );
        });
    }

    long getRandomIntervalMillis() {
        long min = 16L * 60 * 60 * 1000;
        long max = 24L * 60 * 60 * 1000;
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class UpdateInfo {
        @JsonProperty("latest_version")
        public String latestVersion;
        @JsonProperty("release_date")
        public String releaseDate;
        @JsonProperty("download_url")
        public String downloadUrl;
    }
}
