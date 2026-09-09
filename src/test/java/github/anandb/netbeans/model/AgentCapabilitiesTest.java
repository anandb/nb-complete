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
        assertEquals("OpenCode", AgentCapabilities.forName("opencode").displayName());
        assertEquals("Agent", AgentCapabilities.DEFAULT.displayName());
    }

    @Test
    void nullAndUnknownFallBackToDefault() {
        AgentCapabilities def = AgentCapabilities.DEFAULT;
        assertEquals(def, AgentCapabilities.forName(null));
        assertEquals(def, AgentCapabilities.forName("mystery-agent"));
    }

    @Test
    void opencodeHasOwnEntryWithDefaultFlags() {
        // OpenCode keeps the exact capability flags the unknown-default used to carry.
        AgentCapabilities opencode = AgentCapabilities.forName("opencode");
        assertEquals(opencode, AgentCapabilities.forName("OpenCode")); // case-insensitive
        assertEquals("OpenCode", opencode.displayName());
        assertTrue(opencode.sendsMcpServerConfig());
        assertTrue(opencode.injectsEditorContext());
        assertTrue(opencode.supportsTokenStats());
        assertTrue(opencode.supportsMessageIds());
        assertTrue(opencode.supportsMcpServer());
        assertFalse(opencode.supportsSessionSetMode());
        // Same flags as the generic default — only the display name differs.
        AgentCapabilities def = AgentCapabilities.DEFAULT;
        assertEquals(def.sendsMcpServerConfig(), opencode.sendsMcpServerConfig());
        assertEquals(def.injectsEditorContext(), opencode.injectsEditorContext());
        assertEquals(def.supportsTokenStats(), opencode.supportsTokenStats());
        assertEquals(def.supportsMessageIds(), opencode.supportsMessageIds());
        assertEquals(def.supportsMcpServer(), opencode.supportsMcpServer());
        assertEquals(def.supportsSessionSetMode(), opencode.supportsSessionSetMode());
        assertEquals(def.supportsMessageQueue(), opencode.supportsMessageQueue());
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

        // Unknown agents and pi/goose/cursor keep the
        // set_config_option("mode", …) path; opencode now has its own case
        // with the same (non-set_mode) behavior.
        assertFalse(AgentCapabilities.forName("pi-acp").supportsSessionSetMode());
        assertFalse(AgentCapabilities.DEFAULT.supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("goose").supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("cursor").supportsSessionSetMode());
        assertFalse(AgentCapabilities.forName("agent").supportsSessionSetMode());
    }
}