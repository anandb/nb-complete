package github.anandb.netbeans.support;

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;

public final class PluginSettings {

    private static final Logger LOG = Logger.from(PluginSettings.class);
    private static final String KEY_PREAMBLE = "preamble";
    public static final String KEY_CUSTOM_USER_ICON = "customUserIcon";
    private static final String KEY_SESSION_IDLE_TIMEOUT = "sessionIdleTimeout";
    private static final String KEY_MAX_MESSAGES = PreferenceKeys.MAX_MESSAGES;
    private static final int DEFAULT_SESSION_IDLE_TIMEOUT = 600;
    private static final int DEFAULT_MAX_MESSAGES = 100;
    private static final int DEFAULT_TOOLBAR_ICON_SIZE = 24;
    private static final String DEFAULT_PREAMBLE;
    private static final String DEFAULT_CRITICAL_RULES;
    private static final String DEFAULT_WSL_RULES;
    private static final String DEFAULT_WSL_NATIVE_RULES;

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
    private static volatile boolean cachedCompactJsonEnabled = true;
    private static volatile boolean cachedSearchWebEnabled = true;
    private static volatile boolean cachedShowAnnotationsEnabled = true;
    private static volatile boolean cachedViewFileHistoryEnabled = true;
    private static volatile boolean cachedStashDiffEnabled = true;
    private static volatile boolean cachedQuickJumpEnabled = true;
    private static volatile boolean cachedAutoBackupChanges = true;
    /** Cached mini-assistant toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedMiniAssistantEnabled = true;

    private static final PreferenceChangeListener listener = PluginSettings::onPreferenceChanged;

    static {
        DEFAULT_PREAMBLE = nullToEmpty(AgentUtils.readResource("preamble.md"));
        DEFAULT_CRITICAL_RULES = nullToEmpty(AgentUtils.readResource("critical_rules.md"));
        DEFAULT_WSL_RULES = nullToEmpty(AgentUtils.readResource("wsl_rules.md"));
        DEFAULT_WSL_NATIVE_RULES = nullToEmpty(AgentUtils.readResource("wsl_native_rules.md"));

        // Seed cached value and register listener
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        cachedSessionIdleTimeout = prefs.getInt(KEY_SESSION_IDLE_TIMEOUT, DEFAULT_SESSION_IDLE_TIMEOUT);
        cachedMaxMessages = prefs.getInt(KEY_MAX_MESSAGES, DEFAULT_MAX_MESSAGES);
        cachedToolbarIconSize = prefs.getInt(PreferenceKeys.TOOLBAR_ICON_SIZE, DEFAULT_TOOLBAR_ICON_SIZE);
        cachedChatFontSize = prefs.getInt(PreferenceKeys.CHAT_FONT_SIZE, -1);
        cachedSortLinesEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_SORT_LINES, true);
        cachedCompactJsonEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_COMPACT_JSON, true);
        cachedSearchWebEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_SEARCH_WEB, true);
        cachedShowAnnotationsEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_SHOW_ANNOTATIONS, true);
        cachedViewFileHistoryEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_VIEW_FILE_HISTORY, true);
        cachedStashDiffEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_STASH_DIFF, true);
        cachedQuickJumpEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_QUICK_JUMP, true);
        cachedAutoBackupChanges = prefs.getBoolean(PreferenceKeys.AUTO_BACKUP_CHANGES, true);
        cachedMiniAssistantEnabled = prefs.getBoolean(PreferenceKeys.MINI_ASSISTANT_ENABLED, true);
        prefs.addPreferenceChangeListener(listener);
    }

    private PluginSettings() {
    }

    /** Returns the default preamble loaded from the bundled resources. */
    public static String getDefaultPreamble() {
        return DEFAULT_PREAMBLE;
    }

    /** Returns the critical rules loaded from the bundled resources, never editable by the user.
     *  When running through WSL, WSL-specific guidance is appended so the agent can use the
     *  Linux environment. The rule set differs by install mode: when opencode is a Windows
     *  binary hosted through WSL ({@code wsl_rules.md}) native Windows tooling is preferred;
     *  when opencode is a native WSL binary ({@code wsl_native_rules.md}) Linux is preferred. */
    public static String getCriticalRules() {
        String rules = DEFAULT_CRITICAL_RULES;
        if (BinaryResolver.isWslAvailable() && !rules.contains("WSL Environment")) {
            String wslBlock = BinaryResolver.isWindowsHostedOpencode()
                    ? DEFAULT_WSL_RULES : DEFAULT_WSL_NATIVE_RULES;
            if (!wslBlock.isEmpty()) {
                rules = rules + "\n\n" + wslBlock;
            }
        }
        return rules;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
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
        } else if (PreferenceKeys.ACTIONS_COMPACT_JSON.equals(evt.getKey())) {
            cachedCompactJsonEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.ACTIONS_SEARCH_WEB.equals(evt.getKey())) {
            cachedSearchWebEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.ACTIONS_SHOW_ANNOTATIONS.equals(evt.getKey())) {
            cachedShowAnnotationsEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.ACTIONS_VIEW_FILE_HISTORY.equals(evt.getKey())) {
            cachedViewFileHistoryEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.ACTIONS_STASH_DIFF.equals(evt.getKey())) {
            cachedStashDiffEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.ACTIONS_QUICK_JUMP.equals(evt.getKey())) {
            cachedQuickJumpEnabled = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.AUTO_BACKUP_CHANGES.equals(evt.getKey())) {
            cachedAutoBackupChanges = Boolean.parseBoolean(evt.getNewValue());
        } else if (PreferenceKeys.MINI_ASSISTANT_ENABLED.equals(evt.getKey())) {
            cachedMiniAssistantEnabled = evt.getNewValue() == null || Boolean.parseBoolean(evt.getNewValue());
        }
    }

    /** Whether auto backup of uncommitted changes is enabled on turn start. */
    public static boolean isAutoBackupChanges() {
        return cachedAutoBackupChanges;
    }

    public static void setAutoBackupChanges(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.AUTO_BACKUP_CHANGES, enabled);
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

    /** Whether the Sort Lines editor context menu actions are enabled. */
    public static boolean isSortLinesEnabled() {
        return cachedSortLinesEnabled;
    }

    public static void setSortLinesEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_SORT_LINES, enabled);
    }

    /** Whether the Minify JSON editor context menu action is enabled. */
    public static boolean isCompactJsonEnabled() {
        return cachedCompactJsonEnabled;
    }

    public static void setCompactJsonEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_COMPACT_JSON, enabled);
    }

    /** Whether the Search Web editor context menu action is enabled. */
    public static boolean isSearchWebEnabled() {
        return cachedSearchWebEnabled;
    }

    public static void setSearchWebEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_SEARCH_WEB, enabled);
    }

    /** Whether the Toggle Annotations editor context menu action is enabled. */
    public static boolean isShowAnnotationsEnabled() {
        return cachedShowAnnotationsEnabled;
    }

    public static void setShowAnnotationsEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_SHOW_ANNOTATIONS, enabled);
    }

    /** Whether the View File History editor context menu action is enabled. */
    public static boolean isViewFileHistoryEnabled() {
        return cachedViewFileHistoryEnabled;
    }

    public static void setViewFileHistoryEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_VIEW_FILE_HISTORY, enabled);
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

    /** Whether the Mini Assistant dialog is the target for send/ask-to-assistant actions. */
    public static boolean isMiniAssistantEnabled() {
        return cachedMiniAssistantEnabled;
    }

    public static void setMiniAssistantEnabled(boolean enabled) {
        cachedMiniAssistantEnabled = enabled;
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.MINI_ASSISTANT_ENABLED, enabled);
    }
}
