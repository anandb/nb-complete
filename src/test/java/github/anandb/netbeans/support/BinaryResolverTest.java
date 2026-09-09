package github.anandb.netbeans.support;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.HarnessCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("pi-acp"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("goose"));
        assertTrue(BinaryResolver.KNOWN_HARNESSES.contains("agent"));
        // Detection is by ACP entry point only — bare pi / pi-agent are not probed.
        assertFalse(BinaryResolver.KNOWN_HARNESSES.contains("pi"));
        assertFalse(BinaryResolver.KNOWN_HARNESSES.contains("pi-agent"));
        assertFalse(BinaryResolver.PI_HARNESS.contains("agent"));
    }

    @Test
    void knownHarnessesDerivedFromCatalog() {
        // Every catalog launch binary must be part of the detection set...
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            for (String name : harness.binaryNames()) {
                assertTrue(BinaryResolver.KNOWN_HARNESSES.contains(name),
                        harness.id() + "/" + name + " missing from KNOWN_HARNESSES");
            }
        }
        // ...and the set must not contain duplicates or non-catalog binaries.
        long catalogCount = HarnessCatalog.ALL.stream()
                .flatMap(h -> h.binaryNames().stream()).distinct().count();
        assertEquals(catalogCount, BinaryResolver.KNOWN_HARNESSES.size());
    }

    @Test
    void findAllKnownOnPathReturnsCatalogEntries() {
        // Exact detection results depend on the machine, but every returned
        // entry must reference a known catalog harness and a non-blank path.
        for (BinaryResolver.FoundBinary fb : BinaryResolver.findAllKnownOnPath()) {
            assertNotNull(HarnessCatalog.byId(fb.harnessId()), fb.harnessId());
            assertNotNull(HarnessCatalog.byBinaryName(
                    fb.path().substring(fb.path().replace('\\', '/').lastIndexOf('/') + 1)
                            .toLowerCase(java.util.Locale.ROOT)));
            assertFalse(fb.path().isBlank());
        }
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
