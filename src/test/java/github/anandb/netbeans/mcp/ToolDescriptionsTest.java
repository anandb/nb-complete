package github.anandb.netbeans.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDescriptionsTest {

    @Test
    void knownDescriptionIsLoadedAndCached() {
        String first = ToolDescriptions.get("add_task");
        String second = ToolDescriptions.get("add_task");

        assertNotNull(first);
        assertTrue(first.contains("task"));
        assertSame(first, second, "the loaded description must be cached");
    }

    @Test
    void missingDescriptionFailsWithItsResourcePath() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ToolDescriptions.get("missing_tool_description"));

        assertTrue(ex.getMessage().contains("descriptions/missing_tool_description.md"));
    }
}
