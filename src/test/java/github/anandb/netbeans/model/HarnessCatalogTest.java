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
    void allHarnessesPresent() {
        assertEquals(14, HarnessCatalog.ALL.size());
        Set<String> ids = new HashSet<>();
        for (HarnessCatalog.Harness h : HarnessCatalog.ALL) {
            assertTrue(ids.add(h.id()), "duplicate id: " + h.id());
        }
        assertTrue(ids.containsAll(Set.of("opencode", "codex", "goose", "pi", "cursor", "claude", "hermes",
                "gemini", "omp", "openclaw", "devin", "copilot", "kilo", "cline")));
    }

    @Test
    void onboardingOrderIncludesAllHarnesses() {
        Set<String> allIds = new HashSet<>(HarnessCatalog.ALL.stream()
                .map(HarnessCatalog.Harness::id).toList());
        assertEquals(Set.of("omp", "opencode", "codex", "openclaw", "pi", "goose", "cursor", "copilot",
                "claude", "hermes", "gemini", "devin", "kilo", "cline"),
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
        assertEquals(List.of("codex-acp"), HarnessCatalog.CODEX.binaryNames());
    }

    @Test
    void lookupsByIdAndBinaryName() {
        assertEquals(HarnessCatalog.OPENCODE, HarnessCatalog.byId("opencode"));
        assertSame(HarnessCatalog.UNKNOWN, HarnessCatalog.byId("nope"));
        assertEquals(HarnessCatalog.CLAUDE, HarnessCatalog.byBinaryName("claude-agent-acp"));
        assertEquals(HarnessCatalog.CODEX, HarnessCatalog.byBinaryName("codex-acp"));
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
    }

    @Test
    void opencodeV2QueuesMessages() {
        assertSame(HarnessCatalog.OPENCODE_V2,
                HarnessCatalog.forAgentVersion(HarnessCatalog.OPENCODE, "2.0.20"));
        assertTrue(HarnessCatalog.OPENCODE_V2.requiresMessageQueue());
    }

    @Test
    void opencodeV1SteersMessages() {
        assertFalse(HarnessCatalog.OPENCODE.requiresMessageQueue());
        assertSame(HarnessCatalog.OPENCODE,
                HarnessCatalog.forAgentVersion(HarnessCatalog.OPENCODE, "1.9.3"));
    }

    @Test
    void unknownVersionKeepsCatalogCapabilities() {
        assertSame(HarnessCatalog.OPENCODE, HarnessCatalog.forAgentVersion(HarnessCatalog.OPENCODE, null));
        assertSame(HarnessCatalog.OPENCODE, HarnessCatalog.forAgentVersion(HarnessCatalog.OPENCODE, "  "));
        assertSame(HarnessCatalog.OPENCODE, HarnessCatalog.forAgentVersion(HarnessCatalog.OPENCODE, "v2.0"));
        assertSame(HarnessCatalog.UNKNOWN, HarnessCatalog.forAgentVersion(HarnessCatalog.UNKNOWN, "2.0.0"));
        assertNull(HarnessCatalog.forAgentVersion(null, "2.0.0"));
    }

    @Test
    void versionOverrideLeavesSteeringHarnessesUntouched() {
        assertSame(HarnessCatalog.GOOSE, HarnessCatalog.forAgentVersion(HarnessCatalog.GOOSE, "2.0.0"));
        assertSame(HarnessCatalog.CLAUDE, HarnessCatalog.forAgentVersion(HarnessCatalog.CLAUDE, "9.9.9"));
        assertSame(HarnessCatalog.CURSOR, HarnessCatalog.forAgentVersion(HarnessCatalog.CURSOR, "2.0.0"));
    }

    @Test
    void opencodeV2VariantDiffersOnlyInMessageQueue() {
        HarnessCatalog.Harness v1 = HarnessCatalog.OPENCODE;
        HarnessCatalog.Harness v2 = HarnessCatalog.OPENCODE_V2;
        assertEquals(v1.withRequiresMessageQueue(true), v2);
        // Identical identity and launch metadata: id-keyed caches and stored
        // preferences must not see a distinct harness just because it is v2.
        assertEquals(v1.id(), v2.id());
        assertEquals(v1.displayName(), v2.displayName());
        assertEquals(v1.binaryNames(), v2.binaryNames());
        // Not offered during onboarding: ALL keeps only the v1 base entry.
        assertFalse(HarnessCatalog.ALL.contains(v2));
        assertSame(v1, HarnessCatalog.byId("opencode"));
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
        assertTrue(HarnessCatalog.OMP.supportsMessageIds());
        assertTrue(HarnessCatalog.OMP.supportsMcpServer());
        assertTrue(HarnessCatalog.OMP.supportsSessionSetMode());
        assertTrue(HarnessCatalog.OMP.supportsSessionList());
        assertEquals("powershell -c \"irm https://omp.sh/install.ps1 | iex\"", HarnessCatalog.OMP.installWindows());
        assertEquals("curl -fsSL https://omp.sh/install | sh", HarnessCatalog.OMP.installLinux());
        assertEquals("https://omp.sh/docs/acp", HarnessCatalog.OMP.docsUrl());
    }

    @Test
    void copilotCapabilitiesMatchHandshake() {
        // Metadata from the live Copilot ACP handshake: agentInfo "Copilot",
        // sessionCapabilities {close,list}, mcpCapabilities http+sse,
        // session/new returns models + configOptions{mode,model,allow_all}.
        assertEquals(List.of("copilot"), HarnessCatalog.COPILOT.binaryNames());
        assertEquals("--acp", HarnessCatalog.COPILOT.launchArgs());
        assertEquals(HarnessCatalog.COPILOT, HarnessCatalog.byBinaryName("copilot"));
        assertEquals(HarnessCatalog.COPILOT, HarnessCatalog.byBinaryName("copilot.exe"));
        assertFalse(HarnessCatalog.COPILOT.requiresMessageQueue());
        assertTrue(HarnessCatalog.COPILOT.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.COPILOT.injectsEditorContext());
        assertFalse(HarnessCatalog.COPILOT.supportsMessageIds());
        assertTrue(HarnessCatalog.COPILOT.supportsMcpServer());
        assertFalse(HarnessCatalog.COPILOT.supportsSessionSetMode());
        assertTrue(HarnessCatalog.COPILOT.supportsSessionSetConfigOption());
        assertFalse(HarnessCatalog.COPILOT.supportsSessionSetModel());
        assertTrue(HarnessCatalog.COPILOT.supportsModelSelection());
        assertTrue(HarnessCatalog.COPILOT.supportsSessionList());
        assertTrue(HarnessCatalog.COPILOT.supportsAgentList());
    }

    @Test
    void kiloCapabilitiesMatchHandshake() {
        // Catalog data pinned against the live Kilo 7.8.18 initialize: protocol 1,
        // loadSession, sessionCapabilities {close,fork,list,resume}, mcp http+sse,
        // prompt embeddedContext+image. requiresMessageQueue is a client policy:
        // Kilo accepts a follow-up server-side, but the client holds it until
        // turn end instead of letting the running turn be superseded.
        assertEquals(List.of("kilo", "kilocode"), HarnessCatalog.KILO.binaryNames());
        assertEquals("acp", HarnessCatalog.KILO.launchArgs());
        assertEquals(HarnessCatalog.KILO, HarnessCatalog.byBinaryName("kilo"));
        assertEquals(HarnessCatalog.KILO, HarnessCatalog.byBinaryName("kilocode"));
        assertEquals(HarnessCatalog.KILO, HarnessCatalog.byBinaryName("kilo.exe"));
        assertEquals(HarnessCatalog.KILO, HarnessCatalog.byBinaryName("kilo.cmd"));
        assertTrue(HarnessCatalog.KILO.requiresMessageQueue());
        assertTrue(HarnessCatalog.KILO.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.KILO.injectsEditorContext());
        assertTrue(HarnessCatalog.KILO.supportsMessageIds());
        assertTrue(HarnessCatalog.KILO.supportsMcpServer());
        assertTrue(HarnessCatalog.KILO.supportsSessionSetMode());
        assertTrue(HarnessCatalog.KILO.supportsSessionSetConfigOption());
        assertFalse(HarnessCatalog.KILO.supportsSessionSetModel());
        assertTrue(HarnessCatalog.KILO.supportsModelSelection());
        assertTrue(HarnessCatalog.KILO.supportsSessionList());
        assertTrue(HarnessCatalog.KILO.supportsAgentList());
        assertEquals("npm install -g @kilocode/cli", HarnessCatalog.KILO.installWindows());
        assertEquals("brew install Kilo-Org/tap/kilo", HarnessCatalog.KILO.installMac());
        assertEquals("curl -fsSL https://kilo.ai/cli/install | bash", HarnessCatalog.KILO.installLinux());
        assertEquals(List.of("%USERHOME%/.kilo/bin"), HarnessCatalog.KILO.windowsInstallSubDirs());
        assertEquals("https://kilo.ai/docs/code-with-ai/platforms/cli", HarnessCatalog.KILO.docsUrl());
        assertEquals("kilo.svg", HarnessCatalog.harnessIconName("kilo"));
    }

    @Test
    void clineCapabilitiesMatchHandshake() {
        // Catalog data pinned against the live Cline 3.0.74 initialize: protocol 1,
        // loadSession, prompt image only (embeddedContext=false). No
        // sessionCapabilities and no mcpCapabilities are advertised; session/list
        // is -32601 and message ids/usage are absent. requiresMessageQueue is a
        // client policy: a prompt sent mid-turn resolves end_turn immediately
        // while the running turn keeps streaming.
        assertEquals(List.of("cline"), HarnessCatalog.CLINE.binaryNames());
        assertEquals("--acp", HarnessCatalog.CLINE.launchArgs());
        assertEquals(HarnessCatalog.CLINE, HarnessCatalog.byBinaryName("cline"));
        assertEquals(HarnessCatalog.CLINE, HarnessCatalog.byBinaryName("cline.exe"));
        assertEquals(HarnessCatalog.CLINE, HarnessCatalog.byBinaryName("cline.cmd"));
        assertTrue(HarnessCatalog.CLINE.requiresMessageQueue());
        assertFalse(HarnessCatalog.CLINE.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.CLINE.injectsEditorContext());
        assertFalse(HarnessCatalog.CLINE.supportsMessageIds());
        assertFalse(HarnessCatalog.CLINE.supportsMcpServer());
        assertTrue(HarnessCatalog.CLINE.supportsSessionSetMode());
        assertTrue(HarnessCatalog.CLINE.supportsSessionSetConfigOption());
        assertFalse(HarnessCatalog.CLINE.supportsSessionSetModel());
        assertTrue(HarnessCatalog.CLINE.supportsModelSelection());
        assertFalse(HarnessCatalog.CLINE.supportsSessionList());
        assertTrue(HarnessCatalog.CLINE.supportsAgentList());
        assertEquals("npm i -g cline", HarnessCatalog.CLINE.installWindows());
        assertEquals("npm i -g cline", HarnessCatalog.CLINE.installMac());
        assertEquals("npm i -g cline", HarnessCatalog.CLINE.installLinux());
        assertEquals(List.of(), HarnessCatalog.CLINE.windowsInstallSubDirs());
        assertEquals("https://docs.cline.bot/usage/acp", HarnessCatalog.CLINE.docsUrl());
        assertEquals("cline.svg", HarnessCatalog.harnessIconName("cline"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/cline.svg"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/cline_dark.svg"));
    }

    @Test
    void codexCapabilitiesMatchAdapterSource() {
        // Metadata from the codex-acp v2.2.2 source: v1 router registers session/set_mode,
        // session/set_config_option (mode/model/reasoning_effort) and session/list; the
        // initialize response advertises mcpCapabilities http and sessionCapabilities
        // {resume,list,close}; new/load return models and modes. Steering is only the
        // _session/steering extension, so a second session/prompt must be queued.
        assertEquals(List.of("codex-acp"), HarnessCatalog.CODEX.binaryNames());
        assertEquals("", HarnessCatalog.CODEX.launchArgs());
        assertEquals(HarnessCatalog.CODEX, HarnessCatalog.byBinaryName("codex-acp"));
        assertEquals(HarnessCatalog.CODEX, HarnessCatalog.byBinaryName("codex-acp.exe"));
        assertTrue(HarnessCatalog.CODEX.requiresMessageQueue());
        assertTrue(HarnessCatalog.CODEX.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.CODEX.injectsEditorContext());
        assertTrue(HarnessCatalog.CODEX.supportsMessageIds());
        assertTrue(HarnessCatalog.CODEX.supportsMcpServer());
        assertTrue(HarnessCatalog.CODEX.supportsSessionSetMode());
        assertTrue(HarnessCatalog.CODEX.supportsSessionSetConfigOption());
        assertFalse(HarnessCatalog.CODEX.supportsSessionSetModel());
        assertTrue(HarnessCatalog.CODEX.supportsModelSelection());
        assertTrue(HarnessCatalog.CODEX.supportsSessionList());
        assertTrue(HarnessCatalog.CODEX.supportsAgentList());
        assertEquals("npm install -g @agentclientprotocol/codex-acp@2.2.2",
                HarnessCatalog.CODEX.installLinux());
        assertEquals("https://github.com/agentclientprotocol/codex-acp", HarnessCatalog.CODEX.docsUrl());
    }

    @Test
    void harnessIconNameResolvesKnownBinaries() {
        assertEquals("goose.svg", HarnessCatalog.harnessIconName("goose"));
        assertEquals("gemini.svg", HarnessCatalog.harnessIconName("gemini"));
        assertEquals("omp.svg", HarnessCatalog.harnessIconName("omp"));
        assertEquals("agent.svg", HarnessCatalog.harnessIconName(null));
        assertEquals("agent.svg", HarnessCatalog.harnessIconName("unknown-bin"));
        assertEquals("codex.svg", HarnessCatalog.harnessIconName("codex-acp"));
    }
}
