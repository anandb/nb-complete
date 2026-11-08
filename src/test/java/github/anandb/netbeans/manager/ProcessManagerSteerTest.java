package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.SteerEchoes;
import github.anandb.netbeans.support.SteerRequest;
import github.anandb.netbeans.support.SteeringRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The assembled steer request. {@code support/SteerRequest} is unit-tested on its own and so is
 * the tracker that feeds it; what was never exercised is {@link ProcessManager#steerMessage}
 * putting the two together over the wire — the method name, the {@code expectedRunId} taken from
 * the tracked run, and the fail-fast when no run is known.
 */
class ProcessManagerSteerTest {

    private ProcessManager processManager;
    private AcpProtocolClient client;

    @BeforeEach
    void setUp() throws Exception {
        ActiveRunTracker.clear();
        SteerEchoes.clear();
        try {
            processManager = new ProcessManager();
        } catch (Exception e) {
            processManager = null; // headless preferences/lifecycle unavailable
            return;
        }
        client = mock(AcpProtocolClient.class);
        doAnswer(inv -> new CompletableFuture<JsonNode>())
                .when(client).sendRequest(anyString(), any(), anyLong(), any(TimeUnit.class));
        rpcClient().set(client);
    }

    @AfterEach
    void tearDown() {
        ActiveRunTracker.clear();
        SteerEchoes.clear();
        if (processManager != null) {
            processManager.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<AcpProtocolClient> rpcClient() throws Exception {
        Field field = ProcessManager.class.getDeclaredField("rpcClient");
        field.setAccessible(true);
        return (AtomicReference<AcpProtocolClient>) field.get(processManager);
    }

    /** The raw {@code session_info_update} node goose publishes the run id on. */
    private static JsonNode runUpdate(String runId) {
        ObjectNode root = MapperSupplier.get().createObjectNode();
        root.putObject("_meta").putObject("goose").put("activeRunId", runId);
        return root;
    }

    @Test
    void steerRequestCarriesTheTrackedRunIdAndBlocks() {
        if (processManager == null) {
            return;
        }
        ActiveRunTracker.update("s1", runUpdate("run-7"));
        List<Map<String, Object>> blocks = List.of(Map.of("type", "text", "text", "attached"));

        processManager.steerMessage("s1", "hello", blocks);

        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(client).sendRequest(eq(SteerRequest.METHOD), params.capture(), anyLong(), any(TimeUnit.class));
        Map<?, ?> sent = assertInstanceOf(Map.class, params.getValue());
        assertEquals("s1", sent.get("sessionId"));
        assertEquals("run-7", sent.get("expectedRunId"), "the id must come from the tracked run");
        List<?> prompt = assertInstanceOf(List.class, sent.get("prompt"));
        assertEquals(2, prompt.size(), "the text block, then the attachment blocks");
        assertEquals(Map.of("type", "text", "text", "hello"), prompt.get(0));
        assertEquals(blocks.get(0), prompt.get(1));
    }

    @Test
    void steerWithoutATrackedRunFailsBeforeAnythingIsSent() {
        if (processManager == null) {
            return;
        }
        CompletableFuture<JsonNode> future = processManager.steerMessage("s1", "hello", List.of());

        assertTrue(future.isCompletedExceptionally(), "no run id means no steer to issue");
        verify(client, never()).sendRequest(eq(SteerRequest.METHOD), any(), anyLong(), any(TimeUnit.class));
        assertNull(processManager.getActiveRunId("s1"));
    }

    @Test
    void steerSessionSendsTheCrossAdapterSteeringRequest() {
        if (processManager == null) {
            return;
        }
        ObjectNode reply = MapperSupplier.get().createObjectNode().put("outcome", "injected");
        when(client.sendRequest(eq(SteeringRequest.METHOD), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.completedFuture(reply));
        List<Map<String, Object>> blocks = List.of(Map.of("type", "text", "text", "attached"));

        JsonNode result = processManager.steerSession("s1", "hello", blocks).join();

        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(client).sendRequest(eq(SteeringRequest.METHOD), params.capture(), anyLong(), any(TimeUnit.class));
        Map<?, ?> sent = assertInstanceOf(Map.class, params.getValue());
        assertEquals("s1", sent.get("sessionId"));
        assertFalse(sent.containsKey("expectedRunId"), "_session/steering carries no run id");
        List<?> prompt = assertInstanceOf(List.class, sent.get("prompt"));
        assertEquals(2, prompt.size(), "the text block, then the attachment blocks");
        assertEquals(Map.of("type", "text", "text", "hello"), prompt.get(0));
        assertEquals("injected", SteeringRequest.outcome(result));
    }

    /** goose's rejection of a steer whose expectedRunId no longer names the live run. */
    private static AcpRpcException staleRunRejection(String expected, String actual) {
        ObjectNode data = MapperSupplier.get().createObjectNode();
        data.put("message", "expected active run id `" + expected + "` but found `" + actual + "`");
        data.put("expectedRunId", expected);
        data.put("actualRunId", actual);
        return new AcpRpcException(-32602, "Invalid params", data);
    }

    @Test
    void aStaleRunIdIsRetriedOnceWithTheRunIdTheServerReports() throws Exception {
        if (processManager == null) {
            return;
        }
        ActiveRunTracker.update("s1", runUpdate("run-old"));
        ObjectNode ok = MapperSupplier.get().createObjectNode().put("messageId", "steer_2");
        when(client.sendRequest(eq(SteerRequest.METHOD), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.failedFuture(staleRunRejection("run-old", "run-new")),
                            CompletableFuture.completedFuture(ok));

        JsonNode result = processManager.steerMessage("s1", "hello", List.of()).get(5, TimeUnit.SECONDS);

        assertEquals("steer_2", result.get("messageId").asText());
        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(client, times(2)).sendRequest(eq(SteerRequest.METHOD), params.capture(),
                anyLong(), any(TimeUnit.class));
        assertEquals("run-old", ((Map<?, ?>) params.getAllValues().get(0)).get("expectedRunId"));
        assertEquals("run-new", ((Map<?, ?>) params.getAllValues().get(1)).get("expectedRunId"),
                "the retry must carry the id the server says is live");
        assertEquals("run-new", processManager.getActiveRunId("s1"), "the tracker learns the live id");
    }

    @Test
    void aSteerRejectedForAnyOtherReasonIsNotRetried() {
        if (processManager == null) {
            return;
        }
        ActiveRunTracker.update("s1", runUpdate("run-7"));
        when(client.sendRequest(eq(SteerRequest.METHOD), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new AcpRpcException(-32602, "Invalid params",
                                MapperSupplier.get().getNodeFactory().textNode("no active run to steer"))));

        CompletableFuture<JsonNode> future = processManager.steerMessage("s1", "hello", List.of());

        assertTrue(future.isCompletedExceptionally());
        verify(client, times(1)).sendRequest(eq(SteerRequest.METHOD), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void anEndedRunClearsTheSteerExpectationItOwned() {
        if (processManager == null) {
            return;
        }
        ActiveRunTracker.update("s1", runUpdate("run-7"));
        SteerEchoes.expect("s1", "steer_1");

        // goose publishes activeRunId=null when the run finishes.
        ObjectNode ended = MapperSupplier.get().createObjectNode();
        ended.putObject("_meta").putObject("goose").putNull("activeRunId");
        ActiveRunTracker.update("s1", ended);

        assertNull(processManager.getActiveRunId("s1"));
        assertFalse(SteerEchoes.consume("s1", "steer_1"),
                "an echo can never arrive for a finished run, so the expectation must be gone");
    }
}
