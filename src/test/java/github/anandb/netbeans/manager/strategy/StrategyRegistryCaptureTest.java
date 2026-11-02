package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.contract.UsageStatsStore.GroupBy;
import github.anandb.netbeans.model.UsageRecords.GroupTotals;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageKind;
import github.anandb.netbeans.model.UsageRecords.MessageUsageRow;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.ToolTotals;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.SteerEchoes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openide.util.Lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Headless tests for the StrategyRegistry capture hooks. A fake
 * {@code UsageStatsStore} is injected through the Lookup seam, so the tests
 * observe exactly the rows the registry hands over — no H2, no Swirlds, no
 * UI. {@code Lookup} is mocked statically like ProcessManager in
 * SessionManagerTest: the factory method is static on a final-ish class.
 */
class StrategyRegistryCaptureTest {

    /** Collects every row handed to the fake store. */
    static final class RecordingStore implements UsageStatsStore {
        final List<UsageUpdateRow> usage = new ArrayList<>();
        final List<PromptUsageRow> prompts = new ArrayList<>();
        final List<MessageEvent> messages = new ArrayList<>();
        final List<MessageUsageRow> messageUsage = new ArrayList<>();

        @Override
        public void recordUsageUpdate(UsageUpdateRow row) {
            usage.add(row);
        }

        @Override
        public void recordPromptUsage(PromptUsageRow row) {
            prompts.add(row);
        }

        @Override
        public void recordMessage(MessageEvent event) {
            messages.add(event);
        }

        @Override
        public void recordMessageUsage(MessageUsageRow row) {
            messageUsage.add(row);
        }

        @Override
        public void recordToolCall(ToolCallExecution row) {
            throw new UnsupportedOperationException("tool-call capture is not part of this test");
        }

        @Override
        public UsageSummary query(int days, String projectDir, long now) {
            throw new UnsupportedOperationException("query is not part of this test");
        }

        @Override
        public List<GroupTotals> queryGrouped(int days, String projectDir, long now, GroupBy groupBy) {
            throw new UnsupportedOperationException("grouped queries are not part of this test");
        }

        @Override
        public List<ToolTotals> queryToolTotals(int days, String projectDir, long now) {
            throw new UnsupportedOperationException("tool totals are not part of this test");
        }

        @Override
        public void close() {
            // Nothing to release: this double writes to lists only.
        }
    }

    private static final JsonMapper MAPPER = MapperSupplier.get();

    private MockedStatic<Lookup> lookupMock;
    private Lookup lookupInstance;
    private RecordingStore store;

    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        lookupInstance = mock(Lookup.class);
        lookupMock = mockStatic(Lookup.class);
        // The static factory itself: the instance returned must be a Lookup whose
        // lookup() calls we can stub per-class.
        lookupMock.when(Lookup::getDefault).thenReturn(lookupInstance);
        when(lookupInstance.lookup(UsageStatsStore.class)).thenReturn(store);
        when(lookupInstance.lookup(SessionQuery.class)).thenReturn(streamingSessionQuery());
    }

    @AfterEach
    void tearDown() {
        if (lookupMock != null) {
            lookupMock.close();
        }
    }

    private static SessionQuery streamingSessionQuery() {
        return new SessionQuery() {
            @Override public String getCurrentSessionId() { return "ses_capture"; }
            @Override public String getCurrentSessionDirectory() { return "/proj/a"; }
            @Override public SessionState getCurrentState() { return SessionState.STREAMING; }
            @Override public CompletableFuture<List<SessionConfigOption>> loadSessionFromServer(String sessionId, String cwd) {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public String getCustomTitle(String sessionId, String defaultTitle) { return defaultTitle; }
            @Override public String getSessionTitle(String sessionId) { return sessionId; }
            @Override public boolean isDescendantOfCurrent(String sessionId) { return false; }
            @Override public boolean isHidden(String sessionId) { return false; }
            @Override public String getContextUsage(String sessionId) { return null; }
            @Override public Session getSession(String sessionId) { return null; }
            @Override public String getHarnessId() { return "pi"; }
            @Override public String getSessionModelId(String sessionId) { return "m1"; }
            @Override public String getSessionDirectory(String sessionId) { return "/proj/a"; }
        };
    }

    private static UIHandler noopHandler() {
        return mock(UIHandler.class);
    }

    private static StrategyRegistry registry() {
        return new StrategyRegistry();
    }

    /** Builds a goose custom message_usage update. */
    private static SessionUpdate messageUsage(String messageId, long input, long output, long total,
                                              long cacheRead, double cost, long elapsedMs) {
        ObjectNode usage = MAPPER.createObjectNode()
                .put("inputTokens", input)
                .put("outputTokens", output)
                .put("totalTokens", total)
                .put("cacheReadTokens", cacheRead)
                .put("cost", cost)
                .put("elapsedMs", elapsedMs)
                .put("timeToFirstTokenMs", 120)
                .put("isCompaction", false);
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(MessageType.message_usage,
                null, null, messageId, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, usage);
        return new SessionUpdate("2.0", "session/update", new SessionUpdate.Params("ses_capture", data));
    }

    /** Builds a usage_update carrying the gauge and an optional USD cost. */
    private static SessionUpdate usageUpdate(long used, long size, Double amount) {        ObjectNode cost = null;
        if (amount != null) {
            cost = MAPPER.createObjectNode().put("amount", amount).put("currency", "USD");
        }
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(MessageType.usage_update,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, used, size, null, null, cost, null);
        return new SessionUpdate("2.0", "session/update", new SessionUpdate.Params("ses_capture", data));
    }

    /** Builds an agent_message_chunk with the given streaming message id. */
    private static SessionUpdate assistantChunk(String sessionId, String messageId) {
        JsonNode content = MAPPER.createObjectNode().put("type", "text").put("text", "t");
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(MessageType.agent_message_chunk,
                null, null, messageId, content, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        return new SessionUpdate("2.0", "session/update", new SessionUpdate.Params(sessionId, data));
    }

    /** Same builder, using the default streaming session of the usage tests. */
    private static SessionUpdate assistantChunk(String messageId) {
        return assistantChunk("ses_capture", messageId);
    }

    /** An agent chunk carrying the goose {@code _meta} the harness attaches to messages. */
    private static SessionUpdate assistantChunkWithMeta(String messageId, ObjectNode meta) {
        JsonNode content = MAPPER.createObjectNode().put("type", "text").put("text", "t");
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(MessageType.agent_message_chunk,
                null, null, messageId, content, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, meta);
        return new SessionUpdate("2.0", "session/update", new SessionUpdate.Params("ses_capture", data));
    }

    @Test
    void costlessUsageUpdateStillRecordsARow() {
        registry().handle(usageUpdate(55674, 1048576, null), noopHandler());

        assertEquals(1, store.usage.size(), "costless update still writes one row");
        assertEquals(0.0, store.usage.get(0).costAmount(), 1e-9);
        assertEquals(55674L, store.usage.get(0).used());
        assertEquals("USD", store.usage.get(0).costCurrency());
    }

    @Test
    void messageUsageNotificationRecordsTokensTimingAndCompaction() {
        registry().handle(messageUsage("usage-1", 10, 20, 30, 5, 0.002, 900), noopHandler());

        assertEquals(1, store.messageUsage.size());
        MessageUsageRow row = store.messageUsage.get(0);
        assertEquals("usage-1", row.messageId());
        assertEquals(10, row.inputTokens());
        assertEquals(20, row.outputTokens());
        assertEquals(30, row.totalTokens());
        assertEquals(5, row.cacheReadTokens());
        assertEquals(0.002, row.cost());
        assertEquals(900, row.elapsedMs());
        assertEquals(120, row.timeToFirstTokenMs());
        assertFalse(row.compaction());
        assertEquals("m1", row.attribution().modelId());
    }

    @Test
    void harnessReportedModelOverridesTheSessionModel() {
        ObjectNode meta = MAPPER.createObjectNode();
        meta.putObject("goose").putObject("inference")
                .put("provider", "openrouter")
                .put("requestedModel", "openrouter:auto")
                .put("resolvedModel", "openrouter/anthropic/claude-sonnet-4");
        SessionUpdate update = assistantChunkWithMeta("m-meta", meta);

        registry().handle(update, noopHandler());

        assertEquals("openrouter/anthropic/claude-sonnet-4",
                store.messages.get(0).attribution().modelId(),
                "the model the harness says answered wins over the dropdown value");
    }

    @Test
    void repeatedMessageUsageForTheSameMessageIsRecordedOnce() {
        SessionUpdate update = messageUsage("dup-1", 10, 20, 30, 5, 0.002, 900);
        StrategyRegistry registry = registry();
        registry.handle(update, noopHandler());
        registry.handle(update, noopHandler());

        assertEquals(1, store.messageUsage.size());
    }

    @Test
    void replayedMessageUsageIsNotCapturedWhileLoading() {
        when(lookupInstance.lookup(SessionQuery.class))
                .thenReturn(streamingSessionQueryWithState(SessionState.LOADING));

        registry().handle(messageUsage("loading-1", 10, 20, 30, 5, 0.002, 900), noopHandler());

        assertTrue(store.messageUsage.isEmpty());
    }

    @Test
    void usageUpdateCostIsCarriedVerbatimForDeltaComputation() {
        registry().handle(usageUpdate(101015, 1000000, 0.0738938), noopHandler());

        assertEquals(1, store.usage.size());
        assertEquals(0.0738938, store.usage.get(0).costAmount(), 1e-9);
        assertEquals("USD", store.usage.get(0).costCurrency());
        assertEquals("ses_capture", store.usage.get(0).attribution().sessionId());
        assertEquals("pi", store.usage.get(0).attribution().harnessId());
        assertEquals("/proj/a", store.usage.get(0).attribution().project());
    }
    @Test
    void replayedHistoryIsNotCaptured() {
        when(lookupInstance.lookup(SessionQuery.class))
                .thenReturn(streamingSessionQueryWithState(SessionState.LOADING));

        registry().handle(usageUpdate(1, 10, 0.5), noopHandler());
        registry().handle(assistantChunk("m-1"), noopHandler());

        assertTrue(store.usage.isEmpty(), "replayed usage must not be recorded");
        assertTrue(store.messages.isEmpty(), "replayed messages must not be counted");
    }

    @Test
    void repeatedMessageIdCountsOnce() {
        for (int i = 0; i < 5; i++) {
            registry().handle(assistantChunk("msg-1"), noopHandler());
        }
        assertEquals(1, store.messages.size(), "streaming chunks sharing an id count once");
        assertEquals(MessageKind.ASSISTANT, store.messages.get(0).kind());
    }

    @Test
    void distinctMessageIdsCountSeparately() {
        for (int i = 0; i < 3; i++) {
            registry().handle(assistantChunk("ses_distinct", "msg-" + i), noopHandler());
        }
        assertEquals(3, store.messages.size());
    }

    /**
     * The steer-echo skip must sit *after* capture: the text was typed by the user, so it
     * still belongs in the stats even though the panel already drew it locally. Swapping the
     * two statements in StrategyRegistry's user_message_chunk case would keep the display
     * assertion below green and silently drop the message from the usage data.
     */
    @Test
    void aSteeredEchoIsStillCapturedEvenThoughItIsNotDisplayed() {
        SteerEchoes.clear();
        SteerEchoes.expect("ses_capture", "steer_echo_capture_1");
        github.anandb.netbeans.contract.UIHandler handler = noopHandler();

        registry().handle(userChunk("steer_echo_capture_1", "hello"), handler);

        verify(handler, never()).displayMessage(any());
        assertEquals(1, store.messages.size(), "the steered message must still be counted");
        assertEquals(MessageKind.USER, store.messages.get(0).kind());
        assertEquals("ses_capture", store.messages.get(0).attribution().sessionId());
        SteerEchoes.clear();
    }

    /** Builds the user_message_chunk goose replays for a steer, carrying the steer's id. */
    private static SessionUpdate userChunk(String messageId, String text) {
        JsonNode content = MAPPER.createObjectNode().put("type", "text").put("text", text);
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(MessageType.user_message_chunk,
                null, null, messageId, content, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        return new SessionUpdate("2.0", "session/update", new SessionUpdate.Params("ses_capture", data));
    }

    private static SessionQuery streamingSessionQueryWithState(SessionState state) {
        SessionQuery base = streamingSessionQuery();
        return new SessionQuery() {
            @Override public String getCurrentSessionId() { return base.getCurrentSessionId(); }
            @Override public String getCurrentSessionDirectory() { return base.getCurrentSessionDirectory(); }
            @Override public SessionState getCurrentState() { return state; }
            @Override public CompletableFuture<List<SessionConfigOption>> loadSessionFromServer(String id, String cwd) {
                return base.loadSessionFromServer(id, cwd);
            }
            @Override public String getCustomTitle(String sessionId, String defaultTitle) { return base.getCustomTitle(sessionId, defaultTitle); }
            @Override public String getSessionTitle(String sessionId) { return base.getSessionTitle(sessionId); }
            @Override public boolean isDescendantOfCurrent(String sessionId) { return base.isDescendantOfCurrent(sessionId); }
            @Override public boolean isHidden(String sessionId) { return base.isHidden(sessionId); }
            @Override public String getContextUsage(String sessionId) { return base.getContextUsage(sessionId); }
            @Override public Session getSession(String sessionId) { return base.getSession(sessionId); }
            @Override public String getHarnessId() { return base.getHarnessId(); }
            @Override public String getSessionModelId(String sessionId) { return base.getSessionModelId(sessionId); }
            @Override public String getSessionDirectory(String sessionId) { return base.getSessionDirectory(sessionId); }
        };
    }
}
