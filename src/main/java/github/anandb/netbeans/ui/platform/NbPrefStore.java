package github.anandb.netbeans.ui.platform;

import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;

import github.anandb.netbeans.support.PreferenceKeys;

/**
 * {@link PrefStore} backed by {@code NbPreferences.forModule(MODULE_ANCHOR)}.
 * Used by {@link DefaultPlatformBridge} and as the fallback in
 * {@link PlatformBridge#prefStoreSafe()} when the bridge is not registered.
 */
final class NbPrefStore implements PrefStore {

    static final NbPrefStore INSTANCE = new NbPrefStore();

    private NbPrefStore() {}

    private static Preferences prefs() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
    }

    @Override public boolean getBoolean(String key, boolean def) { return prefs().getBoolean(key, def); }
    @Override public void putBoolean(String key, boolean v) { prefs().putBoolean(key, v); }
    @Override public int getInt(String key, int def) { return prefs().getInt(key, def); }
    @Override public void putInt(String key, int v) { prefs().putInt(key, v); }
    @Override public String get(String key, String def) { return prefs().get(key, def); }
    @Override public void put(String key, String v) { prefs().put(key, v); }

    @Override
    public void addChangeListener(PreferenceChangeListener listener) {
        prefs().addPreferenceChangeListener(listener);
    }

    @Override
    public void removeChangeListener(PreferenceChangeListener listener) {
        prefs().removePreferenceChangeListener(listener);
    }
}
