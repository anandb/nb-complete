package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.model.SessionConfigOption;
import github.anandb.netbeans.model.SessionState;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageKind;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import github.anandb.netbeans.model.UsageRecords.UsageSummary;
import github.anandb.netbeans.model.UsageRecords.UsageUpdateRow;
import github.anandb.netbeans.support.MapperSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.openide.util.Lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
        public UsageSummary query(int days, String projectDir, long now) {
            throw new UnsupportedOperationException("query is not part of this test");
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

    /** Builds a usage_update carrying the gauge and an optional USD cost. */
    private static SessionUpdate usageUpdate(long used, long size, Double amount) {
        ObjectNode cost = null;
        if (amount != null) {
            cost = MAPPER.createObjectNode().put("amount", amount).put("currency", "USD");
        }
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(MessageType.usage_update,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, used, size, null, null, cost);
        return new SessionUpdate("2.0", "session/update", new SessionUpdate.Params("ses_capture", data));
    }

    /** Builds an agent_message_chunk with the given streaming message id. */
    private static SessionUpdate assistantChunk(String sessionId, String messageId) {
        JsonNode content = MAPPER.createObjectNode().put("type", "text").put("text", "t");
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(MessageType.agent_message_chunk,
                null, null, messageId, content, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        return new SessionUpdate("2.0", "session/update", new SessionUpdate.Params(sessionId, data));
    }

    /** Same builder, using the default streaming session of the usage tests. */
    private static SessionUpdate assistantChunk(String messageId) {
        return assistantChunk("ses_capture", messageId);
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
