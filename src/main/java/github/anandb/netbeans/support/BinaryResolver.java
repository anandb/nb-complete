package github.anandb.netbeans.support;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        for (String name : nativeHarnessNames()) {
            String found = findOnPath(name);
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
        return binaryNameFromPath(findExecutablePathOrNull());
    }

    /** Maps a resolved executable path to its lowercase binary name, stripping
     *  a {@code .exe} suffix (Windows), or {@code null} when no path is
     *  resolved (no harness configured — the pre-selection default). Windows-
     *  style backslash separators are normalized so basename extraction works
     *  on any OS. Package-private for tests. */
    static String binaryNameFromPath(String path) {
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
     * Searches the system PATH for the given executable name, falling back to
     * the Windows WinGet shim directory if not found there.
     */
    public static String findOnPath(String exeName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : PATH_SPLIT.split(pathEnv)) {
                File f = new File(dir, exeName);
                if (f.exists() && f.canExecute()) {
                    return f.getAbsolutePath();
                }
            }
        }
        return findInWellKnownWindowsLocations(exeName);
    }

    /**
     * Checks well-known Windows install directories for the executable:
     * the WinGet shim directory (%LOCALAPPDATA%\Microsoft\WinGet\Links) and
     * the Chocolatey bin directory (%ProgramData%\chocolatey\bin), both of
     * which are often not on PATH.
     */
    private static String findInWellKnownWindowsLocations(String exeName) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (!isWindows) {
            return null;
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (isNotBlank(localAppData)) {
            File winGet = new File(localAppData, "Microsoft" + File.separator + "WinGet" + File.separator
                    + "Links" + File.separator + exeName);
            if (winGet.exists() && winGet.canExecute()) {
                LOG.info("Found opencode in WinGet Links: {0}", winGet.getAbsolutePath());
                return winGet.getAbsolutePath();
            }
        }
        String programData = System.getenv("ProgramData");
        if (isNotBlank(programData)) {
            File choco = new File(programData, "chocolatey" + File.separator + "bin" + File.separator + exeName);
            if (choco.exists() && choco.canExecute()) {
                LOG.info("Found opencode in Chocolatey bin: {0}", choco.getAbsolutePath());
                return choco.getAbsolutePath();
            }
        }
        return null;
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
            String nativePath = null;
            for (String name : harness.binaryNames()) {
                String p = findOnPath(name);
                if (p != null) {
                    nativePath = p;
                    break;
                }
            }
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
     * Probes the WSL distribution for the first of the given binary names
     * that exists, using a single {@code wsl.exe} invocation.
     *
     * @param names bare Linux binary names, in preference order
     * @return the Linux path of the first binary found, or {@code null}
     */
    static String findFirstKnownOnWslPath(List<String> names) {
        try {
            List<String> cmd = new ArrayList<>(List.of(
                    "wsl.exe", "-e", "bash", "-lc",
                    "for n in \"$@\"; do p=$(command -v \"$n\" 2>/dev/null) && "
                            + "{ printf '%s=%s\\n' \"$n\" \"$p\"; }; done",
                    "--"));
            cmd.addAll(names);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try {
                java.util.Map<String, String> hits = new java.util.LinkedHashMap<>();
                try (var reader = proc.inputReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            hits.putIfAbsent(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                        }
                    }
                }
                proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                for (String name : names) {
                    String p = hits.get(name);
                    if (p != null && !p.isBlank()) {
                        return p;
                    }
                }
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
                if (result != null && !result.isBlank() && proc.exitValue() == 0) {
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
        // The agent is a Linux binary installed inside WSL; use its bare command.
        return "opencode";
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
