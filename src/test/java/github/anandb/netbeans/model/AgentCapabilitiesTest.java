package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests agent-name normalization and capability derivation. */
class AgentCapabilitiesTest {

    @Test
    void caseVariantsMapToSameCapabilities() {
        assertEquals(AgentCapabilities.forName("goose"), AgentCapabilities.forName("GOOSE"));
        assertEquals(AgentCapabilities.forName("goose"), AgentCapabilities.forName(" Goose "));
        assertEquals(AgentCapabilities.forName("pi-acp"), AgentCapabilities.forName("PI-ACP"));
    }

    @Test
    void knownAgentsDeriveExpectedFlags() {
        AgentCapabilities goose = AgentCapabilities.forName("goose");
        assertTrue(goose.supportsMessageQueue());
        assertFalse(goose.supportsTokenStats());
        assertEquals("Goose", goose.displayName());

        AgentCapabilities pi = AgentCapabilities.forName("pi-acp");
        assertEquals(pi, AgentCapabilities.forName("pi"));
        assertEquals(pi, AgentCapabilities.forName("pi-agent"));
        assertTrue(pi.supportsMessageQueue());
        assertFalse(pi.sendsMcpServerConfig());
        assertEquals("Pi", pi.displayName());

        AgentCapabilities cursor = AgentCapabilities.forName("cursor");
        assertEquals(cursor, AgentCapabilities.forName("cursor-agent"));
        assertEquals(cursor, AgentCapabilities.forName("cursor-agent-acp"));
        assertEquals(cursor, AgentCapabilities.forName("agent"));
        assertTrue(cursor.sendsMcpServerConfig());
        assertTrue(cursor.injectsEditorContext());
        assertFalse(cursor.supportsMessageIds());
        assertFalse(cursor.supportsMessageQueue());
        assertEquals("Cursor", cursor.displayName());
        assertEquals("Claude", AgentCapabilities.forName("claude").displayName());
        assertEquals("OpenCode", AgentCapabilities.DEFAULT.displayName());
    }

    @Test
    void nullAndUnknownFallBackToDefault() {
        AgentCapabilities def = AgentCapabilities.DEFAULT;
        assertEquals(def, AgentCapabilities.forName(null));
        assertEquals(def, AgentCapabilities.forName("mystery-agent"));
    }

    @Test
    void sessionSetModeCapabilityDerivedPerAgent() {
        // Only agents whose name starts with "claude" support ACP session/set_mode,
        // since the exact agent name (claude, claude-acp, claude-code, …) is unknown.
        assertTrue(AgentCapabilities.forName("claude").supportsSessionSetMode());
        assertTrue(AgentCapabilities.forName("claude-acp").supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("pi-acp").supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("pi").supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("pi-agent").supportsSessionSetMode());

        // All other agents (pi-acp, opencode/unknown, goose, cursor) keep the
        // set_config_option("mode", …) path.
        assertFalse(AgentCapabilities.forName("pi-acp").supportsSessionSetMode());
        assertFalse(AgentCapabilities.DEFAULT.supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("goose").supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("cursor").supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("agent").supportsSessionSetMode());
    }
}