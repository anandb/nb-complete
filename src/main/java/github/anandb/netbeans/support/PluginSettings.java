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
    private static final String KEY_MAX_MESSAGES = PreferenceKeys.MAX_MESSAGES;
    private static final int DEFAULT_SESSION_IDLE_TIMEOUT = 300;
    private static final int DEFAULT_MAX_MESSAGES = 100;
    private static final int DEFAULT_TOOLBAR_ICON_SIZE = 32;
    private static final String DEFAULT_PREAMBLE;

    /** Cached session idle timeout in seconds — volatile for cross-thread visibility. */
    private static volatile int cachedSessionIdleTimeout = DEFAULT_SESSION_IDLE_TIMEOUT;
    /** Cached max visible message bubbles — volatile for cross-thread visibility. */
    private static volatile int cachedMaxMessages = DEFAULT_MAX_MESSAGES;
    /** Cached toolbar icon size — volatile for cross-thread visibility. */
    private static volatile int cachedToolbarIconSize = DEFAULT_TOOLBAR_ICON_SIZE;

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
        cachedMaxMessages = prefs.getInt(KEY_MAX_MESSAGES, DEFAULT_MAX_MESSAGES);
        cachedToolbarIconSize = prefs.getInt(PreferenceKeys.TOOLBAR_ICON_SIZE, DEFAULT_TOOLBAR_ICON_SIZE);
        prefs.addPreferenceChangeListener(listener);
    }

    private PluginSettings() {
    }

    /** Returns the default preamble loaded from the bundled resources. */
    public static String getDefaultPreamble() {
        return DEFAULT_PREAMBLE;
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
        } else if (KEY_MAX_MESSAGES.equals(evt.getKey())) {
            try {
                if (evt.getNewValue() == null || evt.getNewValue().isBlank()) {
                    cachedMaxMessages = DEFAULT_MAX_MESSAGES;
                } else {
                    int v = Integer.parseInt(evt.getNewValue());
                    cachedMaxMessages = (v < 0) ? DEFAULT_MAX_MESSAGES : v;
                }
            } catch (NumberFormatException e) {
                cachedMaxMessages = DEFAULT_MAX_MESSAGES;
            }
        } else if (PreferenceKeys.TOOLBAR_ICON_SIZE.equals(evt.getKey())) {
            try {
                int v = Integer.parseInt(evt.getNewValue());
                if (v == 16 || v == 24 || v == 28 || v == 32 || v == 48) {
                    cachedToolbarIconSize = v;
                }
            } catch (NumberFormatException e) {
                cachedToolbarIconSize = DEFAULT_TOOLBAR_ICON_SIZE;
            }
        }
    }

    /** Max visible message bubbles before older ones are trimmed. 0 = unlimited. */
    public static int getMaxMessages() {
        return cachedMaxMessages;
    }

    public static void setMaxMessages(int count) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(KEY_MAX_MESSAGES, count);
    }

    /** Toolbar icon size: 24 (small), 28 (medium), 32 (large). */
    public static int getToolbarIconSize() {
        return cachedToolbarIconSize;
    }

    public static void setToolbarIconSize(int size) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(PreferenceKeys.TOOLBAR_ICON_SIZE, size);
    }
}
