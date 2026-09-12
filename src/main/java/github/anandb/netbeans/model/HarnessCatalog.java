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
            boolean supportsMessageQueue,
            boolean sendsMcpServerConfig,
            boolean injectsEditorContext,
            boolean supportsTokenStats,
            boolean supportsMessageIds,
            boolean supportsMcpServer,
            boolean supportsSessionSetMode,
            boolean supportsSessionSetConfigOption,
            boolean supportsModelSelection,
            boolean supportsSessionList,
            /** Bundle key of the message shown when model selection is unavailable; empty when none. */
            String unsupportedModelSelectionMessage,
            String installWindows,
            String installMac,
            String installLinux,
            String prerequisites,
            String docsUrl) {

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

        /** True when the given lowercase binary basename belongs to this harness. */
        public boolean matchesBinary(String binaryName) {
            return binaryName != null && binaryNames.contains(binaryName.toLowerCase(Locale.ROOT));
        }
    }

    public static final Harness OPENCODE = new Harness(
            "opencode", "OpenCode", "opencode",
            List.of("opencode"), "acp",
            false, true, true, true, true, true, false, true, true, true,
            "",
            "winget install SST.opencode",
            "brew install opencode",
            "curl -fsSL https://opencode.ai/install | bash",
            "", "https://opencode.ai/docs/");

    public static final Harness GOOSE = new Harness(
            "goose", "Goose", "goose",
            List.of("goose"), "acp",
            true, true, false, false, true, true, false, true, true, true,
            "",
            "irm https://github.com/block/goose/releases/download/stable/download_cli.sh | iex",
            "brew install block-goose-cli",
            "curl -fsSL https://github.com/block/goose/releases/download/stable/download_cli.sh | bash",
            "", "https://block-goose.mintlify.app/docs/quickstart");

    public static final Harness PI = new Harness(
            "pi", "Pi", "pi-logo",
            List.of("pi-acp"), "",
            true, false, false, false, false, true, false, true, true, true,
            "",
            "powershell -c \"irm https://pi.dev/install.ps1 | iex\"",
            "curl -fsSL https://pi.dev/install.sh | sh",
            "curl -fsSL https://pi.dev/install.sh | sh",
            "Requires Node.js 22+. The ACP entry points (pi-acp / pi-agent) are installed together with pi.",
            "https://github.com/earendil-works/pi-acp");

    public static final Harness CURSOR = new Harness(
            "cursor", "Cursor", "cursor",
            List.of("agent", "cursor-agent"), "acp",
            false, true, true, false, false, true, false, true, true, true,
            "",
            "Install the Cursor CLI from https://cursor.com/cli (Windows installer)",
            "curl https://cursor.com/install -fsSL | bash",
            "curl https://cursor.com/install -fsSL | bash",
            "The ACP entry point is the cursor-agent CLI itself (run with \"agent acp\").",
            "https://cursor.com/docs/cli/acp");

    public static final Harness CLAUDE = new Harness(
            "claude", "Claude", "claude",
            List.of("claude-agent-acp"), "",
            false, true, true, false, true, true, true, true, true, true,
            "",
            "npm install -g @agentclientprotocol/claude-agent-acp",
            "npm install -g @agentclientprotocol/claude-agent-acp",
            "npm install -g @agentclientprotocol/claude-agent-acp",
            "Requires Node.js 18+. Bundles the Claude Agent SDK, so a separate claude CLI install is not needed.",
            "https://github.com/agentclientprotocol/claude-agent-acp");

    public static final Harness HERMES = new Harness(
            "hermes", "Hermes", "hermes",
            List.of("hermes"), "",
            true, true, false, false, false, true, false, true, true, true,
            "",
            "iex (irm https://hermes-agent.nousresearch.com/install.ps1)",
            "curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash",
            "curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash",
            "Requires Python 3.10+. Enable ACP with: cd ~/.hermes/hermes-agent && uv pip install -e '.[acp]'.",
            "https://hermes-agent.nousresearch.com/docs/user-guide/features/acp");

    public static final Harness GEMINI = new Harness(
            "gemini", "Gemini", "gemini",
            List.of("gemini"), "--acp --model gemini-3.5-flash",
            true, true, true, false, true, true, true, false, false, false,
            "MSG_UnsupportedModelSelection",
            "", "", "", "", "");

    public static final Harness OMP = new Harness(
            "omp", "Oh My Pi", "omp",
            List.of("omp"), "acp",
            true, true, true, false, true, true, true, true, true, true,
            "",
            "", "", "", "", "");

    /** Fallback for unknown or unconfigured harnesses. */
    public static final Harness UNKNOWN = new Harness(
            "unknown", "Agent", "agent",
            List.of(), "",
            false, true, true, true, true, true, false, true, true, true,
            "",
            "", "", "", "", "");

    /** All supported harnesses, in the order offered during onboarding. */
    public static final List<Harness> ALL =
            List.of(OPENCODE, PI, GOOSE, CURSOR, CLAUDE, HERMES, GEMINI, OMP);

    /** Looks up a harness by catalog id; {@link #UNKNOWN} when not found. */
    public static Harness byId(String id) {
        for (Harness h : ALL) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return UNKNOWN;
    }

    /** Finds the harness that owns the given lowercase binary basename; {@link #UNKNOWN} when not found. */
    public static Harness byBinaryName(String binaryName) {
        for (Harness h : ALL) {
            if (h.matchesBinary(binaryName)) {
                return h;
            }
        }
        return UNKNOWN;
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
