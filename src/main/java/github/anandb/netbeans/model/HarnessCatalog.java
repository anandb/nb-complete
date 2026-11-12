package github.anandb.netbeans.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Catalog of the ACP harnesses the plugin can launch: lookups, ordering and
 * version-dependent capability deltas. The definitions themselves live in
 * {@code harness-catalog.yaml} and are loaded into the top-level {@link Harness}
 * record by {@link HarnessCatalogLoader}.
 */
public final class HarnessCatalog {

    /**
     * How completely the plugin supports a harness. {@code LIMITED} is a curated
     * product judgment, not a derivation from capability flags: it marks harnesses
     * the user should be warned about before choosing them, and is surfaced in
     * onboarding and in the Options harness chooser.
     */
    public enum SupportTier { FULL, LIMITED }

    /** Loaded once from {@code harness-catalog.yaml}; see that resource for the definitions. */
    private static final HarnessCatalogLoader.Catalog CATALOG = HarnessCatalogLoader.load();

    public static final Harness OMP = CATALOG.require("omp");
    public static final Harness OPENCODE = CATALOG.require("opencode");
    public static final Harness CODEX = CATALOG.require("codex");
    public static final Harness OPENCLAW = CATALOG.require("openclaw");
    public static final Harness PI = CATALOG.require("pi");
    public static final Harness GOOSE = CATALOG.require("goose");
    public static final Harness CURSOR = CATALOG.require("cursor");
    public static final Harness COPILOT = CATALOG.require("copilot");
    public static final Harness CLAUDE = CATALOG.require("claude");
    public static final Harness HERMES = CATALOG.require("hermes");
    public static final Harness GEMINI = CATALOG.require("gemini");
    public static final Harness DEVIN = CATALOG.require("devin");
    public static final Harness KILO = CATALOG.require("kilo");
    public static final Harness CLINE = CATALOG.require("cline");
    public static final Harness JCODE = CATALOG.require("jcode");
    public static final Harness QWEN = CATALOG.require("qwen");

    /** OpenCode v2 capability variant: same identity as {@link #OPENCODE}, but without
     *  mid-turn steering. Selected by {@link #forAgentVersion}; deliberately not in
     *  {@link #ALL} so onboarding, install metadata and id/binary lookups keep
     *  resolving the v1 base entry. */
    public static final Harness OPENCODE_V2 = OPENCODE.withRequiresMessageQueue(true);

    /** Fallback for unknown harnesses. */
    public static final Harness UNKNOWN = CATALOG.unknown();

    /** All supported harnesses, in YAML declaration order. */
    public static final List<Harness> ALL = CATALOG.listed();

    /** Alphabetical display-name order, case-insensitive. */
    public static final Comparator<Harness> BY_DISPLAY_NAME =
            Comparator.comparing(Harness::displayName, String.CASE_INSENSITIVE_ORDER);

    /** All harnesses in alphabetical display-name order; {@link #ALL} keeps its own order. */
    public static List<Harness> sortedByName() {
        List<Harness> sorted = new ArrayList<>(ALL);
        sorted.sort(BY_DISPLAY_NAME);
        return sorted;
    }

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

    /** Shared value for a harness without sign-in guidance; allocation-free. */
    private static final Activation NO_ACTIVATION = new Activation("", "");

    /** Sign-in guidance for a harness id; empty when the harness has none. */
    public static Activation activation(String harnessId) {
        Activation activation = harnessId == null ? null : CATALOG.activations().get(harnessId);
        return activation != null ? activation : NO_ACTIVATION;
    }

    /** Looks up a harness by catalog id; {@link #UNKNOWN} when not found. */
    public static Harness byId(String id) {
        Harness harness = id == null ? null : CATALOG.byId().get(id);
        return harness != null ? harness : UNKNOWN;
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
        String key = stripBinaryExtension(binaryName);
        Harness harness = key == null ? null : CATALOG.byBinaryName().get(key);
        return harness != null ? harness : UNKNOWN;
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
