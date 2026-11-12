package github.anandb.netbeans.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.PromptUsageRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PromptUsageRowsTest {

    private static final JsonMapper MAPPER = MapperSupplier.get();

    private static final Attribution ATTRIBUTION =
            new Attribution("s1", "grok", "grok-4.7", "/proj");

    @Test
    void totalFallsBackToInputPlusOutput() throws Exception {
        PromptUsageRow row = PromptUsageRows.from(ATTRIBUTION,
                MAPPER.readTree("{\"inputTokens\":10,\"outputTokens\":5}"));

        assertEquals(10, row.inputTokens());
        assertEquals(5, row.outputTokens());
        assertEquals(15, row.totalTokens());
    }

    @Test
    void explicitTotalAndThoughtTokensWin() throws Exception {
        PromptUsageRow row = PromptUsageRows.from(ATTRIBUTION, MAPPER.readTree("""
                {"inputTokens":10,"outputTokens":5,"totalTokens":99,
                 "thoughtTokens":7,"reasoningTokens":8,"cachedReadTokens":3}"""));

        assertEquals(99, row.totalTokens());
        assertEquals(7L, row.thoughtTokens(), "thoughtTokens wins over reasoningTokens");
        assertEquals(3L, row.cachedReadTokens());
    }

    @Test
    void reasoningTokensAreTheFallback() throws Exception {
        PromptUsageRow row = PromptUsageRows.from(ATTRIBUTION,
                MAPPER.readTree("{\"reasoningTokens\":8}"));

        assertEquals(8L, row.thoughtTokens());
        assertNull(row.cachedReadTokens());
    }

    @Test
    void nonNumericFieldsStayNull() throws Exception {
        JsonNode usage = MAPPER.readTree(
                "{\"thoughtTokens\":\"7\",\"cachedReadTokens\":\"3\"}");

        PromptUsageRow row = PromptUsageRows.from(ATTRIBUTION, usage);

        assertNull(row.thoughtTokens(), "a text value is not a token count");
        assertNull(row.cachedReadTokens());
    }
}
