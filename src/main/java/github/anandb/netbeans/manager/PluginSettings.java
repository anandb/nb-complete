package github.anandb.netbeans.manager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;
import org.openide.util.NbPreferences;

public final class PluginSettings {

    private static final Logger LOG = Logger.from(PluginSettings.class);
    private static final String KEY_PREAMBLE = "preamble";
    private static final String KEY_CUSTOM_USER_ICON = "customUserIcon";
    private static final String KEY_SESSION_IDLE_TIMEOUT = "sessionIdleTimeout";
    private static final String DEFAULT_PREAMBLE;

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
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).getInt(KEY_SESSION_IDLE_TIMEOUT, 60);
    }

    public static void setSessionIdleTimeout(int seconds) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(KEY_SESSION_IDLE_TIMEOUT, seconds);
    }
}
