package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
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
}
