package github.anandb.netbeans.db;

import github.anandb.netbeans.contract.ModelListControl;
import github.anandb.netbeans.contract.PinnedMessageControl;
import github.anandb.netbeans.contract.SessionStore;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.AvailableModel;
import github.anandb.netbeans.model.MigrationReport;
import github.anandb.netbeans.model.SessionMetadata;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageUsageRow;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.ToolTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.Logger;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import org.openide.modules.Places;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 * Embedded H2 implementation of {@link UsageStatsStore}, {@link SessionStore},
 * {@link PinnedMessageControl} and {@link ModelListControl}. One database lives
 * under the NetBeans user directory, so captured rows survive IDE restarts and
 * are readable with no harness process spawned. It is also the durable home of
 * the input history, the per-session metadata, the per-session pins and the
 * per-harness model lists that used to live in {@code NbPreferences}.
 *
 * <p>Every database operation runs on a single-threaded daemon executor, so a
 * write from the event dispatch thread enqueues and returns without I/O. Because
 * the executor is FIFO, a query submitted after a write observes that write.</p>
 */
@ServiceProviders({
    @ServiceProvider(service = UsageStatsStore.class),
    @ServiceProvider(service = SessionStore.class),
    @ServiceProvider(service = PinnedMessageControl.class),
    @ServiceProvider(service = ModelListControl.class)
})
public class H2UsageStatsStore
        implements UsageStatsStore, SessionStore, PinnedMessageControl, ModelListControl {

    private static final Logger LOG = Logger.from(H2UsageStatsStore.class);

    private final DbLifecycle lifecycle;
    private final UsageCapture capture;
    private final SessionDatasets sessions;
    private final PinModelDatasets pins;
    private final PinModelDatasets.Db pinDb;
    private final PrefsMigration migration;
    private final PrefsMigration.Db migDb;
    private final SessionDatasets.Db sessionDb;

    /** Service-provider constructor: stores the database under the user directory. */
    public H2UsageStatsStore() {
        this(defaultJdbcUrl());
    }

    /**
     * Test constructor.
     *
     * @param jdbcUrl an H2 URL, e.g. {@code jdbc:h2:mem:test;DB_CLOSE_DELAY=-1}
     */
    H2UsageStatsStore(String jdbcUrl) {
        this.lifecycle = new DbLifecycle(LOG, jdbcUrl);
        UsageCapture.Db db = new UsageCapture.Db() {
            @Override
            public Connection connection() throws SQLException {
                return lifecycle.connection();
            }

            @Override
            public void runDb(Runnable task) {
                lifecycle.runDb(task);
            }

            @Override
            public Logger log() {
                return LOG;
            }
        };
        this.pins = new PinModelDatasets();
        this.pinDb = new PinModelDatasets.Db() {
            @Override
            public Connection connection() throws SQLException {
                return lifecycle.connection();
            }

            @Override
            public void runDb(Runnable task) {
                lifecycle.runDb(task);
            }
        };
        this.migration = new PrefsMigration(LOG);
        this.migDb = new PrefsMigration.Db() {
            @Override
            public boolean ensureDatabase() {
                return lifecycle.ensureDatabase();
            }

            @Override
            public Connection connection() throws SQLException {
                return lifecycle.connection();
            }

            @Override
            public SessionDatasets sessions() {
                return sessions;
            }

            @Override
            public SessionDatasets.Db sessionDb() {
                return sessionDb;
            }

            @Override
            public PinModelDatasets pins() {
                return pins;
            }

            @Override
            public PinModelDatasets.Db pinDb() {
                return pinDb;
            }
        };
        this.capture = new UsageCapture(db);
        this.sessions = new SessionDatasets(LOG);
        this.sessionDb = new SessionDatasets.Db() {
            @Override
            public Connection connection() throws SQLException {
                return lifecycle.connection();
            }

            @Override
            public void runDb(Runnable task) {
                lifecycle.runDb(task);
            }

            @Override
            public <T> T call(Callable<T> task, T fallback) {
                return lifecycle.call(task, fallback);
            }

            @Override
            public Logger log() {
                return LOG;
            }
        };
    }

    /** Absolute H2 URL for the per-user database file. */
    private static String defaultJdbcUrl() {
        File userDir = Places.getUserDirectory();
        if (userDir == null) {
            // No configured user directory. Without this guard
            // `new File(null, "beanbot")` yields a *relative* path and the database
            // is created in the process working directory, which is how one ended
            // up in the repository root. Fall back to a temporary location, as the
            // platform itself does for caches.
            userDir = new File(System.getProperty("java.io.tmpdir"), "nbcache");
        }
        File dir = new File(userDir, "beanbot");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOG.log(Level.WARNING, "Could not create usage-stats directory: {0}", dir.getAbsolutePath());
        }
        String path = new File(dir, "usage-stats").getAbsolutePath().replace('\\', '/');
        return "jdbc:h2:" + path;
    }

    private Connection connection() throws SQLException {
        return lifecycle.connection();
    }

    private void runDb(Runnable task) {
        lifecycle.runDb(task);
    }

    private boolean ensureDatabase() {
        return lifecycle.ensureDatabase();
    }

    private <T> T call(java.util.concurrent.Callable<T> task, T fallback) {
        return lifecycle.call(task, fallback);
    }

    @Override
    public void recordUsageUpdate(UsageUpdateRow row) {
        capture.recordUsageUpdate(row);
    }

    @Override
    public void recordPromptUsage(PromptUsageRow row) {
        capture.recordPromptUsage(row);
    }

    @Override
    public void recordMessage(MessageEvent event) {
        capture.recordMessage(event);
    }

    @Override
    public void recordMessageUsage(MessageUsageRow row) {
        capture.recordMessageUsage(row);
    }

    @Override
    public void recordToolCall(ToolCallExecution row) {
        capture.recordToolCall(row);
    }

    // ---- SessionStore: input history and session metadata ----
    //
    // Both datasets live in memory and are populated by load(); reads never touch
    // the database, so they are safe from the EDT. Every database and preference
    // access below runs on dbExecutor, whose single thread is therefore the only
    // serialisation the migration needs.

    @Override
    public void load() {
        call(() -> {
            sessions.loadHistory(sessionDb);
            sessions.loadMetadata(sessionDb);
            loadPins();
            loadModels();
            return null;
        }, null);
    }

    @Override
    public MigrationReport migrateLegacyPrefs() {
        MigrationReport report = call(() -> migration.runPrefsMigration(migDb), MigrationReport.none());
        // Rows the migration just wrote must be readable without a restart.
        load();
        return report;
    }

    /**
     * Spends the one retry an explicit user action may request after the startup
     * open failed. Reopens on the database thread and finishes the preference
     * migration the failed open skipped.
     *
     * <p>The in-memory datasets are deliberately not reloaded: this session kept
     * writing to them while the database was unreachable, and a load would
     * replace them with older rows — dropping input history typed in the
     * meantime. They persist from this point on, and the next launch loads the
     * complete set.</p>
     */
    @Override
    public void retryAfterFailure() {
        if (!lifecycle.tryRearmRetry()) {
            return;
        }
        call(() -> migration.runPrefsMigration(migDb), MigrationReport.none());
    }

    @Override
    public List<String> inputHistory() {
        return sessions.inputHistory();
    }

    @Override
    public void appendInputHistory(String text) {
        sessions.appendInputHistory(sessionDb, text);
    }

    @Override
    public Map<String, SessionMetadata> sessionMetadata(String harnessId) {
        return sessions.sessionMetadata(harnessId);
    }

    @Override
    public void saveSessionMetadata(String harnessId, String sessionId, SessionMetadata sessionMetadata) {
        sessions.saveSessionMetadata(sessionDb, harnessId, sessionId, sessionMetadata);
    }

    @Override
    public void saveSessionMetadataBulk(String harnessId, Map<String, SessionMetadata> updates) {
        sessions.saveSessionMetadataBulk(sessionDb, harnessId, updates);
    }

    // ---- PinnedMessageControl and ModelListControl ----
    //
    // Both datasets are per-session/per-harness and grow with every session or
    // harness, which is what pushed them out of the preferences file. Reads are
    // memory-only (the caches are primed by load()); writes enqueue on dbExecutor.

    @Override
    public void flush() {
        lifecycle.awaitQueuedWrites();
    }

    /**
     * Closes the connection and stops the writer so the database file is
     * unlocked before this module's classloader can be discarded by an in-place
     * upgrade. Idempotent.
     */
    @Override
    public void close() {
        lifecycle.shutdown();
    }

    /** For tests: closes the connection and executor synchronously. Idempotent. */
    synchronized void shutdown() {
        lifecycle.shutdown();
    }

    private void loadPins() throws SQLException {
        pins.loadPins(pinDb);
    }

    private void loadModels() throws SQLException {
        pins.loadModels(pinDb);
    }

    @Override
    public boolean isPinned(String sessionId, String messageId) {
        return pins.isPinned(sessionId, messageId);
    }

    @Override
    public void setPinned(String sessionId, String messageId, boolean pinned) {
        pins.setPinned(pinDb, sessionId, messageId, pinned);
    }

    @Override
    public void loadSession(String sessionId) {
        pins.loadSession(sessionId);
    }

    @Override
    public void retainPinned(String sessionId, Set<String> activeMessageIds) {
        pins.retainPinned(pinDb, sessionId, activeMessageIds);
    }

    @Override
    public void unloadSession(String sessionId) {
        pins.unloadSession(sessionId);
    }

    @Override
    public List<AvailableModel> getModels(String harnessId) {
        return pins.getModels(harnessId);
    }

    @Override
    public void updateModels(String harnessId, List<AvailableModel> list) {
        pins.updateModels(pinDb, harnessId, list);
    }

    void updateModels(String harnessId, List<AvailableModel> list, long now) {
        pins.updateModels(pinDb, harnessId, list, now);
    }


    @Override
    public UsageSummary query(int days, String projectDir, long now) {
        int window = Math.max(days, 1);
        return call(() -> UsageQueries.query(connection(), window, projectDir, now), UsageSummary.empty());
    }

    @Override
    public List<GroupTotals> queryGrouped(int days, String projectDir, long now, GroupBy groupBy) {
        int window = Math.max(days, 1);
        GroupBy key = groupBy != null ? groupBy : GroupBy.MODEL;
        String column = key == GroupBy.HARNESS ? "harness_id" : "model_id";
        final String col = column;
        return call(() -> UsageQueries.queryGrouped(connection(), window, projectDir, now, col), List.of());
    }

    @Override
    public List<ToolTotals> queryToolTotals(int days, String projectDir, long now) {
        int window = Math.max(days, 1);
        return call(() -> UsageQueries.queryToolTotals(connection(), window, projectDir, now), List.of());
    }

}
