package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessedMessageTest {

    private static ProcessedMessage tool(String toolTitle, String status) {
        return new ProcessedMessage(MessageType.tool_call, "", "mid", null,
                toolTitle, "", false, status);
    }

    @Test
    void nullToolTitleDoesNotThrow() {
        // Regression: toolTitle defaults to null; previously NPEd in streaming
        // when a tool_call arrived before the title was set and status was blank.
        ProcessedMessage msg = tool(null, null);
        assertFalse(msg.isIgnorable());
    }

    @Test
    void mcpToolTitleIsIgnorableRegardlessOfStatus() {
        assertTrue(tool("mcp filesystem", null).isIgnorable());
        assertTrue(tool("mcp filesystem", "completed").isIgnorable());
    }

    @Test
    void pendingAndInProgressStatusesAreIgnorable() {
        assertTrue(tool("run_command", "pending").isIgnorable());
        assertTrue(tool("run_command", "in_progress").isIgnorable());
    }

    @Test
    void completedNonMcpToolIsNotIgnorable() {
        assertFalse(tool("run_command", "completed").isIgnorable());
        assertFalse(tool("run_command", "").isIgnorable());
    }

    @Test
    void blankStatusNonMcpToolIsNotIgnorable() {
        assertFalse(tool("edit_file", "   ").isIgnorable());
    }
}
