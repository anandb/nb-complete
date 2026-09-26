package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the harness catalog data: completeness, uniqueness, lookups, and capabilities. */
class HarnessCatalogTest {

    @Test
    void allTenHarnessesPresent() {
        assertEquals(10, HarnessCatalog.ALL.size());
        Set<String> ids = new HashSet<>();
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            assertTrue(ids.add(h.id()), "duplicate id: " + h.id());
        }
        assertTrue(ids.containsAll(Set.of("opencode", "goose", "pi", "cursor", "claude", "hermes", "gemini", "omp", "openclaw", "devin")));
    }

    @Test
    void onboardingOrderIncludesAllHarnesses() {
        Set<String> allIds = new HashSet<>(HarnessCatalog.ALL.stream()
                .map(HarnessCatalog.Harness::id).toList());
        assertEquals(Set.of("omp", "opencode", "openclaw", "pi", "goose", "cursor", "claude", "hermes", "gemini", "devin"),
                allIds);
    }

    @Test
    void everyHarnessHasBinaryNamesAndIcon() {
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            assertFalse(h.binaryNames().isEmpty(), h.id() + ": no binary names");
            assertNotNull(h.iconBase(), h.id() + ": no icon");
        }
    }

    @Test
    void launchBinariesAreAcpEntryPoints() {
        assertEquals(List.of("pi-acp"), HarnessCatalog.PI.binaryNames());
        assertTrue(HarnessCatalog.CLAUDE.binaryNames().contains("claude-agent-acp"));
    }

    @Test
    void lookupsByIdAndBinaryName() {
        assertEquals(HarnessCatalog.OPENCODE, HarnessCatalog.byId("opencode"));
        assertSame(HarnessCatalog.UNKNOWN, HarnessCatalog.byId("nope"));
        assertEquals(HarnessCatalog.CLAUDE, HarnessCatalog.byBinaryName("claude-agent-acp"));
        assertEquals(HarnessCatalog.HERMES, HarnessCatalog.byBinaryName("hermes"));
        assertEquals(HarnessCatalog.GOOSE, HarnessCatalog.byBinaryName("goose"));
        assertEquals(HarnessCatalog.CURSOR, HarnessCatalog.byBinaryName("agent"));
        assertEquals(HarnessCatalog.GEMINI, HarnessCatalog.byBinaryName("gemini"));
        assertEquals(HarnessCatalog.OMP, HarnessCatalog.byBinaryName("omp"));
        assertSame(HarnessCatalog.UNKNOWN, HarnessCatalog.byBinaryName("unknown-bin"));
    }

    @Test
    void caseInsensitiveBinaryMatch() {
        assertEquals(HarnessCatalog.GOOSE, HarnessCatalog.byBinaryName("GOOSE"));
    }

    @Test
    void binaryLookupIgnoresWindowsExecutableExtension() {
        assertEquals(HarnessCatalog.OMP, HarnessCatalog.byBinaryName("omp.exe"));
        assertEquals(HarnessCatalog.PI, HarnessCatalog.byBinaryName("pi-acp.exe"));
        assertEquals(HarnessCatalog.HERMES, HarnessCatalog.byBinaryName("hermes.exe"));
        assertEquals(HarnessCatalog.GOOSE, HarnessCatalog.byBinaryName("GOOSE.EXE"));
        assertEquals(HarnessCatalog.CURSOR, HarnessCatalog.byBinaryName("cursor-agent.cmd"));
        assertEquals(HarnessCatalog.CURSOR, HarnessCatalog.byBinaryName("agent.exe"));
        // Unknown names stay unknown with or without an extension.
        assertSame(HarnessCatalog.UNKNOWN, HarnessCatalog.byBinaryName("unknown-bin.exe"));
        assertSame(HarnessCatalog.UNKNOWN, HarnessCatalog.byBinaryName(""));
        assertSame(HarnessCatalog.UNKNOWN, HarnessCatalog.byBinaryName(null));
    }

    @Test
    void stripBinaryExtensionNormalizesBareAndOrnamentedNames() {
        assertEquals("goose", HarnessCatalog.stripBinaryExtension("GOOSE.EXE"));
        assertEquals("goose", HarnessCatalog.stripBinaryExtension("GOOSE.CMD"));
        assertEquals("pi-acp", HarnessCatalog.stripBinaryExtension("pi-acp"));
        assertEquals("claude-agent-acp", HarnessCatalog.stripBinaryExtension("claude-agent-acp.exe"));
        assertNull(HarnessCatalog.stripBinaryExtension("  "));
        assertNull(HarnessCatalog.stripBinaryExtension(null));
    }

    @Test
    void catalogBinaryNamesAreUnique() {
        List<String> all = HarnessCatalog.ALL.stream()
                .flatMap(h -> h.binaryNames().stream()).toList();
        Set<String> unique = new HashSet<>(all);
        assertEquals(all.size(), unique.size());
    }

    @Test
    void unknownHarnessHasGenericCapabilities() {
        HarnessCatalog.Harness u = HarnessCatalog.UNKNOWN;
        assertEquals("Agent", u.displayName());
        assertTrue(u.sendsMcpServerConfig());
        assertTrue(u.supportsMcpServer());
        assertFalse(u.supportsMessageIds());
    }

    @Test
    void geminiLacksSessionList() {
        assertFalse(HarnessCatalog.GEMINI.supportsSessionList());
        assertFalse(HarnessCatalog.GEMINI.requiresMessageQueue());
        assertFalse(HarnessCatalog.GEMINI.supportsTokenStats());
    }

    @Test
    void openclawLacksAgentList() {
        assertFalse(HarnessCatalog.OPENCLAW.supportsAgentList());
    }

    @Test
    void allHarnessesExceptOpenclawSupportAgentList() {
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            if ("openclaw".equals(h.id())) {
                continue;
            }
            assertTrue(h.supportsAgentList(), h.id() + " should support agent list");
        }
        assertTrue(HarnessCatalog.UNKNOWN.supportsAgentList());
    }

    @Test
    void ompSupportsAllExceptTokenStats() {
        assertFalse(HarnessCatalog.OMP.requiresMessageQueue());
        assertTrue(HarnessCatalog.OMP.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.OMP.injectsEditorContext());
        assertFalse(HarnessCatalog.OMP.supportsTokenStats());
        assertTrue(HarnessCatalog.OMP.supportsMessageIds());
        assertTrue(HarnessCatalog.OMP.supportsMcpServer());
        assertTrue(HarnessCatalog.OMP.supportsSessionSetMode());
        assertTrue(HarnessCatalog.OMP.supportsSessionList());
        assertEquals("powershell -c \"irm https://omp.sh/install.ps1 | iex\"", HarnessCatalog.OMP.installWindows());
        assertEquals("curl -fsSL https://omp.sh/install | sh", HarnessCatalog.OMP.installLinux());
        assertEquals("https://omp.sh/docs/acp", HarnessCatalog.OMP.docsUrl());
    }

    @Test
    void harnessIconNameResolvesKnownBinaries() {
        assertEquals("goose.svg", HarnessCatalog.harnessIconName("goose"));
        assertEquals("gemini.svg", HarnessCatalog.harnessIconName("gemini"));
        assertEquals("omp.svg", HarnessCatalog.harnessIconName("omp"));
        assertEquals("agent.svg", HarnessCatalog.harnessIconName(null));
        assertEquals("agent.svg", HarnessCatalog.harnessIconName("unknown-bin"));
    }
}
