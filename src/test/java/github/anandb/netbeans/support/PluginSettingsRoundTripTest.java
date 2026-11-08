package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

/**
 * One test per stored setting: the setter must reach both the backing
 * preference and the cached accessor that production code reads.
 *
 * <p>Every test restores the value it found, so the class is safe under the
 * random-order profile.</p>
 */
class PluginSettingsRoundTripTest {

    private static Preferences prefs() {
        return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
    }

    /**
     * Waits for a cached accessor to converge. NbPreferences dispatches change
     * events on a background thread, so a value written by the setter can be
     * re-applied by the listener slightly later; asserting the eventual value
     * covers both the synchronous setter update and the listener replay.
     */
    private static boolean eventually(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return condition.getAsBoolean();
            }
        }
        return condition.getAsBoolean();
    }

    private static void assertBooleanRoundTrip(String key, boolean original,
            java.util.function.Consumer<Boolean> setter, java.util.function.BooleanSupplier getter) {
        try {
            setter.accept(!original);
            assertTrue(eventually(() -> getter.getAsBoolean() == !original), key);
            assertEquals(!original, prefs().getBoolean(key, original), key);
        } finally {
            setter.accept(original);
        }
        assertTrue(eventually(() -> getter.getAsBoolean() == original), key);
    }

    private static void assertIntRoundTrip(String key, int original, int probe,
            java.util.function.IntConsumer setter, java.util.function.IntSupplier getter) {
        try {
            setter.accept(probe);
            assertTrue(eventually(() -> getter.getAsInt() == probe), key);
            assertEquals(probe, prefs().getInt(key, original), key);
        } finally {
            setter.accept(original);
        }
        assertTrue(eventually(() -> getter.getAsInt() == original), key);
    }

    // ---- booleans ----------------------------------------------------------

    @Test
    void autoBackupChangesRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.AUTO_BACKUP_CHANGES, PluginSettings.isAutoBackupChanges(),
                PluginSettings::setAutoBackupChanges, PluginSettings::isAutoBackupChanges);
    }

    @Test
    void diffIdeFontRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.DIFF_IDE_FONT, PluginSettings.isDiffIdeFontEnabled(),
                PluginSettings::setDiffIdeFontEnabled, PluginSettings::isDiffIdeFontEnabled);
    }

    @Test
    void sortLinesRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_SORT_LINES, PluginSettings.isSortLinesEnabled(),
                PluginSettings::setSortLinesEnabled, PluginSettings::isSortLinesEnabled);
    }

    @Test
    void compactJsonRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_COMPACT_JSON, PluginSettings.isCompactJsonEnabled(),
                PluginSettings::setCompactJsonEnabled, PluginSettings::isCompactJsonEnabled);
    }

    @Test
    void searchWebRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_SEARCH_WEB, PluginSettings.isSearchWebEnabled(),
                PluginSettings::setSearchWebEnabled, PluginSettings::isSearchWebEnabled);
    }

    @Test
    void showAnnotationsRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_SHOW_ANNOTATIONS,
                PluginSettings.isShowAnnotationsEnabled(),
                PluginSettings::setShowAnnotationsEnabled, PluginSettings::isShowAnnotationsEnabled);
    }

    @Test
    void viewFileHistoryRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_VIEW_FILE_HISTORY,
                PluginSettings.isViewFileHistoryEnabled(),
                PluginSettings::setViewFileHistoryEnabled, PluginSettings::isViewFileHistoryEnabled);
    }

    @Test
    void stashDiffRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_STASH_DIFF, PluginSettings.isStashDiffEnabled(),
                PluginSettings::setStashDiffEnabled, PluginSettings::isStashDiffEnabled);
    }

    @Test
    void quickJumpRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_QUICK_JUMP, PluginSettings.isQuickJumpEnabled(),
                PluginSettings::setQuickJumpEnabled, PluginSettings::isQuickJumpEnabled);
    }

    @Test
    void copyAllItemsRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_COPY_ALL_ITEMS,
                PluginSettings.isCopyAllItemsEnabled(),
                PluginSettings::setCopyAllItemsEnabled, PluginSettings::isCopyAllItemsEnabled);
    }

    @Test
    void miniAssistantRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.MINI_ASSISTANT_ENABLED,
                PluginSettings.isMiniAssistantEnabled(),
                PluginSettings::setMiniAssistantEnabled, PluginSettings::isMiniAssistantEnabled);
    }

    @Test
    void taskRepositoryRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.TASK_REPOSITORY_ENABLED,
                PluginSettings.isTaskRepositoryEnabled(),
                PluginSettings::setTaskRepositoryEnabled, PluginSettings::isTaskRepositoryEnabled);
    }

    @Test
    void preambleToggleRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.PREAMBLE_ENABLED, PluginSettings.isPreambleEnabled(),
                PluginSettings::setPreambleEnabled, PluginSettings::isPreambleEnabled);
    }

    @Test
    void mcpServerToggleRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.MCP_SERVER_ENABLED, PluginSettings.isMcpServerEnabled(),
                PluginSettings::setMcpServerEnabled, PluginSettings::isMcpServerEnabled);
    }

    @Test
    void markdownProjectRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ACTIONS_MARKDOWN_PROJECT,
                PluginSettings.isMarkdownProjectEnabled(),
                PluginSettings::setMarkdownProjectEnabled, PluginSettings::isMarkdownProjectEnabled);
    }

    @Test
    void showHiddenSessionsRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.SHOW_HIDDEN_SESSIONS,
                PluginSettings.isShowHiddenSessions(),
                PluginSettings::setShowHiddenSessions, PluginSettings::isShowHiddenSessions);
    }

    @Test
    void keepOlderMessagesRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.KEEP_OLDER_MESSAGES,
                PluginSettings.isKeepOlderMessages(),
                PluginSettings::setKeepOlderMessages, PluginSettings::isKeepOlderMessages);
    }

    @Test
    void echoUserInputRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.ECHO_USER_INPUT, PluginSettings.isEchoUserInput(),
                PluginSettings::setEchoUserInput, PluginSettings::isEchoUserInput);
    }

    @Test
    void combineToolThoughtRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.COMBINE_TOOL_THOUGHT,
                PluginSettings.isCombineToolThoughtEnabled(),
                PluginSettings::setCombineToolThoughtEnabled,
                PluginSettings::isCombineToolThoughtEnabled);
    }

    @Test
    void useWslRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.USE_WSL, PluginSettings.isUseWsl(),
                PluginSettings::setUseWsl, PluginSettings::isUseWsl);
    }

    @Test
    void checkForUpdatesRoundTrips() {
        assertBooleanRoundTrip(PreferenceKeys.CHECK_FOR_UPDATES, PluginSettings.isCheckForUpdates(),
                PluginSettings::setCheckForUpdates, PluginSettings::isCheckForUpdates);
    }

    // ---- numbers -----------------------------------------------------------

    @Test
    void sessionIdleTimeoutRoundTrips() {
        assertIntRoundTrip("sessionIdleTimeout", PluginSettings.getSessionIdleTimeout(), 42,
                PluginSettings::setSessionIdleTimeout, PluginSettings::getSessionIdleTimeout);
    }

    @Test
    void maxMessagesRoundTrips() {
        assertIntRoundTrip(PreferenceKeys.MAX_MESSAGES, PluginSettings.getMaxMessages(), 7,
                PluginSettings::setMaxMessages, PluginSettings::getMaxMessages);
    }

    @Test
    void toolbarIconSizeRoundTrips() {
        assertIntRoundTrip(PreferenceKeys.TOOLBAR_ICON_SIZE, PluginSettings.getToolbarIconSize(), 32,
                PluginSettings::setToolbarIconSize, PluginSettings::getToolbarIconSize);
    }

    @Test
    void invalidToolbarIconSizeFallsBackToTheDefault() {
        int original = PluginSettings.getToolbarIconSize();
        try {
            PluginSettings.setToolbarIconSize(99);
            assertEquals(24, PluginSettings.getToolbarIconSize());
        } finally {
            PluginSettings.setToolbarIconSize(original);
        }
    }

    @Test
    void chatFontSizeRoundTripsIncludingInherited() {
        assertIntRoundTrip(PreferenceKeys.CHAT_FONT_SIZE, PluginSettings.getChatFontSize(), 19,
                PluginSettings::setChatFontSize, PluginSettings::getChatFontSize);

        int original = PluginSettings.getChatFontSize();
        try {
            PluginSettings.setChatFontSize(-5);
            assertEquals(-1, PluginSettings.getChatFontSize(),
                    "any negative size means 'inherited from theme'");
        } finally {
            PluginSettings.setChatFontSize(original);
        }
    }

    @Test
    void mcpServerPortRoundTrips() {
        assertIntRoundTrip(PreferenceKeys.MCP_SERVER_PORT, PluginSettings.getMcpServerPort(), 8777,
                PluginSettings::setMcpServerPort, PluginSettings::getMcpServerPort);
    }

    // ---- strings and enums -------------------------------------------------

    @Test
    void runCommandAllowlistRoundTrips() {
        String original = PluginSettings.getRunCommandAllowlist();
        try {
            PluginSettings.setRunCommandAllowlist(" git, mvn ");
            assertEquals("git, mvn", PluginSettings.getRunCommandAllowlist());
            assertEquals("git, mvn", prefs().get(PreferenceKeys.RUN_COMMAND_ALLOWLIST, null));
        } finally {
            PluginSettings.setRunCommandAllowlist(original);
        }
    }

    @Test
    void runCommandDenylistRoundTrips() {
        String original = PluginSettings.getRunCommandDenylist();
        try {
            PluginSettings.setRunCommandDenylist("curl, wget");
            assertEquals("curl, wget", PluginSettings.getRunCommandDenylist());
            assertEquals("curl, wget", prefs().get(PreferenceKeys.RUN_COMMAND_DENYLIST, null));
        } finally {
            PluginSettings.setRunCommandDenylist(original);
        }
    }

    @Test
    void acpFsPolicyRoundTrips() {
        AcpFsPolicy original = PluginSettings.getAcpFsPolicy();
        try {
            PluginSettings.setAcpFsPolicy(AcpFsPolicy.READ_ONLY);
            assertEquals(AcpFsPolicy.READ_ONLY, PluginSettings.getAcpFsPolicy());
            assertEquals(AcpFsPolicy.READ_ONLY.storedValue(),
                    prefs().get(PreferenceKeys.ACP_FS_POLICY, null));
        } finally {
            PluginSettings.setAcpFsPolicy(original);
        }
    }

    @Test
    void preambleAndCustomIconRoundTrip() {
        String originalPreamble = PluginSettings.getPreamble();
        String originalIcon = PluginSettings.getCustomUserIcon();
        try {
            PluginSettings.setPreamble("custom preamble");
            assertEquals("custom preamble", PluginSettings.getPreamble());

            PluginSettings.setCustomUserIcon("/tmp/icon.png");
            assertEquals("/tmp/icon.png", PluginSettings.getCustomUserIcon());
        } finally {
            PluginSettings.setPreamble(originalPreamble);
            PluginSettings.setCustomUserIcon(originalIcon);
        }
    }

    @Test
    void oversizedPreambleIsRejectedWithoutChangingTheStoredOne() {
        String original = PluginSettings.getPreamble();
        try {
            String tooLong = "x".repeat(PluginSettings.MAX_PREAMBLE_LENGTH + 1);
            assertThrows(IllegalArgumentException.class, () -> PluginSettings.setPreamble(tooLong));
            assertEquals(original, PluginSettings.getPreamble());
        } finally {
            PluginSettings.setPreamble(original);
        }
    }

    @Test
    void projectSwitchRestartStoresButStaysCapturedAtStartup() {
        boolean originalStored = PluginSettings.isProjectSwitchRestartConfigured();
        boolean originalEffective = PluginSettings.isProjectSwitchRestartEnabled();
        try {
            PluginSettings.setProjectSwitchRestartEnabled(!originalStored);
            assertEquals(!originalStored, PluginSettings.isProjectSwitchRestartConfigured());
            assertEquals(originalEffective, PluginSettings.isProjectSwitchRestartEnabled(),
                    "the running value only changes on the next IDE start");
        } finally {
            PluginSettings.setProjectSwitchRestartEnabled(originalStored);
            PluginSettings.setProjectSwitchRestartForTest(originalEffective);
        }
        assertTrue(PluginSettings.getRestartScopedKeys().contains(
                PreferenceKeys.PROJECT_SWITCH_RESTART_ENABLED));
    }
}
