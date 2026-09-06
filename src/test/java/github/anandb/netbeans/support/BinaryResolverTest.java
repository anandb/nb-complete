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
    void knownHarnessesIncludeCursorAgentAndOthers() {
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("opencode"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("pi-agent"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("pi-acp"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("goose"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("agent"));
        assertFalse(BinaryResolver.PI_HARNESS.contains("agent"));
    }

    @Test
    void nativeHarnessNamesMatchOs() {
        String[] names = BinaryResolver.nativeHarnessNames();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean sawAgent = false;
        for (String name : names) {
            if (isWindows) {
                assertTrue(name.endsWith(".exe") || name.endsWith(".cmd"), name);
            } else {
                assertFalse(name.endsWith(".exe"), name);
                assertFalse(name.endsWith(".cmd"), name);
            }
            if (name.startsWith("agent") || "cursor-agent.cmd".equals(name)) {
                sawAgent = true;
            }
        }
        assertTrue(sawAgent);
    }

    @Test
    void missingPathFallsBackToOpencode() {
        assertEquals("opencode", BinaryResolver.binaryNameFromPath(null));
        assertEquals("opencode", BinaryResolver.binaryNameFromPath(""));
        assertEquals("opencode", BinaryResolver.binaryNameFromPath("  "));
    }

    @Test
    void windowsNativeNamesIncludeCursorAgentCmd() {
        String[] win = BinaryResolver.nativeHarnessNames(true);
        boolean sawCmd = false;
        boolean sawAgentExe = false;
        for (String name : win) {
            if ("cursor-agent.cmd".equals(name)) {
                sawCmd = true;
            }
            if ("agent.exe".equals(name)) {
                sawAgentExe = true;
            }
        }
        assertTrue(sawCmd);
        assertTrue(sawAgentExe);

        for (String name : BinaryResolver.nativeHarnessNames(false)) {
            assertFalse(name.endsWith(".cmd"), name);
        }
    }

    @Test
    void cmdSuffixIsStrippedFromBinaryName() {
        assertEquals("cursor-agent",
                BinaryResolver.binaryNameFromPath("C:\\Users\\me\\AppData\\Local\\cursor-agent.cmd"));
        assertFalse(BinaryResolver.PI_HARNESS.contains("cursor-agent"));
    }
}
