package github.anandb.netbeans.support;

import java.io.File;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class BinaryResolver {

    private static final Logger LOG = Logger.from(BinaryResolver.class);
    private static final Pattern PATH_SPLIT = Pattern.compile(Pattern.quote(File.pathSeparator));

    private BinaryResolver() {}

    /**
     * Resolves the opencode executable path: checks configured path first,
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
     * resolved native opencode executable path, or {@code null} if none is
     * found (configured path or system PATH).
     */
    public static String findExecutablePathOrNull() {
        Preferences nbPrefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String configuredPath = nbPrefs.get("acpExecutablePath", null);
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exeName = isWindows ? "opencode.exe" : "opencode";

        // 1. Configured absolute path
        if (isNotBlank(configuredPath)) {
            File f = new File(configuredPath);
            if (f.isAbsolute() && f.exists()) {
                LOG.fine("Using configured absolute path: {0}", configuredPath);
                return configuredPath;
            } else {
                LOG.warn("Configured path not found: {0}", configuredPath);
            }
        }

        // 2. Search system PATH
        return findOnPath(exeName);
    }

    /**
     * Searches the system PATH for the default opencode binary.
     */
    public static String findOnPath() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return findOnPath(isWindows ? "opencode.exe" : "opencode");
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
     * Returns true if the opencode binary is available (either configured or on PATH).
     * Unlike resolveExecutablePath(), this does not throw.
     */
    public static boolean isAvailable() {
        Preferences nbPrefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String configuredPath = nbPrefs.get("acpExecutablePath", null);
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exeName = isWindows ? "opencode.exe" : "opencode";

        // 1. Check configured absolute path
        if (isNotBlank(configuredPath)) {
            File f = new File(configuredPath);
            if (f.isAbsolute() && f.exists()) {
                return true;
            }
        }

        // 2. Search system PATH
        return findOnPath(exeName) != null;
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
     * Builds the command-line arguments for a WSL-wrapped opencode invocation.
     *
     * <p>When a Windows {@code opencode.exe} is resolvable (Windows-hosted),
     * its path is translated to a WSL mount path (e.g. {@code C:\tools\opencode.exe}
     * -&gt; {@code /mnt/c/tools/opencode.exe}). When opencode is installed inside
     * WSL (no Windows binary), the bare {@code opencode} command is used so the
     * WSL-native binary on the Linux PATH is invoked.
     *
     * @param args the arguments to pass to opencode (e.g. {@code "acp"})
     * @return {@code ["bash", "-lc", "&lt;command&gt; &lt;args&gt;"]}
     */
    public static String[] buildWslArgs(String args) {
        String innerExe = wslInnerCommand();
        return new String[]{"bash", "-lc", innerExe + " " + args};
    }

    /**
     * Returns {@code true} when WSL is in use and opencode is hosted as a
     * Windows binary (an {@code opencode.exe} is resolvable on Windows),
     * rather than installed natively inside the WSL distribution.
     */
    public static boolean isWindowsHostedOpencode() {
        return isWslAvailable() && findExecutablePathOrNull() != null;
    }

    /** Returns the inner command used inside the WSL {@code bash -lc} wrapper. */
    private static String wslInnerCommand() {
        String nativeExe = findExecutablePathOrNull();
        if (nativeExe != null) {
            return toWslPath(nativeExe);
        }
        // opencode is a Linux binary installed inside WSL; use its bare command.
        return "opencode";
    }

    /**
     * Translates a Windows absolute path (e.g. {@code C:\\Users\\foo\\opencode.exe})
     * to its WSL mount path ({@code /mnt/c/Users/foo/opencode.exe}). Passes
     * through non-Windows or already-Linux paths unchanged.
     */
    static String toWslPath(String path) {
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
