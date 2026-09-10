package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the harness catalog data: completeness, uniqueness, and lookups. */
class HarnessCatalogTest {

    @Test
    void allSixHarnessesPresent() {
        assertEquals(6, HarnessCatalog.ALL.size());
        Set<String> ids = new HashSet<>();
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            assertTrue(ids.add(h.id()), "duplicate id: " + h.id());
        }
        assertTrue(ids.containsAll(Set.of("opencode", "goose", "pi", "cursor", "claude", "hermes")));
    }

    @Test
    void onboardingOrderIsOpencodePiGooseCursorClaudeHermes() {
        assertEquals(List.of("opencode", "pi", "goose", "cursor", "claude", "hermes"),
                HarnessCatalog.ALL.stream().map(HarnessCatalog.Harness::id).toList());
    }

    @Test
    void everyHarnessHasInstallCommandsAndDocs() {
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            assertFalse(h.installWindows().isBlank(), h.id() + ": missing Windows install command");
            assertFalse(h.installMac().isBlank(), h.id() + ": missing macOS install command");
            assertFalse(h.installLinux().isBlank(), h.id() + ": missing Linux install command");
            assertFalse(h.docsUrl().isBlank(), h.id() + ": missing docs URL");
            assertFalse(h.binaryNames().isEmpty(), h.id() + ": no binary names");
            assertNotNull(h.iconBase(), h.id() + ": no icon");
        }
    }

    @Test
    void launchBinariesAreAcpEntryPoints() {
        // Only the pi-acp entry point is probed — bare pi / pi-agent are not
        // launchable ACP servers.
        assertEquals(List.of("pi-acp"), HarnessCatalog.PI.binaryNames());
        // Claude's ACP entry point is the claude-agent-acp adapter.
        assertTrue(HarnessCatalog.CLAUDE.binaryNames().contains("claude-agent-acp"));
    }

    @Test
    void lookupsByIdAndBinaryName() {
        assertEquals(HarnessCatalog.OPENCODE, HarnessCatalog.byId("opencode"));
        assertNull(HarnessCatalog.byId("nope"));
        assertEquals(HarnessCatalog.CLAUDE, HarnessCatalog.byBinaryName("claude-agent-acp"));
        assertEquals(HarnessCatalog.HERMES, HarnessCatalog.byBinaryName("hermes"));
        assertEquals(HarnessCatalog.GOOSE, HarnessCatalog.byBinaryName("goose"));
        assertEquals(HarnessCatalog.CURSOR, HarnessCatalog.byBinaryName("agent"));
        assertNull(HarnessCatalog.byBinaryName("unknown-bin"));
    }

    @Test
    void caseInsensitiveBinaryMatch() {
        assertTrue(HarnessCatalog.byBinaryName("GOOSE") instanceof HarnessCatalog.Harness);
        assertEquals(HarnessCatalog.GOOSE, HarnessCatalog.byBinaryName("GOOSE"));
    }

    @Test
    void catalogBinaryNamesAreUnique() {
        List<String> all = HarnessCatalog.ALL.stream()
                .flatMap(h -> h.binaryNames().stream()).toList();
        Set<String> unique = new HashSet<>(all);
        assertEquals(all.size(), unique.size());
    }
}
