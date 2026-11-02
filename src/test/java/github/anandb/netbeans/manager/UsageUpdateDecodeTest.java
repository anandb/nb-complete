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
 * Golden test for the {@code usage_update} decode path: the {@code cost} object
 * must survive Jackson mapping onto {@link SessionUpdate.UpdateData} so the
 * capture path can read it.
 */
class UsageUpdateDecodeTest {

    private static JsonNode json(String source) throws IOException {
        return MapperSupplier.get().readTree(source);
    }

    @Test
    void usageUpdatePayloadCarriesGaugeAndCost() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"ses_f2fe2600bffeqiKy7nvWbjYQ38\",\"update\":{"
            + "\"sessionUpdate\":\"usage_update\",\"used\":70281,\"size\":1000000,"
            + "\"cost\":{\"amount\":0,\"currency\":\"USD\"}}}"));

        assertNotNull(update);
        assertEquals(70281L, update.update().used());
        assertEquals(1000000L, update.update().size());
        assertEquals(0.0, update.update().cost().get("amount").asDouble());
        assertEquals("USD", update.update().cost().get("currency").asText());
    }

    @Test
    void usageUpdateWithoutCostLeavesCostNodeAbsent() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decode(json(
            "{\"sessionId\":\"s1\",\"update\":{\"sessionUpdate\":\"usage_update\",\"used\":5,\"size\":100}}"));

        assertNotNull(update);
        assertNull(update.update().cost());
    }

    @Test
    void customMessageUsageDecodesWithUsageObject() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decodeCustom(json(
            "{\"sessionId\":\"s1\",\"update\":{\"sessionUpdate\":\"message_usage\","
            + "\"messageId\":\"m1\",\"usage\":{\"inputTokens\":10,\"outputTokens\":20,"
            + "\"totalTokens\":30,\"cacheReadTokens\":5,\"cost\":0.002,\"elapsedMs\":900,"
            + "\"timeToFirstTokenMs\":120,\"isCompaction\":false}}}"));

        assertNotNull(update);
        assertEquals(MessageType.message_usage, update.update().type());
        assertEquals("m1", update.update().messageId());
        assertEquals(30L, update.usage().get("totalTokens").asLong());
        assertEquals(5L, update.usage().get("cacheReadTokens").asLong());
    }

    @Test
    void customStatusMessageCarriesAnObjectStatus() throws IOException {
        SessionUpdate update = SessionUpdateDecoder.decodeCustom(json(
            "{\"sessionId\":\"s1\",\"update\":{\"sessionUpdate\":\"status_message\","
            + "\"status\":{\"type\":\"progress\",\"message\":\"Compacting conversation\"}}}"));

        assertNotNull(update);
        assertEquals(MessageType.status_message, update.update().type());
        assertEquals("Compacting conversation", update.statusMessage().get("message").asText());
        assertNull(update.status(), "an object status is never a tool-call status");
    }

    @Test
    void customUsageUpdateIsDroppedToAvoidDoubleCounting() throws IOException {
        assertNull(SessionUpdateDecoder.decodeCustom(json(
            "{\"sessionId\":\"s1\",\"update\":{\"sessionUpdate\":\"usage_update\","
            + "\"used\":1,\"contextLimit\":10}}")));
    }

    @Test
    void unknownCustomVariantIsDropped() throws IOException {
        assertNull(SessionUpdateDecoder.decodeCustom(json(
            "{\"sessionId\":\"s1\",\"update\":{\"sessionUpdate\":\"live_voice_interaction_ended\"}}")));
        assertNull(SessionUpdateDecoder.decodeCustom(json("{\"sessionId\":\"s1\"}")));
    }
}
