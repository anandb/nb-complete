package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.MapperSupplier;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Layer-2 golden tests for the pure {@code session/update} wire decoder: both wire
 * shapes (update-wrapper and bare textual turn-end) plus the never-throws contract.
 */
class SessionUpdateDecoderTest {

    private static JsonNode json(String source) throws IOException {
        return MapperSupplier.get().readTree(source);
    }

    @Test
    void textualRespondingFinishedDecodesToSyntheticUpdate() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"s1\",\"sessionUpdate\":\"responding_finished\"}"));

        assertNotNull(update, "textual turn-end must survive decoding");
        assertEquals(MessageType.responding_finished, update.update().type());
        assertEquals("s1", update.params().sessionId());
        assertNull(update.update().messageId(), "synthetic turn-end carries no messageId");
        assertEquals("2.0", update.jsonrpc());
        assertEquals("session/update", update.method());
    }

    @Test
    void objectFormTurnEndSignalAlsoDecodes() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"s2\",\"sessionUpdate\":{\"type\":\"end_turn\"}}"));

        assertNotNull(update, "object-form turn-end must decode via the rawType branch");
        assertEquals(MessageType.end_turn, update.update().type());
        assertEquals("s2", update.params().sessionId());
    }

    @Test
    void wrappedChunkPreservesMessageIdAndSessionId() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"oc-1\",\"update\":{"
                + "\"sessionUpdate\":\"agent_message_chunk\",\"messageId\":\"m-7\","
                + "\"content\":{\"type\":\"text\",\"text\":\"hello\"}}}"));

        assertNotNull(update);
        assertEquals(MessageType.agent_message_chunk, update.update().type());
        assertEquals("m-7", update.update().messageId());
        assertEquals("oc-1", update.params().sessionId());
    }

    @Test
    void availableCommandsUpdateDecodesToItsOwnType() throws IOException {
        // Decode is type-pure: it maps wire → model. The turn-end decision belongs to
        // SessionLifecycleHandler (layer 3); nothing here flags turn end.
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"g-1\",\"update\":{"
                + "\"sessionUpdate\":\"available_commands_update\","
                + "\"availableCommands\":[{\"name\":\"session_ready\",\"description\":\"ready\"}]}}"));

        assertNotNull(update);
        assertEquals(MessageType.available_commands_update, update.update().type());
        assertEquals(1, update.update().availableCommands().size());
        assertEquals("session_ready", update.update().availableCommands().get(0).name());
    }

    @Test
    void undecodablePayloadReturnsNullInsteadOfThrowing() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"s3\",\"update\":{\"sessionUpdate\":{\"type\":42}}}"));

        assertNull(update, "undecodable payloads are dropped, never thrown");
    }

    @Test
    void nullParamsNeverThrows() {
        assertNull(SessionUpdateDecoder.decode(null), "null params are dropped, never thrown");
        assertNull(SessionUpdateDecoder.decode(MapperSupplier.get().nullNode()), "NullNode is dropped, never thrown");
    }

    @Test
    void unknownFutureTypeSurvivesWithNullType() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"s9\",\"update\":{\"sessionUpdate\":\"some_future_kind\"}}"));
        assertNotNull(update, "unknown enum must not drop the update");
        assertEquals("s9", update.params().sessionId());
        assertNull(update.update().type(), "unknown type maps to null, caller decides");
    }
}
