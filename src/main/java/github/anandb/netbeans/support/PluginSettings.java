package github.anandb.netbeans.support;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;

public final class PluginSettings {

    private static final Logger LOG = Logger.from(PluginSettings.class);
    private static final String KEY_PREAMBLE = "preamble";
    private static final String KEY_CUSTOM_USER_ICON = "customUserIcon";
    private static final String KEY_SESSION_IDLE_TIMEOUT = "sessionIdleTimeout";
    private static final int DEFAULT_SESSION_IDLE_TIMEOUT = 300;
    private static final String DEFAULT_PREAMBLE;

    /** Cached session idle timeout in seconds — volatile for cross-thread visibility. */
    private static volatile int cachedSessionIdleTimeout = DEFAULT_SESSION_IDLE_TIMEOUT;

    private static final PreferenceChangeListener listener = PluginSettings::onPreferenceChanged;

    static {
        String preamble = "";
        try (InputStream in = PluginSettings.class.getResourceAsStream("preamble.md")) {
            if (in != null) {
                preamble = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load preamble.md: {0}", e.getMessage());
        }
        DEFAULT_PREAMBLE = preamble;

        // Seed cached value and register listener
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        cachedSessionIdleTimeout = prefs.getInt(KEY_SESSION_IDLE_TIMEOUT, DEFAULT_SESSION_IDLE_TIMEOUT);
        prefs.addPreferenceChangeListener(listener);
    }

    private PluginSettings() {
    }

    public static String getPreamble() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(KEY_PREAMBLE, DEFAULT_PREAMBLE);
    }

    public static void setPreamble(String preamble) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(KEY_PREAMBLE, preamble == null ? "" : preamble);
    }

    public static String getCustomUserIcon() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(KEY_CUSTOM_USER_ICON, "");
    }

    public static void setCustomUserIcon(String path) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(KEY_CUSTOM_USER_ICON, path == null ? "" : path);
    }

    public static int getSessionIdleTimeout() {
        return cachedSessionIdleTimeout;
    }

    public static void setSessionIdleTimeout(int seconds) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(KEY_SESSION_IDLE_TIMEOUT, seconds);
    }

    private static void onPreferenceChanged(PreferenceChangeEvent evt) {
        if (KEY_SESSION_IDLE_TIMEOUT.equals(evt.getKey())) {
            try {
                cachedSessionIdleTimeout = Integer.parseInt(evt.getNewValue());
            } catch (NumberFormatException e) {
                cachedSessionIdleTimeout = DEFAULT_SESSION_IDLE_TIMEOUT;
            }
        }
    }
}
