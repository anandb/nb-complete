package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BinaryResolver} binary-name resolution used by the MCP
 * token-auth policy: PI-family harness binaries (pi, pi-acp, pi-agent) skip
 * token auth; everything else (opencode, goose, ...) gets token-protected
 * URLs. Windows executables carry a {@code .exe} suffix that must be stripped
 * before matching.
 */
class BinaryResolverTest {

    @Test
    void piHarnessBinariesMatchWithAndWithoutExe() {
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/pi")));
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("C:\\tools\\pi-acp.exe")));
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/local/bin/pi-agent")));
        assertTrue(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("C:\\bin\\PI-AGENT.EXE")));
    }

    @Test
    void nonPiBinariesDoNotMatch() {
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/opencode")));
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("C:\\tools\\opencode.exe")));
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/goose")));
        // Substring look-alikes must not match — exact-name policy only.
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/piano")));
        assertFalse(BinaryResolver.PI_HARNESS.contains(BinaryResolver.binaryNameFromPath("/usr/bin/opi")));
    }

    @Test
    void missingPathFallsBackToOpencode() {
        assertEquals("opencode", BinaryResolver.binaryNameFromPath(null));
        assertEquals("opencode", BinaryResolver.binaryNameFromPath(""));
        assertEquals("opencode", BinaryResolver.binaryNameFromPath("  "));
    }
}
