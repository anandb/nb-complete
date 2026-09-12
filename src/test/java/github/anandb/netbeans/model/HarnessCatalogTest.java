package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the harness catalog data: completeness, uniqueness, lookups, and capabilities. */
class HarnessCatalogTest {

    @Test
    void allEightHarnessesPresent() {
        assertEquals(8, HarnessCatalog.ALL.size());
        Set<String> ids = new HashSet<>();
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            assertTrue(ids.add(h.id()), "duplicate id: " + h.id());
        }
        assertTrue(ids.containsAll(Set.of("opencode", "goose", "pi", "cursor", "claude", "hermes", "gemini", "omp")));
    }

    @Test
    void onboardingOrderIncludesAllHarnesses() {
        assertEquals(List.of("opencode", "pi", "goose", "cursor", "claude", "hermes", "gemini", "omp"),
                HarnessCatalog.ALL.stream().map(HarnessCatalog.Harness::id).toList());
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
        assertTrue(u.supportsMessageIds());
    }

    @Test
    void geminiLacksSessionList() {
        assertFalse(HarnessCatalog.GEMINI.supportsSessionList());
        assertFalse(HarnessCatalog.GEMINI.requiresMessageQueue());
        assertFalse(HarnessCatalog.GEMINI.supportsTokenStats());
    }

    @Test
    void ompSupportsAllExceptTokenStats() {
        assertTrue(HarnessCatalog.OMP.requiresMessageQueue());
        assertTrue(HarnessCatalog.OMP.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.OMP.injectsEditorContext());
        assertFalse(HarnessCatalog.OMP.supportsTokenStats());
        assertTrue(HarnessCatalog.OMP.supportsMessageIds());
        assertTrue(HarnessCatalog.OMP.supportsMcpServer());
        assertTrue(HarnessCatalog.OMP.supportsSessionSetMode());
        assertTrue(HarnessCatalog.OMP.supportsSessionList());
        assertEquals("irm https://omp.sh/install.ps1 | iex", HarnessCatalog.OMP.installWindows());
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
