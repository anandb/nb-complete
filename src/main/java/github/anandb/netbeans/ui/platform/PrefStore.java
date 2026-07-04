package github.anandb.netbeans.ui.platform;

/**
 * Access seam for {@code NbPreferences.forModule(...)} keyed by the module
 * anchor ({@link github.anandb.netbeans.support.PreferenceKeys#MODULE_ANCHOR}).
 * Hides the NetBeans preferences API from DSL-bound views.
 * <p>
 * <b>Hot-path contract (AGENTS.md):</b> preference values read in hot paths
 * must be cached in a {@code static volatile} field with a
 * {@code PreferenceChangeListener}; callers must NOT call {@code getBoolean}
 * on every invocation. Implementations are responsible for honoring this; the
 * interface itself is just the access surface.
 * <p>
 * Swing-free.
 */
public interface PrefStore {
    boolean getBoolean(String key, boolean def);
    void putBoolean(String key, boolean v);
    int getInt(String key, int def);
    void putInt(String key, int v);
    String get(String key, String def);
    void put(String key, String v);
}
