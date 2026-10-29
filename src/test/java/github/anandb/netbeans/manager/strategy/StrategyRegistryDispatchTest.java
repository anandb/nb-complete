package github.anandb.netbeans.manager.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.contract.UIHandler;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.ModelRecords.PlanEntry;
import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.MapperSupplier;

/**
 * {@link StrategyRegistry} dispatch, exercised through its injectable {@link UIHandler}.
 * An empty Lookup is enough: every capture path (usage/message stats) is null-guarded.
 * Static guards (plan detection, capture window) live in {@link StrategyRegistryTest}.
 */
class StrategyRegistryDispatchTest {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private UIHandler handler;
    private StrategyRegistry registry;

    @BeforeEach
    void setUp() {
        handler = mock(UIHandler.class);
        registry = new StrategyRegistry();
        StrategyRegistry.invalidateSession("s1");
    }

    // ---- dispatch guards ----------------------------------------------------

    @Test
    void updateWithoutTypeIsIgnored() {
        registry.handle(update("s1", data(null)), handler);

        verify(handler, never()).displayMessage(any());
        verify(handler, never()).updatePlan(any());
    }

    @Test
    void sessionInfoUpdateRendersNothing() {
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(
                MessageType.session_info_update, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
        registry.handle(update("s1", data), handler);

        verify(handler, never()).displayMessage(any());
        verify(handler, never()).updatePlan(any());
    }

    @Test
    void chunkAnnotatedForTheAssistantIsSkipped() {
        ObjectNode content = MAPPER.createObjectNode();
        content.put("text", "internal reasoning");
        content.putObject("annotations").putArray("audience").add("assistant");
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(
                MessageType.agent_message_chunk, null, null, "m1", content, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        registry.handle(update("s1", data), handler);

        verify(handler, never()).displayMessage(any());
    }

    // ---- text chunks --------------------------------------------------------

    @Test
    void agentChunkIsDisplayedAsStreamingMessage() {
        registry.handle(update("s1", chunk(MessageType.agent_message_chunk, "hello", "m1")), handler);

        ProcessedMessage pm = captureDisplayed();
        assertEquals(MessageType.agent_message_chunk, pm.messageType());
        assertEquals("hello", pm.text());
        assertEquals("m1", pm.messageId());
        assertTrue(pm.streaming());
    }

    @Test
    void userChunkIsDisplayed() {
        registry.handle(update("s1", chunk(MessageType.user_message_chunk, "hi there", "u1")), handler);

        ProcessedMessage pm = captureDisplayed();
        assertEquals(MessageType.user_message_chunk, pm.messageType());
        assertEquals("hi there", pm.text());
    }

    @Test
    void blankUserChunkIsSkipped() {
        registry.handle(update("s1", chunk(MessageType.user_message_chunk, "   ", "u1")), handler);

        verify(handler, never()).displayMessage(any());
    }

    // ---- usage and config ---------------------------------------------------

    @Test
    void usageUpdateForwardsTokens() {
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(
                MessageType.usage_update, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, 120L, 1_000L, null, null, null);

        registry.handle(update("s1", data), handler);

        verify(handler).updateUsage(120L, 1_000L);
    }

    @Test
    void configOptionUpdateForwardsOptions() {
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(
                MessageType.config_option_update, null, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of(), null, null, null, null, null);

        registry.handle(update("s1", data), handler);

        verify(handler).updateConfig(List.of());
        verify(handler, never()).displayMessage(any());
    }

    // ---- plan ---------------------------------------------------------------

    @Test
    void planEntriesAreSortedByPriority() throws Exception {
        JsonNode entries = MAPPER.readTree("""
                [{"content":"low item","status":"pending","priority":"low"},
                 {"content":"high item","status":"pending","priority":"high"},
                 {"content":"medium item","status":"pending","priority":"medium"}]""");
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(
                MessageType.plan, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, entries, null, null);

        registry.handle(update("s1", data), handler);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PlanEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(handler).updatePlan(captor.capture());
        List<PlanEntry> plan = captor.getValue();
        assertEquals(3, plan.size());
        assertEquals("high item", plan.get(0).content());
        assertEquals("medium item", plan.get(1).content());
        assertEquals("low item", plan.get(2).content());
        verify(handler, never()).displayMessage(any());
    }

    @Test
    void emptyPlanIsIgnored() {
        SessionUpdate.UpdateData data = new SessionUpdate.UpdateData(
                MessageType.plan, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, MAPPER.createArrayNode(), null, null);

        registry.handle(update("s1", data), handler);

        verify(handler, never()).updatePlan(any());
    }

    // ---- tool calls ---------------------------------------------------------

    @Test
    void toolCallWithoutIdsIsSkipped() {
        registry.handle(update("s1", toolCall(null, null, "ls", "in_progress")), handler);

        verify(handler, never()).displayMessage(any());
    }

    @Test
    void toolCallIsDisplayedWithItsTitle() {
        registry.handle(update("s1", toolCall("m1", "s1", "ls -la", "in_progress")), handler);

        ProcessedMessage pm = captureDisplayed();
        assertTrue(pm.text().contains("ls -la"), pm.text());
        assertTrue(pm.toolTitle() != null && !pm.toolTitle().isBlank(), "tool title expected");
        assertTrue(pm.streaming());
    }

    @Test
    void completedToolCallIsNotDisplayedTwice() {
        registry.handle(update("s1", toolCall("m1", "s1", "ls", "in_progress")), handler);
        registry.handle(update("s1", toolCall("m1", "s1", "ls", "completed")), handler);

        verify(handler, times(1)).displayMessage(any());
    }

    // ---- helpers ------------------------------------------------------------

    private ProcessedMessage captureDisplayed() {
        ArgumentCaptor<ProcessedMessage> captor = ArgumentCaptor.forClass(ProcessedMessage.class);
        verify(handler, org.mockito.Mockito.atLeastOnce()).displayMessage(captor.capture());
        return captor.getValue();
    }

    private static SessionUpdate update(String sessionId, SessionUpdate.UpdateData data) {
        return new SessionUpdate(SessionUpdate.JSONRPC_VERSION, SessionUpdate.METHOD,
                new SessionUpdate.Params(sessionId, data));
    }

    /** An {@link SessionUpdate.UpdateData} with every field unset except the type. */
    private static SessionUpdate.UpdateData data(MessageType type) {
        return new SessionUpdate.UpdateData(type, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static SessionUpdate.UpdateData chunk(MessageType type, String text, String messageId) {
        ObjectNode content = MAPPER.createObjectNode();
        content.put("text", text);
        return new SessionUpdate.UpdateData(type, null, null, messageId, content, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static SessionUpdate.UpdateData toolCall(String messageId, String toolCallId,
                                                     String command, String status) {
        ObjectNode rawInput = MAPPER.createObjectNode();
        rawInput.put("command", command);
        ObjectNode part = MAPPER.createObjectNode();
        part.put("type", "text");
        part.put("text", "output of " + command);
        var content = MAPPER.createArrayNode().add(part);
        return new SessionUpdate.UpdateData(MessageType.tool_call, command, rawInput, messageId, content,
                null, null, null, null, status, "execute", toolCallId, null, null, null, null, null,
                null, null, null);
    }
}
