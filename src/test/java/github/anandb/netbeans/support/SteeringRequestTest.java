package github.anandb.netbeans.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code _session/steering} request shape and the outcome the reply may carry. Both matter:
 * the request is what an adapter validates, and a reply that is not {@code injected} means the
 * message never reached the turn, which the UI has to undo.
 */
class SteeringRequestTest {

    @Test
    void theMethodNameIsTheExtensionEveryAdapterAgreesOn() {
        assertEquals("_session/steering", SteeringRequest.METHOD);
    }

    @Test
    void textBecomesTheFirstBlockAndAttachmentsFollowIt() {
        List<Map<String, Object>> blocks = List.of(Map.of("type", "text", "text", "attached"));

        Map<String, Object> params = SteeringRequest.params("s1", "hello", blocks);

        assertEquals("s1", params.get("sessionId"));
        assertFalse(params.containsKey("expectedRunId"), "the extension takes no run id");
        List<?> prompt = (List<?>) params.get("prompt");
        assertEquals(2, prompt.size());
        assertEquals(Map.of("type", "text", "text", "hello"), prompt.get(0));
        assertEquals(blocks.get(0), prompt.get(1));
        assertEquals(Map.of("steering", Map.of("idleBehavior", "promptRequired")), params.get("_meta"),
                "an idle steer must be refused, not turned into a detached turn");
    }

    @Test
    void anEmptyTextWithBlocksIsStillAValidRequest() {
        List<Map<String, Object>> blocks = List.of(Map.of("type", "text", "text", "attached"));

        List<?> prompt = (List<?>) SteeringRequest.params("s1", "", blocks).get("prompt");

        assertEquals(1, prompt.size(), "no empty text block is emitted");
        assertEquals(blocks.get(0), prompt.get(0));
    }

    @Test
    void onlyTheInjectedOutcomeCountsAsDelivered() {
        assertTrue(SteeringRequest.isInjected(reply("injected")));
        assertFalse(SteeringRequest.isInjected(reply("promptRequired")));
        assertFalse(SteeringRequest.isInjected(reply("startedNewTurn")));
        assertFalse(SteeringRequest.isInjected(null));
        assertFalse(SteeringRequest.isInjected(MapperSupplier.get().createObjectNode()),
                "a reply with no outcome cannot claim the message landed");
    }

    @Test
    void outcomeIsNullWhenTheReplyCarriesNone() {
        assertNull(SteeringRequest.outcome(null));
        assertNull(SteeringRequest.outcome(MapperSupplier.get().createObjectNode()));
        assertEquals("injected", SteeringRequest.outcome(reply("injected")));
    }

    @Test
    void onlyTheIdleOutcomesCountAsTurnGone() {
        assertTrue(SteeringRequest.isTurnGone(reply("promptRequired")));
        assertTrue(SteeringRequest.isTurnGone(reply("startedNewTurn")));
        // omp answers `failed` when the injection itself broke: that is an error, not a miss.
        assertFalse(SteeringRequest.isTurnGone(reply("failed")));
        assertFalse(SteeringRequest.isTurnGone(reply("injected")));
        assertFalse(SteeringRequest.isTurnGone(null));
    }

    private static JsonNode reply(String outcome) {
        ObjectNode root = MapperSupplier.get().createObjectNode();
        root.put("outcome", outcome);
        return root;
    }
}
