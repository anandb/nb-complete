package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.contract.UsageStatsStore.GroupBy;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageUsageRow;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.ToolTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.UsageTurnDedup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openide.util.Lookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Grok Build never sends a standard {@code usage_update}; its per-turn usage is
 * read from {@code _x.ai/session_notification turn_completed} frames (and the
 * prompt result {@code _meta.usage}). These tests feed the exact wire payloads
 * captured from a live grok 1.0.77 session and check the row plus the dedup.
 */
class GrokUsageCaptureTest {

    private static final JsonMapper MAPPER = MapperSupplier.get();

    /** Collects prompt rows; the other capture hooks are unused here. */
    private static final class RecordingStore implements UsageStatsStore {
        final List<PromptUsageRow> prompts = new ArrayList<>();

        @Override public void recordUsageUpdate(UsageUpdateRow row) { }
        @Override public void recordPromptUsage(PromptUsageRow row) { prompts.add(row); }
        @Override public void recordMessage(MessageEvent event) { }
        @Override public void recordMessageUsage(MessageUsageRow row) { }
        @Override public void recordToolCall(ToolCallExecution row) { }
        @Override public UsageSummary query(int days, String projectDir, long now) {
            throw new UnsupportedOperationException();
        }
        @Override public List<GroupTotals> queryGrouped(int days, String projectDir, long now, GroupBy groupBy) {
            throw new UnsupportedOperationException();
        }
        @Override public List<ToolTotals> queryToolTotals(int days, String projectDir, long now) {
            throw new UnsupportedOperationException();
        }
        @Override public void close() { }
    }

    private MockedStatic<Lookup> lookupMock;
    private RecordingStore store;

    @BeforeEach
    void setUp() {
        UsageTurnDedup.clear();
        store = new RecordingStore();
        Lookup lookup = mock(Lookup.class);
        lookupMock = mockStatic(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(lookup);
        when(lookup.lookup(UsageStatsStore.class)).thenReturn(store);
        when(lookup.lookup(SessionQuery.class)).thenReturn(sessionQuery());
    }

    @AfterEach
    void tearDown() {
        if (lookupMock != null) {
            lookupMock.close();
        }
        UsageTurnDedup.clear();
    }

    /** The {@code turn_completed} frame captured from grok 1.0.77 (camelCase usage). */
    private static JsonNode turnCompleted(String promptId) throws Exception {
        return MAPPER.readTree("""
            {"sessionId":"ses_grok","update":{"sessionUpdate":"turn_completed","prompt_id":"%s",
             "stop_reason":"end_turn","usage":{"inputTokens":15074,"outputTokens":171,
             "totalTokens":15245,"cachedReadTokens":1664,"cacheCreationTokens":0,
             "reasoningTokens":170,"modelCalls":1,"apiDurationMs":13007,"costUsdTicks":286780000}}}
            """.formatted(promptId));
    }

    @Test
    void turnCompletedRecordsOnePromptRow() throws Exception {
        GrokUsageCapture.onNotification(turnCompleted("p1"));

        assertEquals(1, store.prompts.size());
        PromptUsageRow row = store.prompts.get(0);
        assertEquals("ses_grok", row.attribution().sessionId());
        assertEquals(15074, row.inputTokens());
        assertEquals(171, row.outputTokens());
        assertEquals(15245, row.totalTokens());
        assertEquals(170L, row.thoughtTokens());
        assertEquals(1664L, row.cachedReadTokens());
    }

    @Test
    void sameTurnIsRecordedOnce() throws Exception {
        GrokUsageCapture.onNotification(turnCompleted("p1"));
        GrokUsageCapture.onNotification(turnCompleted("p1"));

        assertEquals(1, store.prompts.size(), "one turn must write one row");
    }

    @Test
    void responseCompletedIsIgnored() throws Exception {
        // Per model call, not per turn: capturing it would double-count the turn aggregate.
        GrokUsageCapture.onNotification(MAPPER.readTree("""
            {"sessionId":"ses_grok","update":{"sessionUpdate":"response_completed",
             "usage":{"input_tokens":1,"output_tokens":2,"reasoning_tokens":0}}}
            """));

        assertTrue(store.prompts.isEmpty());
    }

    @Test
    void resultMetaDedupesAgainstTheNotification() throws Exception {
        // The notification arrives first and writes the row...
        GrokUsageCapture.onNotification(turnCompleted("p1"));
        // ...so the result _meta path for the same turn must be skipped.
        assertFalse(UsageTurnDedup.firstCapture("ses_grok", "p1"));
        assertEquals(1, store.prompts.size());
    }

    @Test
    void blankTurnAlwaysCaptures() {
        assertTrue(UsageTurnDedup.firstCapture("ses_grok", ""));
        assertTrue(UsageTurnDedup.firstCapture("ses_grok", ""));
    }

    private static SessionQuery sessionQuery() {
        return new SessionQuery() {
            @Override public String getCurrentSessionId() { return "ses_grok"; }
            @Override public String getCurrentSessionDirectory() { return "/proj/grok"; }
            @Override public SessionState getCurrentState() { return SessionState.STREAMING; }
            @Override public CompletableFuture<List<SessionConfigOption>> loadSessionFromServer(String s, String c) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public String getCustomTitle(String sessionId, String defaultTitle) { return defaultTitle; }
            @Override public String getSessionTitle(String sessionId) { return sessionId; }
            @Override public boolean isDescendantOfCurrent(String sessionId) { return false; }
            @Override public boolean isHidden(String sessionId) { return false; }
            @Override public String getContextUsage(String sessionId) { return null; }
            @Override public Session getSession(String sessionId) { return null; }
            @Override public String getHarnessId() { return "grok"; }
            @Override public String getSessionModelId(String sessionId) { return "grok-4.7"; }
            @Override public String getSessionDirectory(String sessionId) { return "/proj/grok"; }
        };
    }
}
