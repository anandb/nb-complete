package github.anandb.netbeans.project;

import org.openide.modules.OnStart;
import org.openide.awt.NotificationDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;

import github.anandb.netbeans.contract.SessionStore;
import github.anandb.netbeans.contract.UpdateCheckerControl;
import github.anandb.netbeans.model.MigrationReport;
import github.anandb.netbeans.support.AgentUtils;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;
import github.anandb.netbeans.support.PreferencesMigrator;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.openide.util.Lookup;
import org.openide.windows.TopComponent;

@OnStart
public class ACPStartup implements Runnable {
    private static final Logger LOG = Logger.from(ACPStartup.class);

    @Override
    public void run() {
        // @OnStart Runnables execute on the EDT during install/startup. Defer all
        // heavy work (preference migration file I/O, OpenProjects access) to a
        // background thread so plugin installation and IDE startup never block on
        // the EDT (which manifests as a hang/deadlock while installing the plugin).
        RequestProcessor.getDefault().post(() -> {
            // Run before any plugin component reads/writes NbPreferences so data
            // left behind in a previous NetBeans user dir is copied over (the built-in
            // IDE migration skips plugin files because the plugin is imported later).
            PreferencesMigrator.migrateIfNeeded();

            // Runs on this background thread, never the EDT: it reads preferences,
            // writes the database, and leaves the store's data in memory so every
            // later read from the EDT is a memory read.
            migratePreferencesIntoStore();

            LOG.info("ACP Plugin Startup: Initializing Project Manager...");
            ACPProjectManager.getInstance().start();
            checkVersionAndOpen();
            UpdateCheckerControl ucc = Lookup.getDefault().lookup(UpdateCheckerControl.class);
            if (ucc != null) {
                ucc.start();
            } else {
                LOG.warn("UpdateCheckerControl not found — update checks disabled");
            }
        });
    }

    /**
     * Moves the legacy preference data into the local store and loads it. Runs on
     * a background thread; the store's later reads are memory-only.
     *
     * <p>A migration that could not recover every entry tells the user what was
     * skipped. Nothing here is allowed to fail startup: a missing store or a
     * database that will not open leaves the plugin running with empty datasets.</p>
     */
    private static void migratePreferencesIntoStore() {
        SessionStore store = Lookup.getDefault().lookup(SessionStore.class);
        if (store == null) {
            LOG.warn("SessionStore not found — input history and session metadata will not persist");
            return;
        }
        MigrationReport report;
        try {
            report = store.migrateLegacyPrefs();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Preference migration failed; continuing with an empty store", ex);
            return;
        }
        if (report != null && report.hasSkips()) {
            notifySkippedPreferences(report);
        }
    }

    /**
     * One non-modal notification naming what the migration could not recover.
     *
     * <p>Deliberately not a dialog: this is an automatic, non-actionable startup
     * event, and a modal would block the IDE over something the user cannot act
     * on. The skipped entries stay in the preferences for inspection.</p>
     */
    private static void notifySkippedPreferences(MigrationReport report) {
        String message = NbBundle.getMessage(ACPStartup.class, "MSG_PrefsMigrationSkipped",
                report.skipped(), report.total());
        String title = NbBundle.getMessage(ACPStartup.class, "LBL_PrefsMigrationTitle");
        SwingUtilities.invokeLater(() -> NotificationDisplayer.getDefault().notify(
                title, NotificationDisplayer.Priority.LOW.getIcon(), message, null,
                NotificationDisplayer.Priority.LOW));
    }

    private void checkVersionAndOpen() {
        String currentVersion = AgentUtils.getVersion();
        String lastVersion = NbPreferences.forModule(ACPStartup.class).get("lastVersion", "");

        if (!currentVersion.equals(lastVersion)) {
            LOG.info("New version detected ({0}), opening Assistant sidebar (docked left)...", currentVersion);
            NbPreferences.forModule(ACPStartup.class).put("lastVersion", currentVersion);
            NbPreferences.forModule(PreferenceKeys.class).put(PreferenceKeys.HELP_FLASH_PENDING, "true");

            UpdateCheckerControl ucc = Lookup.getDefault().lookup(UpdateCheckerControl.class);
            if (ucc != null) {
                ucc.onInstallOrUpgrade();
            } else {
                LOG.warn("UpdateCheckerControl not found — install/upgrade schedule skipped");
            }
        }

        // Always open the sidebar on startup
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            TopComponent sidebar = WindowManager.getDefault().findTopComponent("AssistantTopComponent");
            if (sidebar != null && !sidebar.isOpened()) {
                Mode explorer = WindowManager.getDefault().findMode("explorer");
                if (explorer != null) {
                    explorer.dockInto(sidebar);
                }
                sidebar.open();
                sidebar.requestActive();
            }
        });
    }
}

