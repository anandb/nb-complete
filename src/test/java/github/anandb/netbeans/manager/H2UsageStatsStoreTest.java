package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageKind;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        costRow(37.33, NOW - DAY);

        UsageSummary summary = store.query(30, null, NOW);
        assertEquals(137.33, summary.totalCost(), 0.001);
        assertEquals(2, summary.days());
        assertEquals(68.665, summary.avgCostPerDay(), 0.001);
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
}
