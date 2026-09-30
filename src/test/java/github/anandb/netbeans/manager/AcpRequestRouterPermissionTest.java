package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.contract.PermissionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Permission decisions must fail CLOSED, and the standard {@code outcome} field
 * and the legacy {@code allow} boolean must never disagree.
 *
 * <p>Regression cover for a fail-open: an unresolvable (null) decision used to map
 * to {@code "once"} — an allow — and the legacy boolean, keyed off the raw option
 * id, wrote {@code allow:true} even when the outcome said {@code "reject"}. A
 * harness reading the compat field was granted access the standard field had just
 * denied. There is no undo for that.
 */
class AcpRequestRouterPermissionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Completes every permission request with the given decision, including null. */
    private static PermissionHandler handlerReturning(String decision) {
        return (sessionId, params, response) -> response.complete(decision);
    }

    private static JsonNode request() {
        return MAPPER.createObjectNode()
                .put("sessionId", "sess-1")
                .put("toolCallId", "call-1");
    }

    private static JsonNode decide(PermissionHandler handler) throws Exception {
        AcpRequestRouter router = new AcpRequestRouter();
        router.setPermissionHandler(handler);
        CompletableFuture<JsonNode> future = router.handleRequestPermission(request());
        // handleRequestPermission hops to the EDT before consulting the handler.
        SwingUtilities.invokeAndWait(() -> { });
        return future.get(5, TimeUnit.SECONDS);
    }

    private static String optionId(JsonNode response) {
        return response.get("outcome").get("optionId").asText();
    }

    @Test
    void nullDecisionRejects() throws Exception {
        JsonNode response = decide(handlerReturning(null));
        assertEquals("reject", optionId(response),
                "A null decision means 'unknown' and must never resolve to an allow");
    }

    @Test
    void nullDecisionDoesNotGrantAccessViaLegacyField() throws Exception {
        JsonNode response = decide(handlerReturning(null));
        assertFalse(response.get("allow").asBoolean(),
                "The legacy allow field must not contradict the outcome field");
    }

    @Test
    void outcomeAndLegacyFieldAlwaysAgree() throws Exception {
        for (String decision : new String[] {null, "allow", "deny", "reject", "allow_always"}) {
            JsonNode response = decide(handlerReturning(decision));
            boolean standardSaysReject = "reject".equals(optionId(response));
            assertEquals(!standardSaysReject, response.get("allow").asBoolean(),
                    "outcome.optionId and allow disagreed for decision: " + decision);
        }
    }

    @Test
    void allowMapsToOnce() throws Exception {
        assertEquals("once", optionId(decide(handlerReturning("allow"))));
    }

    @Test
    void denyMapsToReject() throws Exception {
        assertEquals("reject", optionId(decide(handlerReturning("deny"))));
    }

    @Test
    void harnessSuppliedOptionIdPassesThroughUnchanged() throws Exception {
        // The panel builds buttons from the harness's own options array, so a harness may
        // offer ids we have never seen. Those must survive intact, not collapse to once/reject.
        assertEquals("allow_always", optionId(decide(handlerReturning("allow_always"))));
    }

    @Test
    void noHandlerRejects() throws Exception {
        AcpRequestRouter router = new AcpRequestRouter();
        router.setPermissionHandler(null);
        JsonNode response = router.handleRequestPermission(request()).get(5, TimeUnit.SECONDS);
        assertEquals("reject", optionId(response));
    }
}
