package github.anandb.netbeans.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import org.openide.util.NbPreferences;

import github.anandb.netbeans.model.HarnessCatalog;
import github.anandb.netbeans.model.Harness;

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
    /** One {@code Preferences} value is capped at 8192 chars, and the preamble is a single value. */
    public static final int MAX_PREAMBLE_LENGTH = Preferences.MAX_VALUE_LENGTH;
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
    private static volatile boolean cachedCopyAllItemsEnabled = true;
    private static volatile boolean cachedAutoBackupChanges = true;
    /** Cached mini-assistant toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedMiniAssistantEnabled = true;
    /** Cached task-repository toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedTaskRepositoryEnabled = true;
    /** Cached preamble toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedPreambleEnabled = true;
    /** Cached MCP server toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedMcpServerEnabled = true;
    /** Cached internal MCP server port. 0 = pick a random free port. */
    private static volatile int cachedMcpServerPort = 0;
    /** Cached markdown project type toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedMarkdownProjectEnabled = true;
    /**
     * Cached diff-font toggle, read once at startup by {@code IdeDiffFontHook} — volatile for
     * cross-thread visibility. Defaults to true.
     */
    private static volatile boolean cachedDiffIdeFont = true;
    /** Bubble colours (hex) and chat font family; empty means the theme / system property. */
    private static volatile String cachedUserBubbleLight = "";
    private static volatile String cachedUserBubbleDark = "";
    private static volatile String cachedAssistantBubbleLight = "";
    private static volatile String cachedAssistantBubbleDark = "";
    private static volatile String cachedChatFontFamily = "";
    /** Cached show hidden sessions toggle — volatile for cross-thread visibility. Defaults to false. */
    private static volatile boolean cachedShowHiddenSessions = false;
    /** Cached keep older messages toggle — volatile for cross-thread visibility. Defaults to false. */
    private static volatile boolean cachedKeepOlderMessages = false;
    /** Cached echo user input toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedEchoUserInput = true;
    /** Cached combine tool+thought toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedCombineToolThought = true;
    /** Cached check for updates toggle — volatile for cross-thread visibility. Defaults to true. */
    private static volatile boolean cachedCheckForUpdates = true;
    /** Cached use WSL toggle — volatile for cross-thread visibility. Defaults to false. */
    private static volatile boolean cachedUseWsl = false;
    /**
     * Effective project-switch rebind toggle, captured at startup. Changing the
     * option requires an IDE restart, so the preference listener deliberately
     * does not update it; volatile for cross-thread visibility. Defaults to false.
     */
    private static volatile boolean projectSwitchRestartEnabled = false;
    /** Cached ACP file-access policy; read per fs request, so it must be a cheap volatile read. */
    private static volatile AcpFsPolicy cachedAcpFsPolicy = AcpFsPolicy.DEFAULT;
    /** Raw {@code run_command} allowlist text; parsed by {@link RunCommandPolicy}. */
    private static volatile String cachedRunCommandAllowlist = "";
    /** Raw {@code run_command} denylist text; parsed by {@link RunCommandPolicy}. */
    private static volatile String cachedRunCommandDenylist = "";

    private static final PreferenceChangeListener listener = PluginSettings::onPreferenceChanged;

    /** Overlay text per catalog harness id; each resource is read from the classpath once. */
    private static final Map<String, String> HARNESS_OVERLAYS = new ConcurrentHashMap<>();

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
        cachedCopyAllItemsEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_COPY_ALL_ITEMS, true);
        cachedAutoBackupChanges = prefs.getBoolean(PreferenceKeys.AUTO_BACKUP_CHANGES, true);
        cachedMiniAssistantEnabled = prefs.getBoolean(PreferenceKeys.MINI_ASSISTANT_ENABLED, true);
        cachedTaskRepositoryEnabled = prefs.getBoolean(PreferenceKeys.TASK_REPOSITORY_ENABLED, true);
        cachedPreambleEnabled = prefs.getBoolean(PreferenceKeys.PREAMBLE_ENABLED, true);
        cachedMcpServerEnabled = prefs.getBoolean(PreferenceKeys.MCP_SERVER_ENABLED, true);
        cachedMcpServerPort = prefs.getInt(PreferenceKeys.MCP_SERVER_PORT, 0);
        cachedMarkdownProjectEnabled = prefs.getBoolean(PreferenceKeys.ACTIONS_MARKDOWN_PROJECT, true);
        cachedDiffIdeFont = prefs.getBoolean(PreferenceKeys.DIFF_IDE_FONT, false);
        cachedUserBubbleLight = prefs.get(PreferenceKeys.USER_BUBBLE_LIGHT, "");
        cachedUserBubbleDark = prefs.get(PreferenceKeys.USER_BUBBLE_DARK, "");
        cachedAssistantBubbleLight = prefs.get(PreferenceKeys.ASSISTANT_BUBBLE_LIGHT, "");
        cachedAssistantBubbleDark = prefs.get(PreferenceKeys.ASSISTANT_BUBBLE_DARK, "");
        cachedChatFontFamily = prefs.get(PreferenceKeys.CHAT_FONT_FAMILY, "");
        cachedShowHiddenSessions = prefs.getBoolean(PreferenceKeys.SHOW_HIDDEN_SESSIONS, false);
        cachedKeepOlderMessages = prefs.getBoolean(PreferenceKeys.KEEP_OLDER_MESSAGES, false);
        cachedEchoUserInput = prefs.getBoolean(PreferenceKeys.ECHO_USER_INPUT, true);
        cachedCombineToolThought = prefs.getBoolean(PreferenceKeys.COMBINE_TOOL_THOUGHT, true);
        cachedCheckForUpdates = prefs.getBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true);
        cachedUseWsl = prefs.getBoolean(PreferenceKeys.USE_WSL, false);
        projectSwitchRestartEnabled =
                prefs.getBoolean(PreferenceKeys.PROJECT_SWITCH_RESTART_ENABLED, false);
        cachedAcpFsPolicy = AcpFsPolicy.fromStoredValue(
                prefs.get(PreferenceKeys.ACP_FS_POLICY, AcpFsPolicy.DEFAULT.storedValue()));
        cachedRunCommandAllowlist = prefs.get(PreferenceKeys.RUN_COMMAND_ALLOWLIST, "");
        cachedRunCommandDenylist = prefs.get(PreferenceKeys.RUN_COMMAND_DENYLIST, "");
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
     *  Linux environment. The rule set differs by install mode: when the agent is a Windows
     *  binary hosted through WSL ({@code wsl_rules.md}) native Windows tooling is preferred;
     *  when it is a native WSL binary ({@code wsl_native_rules.md}) Linux is preferred.
     *  Harness-specific tool/permission mechanics live in {@code critical_rules/<harnessId>.md}
     *  and are appended when the selected harness has such an overlay. */
    public static String getCriticalRules() {
        String rules = DEFAULT_CRITICAL_RULES;
        if (BinaryResolver.isWslAvailable() && !rules.contains("WSL Environment")) {
            String wslBlock = BinaryResolver.isWindowsHostedOpencode()
                    ? DEFAULT_WSL_RULES : DEFAULT_WSL_NATIVE_RULES;
            if (!wslBlock.isEmpty()) {
                rules = rules + "\n\n" + wslBlock;
            }
        }
        String overlay = harnessRulesOverlay();
        if (!overlay.isEmpty()) {
            rules = rules + "\n\n" + overlay;
        }
        return rules;
    }

    /**
     * Overlay of the selected harness, or "" when the harness is unknown or has
     * no {@code critical_rules/<id>.md} resource. The preference is read live so
     * a harness switch applies immediately; only the classpath read is cached,
     * since that is the expensive part.
     */
    private static String harnessRulesOverlay() {
        Harness harness = HarnessCatalog.byId(
                NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                        .get(PreferenceKeys.ACP_HARNESS_ID, null));
        if (harness == HarnessCatalog.UNKNOWN) {
            return "";
        }
        return HARNESS_OVERLAYS.computeIfAbsent(harness.id(),
                id -> nullToEmpty(AgentUtils.readResource("critical_rules/" + id + ".md")));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String getPreamble() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).get(KEY_PREAMBLE, DEFAULT_PREAMBLE);
    }

    /** @return whether {@code preamble} can be stored as one preference value. */
    public static boolean isPreambleTooLong(String preamble) {
        return preamble != null && preamble.length() > MAX_PREAMBLE_LENGTH;
    }

    public static void setPreamble(String preamble) {
        String text = preamble == null ? "" : preamble;
        if (text.length() > MAX_PREAMBLE_LENGTH) {
            // The whole preamble is one preference value; storing it silently
            // throws IllegalArgumentException from Preferences.put.
            throw new IllegalArgumentException("Session preamble is " + text.length()
                    + " characters; the maximum is " + MAX_PREAMBLE_LENGTH);
        }
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(KEY_PREAMBLE, text);
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
        cachedSessionIdleTimeout = seconds;
    }

    /** Clamp shared by the listener and {@link #setMaxMessages(int)}. */
    private static int normalizeMaxMessages(int value) {
        return value < 0 ? DEFAULT_MAX_MESSAGES : value;
    }

    /** Accepted sizes shared by the listener and {@link #setToolbarIconSize(int)}. */
    private static int normalizeToolbarIconSize(int value) {
        return (value == 16 || value == 24 || value == 28 || value == 32 || value == 48)
                ? value : DEFAULT_TOOLBAR_ICON_SIZE;
    }

    /** Any negative size means "inherited from the theme". */
    private static int normalizeChatFontSize(int value) {
        return value < 0 ? -1 : value;
    }

    private static Preferences prefs() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
    }

    /** Guarded int read: a malformed stored value must fall back, not kill the dispatcher. */
    private static int intPref(String key, int fallback) {
        try {
            return prefs().getInt(key, fallback);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static void onPreferenceChanged(PreferenceChangeEvent evt) {
        // Re-read the stored value instead of trusting the event payload: events are
        // delivered asynchronously on the JDK's preference dispatcher, so a queued event
        // can arrive after a newer write and would otherwise revert the cache.
        if (KEY_SESSION_IDLE_TIMEOUT.equals(evt.getKey())) {
            cachedSessionIdleTimeout = intPref(KEY_SESSION_IDLE_TIMEOUT, DEFAULT_SESSION_IDLE_TIMEOUT);
        } else if (KEY_MAX_MESSAGES.equals(evt.getKey())) {
            cachedMaxMessages = normalizeMaxMessages(intPref(KEY_MAX_MESSAGES, DEFAULT_MAX_MESSAGES));
        } else if (PreferenceKeys.TOOLBAR_ICON_SIZE.equals(evt.getKey())) {
            cachedToolbarIconSize = normalizeToolbarIconSize(
                    intPref(PreferenceKeys.TOOLBAR_ICON_SIZE, DEFAULT_TOOLBAR_ICON_SIZE));
        } else if (PreferenceKeys.CHAT_FONT_SIZE.equals(evt.getKey())) {
            cachedChatFontSize = normalizeChatFontSize(intPref(PreferenceKeys.CHAT_FONT_SIZE, -1));
        } else if (PreferenceKeys.ACTIONS_SORT_LINES.equals(evt.getKey())) {
            cachedSortLinesEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_SORT_LINES, true);
        } else if (PreferenceKeys.ACTIONS_COMPACT_JSON.equals(evt.getKey())) {
            cachedCompactJsonEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_COMPACT_JSON, true);
        } else if (PreferenceKeys.ACTIONS_SEARCH_WEB.equals(evt.getKey())) {
            cachedSearchWebEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_SEARCH_WEB, true);
        } else if (PreferenceKeys.ACTIONS_SHOW_ANNOTATIONS.equals(evt.getKey())) {
            cachedShowAnnotationsEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_SHOW_ANNOTATIONS, true);
        } else if (PreferenceKeys.ACTIONS_VIEW_FILE_HISTORY.equals(evt.getKey())) {
            cachedViewFileHistoryEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_VIEW_FILE_HISTORY, true);
        } else if (PreferenceKeys.ACTIONS_STASH_DIFF.equals(evt.getKey())) {
            cachedStashDiffEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_STASH_DIFF, true);
        } else if (PreferenceKeys.ACTIONS_QUICK_JUMP.equals(evt.getKey())) {
            cachedQuickJumpEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_QUICK_JUMP, true);
        } else if (PreferenceKeys.ACTIONS_COPY_ALL_ITEMS.equals(evt.getKey())) {
            cachedCopyAllItemsEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_COPY_ALL_ITEMS, true);
        } else if (PreferenceKeys.AUTO_BACKUP_CHANGES.equals(evt.getKey())) {
            cachedAutoBackupChanges = prefs().getBoolean(PreferenceKeys.AUTO_BACKUP_CHANGES, true);
        } else if (PreferenceKeys.MINI_ASSISTANT_ENABLED.equals(evt.getKey())) {
            cachedMiniAssistantEnabled = prefs().getBoolean(PreferenceKeys.MINI_ASSISTANT_ENABLED, true);
        } else if (PreferenceKeys.TASK_REPOSITORY_ENABLED.equals(evt.getKey())) {
            cachedTaskRepositoryEnabled = prefs().getBoolean(PreferenceKeys.TASK_REPOSITORY_ENABLED, true);
        } else if (PreferenceKeys.ACP_FS_POLICY.equals(evt.getKey())) {
            cachedAcpFsPolicy = AcpFsPolicy.fromStoredValue(
                    prefs().get(PreferenceKeys.ACP_FS_POLICY, AcpFsPolicy.DEFAULT.storedValue()));
        } else if (PreferenceKeys.RUN_COMMAND_ALLOWLIST.equals(evt.getKey())) {
            cachedRunCommandAllowlist = prefs().get(PreferenceKeys.RUN_COMMAND_ALLOWLIST, "");
        } else if (PreferenceKeys.RUN_COMMAND_DENYLIST.equals(evt.getKey())) {
            cachedRunCommandDenylist = prefs().get(PreferenceKeys.RUN_COMMAND_DENYLIST, "");
        } else if (PreferenceKeys.PREAMBLE_ENABLED.equals(evt.getKey())) {
            cachedPreambleEnabled = prefs().getBoolean(PreferenceKeys.PREAMBLE_ENABLED, true);
        } else if (PreferenceKeys.MCP_SERVER_ENABLED.equals(evt.getKey())) {
            cachedMcpServerEnabled = prefs().getBoolean(PreferenceKeys.MCP_SERVER_ENABLED, true);
        } else if (PreferenceKeys.MCP_SERVER_PORT.equals(evt.getKey())) {
            cachedMcpServerPort = intPref(PreferenceKeys.MCP_SERVER_PORT, 0);
        } else if (PreferenceKeys.ACTIONS_MARKDOWN_PROJECT.equals(evt.getKey())) {
            cachedMarkdownProjectEnabled = prefs().getBoolean(PreferenceKeys.ACTIONS_MARKDOWN_PROJECT, true);
        } else if (PreferenceKeys.DIFF_IDE_FONT.equals(evt.getKey())) {
            cachedDiffIdeFont = prefs().getBoolean(PreferenceKeys.DIFF_IDE_FONT, false);
        } else if (PreferenceKeys.USER_BUBBLE_LIGHT.equals(evt.getKey())) {
            cachedUserBubbleLight = prefs().get(PreferenceKeys.USER_BUBBLE_LIGHT, "");
        } else if (PreferenceKeys.USER_BUBBLE_DARK.equals(evt.getKey())) {
            cachedUserBubbleDark = prefs().get(PreferenceKeys.USER_BUBBLE_DARK, "");
        } else if (PreferenceKeys.ASSISTANT_BUBBLE_LIGHT.equals(evt.getKey())) {
            cachedAssistantBubbleLight = prefs().get(PreferenceKeys.ASSISTANT_BUBBLE_LIGHT, "");
        } else if (PreferenceKeys.ASSISTANT_BUBBLE_DARK.equals(evt.getKey())) {
            cachedAssistantBubbleDark = prefs().get(PreferenceKeys.ASSISTANT_BUBBLE_DARK, "");
        } else if (PreferenceKeys.CHAT_FONT_FAMILY.equals(evt.getKey())) {
            cachedChatFontFamily = prefs().get(PreferenceKeys.CHAT_FONT_FAMILY, "");
        } else if (PreferenceKeys.SHOW_HIDDEN_SESSIONS.equals(evt.getKey())) {
            cachedShowHiddenSessions = prefs().getBoolean(PreferenceKeys.SHOW_HIDDEN_SESSIONS, false);
        } else if (PreferenceKeys.KEEP_OLDER_MESSAGES.equals(evt.getKey())) {
            cachedKeepOlderMessages = prefs().getBoolean(PreferenceKeys.KEEP_OLDER_MESSAGES, false);
        } else if (PreferenceKeys.ECHO_USER_INPUT.equals(evt.getKey())) {
            cachedEchoUserInput = prefs().getBoolean(PreferenceKeys.ECHO_USER_INPUT, true);
        } else if (PreferenceKeys.COMBINE_TOOL_THOUGHT.equals(evt.getKey())) {
            cachedCombineToolThought = prefs().getBoolean(PreferenceKeys.COMBINE_TOOL_THOUGHT, true);
        } else if (PreferenceKeys.CHECK_FOR_UPDATES.equals(evt.getKey())) {
            cachedCheckForUpdates = prefs().getBoolean(PreferenceKeys.CHECK_FOR_UPDATES, true);
        } else if (PreferenceKeys.USE_WSL.equals(evt.getKey())) {
            cachedUseWsl = prefs().getBoolean(PreferenceKeys.USE_WSL, false);
        }
        // PROJECT_SWITCH_RESTART_ENABLED is intentionally absent: it is read once
        // at startup, so a change takes effect only after an IDE restart.
    }

    /** Whether auto backup of uncommitted changes is enabled on turn start. */
    public static boolean isAutoBackupChanges() {
        return cachedAutoBackupChanges;
    }

    public static void setAutoBackupChanges(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.AUTO_BACKUP_CHANGES, enabled);
        cachedAutoBackupChanges = enabled;
    }

    /** Max visible message bubbles before older ones are trimmed. 0 = unlimited. */
    public static int getMaxMessages() {
        return cachedMaxMessages;
    }

    public static void setMaxMessages(int count) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(KEY_MAX_MESSAGES, count);
        cachedMaxMessages = normalizeMaxMessages(count);
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
        cachedChatFontSize = normalizeChatFontSize(size);
    }

    public static void setToolbarIconSize(int size) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putInt(PreferenceKeys.TOOLBAR_ICON_SIZE, size);
        cachedToolbarIconSize = normalizeToolbarIconSize(size);
    }

    /** Whether the Sort Lines editor context menu actions are enabled. */
    public static boolean isSortLinesEnabled() {
        return cachedSortLinesEnabled;
    }

    public static void setSortLinesEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_SORT_LINES, enabled);
        cachedSortLinesEnabled = enabled;
    }

    /** Whether the Minify JSON editor context menu action is enabled. */
    public static boolean isCompactJsonEnabled() {
        return cachedCompactJsonEnabled;
    }

    public static void setCompactJsonEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_COMPACT_JSON, enabled);
        cachedCompactJsonEnabled = enabled;
    }

    /** Whether the Search Web editor context menu action is enabled. */
    public static boolean isSearchWebEnabled() {
        return cachedSearchWebEnabled;
    }

    public static void setSearchWebEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_SEARCH_WEB, enabled);
        cachedSearchWebEnabled = enabled;
    }

    /** Whether the Toggle Annotations editor context menu action is enabled. */
    public static boolean isShowAnnotationsEnabled() {
        return cachedShowAnnotationsEnabled;
    }

    public static void setShowAnnotationsEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_SHOW_ANNOTATIONS, enabled);
        cachedShowAnnotationsEnabled = enabled;
    }

    /** Whether the View File History editor context menu action is enabled. */
    public static boolean isViewFileHistoryEnabled() {
        return cachedViewFileHistoryEnabled;
    }

    public static void setViewFileHistoryEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_VIEW_FILE_HISTORY, enabled);
        cachedViewFileHistoryEnabled = enabled;
    }

    /** Whether the Stash Diff toolbar button and action are enabled. */
    public static boolean isStashDiffEnabled() {
        return cachedStashDiffEnabled;
    }

    public static void setStashDiffEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_STASH_DIFF, enabled);
        cachedStashDiffEnabled = enabled;
    }

    /** Whether the Quick Jump (Go To File) action is enabled. */
    public static boolean isQuickJumpEnabled() {
        return cachedQuickJumpEnabled;
    }

    public static void setQuickJumpEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).putBoolean(PreferenceKeys.ACTIONS_QUICK_JUMP, enabled);
        cachedQuickJumpEnabled = enabled;
    }

    /** Whether the NetBeans Action Items window has its Copy All toolbar button. */
    public static boolean isCopyAllItemsEnabled() {
        return cachedCopyAllItemsEnabled;
    }

    public static void setCopyAllItemsEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.ACTIONS_COPY_ALL_ITEMS, enabled);
        cachedCopyAllItemsEnabled = enabled;
    }

    /** Whether the Mini Assistant dialog is the target for send/ask-to-assistant actions. */
    public static boolean isMiniAssistantEnabled() {
        return cachedMiniAssistantEnabled;
    }

    public static void setMiniAssistantEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.MINI_ASSISTANT_ENABLED, enabled);
        cachedMiniAssistantEnabled = enabled;
    }

    /** Whether the TaskRepository (todo.txt) feature is enabled. */
    public static boolean isTaskRepositoryEnabled() {
        return cachedTaskRepositoryEnabled;
    }

    public static void setTaskRepositoryEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.TASK_REPOSITORY_ENABLED, enabled);
        cachedTaskRepositoryEnabled = enabled;
    }

    /** Raw comma/space-separated allowlist for {@code run_command}; empty means unrestricted. */
    public static String getRunCommandAllowlist() {
        return cachedRunCommandAllowlist;
    }

    public static void setRunCommandAllowlist(String allowlist) {
        String value = allowlist == null ? "" : allowlist.trim();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .put(PreferenceKeys.RUN_COMMAND_ALLOWLIST, value);
        cachedRunCommandAllowlist = value;
    }

    /** Raw comma/space-separated denylist for {@code run_command}. */
    public static String getRunCommandDenylist() {
        return cachedRunCommandDenylist;
    }

    public static void setRunCommandDenylist(String denylist) {
        String value = denylist == null ? "" : denylist.trim();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .put(PreferenceKeys.RUN_COMMAND_DENYLIST, value);
        cachedRunCommandDenylist = value;
    }

    /** The ACP file-access policy (allow, read-only or deny); all modes are project-confined. */
    public static AcpFsPolicy getAcpFsPolicy() {
        return cachedAcpFsPolicy;
    }

    public static void setAcpFsPolicy(AcpFsPolicy policy) {
        AcpFsPolicy effective = policy != null ? policy : AcpFsPolicy.DEFAULT;
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .put(PreferenceKeys.ACP_FS_POLICY, effective.storedValue());
        cachedAcpFsPolicy = effective;
    }

    /** Whether the user-editable session preamble is sent at turn start. */
    public static boolean isPreambleEnabled() {
        return cachedPreambleEnabled;
    }

    public static void setPreambleEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.PREAMBLE_ENABLED, enabled);
        cachedPreambleEnabled = enabled;
    }

    /** Whether the embedded MCP server is enabled. */
    public static boolean isMcpServerEnabled() {
        return cachedMcpServerEnabled;
    }

    public static void setMcpServerEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.MCP_SERVER_ENABLED, enabled);
        cachedMcpServerEnabled = enabled;
    }

    /**
     * Internal MCP server HTTP port. Returns 0 (default) to pick a random
     * free port at startup; a positive value binds the embedded server to
     * that fixed port.
     */
    public static int getMcpServerPort() {
        return Math.max(0, cachedMcpServerPort);
    }

    public static void setMcpServerPort(int port) {
        int safe = Math.max(0, port);
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putInt(PreferenceKeys.MCP_SERVER_PORT, safe);
        cachedMcpServerPort = safe;
    }

    /** Whether the Markdown project type is enabled (default true). */
    public static boolean isMarkdownProjectEnabled() {
        return cachedMarkdownProjectEnabled;
    }

    public static void setMarkdownProjectEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.ACTIONS_MARKDOWN_PROJECT, enabled);
        cachedMarkdownProjectEnabled = enabled;
    }

    /** Whether the IDE's own diff windows take the plugin's smaller diff font. */
    public static boolean isDiffIdeFontEnabled() {
        return cachedDiffIdeFont;
    }

    public static void setDiffIdeFontEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.DIFF_IDE_FONT, enabled);
        cachedDiffIdeFont = enabled;
    }

    /** User bubble background ({@code #rrggbb}) for the light theme; empty keeps the theme colour. */
    public static String getUserBubbleBackgroundLight() {
        return cachedUserBubbleLight;
    }

    public static void setUserBubbleBackgroundLight(String color) {
        cachedUserBubbleLight = putColor(PreferenceKeys.USER_BUBBLE_LIGHT, color);
    }

    /** User bubble background ({@code #rrggbb}) for the dark theme; empty keeps the theme colour. */
    public static String getUserBubbleBackgroundDark() {
        return cachedUserBubbleDark;
    }

    public static void setUserBubbleBackgroundDark(String color) {
        cachedUserBubbleDark = putColor(PreferenceKeys.USER_BUBBLE_DARK, color);
    }

    /** Assistant bubble background ({@code #rrggbb}) for the light theme; empty keeps the theme colour. */
    public static String getAssistantBubbleBackgroundLight() {
        return cachedAssistantBubbleLight;
    }

    public static void setAssistantBubbleBackgroundLight(String color) {
        cachedAssistantBubbleLight = putColor(PreferenceKeys.ASSISTANT_BUBBLE_LIGHT, color);
    }

    /** Assistant bubble background ({@code #rrggbb}) for the dark theme; empty keeps the theme colour. */
    public static String getAssistantBubbleBackgroundDark() {
        return cachedAssistantBubbleDark;
    }

    public static void setAssistantBubbleBackgroundDark(String color) {
        cachedAssistantBubbleDark = putColor(PreferenceKeys.ASSISTANT_BUBBLE_DARK, color);
    }

    /** Chat font family for all chat text; empty keeps the LAF / system-property choice. */
    public static String getChatFontFamily() {
        return cachedChatFontFamily;
    }

    public static void setChatFontFamily(String family) {
        String value = nullToEmpty(family).trim();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(PreferenceKeys.CHAT_FONT_FAMILY, value);
        cachedChatFontFamily = value;
    }

    /** Stores a colour override, treating a blank value as "not set". */
    private static String putColor(String key, String color) {
        String value = nullToEmpty(color).trim();
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR).put(key, value);
        return value;
    }

    /** Whether to show hidden sessions in the session list. */
    public static boolean isShowHiddenSessions() {
        return cachedShowHiddenSessions;
    }

    public static void setShowHiddenSessions(boolean show) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.SHOW_HIDDEN_SESSIONS, show);
        cachedShowHiddenSessions = show;
    }

    /** Whether to keep older messages instead of trimming. */
    public static boolean isKeepOlderMessages() {
        return cachedKeepOlderMessages;
    }

    public static void setKeepOlderMessages(boolean keep) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.KEEP_OLDER_MESSAGES, keep);
        cachedKeepOlderMessages = keep;
    }

    /** Whether to echo user input locally before server confirms. */
    public static boolean isEchoUserInput() {
        return cachedEchoUserInput;
    }

    public static void setEchoUserInput(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.ECHO_USER_INPUT, enabled);
        cachedEchoUserInput = enabled;
    }

    /** Whether to combine adjacent tool + thought bubbles into one activity panel. */
    public static boolean isCombineToolThoughtEnabled() {
        return cachedCombineToolThought;
    }

    public static void setCombineToolThoughtEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.COMBINE_TOOL_THOUGHT, enabled);
        cachedCombineToolThought = enabled;
    }

    /** Whether to check for updates on startup. */
    public static boolean isCheckForUpdates() {
        return cachedCheckForUpdates;
    }

    public static void setCheckForUpdates(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.CHECK_FOR_UPDATES, enabled);
        cachedCheckForUpdates = enabled;
    }

    /** Whether to use WSL when available (Windows only). */
    public static boolean isUseWsl() {
        return cachedUseWsl;
    }

    public static void setUseWsl(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.USE_WSL, enabled);
        cachedUseWsl = enabled;
    }

    /**
     * Whether switching a session to another project restarts the ACP server so its
     * working directory follows the session (experimental, disabled by default).
     *
     * <p>The effective value is captured at startup; changing the option in the
     * Options dialog requires an IDE restart to take effect.</p>
     */
    public static boolean isProjectSwitchRestartEnabled() {
        return projectSwitchRestartEnabled;
    }

    /** The stored option value, which may differ from the effective value until restart. */
    public static boolean isProjectSwitchRestartConfigured() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .getBoolean(PreferenceKeys.PROJECT_SWITCH_RESTART_ENABLED, false);
    }

    /** Persists the option; the effective value changes only after an IDE restart. */
    public static void setProjectSwitchRestartEnabled(boolean enabled) {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .putBoolean(PreferenceKeys.PROJECT_SWITCH_RESTART_ENABLED, enabled);
    }

    /** Test seam: simulate the value captured by an IDE restart, without touching preferences. */
    public static void setProjectSwitchRestartForTest(boolean enabled) {
        projectSwitchRestartEnabled = enabled;
    }

    // --- Option effect markers -------------------------------------------------

    /** How a change to an option reaches the running IDE. */
    public enum Effect {
        /** Applies as soon as the value is stored. */
        IMMEDIATE,
        /** Applies to sessions created from now on. */
        NEW_SESSION,
        /** Applies to the current chat only after its history is reloaded. */
        SESSION_RELOAD,
        /** Applies only after NetBeans restarts. */
        IDE_RESTART
    }

    private static final String DEFAULT_PROCESS_ARGUMENTS = "acp";

    /**
     * Preferences whose stored value this IDE process reads only while starting, so a
     * change needs a restart before it takes effect. Order is the Options panel's
     * reading order and is reused for the pending-restart bar.
     */
    private static final List<String> IDE_RESTART_KEYS = List.of(
            PreferenceKeys.ACP_EXECUTABLE_PATH,
            PreferenceKeys.PROCESS_ARGUMENTS,
            PreferenceKeys.USE_WSL,
            PreferenceKeys.TOOLBAR_ICON_SIZE,
            PreferenceKeys.PROJECT_SWITCH_RESTART_ENABLED,
            PreferenceKeys.MCP_SERVER_ENABLED,
            PreferenceKeys.MCP_SERVER_PORT);

    /**
     * Records the value of every restart-scoped preference this IDE process started
     * with. Called once from {@code @OnStart}: without a snapshot every option looks
     * like a pending restart, and reverting a change could never clear the marker.
     */
    public static void refreshEffectiveSnapshot() {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        for (String key : IDE_RESTART_KEYS) {
            prefs.put(PreferenceKeys.EFFECTIVE_SNAPSHOT + key, snapshotValue(prefs, key));
        }
    }

    /**
     * Whether {@code key} was changed since this IDE started and therefore still needs
     * a restart. Compares values, not flags, so reverting a change clears the marker.
     */
    public static boolean isRestartPending(String key) {
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        String snapshot = prefs.get(PreferenceKeys.EFFECTIVE_SNAPSHOT + key, null);
        return snapshot != null && !snapshot.equals(snapshotValue(prefs, key));
    }

    /** Restart-scoped keys changed since this IDE started, in panel order. */
    public static List<String> getAllPendingRestartKeys() {
        List<String> pending = new ArrayList<>(IDE_RESTART_KEYS.size());
        for (String key : IDE_RESTART_KEYS) {
            if (isRestartPending(key)) {
                pending.add(key);
            }
        }
        return pending;
    }

    /** Restart-scoped keys in panel order. */
    public static List<String> getRestartScopedKeys() {
        return IDE_RESTART_KEYS;
    }

    /** Canonical string form of a preference value, independent of its stored type. */
    private static String snapshotValue(Preferences prefs, String key) {
        return switch (key) {
            case PreferenceKeys.ACP_EXECUTABLE_PATH -> prefs.get(key, "");
            case PreferenceKeys.PROCESS_ARGUMENTS -> prefs.get(key, DEFAULT_PROCESS_ARGUMENTS);
            case PreferenceKeys.USE_WSL -> String.valueOf(prefs.getBoolean(key, false));
            case PreferenceKeys.TOOLBAR_ICON_SIZE -> String.valueOf(prefs.getInt(key, DEFAULT_TOOLBAR_ICON_SIZE));
            case PreferenceKeys.PROJECT_SWITCH_RESTART_ENABLED -> String.valueOf(prefs.getBoolean(key, false));
            case PreferenceKeys.MCP_SERVER_ENABLED -> String.valueOf(prefs.getBoolean(key, true));
            case PreferenceKeys.MCP_SERVER_PORT -> String.valueOf(prefs.getInt(key, 0));
            default -> throw new IllegalArgumentException("Not a restart-scoped preference: " + key);
        };
    }
}
