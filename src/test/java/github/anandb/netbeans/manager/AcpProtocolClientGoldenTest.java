package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.SteerEchoes;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden transcript replay: fixture wire bytes → real {@link AcpProtocolClient} streaming
 * parse → real {@link SessionUpdateDecoder} → asserted type/id sequences. Pins the
 * behaviors that historically regressed: textual turn-end signals surviving Jackson,
 * UUID string request ids echoed verbatim, malformed neighbors not killing the stream,
 * goose's steer sequence (run announce → queued marker → echo → run end) and the
 * {@code session/resume} request/response round trip.
 */
@Tag("P0")
@Tag("Protocol")
class AcpProtocolClientGoldenTest {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private MockAcpServer server;
    private AcpProtocolClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockAcpServer();
        client = server.client();
    }

    @AfterEach
    void tearDown() throws IOException {
        SteerEchoes.clear();
        ActiveRunTracker.clear();
        server.close();
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = AcpProtocolClientGoldenTest.class.getResourceAsStream("/acp/" + name)) {
            assertNotNull(in, "missing fixture /acp/" + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void awaitCount(IntSupplier count, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (count.getAsInt() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, count.getAsInt(), "timed out waiting for session/update notifications");
        Thread.sleep(100);
        assertEquals(expected, count.getAsInt(), "extra unexpected notifications arrived");
    }

    @Test
    void gooseTurnReplayDecodesInOrderAndEchoesUuidId() throws Exception {
        List<JsonNode> rawParams = new CopyOnWriteArrayList<>();
        client.onNotification("session/update", rawParams::add);
        client.onRequest("session/request_permission",
            params -> CompletableFuture.completedFuture(JsonNodeFactory.instance.objectNode()));

        server.sendToClient(fixture("goose-turn.jsonl"));
        awaitCount(rawParams::size, 4);

        // Decode on the test thread, in wire order.
        List<SessionUpdate> decoded = new java.util.ArrayList<>();
        for (JsonNode params : rawParams) {
            SessionUpdate update = SessionUpdateDecoder.decode(params);
            assertNotNull(update, "fixture update must decode: " + params);
            decoded.add(update);
        }

        assertEquals(MessageType.available_commands_update, decoded.get(0).update().type());
        assertEquals(MessageType.agent_message_chunk, decoded.get(1).update().type());
        assertEquals(MessageType.agent_message_chunk, decoded.get(2).update().type());
        assertEquals(MessageType.responding_finished, decoded.get(3).update().type());

        // available_commands_update arrives at turn START with commands; turn-end is synthetic.
        assertEquals(1, decoded.get(0).update().availableCommands().size());
        assertEquals("session_ready", decoded.get(0).update().availableCommands().get(0).name());
        assertEquals("g-msg-1", decoded.get(1).update().messageId());
        assertEquals("g-msg-1", decoded.get(2).update().messageId());
        assertNull(decoded.get(3).update().messageId(), "synthetic turn-end carries no messageId");
        for (SessionUpdate update : decoded) {
            assertEquals("goose-sess-1", update.params().sessionId());
        }

        // The inbound request carried a UUID string id; the response must echo it verbatim.
        JsonNode response = server.readFromClient();
        JsonNode responseId = response.get("id");
        assertNotNull(responseId, "response must carry an id");
        assertTrue(responseId.isTextual(), "UUID id must not be coerced to a number: " + responseId);
        assertEquals("517ef9d2-8f6a-4d21-9c3e-2b1a4f7e9d00", responseId.asText());
    }

    /**
     * The steer round trip as goose emits it: the run id announced on
     * {@code session_info_update}, the queued-steer marker, the echo carrying the steer's
     * message id, then the run ending. The tracker and the echo expectation must survive
     * the middle of the turn and be forgotten together at the end, or the next steer
     * carries a dead {@code expectedRunId} and the echo renders a second bubble.
     */
    @Test
    void gooseSteerReplayTracksTheRunQueuesTheEchoAndClearsBothAtRunEnd() throws Exception {
        String sessionId = "goose-steer-sess";
        List<SessionUpdate> decoded = new CopyOnWriteArrayList<>();
        List<String> runs = new CopyOnWriteArrayList<>();
        List<Boolean> echoConsumed = new CopyOnWriteArrayList<>();
        client.onNotification("session/update", params -> {
            SessionUpdate update = SessionUpdateDecoder.decode(params);
            decoded.add(update);
            runs.add(ActiveRunTracker.get(sessionId));
            if (update != null && update.update() != null
                    && update.update().type() == MessageType.user_message_chunk) {
                echoConsumed.add(SteerEchoes.consume(sessionId, update.update().messageId()));
            }
        });
        SteerEchoes.expect(sessionId, "steer-1");

        server.sendToClient(fixture("goose-steer.jsonl"));
        awaitCount(decoded::size, 5);

        for (SessionUpdate update : decoded) {
            assertNotNull(update, "every steer transcript update must decode");
        }
        assertEquals(List.of(
                MessageType.session_info_update,
                MessageType.agent_message_chunk,
                MessageType.session_info_update,
                MessageType.user_message_chunk,
                MessageType.session_info_update),
                decoded.stream().map(update -> update.update().type()).toList());
        assertEquals(Arrays.asList("run-7", "run-7", "run-7", "run-7", null), runs,
                "the run id is learnt on announcement and dropped when goose sends null");
        assertEquals("steer-1",
                decoded.get(2).meta().path("goose").path("queuedSteer").path("messageId").asText(),
                "the queued marker names the steer before its echo lands");
        assertEquals("steer-1",
                decoded.get(3).meta().path("goose").path("steer").path("messageId").asText(),
                "the user_message_chunk echo carries the steer id");
        assertEquals(List.of(true), echoConsumed,
                "the locally drawn steer must not be rendered twice");
        assertNull(ActiveRunTracker.get(sessionId), "run end drops the run id");
        assertFalse(SteerEchoes.consume(sessionId, "steer-1"),
                "run end forgets a steer it can no longer echo");
    }

    /**
     * {@code session/resume} is a standard request, not a notification. Pinning the wire
     * shape plus the response envelope proves the client completes the pending future and
     * the manager receives the resumed session payload (cwd + config options) as goose sends it.
     */
    @Test
    void gooseResumeRoundTripCompletesThePendingRequest() throws Exception {
        Map<String, Object> params = Map.of(
                "sessionId", "goose-resume-sess",
                "cwd", "/work/project",
                "mcpServers", List.of());

        CompletableFuture<JsonNode> resumed = client.sendRequest("session/resume", params, 5, TimeUnit.SECONDS);
        JsonNode request = server.readFromClient();

        assertEquals("session/resume", request.get("method").asText());
        assertEquals("goose-resume-sess", request.at("/params/sessionId").asText());
        assertEquals("/work/project", request.at("/params/cwd").asText());

        ObjectNode response = (ObjectNode) MAPPER.readTree(fixture("goose-resume-response.json"));
        response.set("id", request.get("id"));
        server.sendToClient(MAPPER.writeValueAsString(response));

        JsonNode result = resumed.get(5, TimeUnit.SECONDS);
        assertEquals("goose-resume-sess", result.path("sessionId").asText());
        assertEquals("/work/project", result.path("cwd").asText());
        assertEquals("m1", result.at("/configOptions/0/currentValue").asText());
    }

    @Test
    void opencodeTurnReplayDecodesChunksToolCallAndEndTurn() throws Exception {
        List<JsonNode> rawParams = new CopyOnWriteArrayList<>();
        client.onNotification("session/update", rawParams::add);

        server.sendToClient(fixture("opencode-turn.jsonl"));
        awaitCount(rawParams::size, 3);

        SessionUpdate chunk = SessionUpdateDecoder.decode(rawParams.get(0));
        SessionUpdate tool = SessionUpdateDecoder.decode(rawParams.get(1));
        SessionUpdate end = SessionUpdateDecoder.decode(rawParams.get(2));

        assertNotNull(chunk);
        assertNotNull(tool);
        assertNotNull(end);
        assertEquals(MessageType.agent_message_chunk, chunk.update().type());
        assertEquals(MessageType.tool_call, tool.update().type());
        assertEquals(MessageType.end_turn, end.update().type());

        assertEquals("o-msg-1", chunk.update().messageId());
        assertEquals("tc-1", tool.update().toolCallId());
        assertEquals("Read README.md", tool.update().title());
        assertNull(end.update().messageId(), "synthetic turn-end carries no messageId");
        assertEquals("oc-sess-1", end.params().sessionId());
    }

    @Test
    void malformedNeighborDoesNotKillStream() throws Exception {
        // Known gap: RAW non-JSON bytes thrown at readLoop's nextToken() still escape the inner catch and
        // kill the reader thread. This fixture pins only the recoverable case (junk JSON object between
        // valid notifications); raw-byte recovery needs a follow-up catching around nextToken().
        AtomicInteger received = new AtomicInteger();
        List<String> messageIds = new CopyOnWriteArrayList<>();
        client.onNotification("session/update", params -> {
            received.incrementAndGet();
            SessionUpdate update = SessionUpdateDecoder.decode(params);
            if (update != null && update.update() != null) {
                messageIds.add(update.update().messageId());
            }
        });

        server.sendToClient(fixture("malformed-recovery.jsonl"));
        awaitCount(received::get, 2);

        assertEquals(List.of("r-msg-1", "r-msg-2"), messageIds,
            "both neighbors of the garbage must decode, in order");
    }
}
