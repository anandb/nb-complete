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

        AgentCapabilities pi = AgentCapabilities.forName("pi-acp");
        assertTrue(pi.supportsMessageQueue());
        assertFalse(pi.sendsMcpServerConfig());
    }

    @Test
    void nullAndUnknownFallBackToDefault() {
        AgentCapabilities def = AgentCapabilities.DEFAULT;
        assertEquals(def, AgentCapabilities.forName(null));
        assertEquals(def, AgentCapabilities.forName("mystery-agent"));
    }
}
