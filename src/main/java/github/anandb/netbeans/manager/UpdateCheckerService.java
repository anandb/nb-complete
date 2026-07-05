package github.anandb.netbeans.manager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Future;
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

    /** Daemon request processor for the update-check loop. */
    private static final RequestProcessor RP =
            new RequestProcessor("ACP-UpdateChecker", 1, true);

    /** Cached singleton — populated lazily from Lookup. */
    private static volatile UpdateCheckerService instance;

    private volatile boolean running;
    private volatile Future<?> rpFuture;

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
    public void onInstallOrUpgrade() {
        long timeOfExecution = System.currentTimeMillis() + getRandomIntervalMillis();
        prefs().putLong(PreferenceKeys.LAST_CHECKED_FOR_UPDATES, timeOfExecution);
        LOG.info("Plugin installed/upgraded. Scheduled next update check for: {0}", new java.util.Date(timeOfExecution));
    }

    /**
     * Starts the background daemon task on the {@link RequestProcessor}.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    public synchronized void start() {
        if (rpFuture != null && !rpFuture.isDone()) {
            return;
        }
        running = true;
        rpFuture = RP.submit(this::runLoop);
        LOG.info("UpdateCheckerService daemon task submitted.");
    }

    /** Cancels the running update-check loop, if any. */
    public synchronized void cancel() {
        running = false;
        if (rpFuture != null) {
            rpFuture.cancel(true);
            rpFuture = null;
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private static Preferences prefs() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
    }

    private void runLoop() {
        Preferences prefs = prefs();
        boolean checkEnabled = prefs.getBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true);
        String envVal = System.getenv("ACP_CHECK_UPDATES_ON_STARTUP");
        boolean checkOnStartupEnv = "true".equalsIgnoreCase(envVal);

        if (checkEnabled && checkOnStartupEnv) {
            try {
                LOG.info("Checking for updates at startup (ACP_CHECK_UPDATES_ON_STARTUP is enabled)...");
                checkForUpdates();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error checking for updates at startup: {0}", e.getMessage());
            }
        } else {
            LOG.fine("Startup update check skipped (preference enabled: {0}, startup env enabled: {1}).",
                    new Object[]{checkEnabled, checkOnStartupEnv});
        }

        // Set the next time of execution to be 16-24 hours from now
        long initialNextTime = System.currentTimeMillis() + getRandomIntervalMillis();
        prefs.putLong(PreferenceKeys.LAST_CHECKED_FOR_UPDATES, initialNextTime);

        while (running) {
            try {
                long now = System.currentTimeMillis();
                long timeOfExecution = prefs.getLong(PreferenceKeys.LAST_CHECKED_FOR_UPDATES, 0L);

                // If not initialized, set initial execution time.
                if (timeOfExecution == 0L) {
                    timeOfExecution = now + getRandomIntervalMillis();
                    prefs.putLong(PreferenceKeys.LAST_CHECKED_FOR_UPDATES, timeOfExecution);
                }

                if (now >= timeOfExecution) {
                    // Compute next time of execution BEFORE the update check
                    long nextTime = System.currentTimeMillis() + getRandomIntervalMillis();
                    prefs.putLong(PreferenceKeys.LAST_CHECKED_FOR_UPDATES, nextTime);

                    checkEnabled = prefs.getBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true);

                    if (checkEnabled) {
                        try {
                            LOG.info("Checking for updates...");
                            checkForUpdates();
                        } catch (Exception e) {
                            LOG.log(Level.WARNING, "Error checking for updates: {0}", e.getMessage());
                        }
                    } else {
                        LOG.fine("Update check is disabled in settings. Skipping update check.");
                    }
                } else {
                    long sleepTime = timeOfExecution - now;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Unexpected error in update checker loop: {0}", e);
                try {
                    // Prevent tight loop in case of continuous unexpected failures
                    Thread.sleep(60000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void checkForUpdates() throws Exception {
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
