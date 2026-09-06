package github.anandb.netbeans.manager.strategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubAgentTitleResolverTest {

    @Test
    void resolveReturnsNullWhenSessionControlUnavailable() {
        // In headless test env, Lookup.getDefault().lookup(SessionControl.class)
        // returns null, so resolve should return the original title unchanged.
        String result = SubAgentTitleResolver.resolve("MyTool", "session1", "Sub-Agent", " - ReadFile");
        // When sessionControl is null, the method returns title directly
        assertEquals("MyTool", result);
    }

    @Test
    void resolveReturnsNullTitleUnchanged() {
        String result = SubAgentTitleResolver.resolve(null, "session1", "Sub-Agent", " - Thinking");
        assertNull(result);
    }

    @Test
    void resolveWithNullSessionId() {
        // sessionId is null → early return with original title
        String result = SubAgentTitleResolver.resolve("Tool", null, "Sub-Agent", " - ReadFile");
        assertEquals("Tool", result);
    }
}
