package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

/**
 * Verifies that a pending restart is decided by comparing stored values with the snapshot
 * taken at IDE start, so reverting a change clears the marker and a missing snapshot never
 * pretends something is pending.
 */
class PluginSettingsRestartPendingTest {

    private Preferences prefs;

    @BeforeEach
    void setUp() {
        prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        clearRestartKeys();
        PluginSettings.refreshEffectiveSnapshot();
    }

    @AfterEach
    void tearDown() {
        clearRestartKeys();
    }

    @Test
    void nothingIsPendingRightAfterTheSnapshot() {
        assertTrue(PluginSettings.getAllPendingRestartKeys().isEmpty());
    }

    @Test
    void changedValueIsPendingAndRevertingClearsIt() {
        prefs.putInt(PreferenceKeys.MCP_SERVER_PORT, 8080);
        assertEquals(List.of(PreferenceKeys.MCP_SERVER_PORT), PluginSettings.getAllPendingRestartKeys());

        prefs.putInt(PreferenceKeys.MCP_SERVER_PORT, 0);
        assertTrue(PluginSettings.getAllPendingRestartKeys().isEmpty());
    }

    @Test
    void booleanAndStringKeysAreComparedByValue() {
        prefs.putBoolean(PreferenceKeys.MCP_SERVER_ENABLED, false);
        prefs.put(PreferenceKeys.PROCESS_ARGUMENTS, "acp --verbose");

        List<String> pending = PluginSettings.getAllPendingRestartKeys();

        assertTrue(pending.contains(PreferenceKeys.MCP_SERVER_ENABLED));
        assertTrue(pending.contains(PreferenceKeys.PROCESS_ARGUMENTS));
        assertFalse(pending.contains(PreferenceKeys.USE_WSL));
    }

    @Test
    void withoutASnapshotNothingIsPending() {
        prefs.remove(PreferenceKeys.EFFECTIVE_SNAPSHOT + PreferenceKeys.MCP_SERVER_PORT);
        prefs.putInt(PreferenceKeys.MCP_SERVER_PORT, 9090);

        assertFalse(PluginSettings.isRestartPending(PreferenceKeys.MCP_SERVER_PORT));
    }

    private void clearRestartKeys() {
        for (String key : PluginSettings.getRestartScopedKeys()) {
            prefs.remove(PreferenceKeys.EFFECTIVE_SNAPSHOT + key);
            prefs.remove(key);
        }
    }
}
