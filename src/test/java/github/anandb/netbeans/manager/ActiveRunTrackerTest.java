package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActiveRunTrackerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void clearRuns() {
        ActiveRunTracker.clear();
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    void setAndClearTrackRunIds() {
        ActiveRunTracker.set("s1", "run-1");
        assertEquals("run-1", ActiveRunTracker.get("s1"));

        ActiveRunTracker.set("s1", "run-2");
        assertEquals("run-2", ActiveRunTracker.get("s1"));

        ActiveRunTracker.clear("s1");
        assertNull(ActiveRunTracker.get("s1"));

        ActiveRunTracker.set("s2", "run-3");
        ActiveRunTracker.clear();
        assertNull(ActiveRunTracker.get("s2"));
    }

    @Test
    void invalidSetArgumentsAreIgnored() {
        ActiveRunTracker.set(null, "run");
        ActiveRunTracker.set("s1", null);
        ActiveRunTracker.set("s1", "  ");

        assertNull(ActiveRunTracker.get("s1"));
        assertNull(ActiveRunTracker.get(null));
    }

    @Test
    void updateIgnoresUpdatesWithoutARunId() {
        ActiveRunTracker.set("s1", "run-1");

        ActiveRunTracker.update(null, node("{}"));
        ActiveRunTracker.update("s1", null);
        ActiveRunTracker.update("s1", node("\"text\""));
        ActiveRunTracker.update("s1", node("{}"));
        ActiveRunTracker.update("s1", node("{\"_meta\":{}}"));
        ActiveRunTracker.update("s1", node("{\"_meta\":{\"goose\":{}}}"));

        assertEquals("run-1", ActiveRunTracker.get("s1"));
    }

    @Test
    void textualRunIdIsTrimmedAndStored() {
        ActiveRunTracker.update("s1",
                node("{\"_meta\":{\"goose\":{\"activeRunId\":\"  run-7  \"}}}"));

        assertEquals("run-7", ActiveRunTracker.get("s1"));
    }

    @Test
    void nullNonTextualAndBlankRunIdsEndTheRun() {
        ActiveRunTracker.set("s1", "run-1");
        ActiveRunTracker.update("s1", node("{\"_meta\":{\"goose\":{\"activeRunId\":null}}}"));
        assertNull(ActiveRunTracker.get("s1"));

        ActiveRunTracker.set("s1", "run-2");
        ActiveRunTracker.update("s1", node("{\"_meta\":{\"goose\":{\"activeRunId\":42}}}"));
        assertNull(ActiveRunTracker.get("s1"));

        ActiveRunTracker.set("s1", "run-3");
        ActiveRunTracker.update("s1", node("{\"_meta\":{\"goose\":{\"activeRunId\":\"   \"}}}"));
        assertNull(ActiveRunTracker.get("s1"));
    }
}
