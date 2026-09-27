package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.MigrationReport;
import github.anandb.netbeans.model.SessionMetadata;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageKind;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.support.PreferenceKeys;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.modules.Places;
import org.openide.util.NbPreferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless H2 tests for the usage-stats store. Each test runs against a private
 * in-memory database; the reopen test uses a temp-directory file database.
 */
class H2UsageStatsStoreTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long DAY = 86_400_000L;

    private String url;
    private H2UsageStatsStore store;

    @BeforeEach
    void setUp() {
        url = "jdbc:h2:mem:usage-" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        store = new H2UsageStatsStore(url);
    }

    @AfterEach
    void tearDown() {
        store.shutdown();
    }

    private static Attribution att(String sessionId, String harness, String model, String project) {
        return new Attribution(sessionId, harness, model, project);
    }

    private void costRow(double amount, long at) {
        store.recordUsageUpdate(new UsageUpdateRow(att("s1", "opencode", "m1", "/proj/a"), at,
                70281, 1000000L, amount, "USD"));
    }

    private long scalarLong(String sql) throws SQLException {
        Object value = scalar(sql);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private Object scalar(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getObject(1) : null;
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url);
                Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    @Test
    void bothSeparatorFormsOfOneModelGroupTogether() throws SQLException {
        // Hermes reports "provider:model", the config options report
        // "provider/model"; the same model must not become two groups.
        store.recordPromptUsage(new PromptUsageRow(
                att("s1", "hermes", "opencode-go:deepseek-v4.1-flash", "/p"), NOW, 10, 1, 11, null, null));
        store.recordPromptUsage(new PromptUsageRow(
                att("s2", "omp", "opencode-go/deepseek-v4.1-flash", "/p"), NOW, 20, 2, 22, null, null));

        List<GroupTotals> groups = store.queryGrouped(1, null, NOW, UsageStatsStore.GroupBy.MODEL);

        assertEquals(1, groups.size(), "one model, not two");
        assertEquals("opencode-go/deepseek-v4.1-flash", groups.get(0).groupKey());
        assertEquals(30, groups.get(0).inputTokens());
        assertEquals("opencode-go/deepseek-v4.1-flash",
                scalar("SELECT model_id FROM prompt_usage WHERE session_id = 's1'"),
                "the colon form is canonicalised before it reaches the column");
    }

    @Test
    void legacyRowsWithTheColonSeparatorAreNormalisedOnOpen() throws SQLException {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "hermes", "m", "/p"), NOW, 1, 1, 2, null, null));
        store.query(1, null, NOW); // FIFO: the write above has landed and the schema exists

        // Store the row the way a pre-normalisation build would have, and mark the
        // database as that old version so opening it again runs the upgrade.
        execute("UPDATE prompt_usage SET model_id = 'opencode-go:deepseek-v4.1-flash'");
        execute("MERGE INTO usage_meta (meta_key, meta_value) VALUES ('schema_version', '3')");

        H2UsageStatsStore reopened = new H2UsageStatsStore(url);
        try {
            List<GroupTotals> groups = reopened.queryGrouped(1, null, NOW, UsageStatsStore.GroupBy.MODEL);
            assertEquals("opencode-go/deepseek-v4.1-flash", groups.get(0).groupKey(),
                    "an existing row is rewritten, so old data joins the same group");
        } finally {
            reopened.shutdown();
        }
    }

    @Test
    void aModelIdThatIsAbsentStaysAbsent() throws SQLException {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "omp", "   ", "/p"), NOW, 1, 1, 2, null, null));
        store.query(1, null, NOW);

        assertNull(scalar("SELECT model_id FROM prompt_usage"),
                "a blank model must not become an empty group key");
    }

    @Test
    void theTestUserDirectoryLivesUnderTarget() {
        // Outside a real IDE getUserDirectory() is null, and
        // `new File(null, "beanbot")` silently yields a *relative* path — which is
        // how a database ended up in the repository root. The build pins the test
        // JVM's user directory under target/, so nothing a test resolves can write
        // into the working tree.
        File userDir = Places.getUserDirectory();
        assertNotNull(userDir, "the test JVM must be given a NetBeans user directory");
        Path target = new File("target").toPath().toAbsolutePath().normalize();
        assertTrue(userDir.toPath().toAbsolutePath().normalize().startsWith(target),
                "the test user directory must live under target/, was " + userDir);
    }

    @Test
    void usageUpdateKeepsGaugeAndReportsUsd() throws SQLException {
        costRow(0, NOW);

        UsageSummary summary = store.query(1, null, NOW);
        assertEquals(1, summary.sessions());
        assertEquals(0.0, summary.totalCost());
        assertEquals(70281L, scalarLong("SELECT used_tokens FROM usage_update"));
        assertEquals(1000000L, scalarLong("SELECT size_tokens FROM usage_update"));
        assertEquals("USD", scalar("SELECT cost_currency FROM usage_update"));
    }

    @Test
    void costlessUpdateStillWritesARowAndKeepsAbsentSize() throws SQLException {
        store.recordUsageUpdate(new UsageUpdateRow(att("s1", "omp", "m1", "/proj/a"), NOW,
                1234, null, 0, "USD"));

        assertEquals(1, store.query(1, null, NOW).sessions());
        assertNull(scalar("SELECT size_tokens FROM usage_update"));
    }

    @Test
    void promptUsageSumsEachFieldOnlyWithItsOwnKind() {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                5481, 47, 70358, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                100, 20, 120, 5L, 200L));

        UsageSummary summary = store.query(1, null, NOW);
        assertEquals(5581, summary.inputTokens());
        assertEquals(67, summary.outputTokens());
        assertEquals(200, summary.cachedReadTokens());
    }

    @Test
    void absentOptionalTokenFieldsStayAbsentOnTheRow() throws SQLException {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                5481, 47, 70358, null, null));
        store.query(1, null, NOW);

        assertNull(scalar("SELECT thought_tokens FROM prompt_usage"));
        assertNull(scalar("SELECT cached_read_tokens FROM prompt_usage"));
    }

    @Test
    void projectFilterNarrowsAggregatesAcrossAModelSwitch() throws SQLException {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                10, 1, 11, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m2", "/proj/a"), NOW,
                20, 2, 22, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s2", "goose", "m3", "/proj/b"), NOW,
                100, 5, 105, null, null));

        UsageSummary all = store.query(1, null, NOW);
        assertEquals(2, all.sessions());
        assertEquals(130, all.inputTokens());

        UsageSummary projectA = store.query(1, "/proj/a", NOW);
        assertEquals(1, projectA.sessions());
        assertEquals(30, projectA.inputTokens());
        assertEquals(2, scalarLong("SELECT COUNT(DISTINCT model_id) FROM prompt_usage WHERE project = '/proj/a'"));
    }

    @Test
    void overviewMessageCountSpansAllFourKinds() {
        for (int i = 0; i < 3; i++) {
            store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.USER));
        }
        for (int i = 0; i < 5; i++) {
            store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.ASSISTANT));
        }
        for (int i = 0; i < 2; i++) {
            store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.TOOL));
        }
        for (int i = 0; i < 4; i++) {
            store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.THOUGHT));
        }

        UsageSummary summary = store.query(1, null, NOW);
        assertEquals(14, summary.messages());
        assertEquals(1, summary.sessions());
    }

    @Test
    void totalCostSumsAmountsAndAveragesOverActiveDays() {
        costRow(100.0, NOW);
        costRow(137.33, NOW - DAY);

        UsageSummary summary = store.query(30, null, NOW);
        assertEquals(137.33, summary.totalCost(), 0.001);
        assertEquals(2, summary.days());
        assertEquals(68.665, summary.avgCostPerDay(), 0.001);
    }

    /**
     * Harnesses (pi-acp) repeat the session's cumulative cost across
     * usage_update notifications. Deltas against the previous row keep the
     * summed cost equal to the final running total.
     */
    @Test
    void repeatedCumulativeCostStoresDeltasNotGross() {
        costRow(0.0, NOW);            // reported running total: 0
        costRow(0.0538927, NOW);      // first non-zero running total
        costRow(0.0538927, NOW);      // duplicate notification, no new spend
        costRow(0.05805822, NOW - DAY);
        costRow(0.0738938, NOW - DAY);

        UsageSummary summary = store.query(30, null, NOW);
        assertEquals(0.0738938, summary.totalCost(), 1e-5);
        // Five gauge rows in scope, one session.
        assertEquals(1, summary.sessions());
    }

    @Test
    void averageAndMedianUsePerSessionTokenSums() {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                900, 100, 1000, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s2", "opencode", "m1", "/proj/a"), NOW,
                3000, 0, 3000, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s3", "opencode", "m1", "/proj/a"), NOW,
                9000, 0, 9000, null, null));

        UsageSummary summary = store.query(1, null, NOW);
        assertEquals(4333.33, summary.avgTokensPerSession(), 0.01);
        assertEquals(3000.0, summary.medianTokensPerSession(), 0.01);
    }

    @Test
    void daysCountsDistinctCalendarDays() {
        store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.USER));
        store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW - DAY, MessageKind.USER));
        store.recordMessage(new MessageEvent(att("s2", "opencode", "m1", "/proj/a"), NOW - 2 * DAY, MessageKind.USER));
        store.recordMessage(new MessageEvent(att("s2", "opencode", "m1", "/proj/a"), NOW, MessageKind.USER));

        UsageSummary summary = store.query(3, null, NOW);
        assertEquals(4, summary.messages());
        assertEquals(3, summary.days());
    }

    @Test
    void rowsSurviveReopeningTheDatabase(@TempDir Path tempDir) throws SQLException {
        String fileUrl = "jdbc:h2:" + tempDir.resolve("usage").toAbsolutePath().toString().replace('\\', '/');

        H2UsageStatsStore first = new H2UsageStatsStore(fileUrl);

        first.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.ASSISTANT));
        assertEquals(1, first.query(1, null, NOW).messages());
        first.shutdown();

        assertTrue(Files.exists(tempDir.resolve("usage.mv.db")), "database file must live under the user directory");

        H2UsageStatsStore second = new H2UsageStatsStore(fileUrl);
        assertEquals(1, second.query(1, null, NOW).messages());
        second.shutdown();
    }

    @Test
    void legacyDatabaseWithoutSchemaVersionIsResetOnce() throws SQLException {
        String legacyUrl = "jdbc:h2:mem:legacy-"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        // A pre-version database: one message row and no schema_version marker,
        // as written by the build that counted replayed history on every load.
        try (Connection c = DriverManager.getConnection(legacyUrl);
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE message_event (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " session_id VARCHAR(512), harness_id VARCHAR(128), model_id VARCHAR(512),"
                    + " project VARCHAR(2048), captured_at BIGINT NOT NULL, kind VARCHAR(16) NOT NULL)");
            st.execute("INSERT INTO message_event (session_id, captured_at, kind)"
                    + " VALUES ('s1', " + NOW + ", 'USER')");
        }

        H2UsageStatsStore migrated = new H2UsageStatsStore(legacyUrl);
        assertEquals(0, migrated.query(1, null, NOW).messages(),
                "rows captured before replay suppression must be dropped once");
        migrated.recordMessage(new MessageEvent(att("s1", "pi", "m1", "/proj/a"), NOW, MessageKind.USER));
        assertEquals(1, migrated.query(1, null, NOW).messages());
        migrated.shutdown();

        H2UsageStatsStore versioned = new H2UsageStatsStore(legacyUrl);
        assertEquals(1, versioned.query(1, null, NOW).messages(),
                "versioned rows must survive a normal reopen");
        versioned.shutdown();
    }

    /**
     * Version-2 databases (DOUBLE cost column, valid delta rows) must keep
     * their rows when the store widens cost_amount to NUMERIC(20,6), and
     * must accept further delta inserts after the in-place migration.
     */
    @Test
    void versionTwoCostRowsSurviveTheNumericMigration(@TempDir Path tempDir) throws SQLException {
        String fileUrl = "jdbc:h2:" + tempDir.resolve("usage-v2").toAbsolutePath().toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(fileUrl);
                Statement st = c.createStatement()) {
            st.execute("CREATE TABLE usage_meta (meta_key VARCHAR(64) PRIMARY KEY,"
                    + " meta_value VARCHAR(64))");
            st.execute("INSERT INTO usage_meta VALUES ('schema_version','2')");
            st.execute("CREATE TABLE usage_update (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + " session_id VARCHAR(512), harness_id VARCHAR(128), model_id VARCHAR(512),"
                    + " project VARCHAR(2048), captured_at BIGINT NOT NULL, used_tokens BIGINT NOT NULL,"
                    + " size_tokens BIGINT, cost_amount DOUBLE NOT NULL, cost_currency VARCHAR(16))");
            st.execute("INSERT INTO usage_update (session_id, captured_at, used_tokens, cost_amount)"
                    + " VALUES ('s1', " + NOW + ", 10, 0.5)");
        }

        H2UsageStatsStore upgraded = new H2UsageStatsStore(fileUrl);
        UsageSummary summary = upgraded.query(1, null, NOW);
        assertEquals(0.5, summary.totalCost(), 1e-9, "v2 gross row must survive as the first delta");
        // A new cumulative gross of 0.6 over the preserved 0.5 stores a 0.1 delta.
        upgraded.recordUsageUpdate(new UsageUpdateRow(att("s1", "pi", "m1", "/proj/a"), NOW, 20, null, 0.6, "USD"));
        assertEquals(0.6, upgraded.query(1, null, NOW).totalCost(), 1e-9);

        assertEquals("4", upgradedScalarVersion(fileUrl),
                "version 4 is the model-id canonicalisation upgrade");
        upgraded.shutdown();
    }

    private static String upgradedScalarVersion(String url) throws SQLException {
        try (Connection c = DriverManager.getConnection(url);
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT meta_value FROM usage_meta WHERE meta_key = 'schema_version'")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    @Test
    void groupedByModelAggregatesAllThreeTables() {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                100, 10, 110, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                60, 2, 62, null, 80L));
        store.recordUsageUpdate(new UsageUpdateRow(att("s1", "opencode", "m1", "/proj/a"), NOW,
                70281, 1000000L, 0.25, "USD"));
        store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.TOOL));
        store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.TOOL));
        store.recordMessage(new MessageEvent(att("s1", "opencode", "m1", "/proj/a"), NOW, MessageKind.ASSISTANT));

        List<GroupTotals> rows = store.queryGrouped(1, null, NOW, UsageStatsStore.GroupBy.MODEL);
        assertEquals(1, rows.size());
        GroupTotals m1 = rows.get(0);
        assertEquals("m1", m1.groupKey());
        assertEquals(1, m1.sessions());
        assertEquals(1, m1.messages(), "tool events must not inflate the message column");
        assertEquals(2, m1.toolCalls());
        assertEquals(160, m1.inputTokens());
        assertEquals(12, m1.outputTokens());
        assertEquals(80, m1.cachedReadTokens());
        assertEquals(0.25, m1.cost(), 1e-9);
    }

    @Test
    void groupedByHarnessSplitsModelsOfSameAgent() {
        store.recordPromptUsage(new PromptUsageRow(att("s1", "omp", "mA", "/proj/a"), NOW, 10, 1, 11, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s1", "omp", "mB", "/proj/a"), NOW, 100, 5, 105, null, null));
        store.recordPromptUsage(new PromptUsageRow(att("s2", "goose", "mC", "/proj/b"), NOW, 500, 50, 550, null, null));
        store.recordMessage(new MessageEvent(att("s1", "omp", "mA", "/proj/a"), NOW, MessageKind.USER));
        store.recordMessage(new MessageEvent(att("s2", "goose", "mC", "/proj/b"), NOW, MessageKind.TOOL));

        List<GroupTotals> rows = store.queryGrouped(1, null, NOW, UsageStatsStore.GroupBy.HARNESS);
        assertEquals(2, rows.size());
        GroupTotals omp = rows.stream().filter(r -> "omp".equals(r.groupKey())).findFirst().orElseThrow();
        GroupTotals goose = rows.stream().filter(r -> "goose".equals(r.groupKey())).findFirst().orElseThrow();
        assertEquals(1, omp.sessions(), "one model-less harness row shares one session id");
        assertEquals(110, omp.inputTokens());
        assertEquals(1, omp.messages());
        assertEquals(0, omp.toolCalls());
        assertEquals(500, goose.inputTokens());
        assertEquals(1, goose.toolCalls());
    }

    @Test
    void groupedUnknownAttributionSurfacesAsNullKey() {
        store.recordMessage(new MessageEvent(new Attribution("sx", null, null, null), NOW, MessageKind.USER));
        store.recordPromptUsage(new PromptUsageRow(new Attribution("sx", null, "mx", null), NOW,
                42, 2, 44, null, null));

        List<GroupTotals> byModel = store.queryGrouped(1, null, NOW, UsageStatsStore.GroupBy.MODEL);
        assertEquals(2, byModel.size(), "one group for the mx prompt row, one for the unattributed message");
        GroupTotals unknown = byModel.stream().filter(r -> r.groupKey() == null).findFirst().orElseThrow();
        assertEquals(1, unknown.messages());
        assertEquals(0, unknown.inputTokens());
        GroupTotals mx = byModel.stream().filter(r -> "mx".equals(r.groupKey())).findFirst().orElseThrow();
        assertEquals(42, mx.inputTokens());
        assertEquals(0, mx.messages());
    }

    // ---- SessionStore: migration and the two migrated datasets ----

    @Test
    void migrationMovesHistoryAndMetadataThenDeletesTheSources() throws Exception {
        clearMigratedPrefs();
        Preferences root = prefs();
        root.put(PreferenceKeys.INPUT_HISTORY_COUNT, "2");
        root.put(PreferenceKeys.INPUT_HISTORY_PREFIX + "0", "first");
        root.put(PreferenceKeys.INPUT_HISTORY_PREFIX + "1", "second");
        root.put("caveman.enabled", "true");
        root.put("gemini_local_sessions_omp", "s1,s2");
        root.node("sessmeta_omp").put("s1",
                "{\"title\":\"T1\",\"hidden\":true,\"cwd\":\"/p\",\"usage\":\"100,200\"}");
        root.node("sessmeta_omp").put("s2", "{\"hidden\":false}");
        // 0 is the newest in the old id lists, so s2 must come back first.
        root.node("sessids_omp").put("s1", "1");
        root.node("sessids_omp").put("s2", "0");
        root.flush();

        MigrationReport report = store.migrateLegacyPrefs();

        assertEquals(0, report.skipped(), "nothing in this fixture is unreadable");
        assertEquals(List.of("first", "second"), store.inputHistory());
        Map<String, SessionMetadata> sessions = store.sessionMetadata("omp");
        assertEquals(List.of("s2", "s1"), new ArrayList<>(sessions.keySet()),
                "newest first, as the dropped id list ordered it");
        assertEquals("T1", sessions.get("s1").title());
        assertTrue(sessions.get("s1").hidden());
        assertEquals("/p", sessions.get("s1").cwd());
        assertEquals("100,200", sessions.get("s1").usage());

        assertNull(root.get(PreferenceKeys.INPUT_HISTORY_PREFIX + "0", null), "migrated keys are deleted");
        assertNull(root.get("caveman.enabled", null), "an obsolete key goes with them");
        assertNull(root.get("gemini_local_sessions_omp", null), "the legacy CSV list is superseded");
        assertFalse(hasChildNode("sessmeta_omp"), "the metadata node is gone");
        assertFalse(hasChildNode("sessids_omp"), "the id node is gone");

        MigrationReport second = store.migrateLegacyPrefs();
        assertEquals(0, second.total(), "a completed migration does not run again");
        assertEquals(2, store.sessionMetadata("omp").size());
    }

    @Test
    void migrationSkipsUnreadableEntriesAndKeepsTheirKeys() throws Exception {
        clearMigratedPrefs();
        Preferences node = prefs().node("sessmeta_omp");
        node.put("good", "{\"title\":\"G\"}");
        node.put("bad", "{not json");
        node.put("worse", "{");
        prefs().flush();

        MigrationReport report = store.migrateLegacyPrefs();

        assertEquals(1, report.migrated());
        assertEquals(2, report.skipped());
        assertTrue(report.hasSkips());
        assertEquals(2, report.reasons().size(), "one reason per skipped entry");
        assertEquals("G", store.sessionMetadata("omp").get("good").title());
        assertTrue(hasChildNode("sessmeta_omp"), "a node with skipped entries is kept for inspection");
        assertEquals("{not json", node.get("bad", null));
    }

    @Test
    void migrationSurvivesADatabaseItCannotOpenAndKeepsThePreferences() throws Exception {
        clearMigratedPrefs();
        Preferences root = prefs();
        root.put(PreferenceKeys.INPUT_HISTORY_PREFIX + "0", "kept");
        root.node("sessmeta_omp").put("s1", "{\"title\":\"T\"}");
        root.flush();

        // A regular file where a directory would have to be: opening must fail.
        File blocker = File.createTempFile("beanbot-not-a-dir", ".tmp");
        blocker.deleteOnExit();
        H2UsageStatsStore broken = new H2UsageStatsStore("jdbc:h2:" + blocker.getAbsolutePath() + "/db");
        try {
            MigrationReport report = broken.migrateLegacyPrefs();

            assertNotNull(report, "the migration always reports, even when it can do nothing");
            assertTrue(broken.inputHistory().isEmpty());
            assertTrue(broken.sessionMetadata("omp").isEmpty());
            assertEquals("kept", root.get(PreferenceKeys.INPUT_HISTORY_PREFIX + "0", null),
                    "data must survive a database that cannot be written");
            assertTrue(hasChildNode("sessmeta_omp"));
        } finally {
            broken.shutdown();
        }
    }

    @Test
    void unqualifiedMetadataLandsInTheUnconfiguredBucket() throws Exception {
        clearMigratedPrefs();
        prefs().node("sessmeta").put("x", "{\"title\":\"Unqualified\",\"hidden\":true}");
        prefs().flush();

        store.migrateLegacyPrefs();

        assertEquals("Unqualified", store.sessionMetadata(null).get("x").title(),
                "metadata written before a harness existed has no harness bucket of its own");
        assertTrue(store.sessionMetadata("omp").isEmpty());
    }

    @Test
    void legacyPerSessionKeysAreFoldedIntoTheRow() throws Exception {
        clearMigratedPrefs();
        Preferences root = prefs();
        root.put("session_title_x", "Legacy");
        root.put("session_hidden_x", "true");
        root.put("session_usage_x", "5,10");
        root.flush();

        store.migrateLegacyPrefs();

        SessionMetadata meta = store.sessionMetadata(null).get("x");
        assertNotNull(meta, "a session known only from per-session keys is still migrated");
        assertEquals("Legacy", meta.title());
        assertTrue(meta.hidden());
        assertEquals("5,10", meta.usage());
        assertNull(root.get("session_title_x", null), "the folded keys are deleted");
    }

    @Test
    void sessionIdsContainingSeparatorsRoundTrip() throws Exception {
        store.saveSessionMetadata("omp", "id,with,commas", new SessionMetadata("C", null, false, "/p"));
        store.load();

        assertEquals("C", store.sessionMetadata("omp").get("id,with,commas").title(),
                "a session id is an opaque key, not a delimited field");
    }

    @Test
    void historyIsCappedAndDropsTheOldest() {
        for (int i = 0; i < 1030; i++) {
            store.appendInputHistory("entry-" + i);
        }
        List<String> history = store.inputHistory();

        assertEquals(1024, history.size());
        assertEquals("entry-6", history.get(0), "the oldest entries are dropped first");
        assertEquals("entry-1029", history.get(history.size() - 1), "the newest is last");
    }

    @Test
    void anInterruptedMigrationDoesNotDuplicateHistoryOnRetry() throws Exception {
        clearMigratedPrefs();
        Preferences root = prefs();
        root.put(PreferenceKeys.INPUT_HISTORY_PREFIX + "0", "first");
        root.put(PreferenceKeys.INPUT_HISTORY_PREFIX + "1", "second");
        root.node("sessmeta_omp").put("s1", "{\"title\":\"T\"}");
        root.flush();

        store.migrateLegacyPrefs();
        assertEquals(List.of("first", "second"), store.inputHistory());

        // A run interrupted after its inserts but before its marker leaves exactly
        // this state: rows present, marker absent, preferences still in place.
        execute("DELETE FROM usage_meta WHERE meta_key = 'prefs_migrated'");
        store.migrateLegacyPrefs();

        assertEquals(List.of("first", "second"), store.inputHistory(),
                "a retry must not duplicate what the interrupted run already wrote");
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM input_history"));
        assertEquals("T", store.sessionMetadata("omp").get("s1").title(),
                "the metadata upsert absorbs the retry");
    }

    @Test
    void aRowThatFailsToWriteKeepsItsPreferenceData() throws Exception {
        clearMigratedPrefs();
        // A directory longer than the cwd column: the row cannot be stored, so its
        // preference entry is the only surviving copy and must not be deleted.
        String tooLong = "/" + "x".repeat(2100);
        String json = "{\"title\":\"T\",\"cwd\":\"" + tooLong + "\"}";
        Preferences node = prefs().node("sessmeta_omp");
        node.put("s1", json);
        prefs().node("sessids_omp").put("s1", "0");
        prefs().flush();

        MigrationReport report = store.migrateLegacyPrefs();

        assertEquals(1, report.skipped(), "the oversized row cannot be stored");
        assertNull(store.sessionMetadata("omp").get("s1"), "and it is absent from the database");
        assertTrue(hasChildNode("sessmeta_omp"), "its only copy must survive the run");
        assertEquals(json, node.get("s1", null));
        assertTrue(hasChildNode("sessids_omp"),
                "the ordering that node still needs must survive with it");
    }

    @Test
    void writeBeforeTheCacheLoadsKeepsTheStoredFields() {
        // Seed a row through a store whose cache has loaded, so the database holds it.
        store.saveSessionMetadata("omp", "s1", new SessionMetadata("Saved", "10,20", true, "/p"));
        store.load();
        assertEquals("Saved", store.sessionMetadata("omp").get("s1").title());

        // A second store on the same database starts with an empty cache — the state
        // a write racing startup sees. Its caller derives the record from nothing,
        // so its blank fields mean "unknown" and must not wipe the stored row.
        H2UsageStatsStore racing = new H2UsageStatsStore(url);
        try {
            racing.saveSessionMetadata("omp", "s1", new SessionMetadata(null, null, false, null));
            racing.load();

            SessionMetadata stored = racing.sessionMetadata("omp").get("s1");
            assertEquals("Saved", stored.title(), "an unknown field must not overwrite a saved one");
            assertEquals("10,20", stored.usage());
            assertTrue(stored.hidden(), "the stored flag stands until the cache has loaded");
            assertEquals("/p", stored.cwd());
        } finally {
            racing.shutdown();
        }
    }

    @Test
    void writeAfterTheCacheLoadsReplacesTheFieldsAsGiven() {
        store.saveSessionMetadata("omp", "s1", new SessionMetadata("Saved", "10,20", true, "/p"));
        store.load();

        // Now the caller can see the row, so a blank means "clear this".
        store.saveSessionMetadata("omp", "s1", new SessionMetadata("Renamed", null, false, null));
        store.load();

        SessionMetadata stored = store.sessionMetadata("omp").get("s1");
        assertEquals("Renamed", stored.title());
        assertNull(stored.usage(), "clearing a field is honoured once the cache has loaded");
        assertFalse(stored.hidden());
        assertNull(stored.cwd());
    }

    @Test
    void metadataRoundTripsThroughTheDatabase() {
        store.saveSessionMetadata("omp", "s1", new SessionMetadata("One", "10,20", true, "/p"));
        store.saveSessionMetadata("omp", "s2", new SessionMetadata(null, null, false, null));

        store.load();

        Map<String, SessionMetadata> sessions = store.sessionMetadata("omp");
        assertEquals(2, sessions.size());
        assertEquals("One", sessions.get("s1").title());
        assertEquals("10,20", sessions.get("s1").usage());
        assertTrue(sessions.get("s1").hidden());
        assertEquals("/p", sessions.get("s1").cwd());
        assertNull(sessions.get("s2").title(), "an absent title stays absent");
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
    }

    private static boolean hasChildNode(String name) throws BackingStoreException {
        for (String child : prefs().childrenNames()) {
            if (name.equals(child)) {
                return true;
            }
        }
        return false;
    }

    /** Removes only the keys and nodes the migration consumes, leaving other prefs alone. */
    private static void clearMigratedPrefs() throws BackingStoreException {
        Preferences root = prefs();
        for (String key : root.keys()) {
            if (key.startsWith(PreferenceKeys.INPUT_HISTORY_PREFIX)
                    || key.equals(PreferenceKeys.INPUT_HISTORY_COUNT)
                    || key.startsWith("session_title_")
                    || key.startsWith("session_hidden_")
                    || key.startsWith("session_usage_")
                    || key.startsWith("gemini_local_sessions")
                    || "caveman.enabled".equals(key)) {
                root.remove(key);
            }
        }
        for (String child : root.childrenNames()) {
            if (child.startsWith("sessmeta") || child.startsWith("sessids")) {
                root.node(child).removeNode();
            }
        }
        root.flush();
    }
}

