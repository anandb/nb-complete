package github.anandb.netbeans.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            /** True when a second {@code session/prompt} does not steer the in-flight
             *  turn, so messages must be queued until turn end (goose; OpenCode v2). */
            boolean requiresMessageQueue,
            boolean sendsMcpServerConfig,
            boolean injectsEditorContext,
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

        /**
         * Returns a copy of this harness with only the message-queue capability
         * replaced. Used to derive a runtime variant (e.g. OpenCode v2) from its
         * catalog base without duplicating the remaining launch metadata, so the
         * two variants can never drift apart on any other flag.
         */
        public Harness withRequiresMessageQueue(boolean queueMessages) {
            return new Harness(id, displayName, iconBase, binaryNames, launchArgs, queueMessages,
                    sendsMcpServerConfig, injectsEditorContext, supportsMessageIds, supportsMcpServer,
                    supportsSessionSetMode, supportsSessionSetConfigOption, supportsSessionSetModel,
                    supportsModelSelection, supportsSessionList, supportsAgentList,
                    unsupportedModelSelectionMessage, unsupportedModelSelectionPlaceholder,
                    installWindows, installMac, installLinux, prerequisites, docsUrl,
                    windowsInstallSubDirs);
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
        false, true, true, true, true, false, true, false, true, true, true,
        "",
        "",
        "winget install SST.opencode",
        "brew install anomalyco/tap/opencode",
        "curl -fsSL https://opencode.ai/install | bash",
        "", "https://opencode.ai/docs/",
        List.of()
    );

    /**
     * OpenCode v2 capability variant: identical identity and launch metadata as
     * {@link #OPENCODE}, but without mid-turn steering — a second
     * {@code session/prompt} is not folded into the running turn, so the client
     * must queue messages until turn end. Selected at handshake by
     * {@link #forAgentVersion(Harness, String)} from the reported
     * {@code agentInfo.version}; deliberately NOT part of {@link #ALL} so
     * onboarding, install metadata and id/binary lookups keep resolving the v1
     * base entry.
     */
    public static final Harness OPENCODE_V2 = OPENCODE.withRequiresMessageQueue(true);

    /**
     * Codex via the {@code codex-acp} ACP adapter. The adapter speaks ACP v1 to
     * this client (v2 folds {@code session/load} into {@code session/resume});
     * its mid-turn steering is only available through the {@code _session/steering}
     * extension this client does not implement, so messages queue until turn end.
     * The bundled {@code @openai/codex} dependency means a separate Codex CLI
     * install is not needed.
     */
    public static final Harness CODEX = new Harness(
        "codex", "Codex", "codex",
        List.of("codex-acp"), "",
        true, true, true, true, true, true, true, false, true, true, true,
        "",
        "",
        "npm install -g @agentclientprotocol/codex-acp@2.2.2",
        "npm install -g @agentclientprotocol/codex-acp@2.2.2",
        "npm install -g @agentclientprotocol/codex-acp@2.2.2",
        "Requires Node.js and a ChatGPT account or OPENAI_API_KEY. Bundles the Codex CLI, "
                + "so a separate codex install is not needed.",
        "https://github.com/agentclientprotocol/codex-acp",
        List.of()
    );

    public static final Harness GOOSE = new Harness(
        "goose", "Goose", "goose",
        List.of("goose"), "acp",
        true, true, false, true, true, false, true, false, true, true, true,
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
            true, true, false, false, true, false, true, false, true, true, true,
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
        false, true, true, false, true, false, true, false, true, true, true,
        "",
        "",
        "powershell -c \"irm 'https://cursor.com/install?win32=true' | iex\"",
        "curl https://cursor.com/install -fsSL | bash",
        "curl https://cursor.com/install -fsSL | bash",
        "The ACP entry point is the cursor-agent CLI itself (run with \"agent acp\").",
        "https://cursor.com/docs/cli/acp",
        List.of()
    );

    public static final Harness COPILOT = new Harness(
        "copilot", "Copilot", "copilot",
        List.of("copilot"), "--acp",
        false, true, true, false, true, false, true, false, true, true, true,
        "",
        "",
        "npm install -g @github/copilot",
        "npm install -g @github/copilot",
        "npm install -g @github/copilot",
        "Requires Node.js 22+. Authenticate with \"copilot login\" before using ACP.",
        "https://github.com/github/copilot-cli",
        List.of()
    );

    public static final Harness CLAUDE = new Harness(
        "claude", "Claude", "claude",
        List.of("claude-agent-acp"), "",
        false, true, true, true, true, true, true, false, true, true, true,
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
            true, true, false, false, true, false, true, true, true, true, true,
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
        List.of("gemini"), "--acp --model gemini-3.8-flash",
        false, true, true, true, true, true, false, false, false, false, true,
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
        false, true, true, true, true, true, true, false, true, true, true,
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
        true, false, true, false, true, false, true, false, false, false, false,
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
        false, true, true, false, true, false, true, false, true, true, true,
        "",
        "",
        "powershell -c \"irm https://static.devin.ai/cli/setup.ps1 | iex\"",
        "brew install --cask devin-cli",
        "curl -fsSL https://cli.devin.ai/install.sh | bash",
        "",
        "https://docs.devin.ai/cli",
        List.of()
    );

    /**
     * Kilo Code: an OpenCode-derived ACP agent with its own version scheme
     * ({@code 7.x}) and prompt queue, so it is catalogued on its own rather than
     * as an {@link #OPENCODE} variant. Messages are queued by the client until
     * turn end ({@code requiresMessageQueue}) instead of interleaved. Model
     * switching uses the stable {@code model} config option; Kilo also exposes
     * the thinking variant as a separate {@code effort} option, and its
     * dedicated model request is only registered as {@code unstable_}.
     */
    public static final Harness KILO = new Harness(
        "kilo", "Kilo Code", "kilo",
        List.of("kilo", "kilocode"), "acp",
        true, true, true, true, true, true, true, false, true, true, true,
        "",
        "",
        "npm install -g @kilocode/cli",
        "brew install Kilo-Org/tap/kilo",
        "curl -fsSL https://kilo.ai/cli/install | bash",
        "Authenticate with kilo auth login (or run kilo and use /connect) before connecting. "
                + "The npm route requires Node.js.",
        "https://kilo.ai/docs/code-with-ai/platforms/cli",
        List.of("%USERHOME%/.kilo/bin")
    );

    /**
     * Cline: a native ACP agent launched as {@code cline --acp}. Its ACP layer
     * is v1 but sparse — no session capability block, no {@code session/list},
     * no message ids and no usage updates — and it ignores the per-session
     * {@code mcpServers} field, so the plugin's MCP server is never offered. A
     * prompt sent during a running turn resolves {@code end_turn} immediately
     * while that turn continues, so messages must be queued
     * ({@code requiresMessageQueue}). Model and mode are driven by config
     * options and {@code session/set_mode}; the dedicated model request is only
     * registered as {@code unstable_}. {@code session/new} returns plan/act
     * modes, so the agent selector is offered. Credentials come from a prior
     * {@code cline auth} (or {@code CLINE_API_KEY}); BeanBot has no ACP
     * authenticate client.
     */
    public static final Harness CLINE = new Harness(
        "cline", "Cline", "cline",
        List.of("cline"), "--acp",
        true, false, true, false, false, true, true, false, true, false, true,
        "",
        "",
        "npm i -g cline",
        "npm i -g cline",
        "npm i -g cline",
        "Requires Cline CLI 3.0.74+ (npm i -g cline@latest). Run cline auth once (or set CLINE_API_KEY) "
                + "before connecting; ACP sessions start no interactive login.",
        "https://docs.cline.bot/usage/acp",
        List.of()
    );

    /** Fallback for unknown harnesses. */
    public static final Harness UNKNOWN = new Harness(
        "unknown", "Agent", "agent",
        List.of(), "",
        true, true, true, false, true, false, true, false, true, true, true,
        "",
        "",
        "", "", "", "", "",
        List.of()
    );

    /** All supported harnesses, in the order offered during onboarding. */
    public static final List<Harness> ALL =
            List.of(OMP, OPENCODE, CODEX, OPENCLAW, PI, GOOSE, CURSOR, COPILOT, CLAUDE, HERMES, GEMINI, DEVIN,
                    KILO, CLINE);

    /**
     * Post-selection activation guidance: how to authenticate a harness's CLI
     * before its first session. {@code command} is empty when the harness signs
     * in interactively on first run; {@code hint} always explains what to do.
     * Like {@link Harness#prerequisites()}, this is plain install-time text, not
     * a localized message.
     */
    public record Activation(String command, String hint) {
        /** True when there is a concrete command the user can copy and run. */
        public boolean hasCommand() {
            return command != null && !command.isBlank();
        }
    }

    /**
     * Per-harness sign-in guidance, keyed by catalog id. Derived from the
     * per-harness pages in the user guide; keep the two in step. A harness with
     * no entry (including {@link #UNKNOWN}) yields an empty activation.
     */
    private static final Map<String, Activation> ACTIVATIONS = Map.ofEntries(
        Map.entry("opencode", new Activation("opencode auth login",
                "Sign in once and connect a model provider.")),
        Map.entry("codex", new Activation("",
                "The first session asks you to sign in with a ChatGPT account; "
                        + "set OPENAI_API_KEY to skip that.")),
        Map.entry("goose", new Activation("goose configure",
                "Connect a model provider once.")),
        Map.entry("pi", new Activation("",
                "Connect a model provider in the Pi CLI before the first session.")),
        Map.entry("cursor", new Activation("agent login",
                "Register once, or set CURSOR_API_KEY.")),
        Map.entry("copilot", new Activation("copilot login",
                "Sign in with an account that has Copilot access, or set GITHUB_TOKEN.")),
        Map.entry("claude", new Activation("",
                "Set ANTHROPIC_API_KEY before the first session.")),
        Map.entry("hermes", new Activation("hermes",
                "Start it once and connect a model provider.")),
        Map.entry("gemini", new Activation("",
                "The first run opens the Google sign-in flow; set GEMINI_API_KEY to skip it.")),
        Map.entry("omp", new Activation("omp",
                "Start it once and use /login to connect a model provider.")),
        Map.entry("openclaw", new Activation("",
                "Start OpenClaw once to connect a Gateway and a model provider.")),
        Map.entry("devin", new Activation("devin auth login",
                "Sign in once, or set WINDSURF_API_KEY.")),
        Map.entry("kilo", new Activation("kilo auth login",
                "Sign in once, or start kilo and use /connect.")),
        Map.entry("cline", new Activation("cline auth",
                "Sign in once, or set CLINE_API_KEY.")));

    /** Sign-in guidance for a harness id; empty when the harness has none. */
    public static Activation activation(String harnessId) {
        Activation activation = harnessId == null ? null : ACTIVATIONS.get(harnessId);
        return activation != null ? activation : new Activation("", "");
    }

    /** Looks up a harness by catalog id; {@link #UNKNOWN} when not found. */
    public static Harness byId(String id) {
        for (Harness h : ALL) {
            if (h.id().equals(id)) {
                return h;
            }
        }
        return UNKNOWN;
    }

    /** First OpenCode major version without mid-turn steering; v2 and later
     *  require the client to queue messages until turn end. */
    private static final int OPENCODE_STEERING_REMOVED_MAJOR = 2;

    /**
     * Applies agent-version-dependent capability deltas to a harness resolved
     * from stored preferences or the launch binary.
     *
     * <p>The catalog holds one entry per harness, but a harness's capabilities
     * can change across major releases: OpenCode v2 dropped mid-turn steering,
     * so its messages must be queued ({@link #OPENCODE_V2}) instead of delivered
     * immediately. This is the single extension point for such deltas; call it
     * with the {@code agentInfo.version} from the {@code initialize} response
     * before publishing the resolved harness.</p>
     *
     * @param harness      the catalog harness resolved for this process
     * @param agentVersion the reported agent version, or {@code null} when absent
     * @return the runtime variant to use, or {@code harness} unchanged when the
     *         version is unknown or carries no delta
     */
    public static Harness forAgentVersion(Harness harness, String agentVersion) {
        if (harness == null || !OPENCODE.id().equals(harness.id())) {
            return harness;
        }
        return majorVersion(agentVersion) >= OPENCODE_STEERING_REMOVED_MAJOR ? OPENCODE_V2 : harness;
    }

    /**
     * Parses the leading major component of a version string
     * ({@code "2.0.20"} → {@code 2}). Returns a negative value when the version
     * is null, blank, or does not start with digits, so callers can treat it as
     * "unknown" rather than "v1".
     */
    private static int majorVersion(String version) {
        if (version == null || version.isBlank()) {
            return -1;
        }
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(version.substring(0, end));
        } catch (NumberFormatException overflow) {
            return -1;
        }
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
