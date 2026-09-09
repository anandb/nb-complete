package github.anandb.netbeans.model;

import java.util.List;
import java.util.Locale;

/**
 * Catalog of the ACP harnesses the plugin can launch. Single source of truth
 * for display names, toolbar icons, binary names (native and WSL), default
 * launch arguments, per-OS install commands, and documentation links.
 * Pure data — zero dependencies (model layer).
 */
public final class HarnessCatalog {

    /** One supported harness and everything needed to detect, launch, and install it. */
    public record Harness(
            String id,
            String displayName,
            String iconBase,
            List<String> binaryNames,
            String launchArgs,
            String installWindows,
            String installMac,
            String installLinux,
            String prerequisites,
            String docsUrl) {

        /** True when the given lowercase binary basename belongs to this harness. */
        public boolean matchesBinary(String binaryName) {
            return binaryName != null && binaryNames.contains(binaryName.toLowerCase(Locale.ROOT));
        }
    }

    public static final Harness OPENCODE = new Harness(
            "opencode",
            "OpenCode",
            "logo",
            List.of("opencode"),
            "acp",
            "winget install SST.opencode",
            "brew install opencode",
            "curl -fsSL https://opencode.ai/install | bash",
            "",
            "https://opencode.ai/docs/");

    public static final Harness GOOSE = new Harness(
            "goose",
            "Goose",
            "goose",
            List.of("goose"),
            "acp",
            "irm https://github.com/block/goose/releases/download/stable/download_cli.sh | iex",
            "brew install block-goose-cli",
            "curl -fsSL https://github.com/block/goose/releases/download/stable/download_cli.sh | bash",
            "",
            "https://block-goose.mintlify.app/docs/quickstart");

    public static final Harness PI = new Harness(
            "pi",
            "Pi",
            "pi-logo",
            List.of("pi-acp"),
            "",
            "powershell -c \"irm https://pi.dev/install.ps1 | iex\"",
            "curl -fsSL https://pi.dev/install.sh | sh",
            "curl -fsSL https://pi.dev/install.sh | sh",
            "Requires Node.js 22+. The ACP entry points (pi-acp / pi-agent) are installed together with pi.",
            "https://github.com/earendil-works/pi-acp");

    public static final Harness CURSOR = new Harness(
            "cursor",
            "Cursor",
            "cursor",
            List.of("agent", "cursor-agent"),
            "acp",
            "Install the Cursor CLI from https://cursor.com/cli (Windows installer)",
            "curl https://cursor.com/install -fsSL | bash",
            "curl https://cursor.com/install -fsSL | bash",
            "The ACP entry point is the cursor-agent CLI itself (run with \"agent acp\").",
            "https://cursor.com/docs/cli/acp");

    public static final Harness CLAUDE = new Harness(
            "claude",
            "Claude",
            "claude",
            List.of("claude-agent-acp"),
            "",
            "npm install -g @agentclientprotocol/claude-agent-acp",
            "npm install -g @agentclientprotocol/claude-agent-acp",
            "npm install -g @agentclientprotocol/claude-agent-acp",
            "Requires Node.js 18+. Bundles the Claude Agent SDK, so a separate claude CLI install is not needed.",
            "https://github.com/agentclientprotocol/claude-agent-acp");

    /** All supported harnesses, in the order offered during onboarding:
     *  OpenCode, Pi, Goose, Cursor, Claude. */
    public static final List<Harness> ALL =
            List.of(OPENCODE, PI, GOOSE, CURSOR, CLAUDE);

    /** Looks up a harness by catalog id; {@code null} when unknown. */
    public static Harness byId(String id) {
        for (Harness h : ALL) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return null;
    }

    /** Finds the harness that owns the given lowercase binary basename; {@code null} when unknown. */
    public static Harness byBinaryName(String binaryName) {
        for (Harness h : ALL) {
            if (h.matchesBinary(binaryName)) {
                return h;
            }
        }
        return null;
    }

    private HarnessCatalog() {}
}
