package github.anandb.netbeans.support;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;

/**
 * Persists each Beanbot Tasks repository's tasks-file (todo.txt) path +
 * display name in this module's preference node.
 *
 * <p>Because it lives in the module node ({@code io.github.anandb.beanbot}),
 * {@link PreferencesMigrator} copies it across NetBeans user directories on
 * upgrade. Only <em>paths/metadata</em> migrate — the external todo.txt files
 * themselves are user-maintained and never copied. This mirrors (as a
 * fallback) the framework-persisted {@code RepositoryInfo}, which also holds
 * the tasks-file path.</p>
 */
public final class TasksMetadata {

    private static final String KEY_PREFIX = "tasksRepo.";

    private TasksMetadata() {
    }

    private static Preferences prefs() {
        // Module preference node -> config/Preferences/io/github/anandb/beanbot,
        // which PreferencesMigrator copies across NetBeans user directories.
        return NbPreferences.forModule(TasksMetadata.class);
    }

    private static String sanitize(String repoId) {
        return repoId == null ? "" : repoId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static void put(String repoId, String tasksPath, String displayName) {
        String k = KEY_PREFIX + sanitize(repoId);
        Preferences p = prefs();
        p.put(k + ".tasksPath", tasksPath == null ? "" : tasksPath);
        p.put(k + ".displayName", displayName == null ? "" : displayName);
    }

    public static String tasksPathOf(String repoId) {
        return prefs().get(KEY_PREFIX + sanitize(repoId) + ".tasksPath", "");
    }

    public static String displayNameOf(String repoId) {
        return prefs().get(KEY_PREFIX + sanitize(repoId) + ".displayName", "");
    }

    public static void remove(String repoId) {
        String k = KEY_PREFIX + sanitize(repoId);
        Preferences p = prefs();
        p.remove(k + ".tasksPath");
        p.remove(k + ".displayName");
    }

    /** All repository ids with saved metadata, sorted for deterministic order. */
    public static List<String> allIds() {
        try {
            String[] keys = prefs().keys();
            List<String> ids = new ArrayList<>();
            for (String key : keys) {
                if (key.startsWith(KEY_PREFIX) && key.endsWith(".tasksPath")) {
                    ids.add(key.substring(KEY_PREFIX.length(),
                            key.length() - ".tasksPath".length()));
                }
            }
            ids.sort(String::compareTo);
            return ids;
        } catch (BackingStoreException ex) {
            return List.of();
        }
    }
}