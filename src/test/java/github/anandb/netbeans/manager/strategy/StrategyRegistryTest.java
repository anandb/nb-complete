package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import github.anandb.netbeans.model.MessageType;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.support.MapperSupplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyRegistryTest {

    private static final JsonMapper MAPPER = MapperSupplier.get();

    private static SessionUpdate createUpdateWithOutput(String outputText) {
        JsonNode rawOutput = MAPPER.createObjectNode().put("output", outputText);
        SessionUpdate.UpdateData ud = new SessionUpdate.UpdateData(
                MessageType.tool_call, null, null, "m1", null, null, null, null, null,
                "completed", null, null, rawOutput, null, null, null, null, null
        );
        SessionUpdate.Params params = new SessionUpdate.Params("s1", ud);
        return new SessionUpdate("2.0", "session/update", params);
    }

    @Test
    void planEntryCompactJson() {
        SessionUpdate update = createUpdateWithOutput(
                "[{\"content\":\"x\",\"status\":\"y\",\"priority\":\"z\"}]"
        );
        assertTrue(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void planEntryWithWhitespaceAfterBracket() {
        SessionUpdate update = createUpdateWithOutput(
                "[ {\"content\":\"x\",\"status\":\"y\",\"priority\":\"z\"}]"
        );
        assertTrue(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void planEntryPrettyPrinted() {
        SessionUpdate update = createUpdateWithOutput(
                "[\n  {\"content\":\"x\",\"status\":\"y\",\"priority\":\"z\"}\n]"
        );
        assertTrue(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void gitLogLineNotPlan() {
        SessionUpdate update = createUpdateWithOutput(
                "[main 4884fb0] fix: guard 5 potential NPE paths"
        );
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void nonJsonTextNotPlan() {
        SessionUpdate update = createUpdateWithOutput("hello world");
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void jsonObjectNotPlan() {
        SessionUpdate update = createUpdateWithOutput("{\"key\":\"val\"}");
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void jsonArrayOfPrimitivesNotPlan() {
        SessionUpdate update = createUpdateWithOutput("[1, 2, 3]");
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void emptyOutputNotPlan() {
        SessionUpdate update = createUpdateWithOutput("");
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void nullOutputIsNotPlan() {
        SessionUpdate.Params params = new SessionUpdate.Params("s1", null);
        SessionUpdate update = new SessionUpdate("2.0", "session/update", params);
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void nonCompletedStatusIsNotPlan() {
        JsonNode rawOutput = MAPPER.createObjectNode().put("output", "[{\"content\":\"x\",\"status\":\"y\",\"priority\":\"z\"}]");
        SessionUpdate.UpdateData ud = new SessionUpdate.UpdateData(
                MessageType.tool_call, null, null, "m1", null, null, null, null, null,
                "running", null, null, rawOutput, null, null, null, null, null
        );
        SessionUpdate.Params params = new SessionUpdate.Params("s1", ud);
        SessionUpdate update = new SessionUpdate("2.0", "session/update", params);
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }

    @Test
    void planEntryMissingFieldsNotPlan() {
        SessionUpdate update = createUpdateWithOutput(
                "[{\"name\":\"foo\",\"value\":\"bar\"}]"
        );
        assertFalse(StrategyRegistry.isPlanToolCall(update));
    }
}
