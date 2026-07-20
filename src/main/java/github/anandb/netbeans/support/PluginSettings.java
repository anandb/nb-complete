package github.anandb.netbeans.support;

import org.apache.commons.lang3.exception.ExceptionUtils;
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
    private static final int DEFAULT_SESSION_IDLE_TIMEOUT = 600;
    private static final int DEFAULT_MAX_MESSAGES = 100;
    private static final int DEFAULT_TOOLBAR_ICON_SIZE = 24;
    private static final String DEFAULT_PREAMBLE;

    /** Cached session idle timeout in seconds — volatile for cross-thread visibility. */
    private static volatile int cachedSessionIdleTimeout = DEFAULT_SESSION_IDLE_TIMEOUT;
    /** Cached max visible message bubbles — volatile for cross-thread visibility. */
    private static volatile int cachedMaxMessages = DEFAULT_MAX_MESSAGES;
    /** Cached toolbar icon size — volatile for cross-thread visibility. */
    private static volatile int cachedToolbarIconSize = DEFAULT_TOOLBAR_ICON_SIZE;
    /** Cached chat font size — volatile for cross-thread visibility. -1 = inherited. */
    private static volatile int cachedChatFontSize = -1;
    /** Cached actions toggles — volatile for cross-thread visibility. All default to true. */
    private static volatile boolean cachedSortLinesEnabled = true;
    private static volatile boolean cachedStashDiffEnabled = true;
    private static volatile boolean cachedQuickJumpEnabled = true;

    private static final PreferenceChangeListener listener = PluginSettings::onPreferenceChanged;

    static {
        String preamble = "";
        try (InputStream in = PluginSettings.class.getResourceAsStream("preamble.md")) {
            if (in != null) {
                preamble = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load preamble.md: {0}", ExceptionUtils.getMessage(e));
        }
        DEFAULT_PREAMBLE = preamble;

        // Seed cached value and register listener
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        cachedSessionIdleTimeout = prefs.getInt(KEY_SESSION_IDLE_TIMEOUT, DEFAULT_SESSION_IDLE_TIMEOUT);
        cachedMaxMessages = prefs.getInt(KEY_MAX_MESSAGES, DEFAULT_MAX_MESSAGES);
        cachedToolbarIconSize = prefs.getInt(PreferenceKeys.TOOLBAR_ICON_SIZE, DEFAULT_TOOLBAR_ICON_SIZE);
        cachedChatFontSize = prefs.getInt(PreferenceKeys.CHAT_FONT_SIZE, -1);
        cachedSortLinesEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_SORT_LINES, true);
        cachedStashDiffEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_STASH_DIFF, true);
        cachedQuickJumpEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_QUICK_JUMP, true);
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
                } else {
                    cachedToolbarIconSize = DEFAULT_TOOLBAR_ICON_SIZE;
                }
            } catch (NumberFormatException e) {
                cachedToolbarIconSize = DEFAULT_TOOLBAR_ICON_SIZE;
            }
        } else if (PreferenceKeys.CHAT_FONT_SIZE.equals(evt.getKey())) {
            try {
                int v = Integer.parseInt(evt.getNewValue());
                cachedChatFontSize = (v < 0) ? -1 : v;
            } catch (NumberFormatException e) {
                cachedChatFontSize = -1;
            }
        } else if (PreferenceKeys.ACTIONS_SORT_LINES.equals(evt.getKey())) {
            cachedSortLinesEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.ACTIONS_STASH_DIFF.equals(evt.getKey())) {
            cachedStashDiffEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.ACTIONS_QUICK_JUMP.equals(evt.getKey())) {
            cachedQuickJumpEnabled = Boolean.parseBoolean(evt.getNewValue());
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

    /**
     * Chat font size in px. Returns -1 if inherited from theme.
     * The effective size is computed as: (size == -1) ? ThemeManager.getFont().getSize() - 2 : size.
     */
    public static int getChatFontSize() {
        return cachedChatFontSize;
    }

    public static void setChatFontSize(int size) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(PreferenceKeys.CHAT_FONT_SIZE, size);
    }

    public static void setToolbarIconSize(int size) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(PreferenceKeys.TOOLBAR_ICON_SIZE, size);
    }

    /** Whether sort lines (ascending/descending) and minify JSON actions are enabled. */
    public static boolean isSortLinesEnabled() {
        return cachedSortLinesEnabled;
    }

    public static void setSortLinesEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_SORT_LINES, enabled);
    }

    /** Whether the Stash Diff toolbar button and action are enabled. */
    public static boolean isStashDiffEnabled() {
        return cachedStashDiffEnabled;
    }

    public static void setStashDiffEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_STASH_DIFF, enabled);
    }

    /** Whether the Quick Jump (Go To File) action is enabled. */
    public static boolean isQuickJumpEnabled() {
        return cachedQuickJumpEnabled;
    }

    public static void setQuickJumpEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_QUICK_JUMP, enabled);
    }
}
