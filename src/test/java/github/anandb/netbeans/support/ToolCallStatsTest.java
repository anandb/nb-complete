package github.anandb.netbeans.support;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallStatsTest {

    @BeforeEach
    void clearCounter() {
        // MCP package tests exercise McpTools.callTool in this same forked JVM and
        // so record into the static counter before this class runs.
        ToolCallStats.drain();
    }

    @Test
    void recordAccumulatesPerToolAndDrainResets() {
        ToolCallStats.record("read_file");
        ToolCallStats.record("read_file");
        ToolCallStats.record("search_project");

        Map<String, Long> drained = ToolCallStats.drain();

        assertEquals(2L, drained.get("read_file"));
        assertEquals(1L, drained.get("search_project"));
        assertTrue(ToolCallStats.drain().isEmpty(), "the second drain sees a fresh turn");
    }

    @Test
    void recordIgnoresBlankNames() {
        ToolCallStats.record(null);
        ToolCallStats.record("  ");

        assertTrue(ToolCallStats.drain().isEmpty());
    }
}
