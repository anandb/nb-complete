package github.anandb.netbeans.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import github.anandb.netbeans.model.HarnessCatalog;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class BinaryResolver {

    private static final Logger LOG = Logger.from(BinaryResolver.class);
    private static final Pattern PATH_SPLIT = Pattern.compile(Pattern.quote(File.pathSeparator));

    /** ACP harness binaries that cannot carry auth tokens on MCP server URLs.
     *  The embedded MCP server skips token enforcement when one of these runs.
     *  Package-visible for tests. */
    static final Set<String> PI_HARNESS = Set.of("pi", "pi-acp", "pi-agent");

    /** Linux system package directories, owned by the distribution's package
     *  manager. No catalog harness ships in one: every installer writes to a
     *  harness-owned directory, a user bin directory, a brew/npm prefix or
     *  %LOCALAPPDATA%. A command found here that shares a harness binary's name
     *  is the distro's, not the harness's — Debian/Ubuntu ship a {@code kilo}
     *  text editor in {@code /usr/bin}. Such candidates are excluded. */
    private static final Set<String> SYSTEM_PACKAGE_DIRS = Set.of("/bin", "/sbin", "/usr/bin", "/usr/sbin");

    /** Known ACP harness binaries (launch-capable entry points), derived from
     *  {@link HarnessCatalog}. Linux names without {@code .exe}. */
    public static final List<String> KNOWN_HARNESSES = HarnessCatalog.ALL.stream()
            .flatMap(h -> h.binaryNames().stream())
            .distinct()
            .toList();

    private BinaryResolver() {}

    /** Linux / WSL binary names for known harnesses. */
    public static String[] linuxHarnessNames() {
        return KNOWN_HARNESSES.toArray(String[]::new);
    }

    /** Native binary names for this OS ({@code .exe} suffix on Windows).
     *  Native Windows also includes {@code cursor-agent.cmd} (Cursor Agent launcher).
     *  WSL / Linux discovery does not use the {@code .cmd} name. */
    public static String[] nativeHarnessNames() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return nativeHarnessNames(isWindows);
    }

    /** Package-visible so tests can cover the Windows list without a Windows JVM. */
    static String[] nativeHarnessNames(boolean isWindows) {
        if (!isWindows) {
            return linuxHarnessNames();
        }
        String[] names = new String[KNOWN_HARNESSES.size() + 1];
        int i = 0;
        for (String n : KNOWN_HARNESSES) {
            names[i++] = n + ".exe";
        }
        names[i] = "cursor-agent.cmd";
        return names;
    }

    /** First known harness found on PATH, or {@code null}. */
    private static String findFirstKnownOnPath() {
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            String found = findHarnessBinary(harness);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Returns the lowercase basename (with any {@code .exe} suffix stripped) of
     * the configured ACP server binary, or {@code null} when no binary is
     * configured (the pre-selection default). This is the same binary that {@link #buildWslArgs(String...)}
     * wraps on WSL, since both derive from {@link #findExecutablePathOrNull()}.
     */
    public static String resolveBinaryName() {
        String name = binaryNameFromPath(findExecutablePathOrNull());
        if (name != null) {
            return name;
        }
        // Fallback 1: stored harness ID from onboarding/options panel.
        String harnessId = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .get(PreferenceKeys.ACP_HARNESS_ID, null);
        if (isNotBlank(harnessId)) {
            HarnessCatalog.Harness h = HarnessCatalog.byId(harnessId);
            if (h != null && !h.binaryNames().isEmpty()) {
                return h.binaryNames().get(0);
            }
        }
        // Fallback 2: extract basename from the stored path directly.
        // Handles unknown/custom harnesses not in the catalog.
        String storedPath = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .get(PreferenceKeys.ACP_EXECUTABLE_PATH, null);
        return binaryNameFromPath(storedPath);
    }

    /**
     * Returns the display name for the configured harness (e.g. "Oh My Pi").
     * Used for tooltip rendering before the server handshake provides the
     * agent capabilities. Falls back to {@code null} when nothing is configured.
     */
    public static String resolveDisplayName() {
        String name = resolveBinaryName();
        if (name == null) {
            return null;
        }
        // Known catalog harness — use its display name.
        HarnessCatalog.Harness h = HarnessCatalog.byBinaryName(name);
        if (h != null) {
            return h.displayName();
        }
        // Stored display name from onboarding/options panel.
        String stored = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .get(PreferenceKeys.ACP_HARNESS_DISPLAY_NAME, null);
        if (isNotBlank(stored)) {
            return stored;
        }
        // Unknown harness — capitalize the binary name.
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /** Maps a resolved executable path to its lowercase binary name, stripping
     *  a {@code .exe} suffix (Windows), or {@code null} when no path is
     *  resolved (no harness configured — the pre-selection default). Windows-
     *  style backslash separators are normalized so basename extraction works
     *  on any OS. */
    public static String binaryNameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String name = new File(path.replace('\\', '/')).getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns {@code true} when the configured ACP binary is a PI harness
     * variant ({@code pi}, {@code pi-acp}, {@code pi-agent}). These harnesses
     * do not support auth tokens on MCP server URLs, so the embedded MCP
     * server runs without token auth for them; all other agents (opencode,
     * goose, ...) get token-protected URLs.
     */
    public static boolean isPiHarness() {
        String name = resolveBinaryName();
        return name != null && PI_HARNESS.contains(name);
    }

    /**
     * Resolves the agent executable path: checks configured path first,
     * then searches system PATH. Throws IllegalStateException if not found.
     */
    public static String resolveExecutablePath() {
        String found = findExecutablePathOrNull();
        if (found == null) {
            LOG.warn("Binary not found: no configured path and not on system PATH");
            throw new IllegalStateException(NbBundle.getMessage(BinaryResolver.class, "ERR_BinaryNotFound"));
        }
        return found;
    }

    /**
     * Non-throwing variant of {@link #resolveExecutablePath()}: returns the
     * resolved native agent executable path, or {@code null} if none is
     * found (configured path or system PATH).
     */
    public static String findExecutablePathOrNull() {
        Preferences nbPrefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String configuredPath = nbPrefs.get("acpExecutablePath", null);

        // 1. Configured absolute path (native binary that exists)
        if (isNotBlank(configuredPath)) {
            File f = new File(configuredPath);
            if (f.isAbsolute() && f.exists()) {
                LOG.fine("Using configured absolute path: {0}", configuredPath);
                return configuredPath;
            }
            // WSL-internal paths (e.g. /usr/local/bin/goose) never exist on
            // the Windows filesystem; accept them when WSL launching is enabled.
            if (configuredPath.startsWith("/") && isWslAvailable()) {
                LOG.fine("Using configured WSL-internal path: {0}", configuredPath);
                return configuredPath;
            }
            LOG.warn("Configured path not found: {0}", configuredPath);
        }

        // 2. Binaries found on PATH are never auto-used — the user must pick
        // one through the onboarding selection UI, which persists the choice
        // as the configured path above.
        return null;
    }

    /**
     * Searches the system PATH for the first known ACP harness binary.
     */
    public static String findOnPath() {
        return findFirstKnownOnPath();
    }

    /**
     * Returns every executable candidate for {@code exeName}, ordered like
     * {@code which -a}: harness-owned install directories first (e.g. Oh My Pi's
     * %LOCALAPPDATA%\omp, Kilo Code's {@code ~/.kilo/bin}), then candidates owned
     * by the IDE's user (macOS/Linux), then the rest in PATH order followed by
     * the well-known install directories common to all harnesses
     * ({@code ~/.local/bin}, the WinGet shim directory and the Chocolatey bin
     * directory), then the macOS Devin app bundle.
     *
     * <p>Dubious candidates are not returned at all: a hit inside a Linux system
     * package directory is excluded, because no catalog harness ships in a
     * distro package and a distro command may share the binary's name (Debian's
     * {@code kilo} text editor; a generic {@code agent}). This is what lets callers
     * pick the right binary when {@link #findOnPath(String)} alone would have
     * taken a same-named stranger.
     *
     * <p>Catalog names are extension-less ({@code goose}); the installed file
     * may be {@code goose.exe} / {@code goose.cmd} / {@code goose.bat}, so every
     * probed directory is also searched for those forms.
     */
    public static List<String> findAllOnPath(String exeName) {
        return findCandidates(exeName).stream().map(Candidate::path).toList();
    }

    /**
     * Returns the most trusted candidate for {@code exeName}: the first entry of
     * {@link #findAllOnPath(String)}, or {@code null} when nothing qualifies.
     */
    public static String findOnPath(String exeName) {
        List<Candidate> candidates = findCandidates(exeName);
        return candidates.isEmpty() ? null : candidates.get(0).path();
    }

    /**
     * All qualifying candidates, in probe order, with the harness-owned flag kept
     * for cross-name ranking. Package-visible for tests.
     */
    static List<Candidate> findCandidates(String exeName) {
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ProbeDir probe : probeDirs(exeName)) {
            String hit = probeDir(probe.dir(), exeName);
            if (hit == null) {
                continue;
            }
            if (isSystemPackageDir(probe.dir()) && !isTrustedSystemCandidate(hit)) {
                LOG.fine("Ignoring system package candidate {0} for {1}", hit, exeName);
                continue;
            }
            if (seen.add(canonicalPath(hit))) {
                candidates.add(new Candidate(hit, probe.harnessOwned(), isUserOwned(hit)));
            }
        }
        String mac = findInMacAppBundleLocations(exeName);
        if (mac != null && seen.add(canonicalPath(mac))) {
            candidates.add(new Candidate(mac, false, isUserOwned(mac)));
        }
        // Stable sort: harness-owned install dirs first, then files owned by the
        // IDE's user (a root-owned /usr/local install loses to ~/.local/bin),
        // otherwise probe order is kept.
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.harnessOwned() ? 0 : 1)
                .thenComparingInt(c -> c.userOwned() ? 0 : 1));
        return candidates;
    }

    /** One executable candidate: where it is, whether a harness installer owns
     *  its directory, and whether the IDE's user owns the file. */
    record Candidate(String path, boolean harnessOwned, boolean userOwned) { }

    /** A probed directory and whether the harness installer owns it. */
    private record ProbeDir(File dir, boolean harnessOwned) { }

    /** Probe directories in trust order: harness-owned, PATH, common install dirs. */
    private static List<ProbeDir> probeDirs(String exeName) {
        List<ProbeDir> dirs = new ArrayList<>();
        for (File dir : harnessInstallDirs(exeName)) {
            dirs.add(new ProbeDir(dir, true));
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : PATH_SPLIT.split(pathEnv)) {
                dirs.add(new ProbeDir(new File(dir), false));
            }
        }
        for (File dir : commonInstallDirs()) {
            dirs.add(new ProbeDir(dir, false));
        }
        return dirs;
    }

    /**
     * True when a candidate found in a system package directory is plausibly a
     * harness install rather than a distro command, so it is not silently lost:
     * the file is owned by the IDE's user, or it is a symlink into a node
     * package directory — a global {@code npm}/{@code bun} install that was run
     * as root writes {@code /usr/bin/<name>} as a link into
     * {@code .../node_modules/<package>/...}.
     *
     * <p>Distro packages ship either a regular root-owned file or a symlink
     * into {@code /usr/lib}, so Debian's {@code kilo} text editor and a generic
     * {@code agent} stay excluded and can never shadow a real harness.
     * Package-visible for tests.
     */
    static boolean isTrustedSystemCandidate(String path) {
        java.nio.file.Path file;
        try {
            file = Paths.get(path);
        } catch (RuntimeException e) { // InvalidPathException, or a null caller
            return false;
        }
        if (!Files.exists(file)) {
            return false;
        }
        if (isUserOwned(path)) {
            return true;
        }
        try {
            if (!Files.isSymbolicLink(file)) {
                return false;
            }
            String target = Files.readSymbolicLink(file).toString().replace('\\', '/');
            return target.contains("node_modules/");
        } catch (IOException | SecurityException e) {
            LOG.fine("Cannot inspect system candidate {0}: {1}", path, e.getMessage());
            return false;
        }
    }


    /**
     * True when the executable name is generic enough that another program can
     * own it system-wide: a same-named file exists in a system package directory.
     * Matches {@code kilo} (Debian/Ubuntu text editor) and {@code agent} (Cursor
     * CLI's binary name). Package-visible for tests.
     */
    static boolean isGenericBinaryName(String exeName) {
        for (String dir : SYSTEM_PACKAGE_DIRS) {
            if (probeDir(new File(dir), exeName) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a harness to its best binary path, or {@code null}. Candidates are
     * ranked so a same-named stranger never wins:
     * <ol>
     *   <li>a binary in a harness-owned install directory (best);</li>
     *   <li>a file owned by the same user the IDE runs as, before a root-owned
     *       install (macOS/Linux);</li>
     *   <li>a distinctive name ({@code kilocode}, {@code cursor-agent}) before a
     *       generic one ({@code kilo}, {@code agent}) that a distro command also
     *       owns.</li>
     * </ol>
     * Ties keep catalog name order, then probe order. Package-visible for tests.
     */
    static String findHarnessBinary(HarnessCatalog.Harness harness) {
        String bestPath = null;
        int bestRank = Integer.MAX_VALUE;
        for (String name : harness.binaryNames()) {
            boolean generic = isGenericBinaryName(name);
            for (Candidate candidate : findCandidates(name)) {
                int rank = candidateRank(candidate, generic);
                if (rank < bestRank) {
                    bestRank = rank;
                    bestPath = candidate.path();
                }
            }
        }
        return bestPath;
    }

    /**
     * Rank of a candidate for cross-name selection: harness-owned dirs beat
     * everything, then same-user files beat files owned by another user
     * (e.g. a root-owned {@code /usr/local/bin} install), then distinctive names
     * beat generic ones. Package-visible for tests.
     */
    static int candidateRank(Candidate candidate, boolean genericName) {
        int rank = candidate.harnessOwned() ? 0 : (candidate.userOwned() ? 2 : 4);
        return rank + (genericName ? 1 : 0);
    }

    /** True on platforms where a file owner is meaningful for this check. */
    private static boolean ownerCheckSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return !os.contains("win");
    }

    /**
     * True when {@code path} is owned by the same user the IDE runs as, compared
     * by uid on macOS/Linux. Returns {@code true} when ownership cannot be
     * determined (Windows, filesystems without POSIX owners, access denied) so an
     * unknown owner never disqualifies a real harness. Package-visible for tests.
     */
    static boolean isUserOwned(String path) {
        if (!ownerCheckSupported()) {
            return true;
        }
        try {
            Object fileUid = Files.getAttribute(Paths.get(path), "unix:uid");
            int ideUid = ideUid();
            return !(fileUid instanceof Number) || ideUid < 0 || ((Number) fileUid).intValue() == ideUid;
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            LOG.fine("Owner check unavailable for {0}: {1}", path, e.getMessage());
            return true;
        }
    }

    /** UID of the user the IDE runs as, from the home directory it was started
     *  with; {@code -1} when unknown. */
    private static int ideUid() {
        String home = System.getProperty("user.home");
        if (isBlank(home)) {
            return -1;
        }
        try {
            Object uid = Files.getAttribute(Paths.get(home), "unix:uid");
            return uid instanceof Number ? ((Number) uid).intValue() : -1;
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            LOG.fine("Cannot determine IDE uid from {0}: {1}", home, e.getMessage());
            return -1;
        }
    }

    /**
     * True when {@code dir} is a Linux system package directory, the one place a
     * distro command is installed and no harness installer writes.
     * Package-visible for tests.
     */
    static boolean isSystemPackageDir(File dir) {
        return dir != null && SYSTEM_PACKAGE_DIRS.contains(canonicalDir(dir));
    }

    /**
     * True when {@code path} (an executable, not a directory) lives in a Linux
     * system package directory. Package-visible for tests.
     */
    static boolean isSystemPackagePath(String path) {
        return isSystemPackageDir(new File(path).getParentFile());
    }

    /** Canonical form of a probe directory; falls back to a normalized absolute
     *  path when the directory cannot be resolved (it may not exist yet). */
    private static String canonicalDir(File dir) {
        if (dir == null) {
            return "";
        }
        try {
            return dir.getCanonicalPath().replace('\\', '/');
        } catch (IOException e) {
            return dir.getAbsoluteFile().toPath().normalize().toString().replace('\\', '/');
        }
    }

    /** Canonical form of a candidate file, for de-duplication. */
    private static String canonicalPath(String path) {
        return canonicalDir(new File(path));
    }

    /**
     * Well-known install directories common to all harnesses, in probe order:
     * {@code ~/.local/bin}, %LOCALAPPDATA%\Microsoft\WinGet\Links and
     * %ProgramData%\chocolatey\bin. Windows-only entries resolve from the
     * environment variables and are skipped when unset.
     */
    private static List<File> commonInstallDirs() {
        List<File> dirs = new ArrayList<>();
        String userHome = System.getProperty("user.home");
        if (isNotBlank(userHome)) {
            dirs.add(new File(userHome, ".local" + File.separator + "bin"));
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (isNotBlank(localAppData)) {
            dirs.add(new File(localAppData, "Microsoft" + File.separator + "WinGet" + File.separator + "Links"));
        }
        String programData = System.getenv("ProgramData");
        if (isNotBlank(programData)) {
            dirs.add(new File(programData, "chocolatey" + File.separator + "bin"));
        }
        return dirs;
    }

    /**
     * Harness-specific install directories, in probe order, from the catalog's
     * {@code windowsInstallSubdirs}. Each entry starts with {@code %LOCALAPPDATA%}
     * or {@code %USERHOME%}, which is expanded from the environment
     * variable/user property before use.
     */
    private static List<File> harnessInstallDirs(String exeName) {
        HarnessCatalog.Harness h = HarnessCatalog.byBinaryName(exeName.toLowerCase(Locale.ROOT));
        List<String> subdirs = h.windowsInstallSubDirs();
        if (subdirs.isEmpty()) {
            return List.of();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        String userHome = System.getProperty("user.home");
        List<File> dirs = new ArrayList<>();
        for (String subdir : subdirs) {
            String resolved;
            if (subdir.startsWith("%LOCALAPPDATA%")) {
                if (isBlank(localAppData)) {
                    continue;
                }
                resolved = localAppData + subdir.substring("%LOCALAPPDATA%".length());
            } else if (subdir.startsWith("%USERHOME%")) {
                if (isBlank(userHome)) {
                    continue;
                }
                resolved = userHome + subdir.substring("%USERHOME%".length());
            } else {
                resolved = subdir;
            }
            dirs.add(new File(resolved));
        }
        return dirs;
    }

    /**
     * Returns the absolute path of {@code exeName} inside {@code dir}, also
     * accepting the Windows {@code .exe} / {@code .cmd} extensions of an
     * extension-less catalog name. Returns {@code null} when nothing matches.
     */
    private static String probeDir(File dir, String exeName) {
        for (String name : executableNameVariants(exeName)) {
            File candidate = new File(dir, name);
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Checks well-known macOS app bundle locations for the executable.
     * Currently checks for Devin CLI inside the Devin.app bundle.
     */
    private static String findInMacAppBundleLocations(String exeName) {
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        if (!isMac) {
            return null;
        }
        // Devin CLI is inside the Devin.app bundle
        if ("devin".equals(exeName)) {
            File devinBin = new File("/Applications/Devin.app/Contents/Resources/app/extensions/windsurf/devin/bin/devin");
            if (devinBin.exists() && devinBin.canExecute()) {
                LOG.info("Found devin in Devin.app bundle: {0}", devinBin.getAbsolutePath());
                return devinBin.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * The bare catalog name plus every executable form we may find it under:
     * {@code .exe}, {@code .cmd} and {@code .bat} (WinGet, Chocolatey and
     * {@code ~/.local/bin} install with the extension while catalog names
     * carry none). Tried in every probed location, on every OS.
     */
    private static List<String> executableNameVariants(String exeName) {
        if (exeName.endsWith(".exe") || exeName.endsWith(".cmd") || exeName.endsWith(".bat")) {
            return List.of(exeName);
        }
        return List.of(exeName, exeName + ".exe", exeName + ".cmd", exeName + ".bat");
    }

    /**
     * Returns true if an agent binary is explicitly configured (a native path
     * that exists, or a WSL-internal path while WSL launching is enabled).
     * Binaries merely found on PATH do not count — the user must select one
     * through the onboarding UI first. Unlike resolveExecutablePath(), this
     * does not throw.
     */
    public static boolean isAvailable() {
        return findExecutablePathOrNull() != null;
    }

    /** One harness binary found on this system: the catalog id, the binary
     *  basename, and the absolute path (native or WSL-internal). */
    public record FoundBinary(String harnessId, String displayName, String path) { }

    /**
     * Detects every catalog harness available on this system.
     *
     * <p>Scans the native PATH (plus well-known Windows install dirs) for each
     * harness's launch binaries. When WSL is available and enabled, the WSL
     * distribution is additionally probed (in a single batched {@code wsl.exe}
     * call) so Linux-side installs are detected too; WSL paths are reported
     * when the native PATH has no hit for that harness.
     *
     * <p>Runs blocking filesystem/probe I/O — call from a background thread.
     *
     * @return one entry per detected harness, in catalog order; empty when none
     */
    public static List<FoundBinary> findAllKnownOnPath() {
        List<FoundBinary> found = new ArrayList<>();
        for (HarnessCatalog.Harness harness : HarnessCatalog.ALL) {
            String nativePath = findHarnessBinary(harness);
            if (nativePath != null) {
                found.add(new FoundBinary(harness.id(), harness.displayName(), nativePath));
                continue;
            }
            if (isWslAvailable()) {
                String wslPath = findFirstKnownOnWslPath(harness.binaryNames());
                if (wslPath != null) {
                    found.add(new FoundBinary(harness.id(), harness.displayName(), wslPath));
                }
            }
        }
        return found;
    }

    /**
     * Probes the WSL distribution for the best of the given binary names, using
     * a single {@code wsl.exe} invocation. Applies the same policy as native
     * detection — a distinctive name beats a generic one ({@code kilocode} over
     * a distro {@code kilo}, {@code cursor-agent} over a generic {@code agent}),
     * and catalog name order breaks ties — with one deliberate difference: every
     * hit inside a Linux system package directory is ignored, because file
     * ownership and symlink targets cannot be inspected from the host without a
     * second round trip, so {@link #isTrustedSystemCandidate(String)} cannot
     * vouch for it.
     *
     * @param names bare Linux binary names, in preference order
     * @return the Linux path of the best binary found, or {@code null}
     */
    static String findFirstKnownOnWslPath(List<String> names) {
        try {
            List<String> cmd = new ArrayList<>(List.of(
                    "wsl.exe", "-e", "bash", "-lc",
                    "for n in \"$@\"; do p=$(command -v \"$n\" 2>/dev/null) && "
                            + "{ printf 'hit:%s=%s\\n' \"$n\" \"$p\"; }; "
                            + "for d in /usr/bin /bin /usr/sbin /sbin; do "
                            + "[ -x \"$d/$n\" ] && { printf 'sys:%s\\n' \"$n\"; break; }; done; done",
                    "--"));
            cmd.addAll(names);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try {
                java.util.Map<String, String> hits = new java.util.LinkedHashMap<>();
                Set<String> generic = new HashSet<>();
                try (var reader = proc.inputReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("sys:")) {
                            generic.add(line.substring("sys:".length()).trim());
                            continue;
                        }
                        int eq = line.indexOf('=');
                        if (eq > "hit:".length() && line.startsWith("hit:")) {
                            hits.putIfAbsent(line.substring("hit:".length(), eq).trim(),
                                    line.substring(eq + 1).trim());
                        }
                    }
                }
                proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                String bestPath = null;
                int bestRank = Integer.MAX_VALUE;
                for (String name : names) {
                    String p = hits.get(name);
                    if (p == null || p.isBlank() || isSystemPackagePath(p)) {
                        continue;
                    }
                    int rank = generic.contains(name) ? 2 : 1;
                    if (rank < bestRank) {
                        bestRank = rank;
                        bestPath = p;
                    }
                }
                return bestPath;
            } finally {
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                }
            }
        } catch (Exception e) {
            LOG.fine("WSL batch probe failed: {0}", e.getMessage());
        }
        return null;
    }

    /**
     * Returns {@code true} if WSL ({@code wsl.exe}) is available on this
     * Windows system and the user has not disabled WSL usage in the options.
     * Always returns {@code false} on non-Windows platforms.
     */
    public static boolean isWslAvailable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) {
            return false;
        }
        boolean enabled = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .getBoolean(PreferenceKeys.USE_WSL, false);
        if (!enabled) {
            return false;
        }
        return findOnPath("wsl.exe") != null;
    }

    /**
     * Probes inside the WSL distribution for the given executable.
     *
     * Runs {@code wsl.exe -e bash -lc "which <exeName>"} and returns the
     * Linux path (e.g. {@code /usr/local/bin/opencode}) if found, or
     * {@code null} if the binary is not on the distro's PATH.
     *
     * @param exeName bare Linux binary name (e.g. {@code "opencode"})
     */
    public static String findOnWslPath(String exeName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "wsl.exe", "-e", "bash", "-lc",
                    "exec which \"$1\" 2>/dev/null", "--", exeName);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try {
                String result;
                try (var reader = proc.inputReader()) {
                    result = reader.readLine();
                }
                proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                if (result != null && !result.isBlank() && proc.exitValue() == 0
                        && !isSystemPackagePath(result.strip())) {
                    return result.strip();
                }
            } finally {
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                }
            }
        } catch (Exception e) {
            LOG.fine("WSL probe failed for {0}: {1}", exeName, e.getMessage());
        }
        return null;
    }

    /**
     * Builds the command-line arguments for a WSL-wrapped agent invocation.
     *
     * <p>When a Windows {@code opencode.exe} is resolvable (Windows-hosted),
     * its path is translated to a WSL mount path (e.g. {@code C:\tools\opencode.exe}
     * -&gt; {@code /mnt/c/tools/opencode.exe}). When opencode is installed inside
     * WSL (no Windows binary), the bare {@code opencode} command is used so the
     * WSL-native binary on the Linux PATH is invoked.
     *
     * <p>The command and every argument are passed as separate argv tokens —
     * nothing is interpolated into the shell script — so the user-editable
     * process arguments cannot inject shell commands. The fixed script
     * {@code exec "$0" "$@"} runs the resolved binary with its args verbatim.
     *
     * @param argTokens the arguments to pass to the agent, already split into
     *                  individual tokens (e.g. {@code {"acp"}} or
     *                  {@code {"stats","--days","7"}}); never interpolated
     * @return {@code ["wsl.exe","-e","bash","-lc","exec \"$0\" \"$@\"", innerExe, arg...]}
     */
    public static String[] buildWslArgs(String... argTokens) {
        String innerExe = wslInnerCommand();
        String[] cmd = new String[5 + 1 + argTokens.length];
        cmd[0] = "wsl.exe";
        cmd[1] = "-e";
        cmd[2] = "bash";
        cmd[3] = "-lc";
        // Fixed shell script: positional $0 = innerExe, $@ = the arg tokens.
        // The tokens are delivered as bash argv, not parsed as a command, so
        // shell metacharacters in user arguments have no effect.
        cmd[4] = "exec \"$0\" \"$@\"";
        cmd[5] = innerExe;
        System.arraycopy(argTokens, 0, cmd, 6, argTokens.length);
        return cmd;
    }

    /**
     * Splits a raw process-arguments string into safe argv tokens using the
     * same quoting rules as the non-WSL launch path. Use this when the
     * arguments come from user-editable preferences as a single string.
     */
    public static String[] tokenizeArgs(String args) {
        if (isBlank(args)) {
            return new String[0];
        }
        // Reuse commons-exec's quoting rules (same as the non-WSL launch path)
        // so a single user-supplied string is split into safe argv tokens.
        CommandLine cl =
                new CommandLine("dummy").addArguments(args, true);
        String[] all = cl.toStrings();
        // toStrings() includes the program ("dummy") as element 0; drop it.
        String[] tokens = new String[all.length - 1];
        System.arraycopy(all, 1, tokens, 0, tokens.length);
        return tokens;
    }

    /**
     * Returns {@code true} when WSL is in use and the agent is hosted as a
     * Windows binary (an {@code opencode.exe} is resolvable on Windows),
     * rather than installed natively inside the WSL distribution.
     */
    public static boolean isWindowsHostedOpencode() {
        return isWslAvailable() && findExecutablePathOrNull() != null;
    }

    /** Returns the inner command used inside the WSL {@code bash -lc} wrapper. */
    private static String wslInnerCommand() {
        // A configured WSL-internal path (e.g. /usr/local/bin/goose) does not
        // exist on the Windows filesystem, so findExecutablePathOrNull() skips
        // it — honor it here so non-default harnesses launch inside WSL.
        Preferences nbPrefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String configuredPath = nbPrefs.get("acpExecutablePath", null);
        if (isNotBlank(configuredPath) && configuredPath.startsWith("/")) {
            return configuredPath;
        }
        String nativeExe = findExecutablePathOrNull();
        if (nativeExe != null) {
            return toWslPath(nativeExe);
        }
        // Check all known harness binaries inside WSL — no hardcoded fallback.
        String wslPath = findFirstKnownOnWslPath(KNOWN_HARNESSES);
        if (wslPath != null) {
            return wslPath;
        }
        // Nothing found; return first known name so the WSL command still
        // runs — the server start will surface the "not found" error.
        return KNOWN_HARNESSES.get(0);
    }

    /**
     * Translates a Windows absolute path (e.g. {@code C:\\Users\\foo\\opencode.exe})
     * to its WSL mount path ({@code /mnt/c/Users/foo/opencode.exe}). Passes
     * through non-Windows or already-Linux paths unchanged.
     */
    public static String toWslPath(String path) {
        if (isBlank(path)) {
            return path;
        }
        String p = path.replace('\\', '/');
        if (p.length() >= 2 && Character.isLetter(p.charAt(0)) && p.charAt(1) == ':') {
            char drive = Character.toLowerCase(p.charAt(0));
            p = "/mnt/" + drive + p.substring(2);
        }
        return p;
    }

    /**
     * Checks whether the given command name exists and is executable on the system PATH.
     */
    public static boolean isInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (isBlank(pathEnv)) {
            return false;
        }
        for (String p : PATH_SPLIT.split(pathEnv)) {
            File f = new File(p, command);
            if (f.exists() && f.canExecute()) {
                return true;
            }
        }
        return false;
    }
}
