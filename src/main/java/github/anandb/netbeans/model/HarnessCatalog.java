package github.anandb.netbeans.model;

import java.util.List;
import java.util.Locale;
import org.openide.util.NbBundle;

/**
 * Catalog of the ACP harnesses the plugin can launch. Single source of truth
 * for display names, toolbar icons, binary names (native and WSL), default
 * launch arguments, capability flags, per-OS install commands, and documentation links.
 * Data-only record; the only dependency is NbBundle for user-facing message lookup.
 */
public final class HarnessCatalog {

    /** One supported harness and everything needed to detect, launch, and use it. */
    public record Harness(
            String id,
            String displayName,
            String iconBase,
            List<String> binaryNames,
            String launchArgs,
            /** True when a second {@code session/prompt} drops the in-flight turn (goose). */
            boolean requiresMessageQueue,
            boolean sendsMcpServerConfig,
            boolean injectsEditorContext,
            boolean supportsTokenStats,
            boolean supportsMessageIds,
            boolean supportsMcpServer,
            boolean supportsSessionSetMode,
            boolean supportsSessionSetConfigOption,
            /** True when model switching must go through ACP {@code session/set_model} (Hermes). */
            boolean supportsSessionSetModel,
            boolean supportsModelSelection,
            boolean supportsSessionList,
            /** True when the agent/mode dropdown is offered; false disables it (OpenClaw). */
            boolean supportsAgentList,
            /** Bundle key of the message shown when model selection is unavailable; empty when none. */
            String unsupportedModelSelectionMessage,
            /** Bundle key of the placeholder item text shown in the disabled model
             *  dropdown when model selection is unavailable; empty when none. */
            String unsupportedModelSelectionPlaceholder,
            String installWindows,
            String installMac,
            String installLinux,
            String prerequisites,
            String docsUrl,
            List<String> windowsInstallSubDirs) {

        /**
         * Resolves the unsupported-model-selection message from the model
         * bundle. The record field holds the bundle key (empty when the
         * harness has no such message), keeping the literal text localized.
         */
        @Override
        public String unsupportedModelSelectionMessage() {
            return unsupportedModelSelectionMessage == null || unsupportedModelSelectionMessage.isEmpty()
                    ? "" : NbBundle.getMessage(HarnessCatalog.class, unsupportedModelSelectionMessage);
        }

        /** Resolves the placeholder item text for the disabled model dropdown
         *  (empty when the harness has none). */
        @Override
        public String unsupportedModelSelectionPlaceholder() {
            return unsupportedModelSelectionPlaceholder == null || unsupportedModelSelectionPlaceholder.isEmpty()
                    ? "" : NbBundle.getMessage(HarnessCatalog.class, unsupportedModelSelectionPlaceholder);
        }

        /** True when the given binary basename belongs to this harness, with or
         *  without a Windows executable extension ({@code omp}, {@code omp.exe}). */
        public boolean matchesBinary(String binaryName) {
            String base = stripBinaryExtension(binaryName);
            return base != null && binaryNames.contains(base);
        }
    }

    public static final Harness OPENCODE = new Harness(
        "opencode", "OpenCode", "opencode",
        List.of("opencode"), "acp",
        false, true, true, true, true, true, false, true, false, true, true, true,
        "",
        "",
        "winget install SST.opencode",
        "brew install anomalyco/tap/opencode",
        "curl -fsSL https://opencode.ai/install | bash",
        "", "https://opencode.ai/docs/",
        List.of()
    );

    public static final Harness GOOSE = new Harness(
        "goose", "Goose", "goose",
        List.of("goose"), "acp",
        true, true, false, false, true, true, false, true, false, true, true, true,
        "",
        "",
        "powershell -c \"iwr https://raw.githubusercontent.com/aaif-goose/goose/main/download_cli.ps1 -OutFile download_cli.ps1; .\\download_cli.ps1\"",
        "brew install block-goose-cli",
        "curl -fsSL https://github.com/aaif-goose/goose/releases/download/stable/download_cli.sh | bash",
        "", "https://goose-docs.ai/docs/getting-started/installation",
        List.of()
    );

    public static final Harness PI = new Harness(
            "pi", "Pi", "pi-logo",
            List.of("pi-acp"), "",
            true, true, false, false, false, true, false, true, false, true, true, true,
            "",
            "",
            "powershell -c \"irm https://pi.dev/install.ps1 | iex\"",
            "curl -fsSL https://pi.dev/install.sh | sh",
            "curl -fsSL https://pi.dev/install.sh | sh",
            "Requires Node.js 22+ and pi 0.80.4+ on your PATH. Install the ACP adapter separately: "
                    + "npm install -g @geohar/pi-acp. For MCP Tools, add pi-mcp-adapter to your Pi packages.",
            "https://github.com/georgeharker/pi-acp",
            List.of("%LOCALAPPDATA%\\pi-node\\current")
    );

    public static final Harness CURSOR = new Harness(
        "cursor", "Cursor", "cursor",
        List.of("agent", "cursor-agent"), "acp",
        false, true, true, false, false, true, false, true, false, true, true, true,
        "",
        "",
        "powershell -c \"irm 'https://cursor.com/install?win32=true' | iex\"",
        "curl https://cursor.com/install -fsSL | bash",
        "curl https://cursor.com/install -fsSL | bash",
        "The ACP entry point is the cursor-agent CLI itself (run with \"agent acp\").",
        "https://cursor.com/docs/cli/acp",
        List.of()
    );

    public static final Harness CLAUDE = new Harness(
        "claude", "Claude", "claude",
        List.of("claude-agent-acp"), "",
        false, true, true, false, true, true, true, true, false, true, true, true,
        "",
        "",
        "npm install -g @agentclientprotocol/claude-agent-acp",
        "npm install -g @agentclientprotocol/claude-agent-acp",
        "npm install -g @agentclientprotocol/claude-agent-acp",
        "Requires Node.js 18+. Bundles the Claude Agent SDK, so a separate claude CLI install is not needed.",
        "https://github.com/agentclientprotocol/claude-agent-acp",
        List.of()
    );

    public static final Harness HERMES = new Harness(
            "hermes", "Hermes", "hermes",
            List.of("hermes"), "acp",
            true, true, false, false, false, true, false, true, true, true, true, true,
            "",
            "",
            "powershell -c \"iex (irm https://hermes-agent.nousresearch.com/install.ps1)\"",
            "curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash",
            "curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash",
            "Requires Python 3.10+. Enable ACP with: cd ~/.hermes/hermes-agent && uv pip install -e '.[acp]'.",
            "https://hermes-agent.nousresearch.com/docs/user-guide/features/acp",
            List.of("%LOCALAPPDATA%\\hermes\\bin")
    );

    public static final Harness GEMINI = new Harness(
        "gemini", "Gemini", "gemini",
        List.of("gemini"), "--acp --model gemini-3.5-flash",
        false, true, true, false, true, true, true, false, false, false, false, true,
        "MSG_UnsupportedModelSelection",
        "MSG_GeminiUnsupportedModelSelectionPlaceholder",
        "npm install -g @google/gemini-cli", "npm install -g @google/gemini-cli", "npm install -g @google/gemini-cli",
        "Requires Node.js 20+",
        "https://geminicli.com/docs/get-started/installation/",
        List.of()
    );

    public static final Harness OMP = new Harness(
        "omp", "Oh My Pi", "omp",
        List.of("omp"), "acp",
        false, true, true, false, true, true, true, true, false, true, true, true,
        "",
        "",
        "powershell -c \"irm https://omp.sh/install.ps1 | iex\"",
        "curl -fsSL https://omp.sh/install | sh",
        "curl -fsSL https://omp.sh/install | sh",
        "Authenticate a model provider in a terminal (omp, then /login) before ACP. The ACP entry point is omp acp.",
        "https://omp.sh/docs/acp",
        List.of("%LOCALAPPDATA%\\omp")
    );

    public static final Harness OPENCLAW = new Harness(
        "openclaw", "OpenClaw", "openclaw",
        List.of("openclaw"), "acp",
        true, false, true, false, false, true, false, true, false, false, false, false,
        "MSG_OpenClawUnsupportedModelSelection",
        "MSG_OpenClawUnsupportedModelSelectionPlaceholder",
        "powershell -c \"iwr -useb https://openclaw.ai/install.ps1 | iex\"",
        "curl -fsSL https://openclaw.ai/install.sh | bash",
        "curl -fsSL https://openclaw.ai/install.sh | bash",
        "Requires Node 24.16+ or 26.1+ - Node 26 is recommended; the installer provisions Node 26 on macOS and Node 24 LTS on Linux",
        "https://docs.openclaw.ai/install",
        List.of()
    );

    public static final Harness DEVIN = new Harness(
        "devin", "Devin", "devin",
        List.of("devin"), "acp",
        false, true, true, false, false, true, false, true, false, true, true, true,
        "",
        "",
        "powershell -c \"irm https://static.devin.ai/cli/setup.ps1 | iex\"",
        "brew install --cask devin-cli",
        "curl -fsSL https://cli.devin.ai/install.sh | bash",
        "",
        "https://docs.devin.ai/cli",
        List.of()
    );

    /** Fallback for unknown harnesses. */
    public static final Harness UNKNOWN = new Harness(
        "unknown", "Agent", "agent",
        List.of(), "",
        true, true, true, false, false, true, false, true, false, true, true, true,
        "",
        "",
        "", "", "", "", "",
        List.of()
    );

    /** All supported harnesses, in the order offered during onboarding. */
    public static final List<Harness> ALL =
            List.of(OMP, OPENCODE, OPENCLAW, PI, GOOSE, CURSOR, CLAUDE, HERMES, GEMINI, DEVIN);

    /** Looks up a harness by catalog id; {@link #UNKNOWN} when not found. */
    public static Harness byId(String id) {
        for (Harness h : ALL) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return UNKNOWN;
    }

    /** Finds the harness that owns the given binary basename; {@link #UNKNOWN} when not found.
     *  Accepts bare names and Windows-ornamented ones ({@code omp.exe}). */
    public static Harness byBinaryName(String binaryName) {
        for (Harness h : ALL) {
            if (h.matchesBinary(binaryName)) {
                return h;
            }
        }
        return UNKNOWN;
    }

    /**
     * Normalizes a binary basename for catalog lookup: trims a trailing Windows
     * executable extension ({@code .exe} / {@code .cmd}) and lowercases, so
     * {@code "OMP.EXE"} and {@code "omp"} both yield {@code "omp"}.
     * Returns {@code null} for null or blank input. Mirrors
     * {@code BinaryResolver.binaryNameFromPath} — the two extension sets must
     * stay identical.
     */
    public static String stripBinaryExtension(String binaryName) {
        if (binaryName == null || binaryName.isBlank()) {
            return null;
        }
        String name = binaryName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe") || name.endsWith(".cmd")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }

    /**
     * Maps a resolved harness binary name to its toolbar icon file name
     * (theme-aware base name; the dark variant is selected by the icon loader).
     * Falls back to the plugin logo for harnesses without a dedicated icon.
     *
     * @param binaryName resolved harness binary name, e.g. {@code "cursor-agent"}
     * @return icon file name, never null
     */
    public static String harnessIconName(String binaryName) {
        if (binaryName == null) {
            return "agent.svg";
        }
        Harness h = byBinaryName(binaryName);
        return h != null && h != UNKNOWN ? h.iconBase() + ".svg" : "agent.svg";
    }

    private HarnessCatalog() {}
}
