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
        assertEquals(19, HarnessCatalog.ALL.size());
        Set<String> ids = new HashSet<>();
        for (Harness h : HarnessCatalog.ALL) {
            assertTrue(ids.add(h.id()), "duplicate id: " + h.id());
        }
        assertTrue(ids.containsAll(Set.of("opencode", "codex", "goose", "pi", "cursor", "claude", "hermes",
                "gemini", "omp", "openclaw", "devin", "copilot", "kilo", "cline", "jcode", "qwen", "kimi",
                "grok", "openhands")));
    }

    @Test
    void allHarnessesExposeTheKnownIdSet() {
        Set<String> allIds = new HashSet<>(HarnessCatalog.ALL.stream()
                .map(Harness::id).toList());
        assertEquals(Set.of("omp", "opencode", "codex", "openclaw", "pi", "goose", "cursor", "copilot",
                "claude", "hermes", "gemini", "devin", "kilo", "cline", "jcode", "qwen", "kimi", "grok",
                "openhands"),
                allIds);
    }

    @Test
    void everyHarnessHasBinaryNamesAndIcon() {
        for (Harness h : HarnessCatalog.ALL) {
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
    void everyHarnessHasActivationGuidance() {
        for (Harness h : HarnessCatalog.ALL) {
            HarnessCatalog.Activation activation = HarnessCatalog.activation(h.id());
            assertFalse(activation.hint().isBlank(), h.id() + ": no sign-in hint");
        }
        // Harnesses that need no interactive login still explain the first-run flow.
        assertFalse(HarnessCatalog.activation("gemini").hasCommand());
        assertTrue(HarnessCatalog.activation("codex").hint().contains("OPENAI_API_KEY"));
        // Concrete, copyable commands for the harnesses that have one.
        assertEquals("kilo auth login", HarnessCatalog.activation("kilo").command());
        assertEquals("agent login", HarnessCatalog.activation("cursor").command());
        assertEquals("copilot login", HarnessCatalog.activation("copilot").command());
        assertEquals("cline auth", HarnessCatalog.activation("cline").command());
        assertEquals("jcode login", HarnessCatalog.activation("jcode").command());
        // Unknown ids and the fallback harness yield an empty activation.
        assertTrue(HarnessCatalog.activation("nope").hint().isBlank());
        assertTrue(HarnessCatalog.activation(null).hint().isBlank());
        assertTrue(HarnessCatalog.activation(HarnessCatalog.UNKNOWN.id()).hint().isBlank());
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
        assertEquals(HarnessCatalog.JCODE, HarnessCatalog.byBinaryName("jcode"));
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
        Harness u = HarnessCatalog.UNKNOWN;
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
    void gooseQueuesPromptsButSteersThroughItsOwnExtension() {
        // goose rejects a second session/prompt mid-turn ("session already has active
        // run …; use _goose/unstable/session/steer"), so it queues as a fallback.
        assertTrue(HarnessCatalog.GOOSE.requiresMessageQueue());
        assertTrue(HarnessCatalog.GOOSE.supportsSessionSteer());
        assertFalse(HarnessCatalog.OPENCODE.supportsSessionSteer());
        assertFalse(HarnessCatalog.OPENCODE_V2.supportsSessionSteer());
        assertFalse(HarnessCatalog.KILO.supportsSessionSteer());
        assertFalse(HarnessCatalog.UNKNOWN.supportsSessionSteer());
    }

    @Test
    void onlyHermesSteersThroughASlashCommand() {
        assertTrue(HarnessCatalog.HERMES.supportsSlashSteer());
        assertFalse(HarnessCatalog.GOOSE.supportsSlashSteer());
        assertFalse(HarnessCatalog.OPENCODE.supportsSlashSteer());
        assertFalse(HarnessCatalog.UNKNOWN.supportsSlashSteer());
    }

    @Test
    void onlyGooseRenamesSessionsOnTheServer() {
        // ACP has no rename; goose implements _goose/unstable/session/rename.
        assertTrue(HarnessCatalog.GOOSE.supportsSessionRename());
        assertFalse(HarnessCatalog.OPENCODE.supportsSessionRename());
        assertFalse(HarnessCatalog.UNKNOWN.supportsSessionRename());
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
        Harness v1 = HarnessCatalog.OPENCODE;
        Harness v2 = HarnessCatalog.OPENCODE_V2;
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
    void allHarnessesExceptOpenclawAndJcodeSupportAgentList() {
        for (Harness h : HarnessCatalog.ALL) {
            if ("openclaw".equals(h.id()) || "jcode".equals(h.id())) {
                continue;
            }
            assertTrue(h.supportsAgentList(), h.id() + " should support agent list");
        }
        assertFalse(HarnessCatalog.JCODE.supportsAgentList());
        assertTrue(HarnessCatalog.UNKNOWN.supportsAgentList());
    }

    @Test
    void onlyJcodeSeparatesReplayChunks() {
        for (Harness h : HarnessCatalog.ALL) {
            assertEquals("jcode".equals(h.id()), h.separateBubblesPerReplayChunk(),
                    h.id() + " replay-chunk capability");
        }
        assertFalse(HarnessCatalog.UNKNOWN.separateBubblesPerReplayChunk());
    }

    @Test
    void onlyOpenHandsSendsItsSystemPromptAsAThought() {
        for (Harness h : HarnessCatalog.ALL) {
            assertEquals("openhands".equals(h.id()), h.sendsSystemPromptAsThought(),
                    h.id() + " system-prompt thought capability");
        }
        assertFalse(HarnessCatalog.UNKNOWN.sendsSystemPromptAsThought());
    }

    @Test
    void onlyTheCuratedSetHasLimitedSupport() {
        java.util.Set<String> limitedIds = java.util.Set.of("openclaw", "jcode", "gemini", "pi", "cline", "openhands");
        for (Harness h : HarnessCatalog.ALL) {
            boolean limited = limitedIds.contains(h.id());
            assertEquals(limited, h.hasLimitedSupport(), h.id() + " support tier");
            assertEquals(limited ? HarnessCatalog.SupportTier.LIMITED : HarnessCatalog.SupportTier.FULL,
                    h.supportTier(), h.id() + " support tier");
            assertEquals(limited, !h.limitations().isBlank(), h.id() + " limitations text");
        }
        assertFalse(HarnessCatalog.UNKNOWN.hasLimitedSupport());
    }

    @Test
    void onlyPiAndJcodeRejectMcpAuthTokens() {
        for (Harness h : HarnessCatalog.ALL) {
            boolean tokenless = "pi".equals(h.id()) || "jcode".equals(h.id());
            assertEquals(!tokenless, h.acceptsMcpAuthToken(), h.id() + " MCP token capability");
        }
        // A custom binary the catalog cannot identify keeps the token requirement.
        assertTrue(HarnessCatalog.UNKNOWN.acceptsMcpAuthToken());
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
    void jcodeCapabilitiesMatchHandshake() {
        // Catalog data pinned against the jcode 0.99.1 ACP source: initialize
        // advertises loadSession, sessionCapabilities {close,resume} and prompt
        // embeddedContext+image. session/list and session/set_mode are not
        // implemented; session/prompt is rejected while a turn runs ("already
        // processing a prompt"), and ACP mcpServers are validated but not started.
        assertEquals(List.of("jcode"), HarnessCatalog.JCODE.binaryNames());
        assertEquals("acp", HarnessCatalog.JCODE.launchArgs());
        assertEquals(HarnessCatalog.JCODE, HarnessCatalog.byBinaryName("jcode"));
        assertEquals(HarnessCatalog.JCODE, HarnessCatalog.byBinaryName("jcode.exe"));
        assertEquals(HarnessCatalog.JCODE, HarnessCatalog.byBinaryName("jcode.cmd"));
        assertTrue(HarnessCatalog.JCODE.requiresMessageQueue());
        assertFalse(HarnessCatalog.JCODE.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.JCODE.injectsEditorContext());
        assertFalse(HarnessCatalog.JCODE.supportsMessageIds());
        assertFalse(HarnessCatalog.JCODE.supportsMcpServer());
        assertFalse(HarnessCatalog.JCODE.supportsSessionSetMode());
        assertTrue(HarnessCatalog.JCODE.supportsSessionSetConfigOption());
        assertFalse(HarnessCatalog.JCODE.supportsSessionSetModel());
        assertTrue(HarnessCatalog.JCODE.supportsModelSelection());
        assertFalse(HarnessCatalog.JCODE.supportsSessionList());
        assertFalse(HarnessCatalog.JCODE.supportsAgentList());
        assertTrue(HarnessCatalog.JCODE.separateBubblesPerReplayChunk());
        assertFalse(HarnessCatalog.JCODE.acceptsMcpAuthToken());
        assertEquals("powershell -c \"irm https://jcode.sh/install.ps1 | iex\"",
                HarnessCatalog.JCODE.installWindows());
        assertEquals("curl -fsSL https://jcode.sh/install | bash", HarnessCatalog.JCODE.installMac());
        assertEquals("curl -fsSL https://jcode.sh/install | bash", HarnessCatalog.JCODE.installLinux());
        assertEquals(List.of("%LOCALAPPDATA%\\jcode\\bin"), HarnessCatalog.JCODE.windowsInstallSubDirs());
        assertEquals("https://jcode.sh/docs", HarnessCatalog.JCODE.docsUrl());
        assertEquals("jcode.svg", HarnessCatalog.harnessIconName("jcode"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/jcode.svg"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/jcode_dark.svg"));
    }

    @Test
    void qwenCapabilitiesMatchHandshake() {
        // Pinned against the live qwen-code 0.27.2 initialize: loadSession,
        // sessionCapabilities {list,resume} (no close/delete), mcp http+sse, prompt
        // image+audio+embeddedContext. session/new returns models and both
        // session/set_mode and session/set_config_option are implemented, so model
        // selection works. Chunks carry no messageId, and there is no steering
        // extension: a second session/prompt mid-turn supersedes the running turn
        // (the first execution is cancelled and its result is -32603 Internal error),
        // so a second prompt must be queued until turn end.
        assertEquals(List.of("qwen"), HarnessCatalog.QWEN.binaryNames());
        assertEquals("--acp", HarnessCatalog.QWEN.launchArgs());
        assertEquals(HarnessCatalog.QWEN, HarnessCatalog.byBinaryName("qwen"));
        assertEquals(HarnessCatalog.QWEN, HarnessCatalog.byBinaryName("qwen.exe"));
        assertEquals(HarnessCatalog.QWEN, HarnessCatalog.byBinaryName("qwen.cmd"));
        assertTrue(HarnessCatalog.QWEN.requiresMessageQueue());
        assertTrue(HarnessCatalog.QWEN.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.QWEN.injectsEditorContext());
        assertFalse(HarnessCatalog.QWEN.supportsMessageIds());
        assertTrue(HarnessCatalog.QWEN.supportsMcpServer());
        assertTrue(HarnessCatalog.QWEN.supportsSessionSetMode());
        assertTrue(HarnessCatalog.QWEN.supportsSessionSetConfigOption());
        assertFalse(HarnessCatalog.QWEN.supportsSessionSetModel());
        assertTrue(HarnessCatalog.QWEN.supportsModelSelection());
        assertTrue(HarnessCatalog.QWEN.supportsSessionList());
        assertTrue(HarnessCatalog.QWEN.supportsAgentList());
        assertEquals("npm install -g @qwen-code/qwen-code", HarnessCatalog.QWEN.installWindows());
        assertEquals("npm install -g @qwen-code/qwen-code", HarnessCatalog.QWEN.installMac());
        assertEquals("npm install -g @qwen-code/qwen-code", HarnessCatalog.QWEN.installLinux());
        assertEquals(List.of(), HarnessCatalog.QWEN.windowsInstallSubDirs());
        assertEquals("https://qwenlm.github.io/qwen-code-docs/", HarnessCatalog.QWEN.docsUrl());
        assertEquals("qwen.svg", HarnessCatalog.harnessIconName("qwen"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/qwen.svg"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/qwen_dark.svg"));
    }

    @Test
    void kimiCapabilitiesMatchHandshake() {
        // Pinned against the Kimi Code CLI acp-server source: loadSession, session
        // capabilities {list,resume,close,delete,fork,additionalDirectories}, prompt
        // image+embeddedContext (audio false), mcp http+sse. session/set_mode and
        // set_config_option are implemented; chunks carry no messageId. A plain mid-turn
        // prompt is queued by the engine and the RPC returns undefined, so the client
        // queues to keep the turn-end signal honest.
        assertEquals(List.of("kimi"), HarnessCatalog.KIMI.binaryNames());
        assertEquals("acp", HarnessCatalog.KIMI.launchArgs());
        assertEquals(HarnessCatalog.KIMI, HarnessCatalog.byBinaryName("kimi"));
        assertEquals(HarnessCatalog.KIMI, HarnessCatalog.byBinaryName("kimi.exe"));
        assertTrue(HarnessCatalog.KIMI.requiresMessageQueue());
        assertTrue(HarnessCatalog.KIMI.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.KIMI.injectsEditorContext());
        assertFalse(HarnessCatalog.KIMI.supportsMessageIds());
        assertTrue(HarnessCatalog.KIMI.supportsMcpServer());
        assertTrue(HarnessCatalog.KIMI.supportsSessionSetMode());
        assertTrue(HarnessCatalog.KIMI.supportsSessionSetConfigOption());
        assertTrue(HarnessCatalog.KIMI.supportsModelSelection());
        assertTrue(HarnessCatalog.KIMI.supportsSessionList());
        assertTrue(HarnessCatalog.KIMI.stripsSystemReminders());
        assertEquals("https://moonshotai.github.io/kimi-code/", HarnessCatalog.KIMI.docsUrl());
        assertEquals("kimi.svg", HarnessCatalog.harnessIconName("kimi"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/kimi.svg"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/kimi_dark.svg"));
    }

    @Test
    void grokCapabilitiesMatchHandshake() {
        // Pinned against the Grok Build acp_agent source: loadSession, session
        // capabilities {list,close,resume} (no delete/fork), prompt embeddedContext,
        // mcp http+sse. session/set_mode and set_config_option are implemented and
        // session/new returns models. Mid-turn messages queue.
        assertEquals(List.of("grok"), HarnessCatalog.GROK.binaryNames());
        assertEquals("agent stdio", HarnessCatalog.GROK.launchArgs());
        assertEquals(HarnessCatalog.GROK, HarnessCatalog.byBinaryName("grok"));
        assertEquals(HarnessCatalog.GROK, HarnessCatalog.byBinaryName("grok.exe"));
        assertTrue(HarnessCatalog.GROK.requiresMessageQueue());
        assertTrue(HarnessCatalog.GROK.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.GROK.injectsEditorContext());
        assertFalse(HarnessCatalog.GROK.supportsMessageIds());
        assertTrue(HarnessCatalog.GROK.supportsMcpServer());
        assertTrue(HarnessCatalog.GROK.supportsSessionSetMode());
        assertTrue(HarnessCatalog.GROK.supportsSessionSetConfigOption());
        assertTrue(HarnessCatalog.GROK.supportsModelSelection());
        assertTrue(HarnessCatalog.GROK.supportsSessionList());
        assertTrue(HarnessCatalog.GROK.supportsInterject());
        assertFalse(HarnessCatalog.KIMI.supportsInterject());
        assertEquals("https://x.ai/cli", HarnessCatalog.GROK.docsUrl());
        assertEquals("grok.svg", HarnessCatalog.harnessIconName("grok"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/grok.svg"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/grok_dark.svg"));
    }

    @Test
    void openhandsCapabilitiesMatchHandshake() {
        // Pinned against the live OpenHands CLI 1.14.0 (SDK 1.16.1): initialize advertises
        // loadSession, prompt image+embeddedContext and MCP http+sse; session/new returns
        // three confirmation modes. session/list, close, delete, resume and set_model answer
        // -32601; set_config_option is a no-op and no models/config options are returned.
        // Transcript replay is session/load. A second prompt starts a second run loop on the
        // same conversation, so the client queues mid-turn messages.
        assertEquals(List.of("openhands"), HarnessCatalog.OPENHANDS.binaryNames());
        assertEquals("acp", HarnessCatalog.OPENHANDS.launchArgs());
        assertEquals(HarnessCatalog.OPENHANDS, HarnessCatalog.byBinaryName("openhands"));
        assertEquals(HarnessCatalog.OPENHANDS, HarnessCatalog.byBinaryName("openhands.exe"));
        assertTrue(HarnessCatalog.OPENHANDS.requiresMessageQueue());
        assertFalse(HarnessCatalog.OPENHANDS.sendsMcpServerConfig());
        assertTrue(HarnessCatalog.OPENHANDS.injectsEditorContext());
        assertFalse(HarnessCatalog.OPENHANDS.supportsMessageIds());
        assertFalse(HarnessCatalog.OPENHANDS.supportsMcpServer());
        assertTrue(HarnessCatalog.OPENHANDS.supportsSessionSetMode());
        assertTrue(HarnessCatalog.OPENHANDS.supportsAgentList());
        assertFalse(HarnessCatalog.OPENHANDS.supportsSessionSetConfigOption());
        assertFalse(HarnessCatalog.OPENHANDS.supportsSessionSetModel());
        assertFalse(HarnessCatalog.OPENHANDS.supportsModelSelection());
        assertFalse(HarnessCatalog.OPENHANDS.supportsSessionList());
        assertFalse(HarnessCatalog.OPENHANDS.supportsSessionRename());
        assertFalse(HarnessCatalog.OPENHANDS.supportsInterject());
        assertTrue(HarnessCatalog.OPENHANDS.acceptsMcpAuthToken());
        assertTrue(HarnessCatalog.OPENHANDS.sendsSystemPromptAsThought());
        assertTrue(HarnessCatalog.OPENHANDS.hasLimitedSupport());
        assertFalse(HarnessCatalog.OPENHANDS.limitations().isBlank());
        assertEquals("https://docs.openhands.dev/openhands/usage/run-openhands/acp",
                HarnessCatalog.OPENHANDS.docsUrl());
        assertEquals("openhands.svg", HarnessCatalog.harnessIconName("openhands"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/openhands.svg"));
        assertNotNull(HarnessCatalogTest.class.getResource("/github/anandb/netbeans/ui/icons/openhands_dark.svg"));
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

    @Test
    void sortedByNameIsAlphabetical() {
        List<Harness> sorted = HarnessCatalog.sortedByName();

        assertEquals(HarnessCatalog.ALL.size(), sorted.size());
        List<String> names = sorted.stream().map(Harness::displayName).toList();
        List<String> expected = new java.util.ArrayList<>(names);
        expected.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals(expected, names, "sortedByName must be alphabetical, case-insensitive");
    }
}
