package github.anandb.netbeans.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Migrates this plugin's preferences from a previous NetBeans user directory
 * into the current one.
 *
 * <p>On the first launch of a new IDE version NetBeans runs its built-in
 * user-directory migration, which copies most user data forward. That copy only
 * includes preference files for modules already installed in the new IDE at
 * migration time. Because this plugin is typically imported into the new IDE
 * <em>after</em> the upgrade, its data file
 * ({@code config/Preferences/io/github/anandb/beanbot.properties}, which also
 * holds the cached session titles, hidden flags, usage, pinned messages and
 * input history) is left behind in the previous user directory.</p>
 *
 * <p>On startup {@link #migrateIfNeeded()} copies that file from the most
 * recently used previous NetBeans user directory (a sibling of the current one)
 * when the current user directory does not already contain plugin data.</p>
 */
public final class PreferencesMigrator {

    private static final Logger LOG = Logger.from(PreferencesMigrator.class);

    /** Relative path, under {@code config/Preferences}, of this module's preference node. */
    static final String MODULE_PREFS_RELATIVE_PATH = "io/github/anandb/beanbot";

    private PreferencesMigrator() {
    }

    /**
     * Copies this plugin's preferences into the current user directory from the
     * most recent previous NetBeans user directory that has them, but only when
     * the current user directory does not yet contain them. Safe to call on
     * every startup; it is a no-op unless a migration is actually required.
     */
    public static void migrateIfNeeded() {
        File currentUserDir = new File(System.getProperty("netbeans.user", ""));
        File currentPrefs = preferencesFile(currentUserDir);
        if (currentPrefs.exists()) {
            return; // plugin data already present in this user directory
        }
        File source = findSourcePreferences(currentUserDir);
        if (source == null) {
            LOG.fine("No previous NetBeans user directory with plugin data found; skipping migration");
            return;
        }
        try {
            copyRecursively(source, currentPrefs);
            LOG.info("Migrated plugin preferences from previous user directory: {0}", source.getPath());
        } catch (IOException ex) {
            LOG.warn("Failed to migrate plugin preferences from {0}: {1}", source.getPath(), ex);
        }
    }

    /** The plugin preference file location inside a NetBeans user directory. */
    static File preferencesFile(File userDir) {
        return new File(new File(userDir, "config" + File.separator + "Preferences"), MODULE_PREFS_RELATIVE_PATH);
    }

    /**
     * Locates the plugin preference file in the previous NetBeans user directory
     * (a sibling of the current one), or {@code null} if no such file exists.
     * Selection prefers the highest versioned user directory older than the
     * current one (e.g. for a current {@code 31} it prefers {@code 31-rc2} over
     * {@code 30}), falling back to the most recently modified when versions
     * cannot be compared.
     */
    static File findSourcePreferences(File currentUserDir) {
        File root = userDirRoot(currentUserDir);
        if (root == null || !root.isDirectory()) {
            return null;
        }
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        long currentRank = versionRank(currentUserDir.getName());
        File best = null;
        long bestRank = Long.MIN_VALUE;
        for (File child : children) {
            if (child.equals(currentUserDir)) {
                continue;
            }
            File candidate = preferencesFile(child);
            if (!candidate.exists()) {
                continue;
            }
            long rank = versionRank(child.getName());
            if (currentRank != Long.MIN_VALUE && rank != Long.MIN_VALUE && rank > currentRank) {
                continue; // a user directory newer than the current IDE
            }
            if (best == null || rank > bestRank
                    || (rank == bestRank && candidate.lastModified() > best.lastModified())) {
                best = candidate;
                bestRank = rank;
            }
        }
        return best;
    }

    /**
     * Returns a comparable rank for a versioned NetBeans user directory name
     * such as {@code 30}, {@code 31-rc2} or {@code 31}, where stable versions
     * outrank their RCs ({@code 31-rc1} &lt; {@code 31-rc2} &lt; {@code 31}).
     * Returns {@link Long#MIN_VALUE} when the name is not version-like.
     */
    static long versionRank(String name) {
        if (name == null || name.isEmpty()) {
            return Long.MIN_VALUE;
        }
        int i = 0;
        while (i < name.length() && Character.isDigit(name.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return Long.MIN_VALUE;
        }
        long major;
        try {
            major = Long.parseLong(name.substring(0, i));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
        String rest = name.substring(i);
        if (rest.isEmpty()) {
            return major * 1000 + 999; // stable release
        }
        if (rest.startsWith("-rc")) {
            String rc = rest.substring(3);
            try {
                return major * 1000 + 100 + (rc.isEmpty() ? 0 : Long.parseLong(rc));
            } catch (NumberFormatException e) {
                return major * 1000 + 100;
            }
        }
        return Long.MIN_VALUE; // unrecognised suffix — not version-like
    }

    /**
     * The directory holding the versioned NetBeans user directories. Prefers
     * the {@code netbeans.default_userdir_root} property and falls back to the
     * parent of the current user directory.
     */
    static File userDirRoot(File currentUserDir) {
        String root = System.getProperty("netbeans.default_userdir_root");
        if (root != null && !root.isBlank()) {
            return new File(root);
        }
        return currentUserDir == null ? null : currentUserDir.getParentFile();
    }

    /** Recursively copies {@code source} (file or directory) to {@code target}. */
    static void copyRecursively(File source, File target) throws IOException {
        if (source.isDirectory()) {
            Files.createDirectories(target.toPath());
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursively(child, new File(target, child.getName()));
                }
            }
        } else {
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
