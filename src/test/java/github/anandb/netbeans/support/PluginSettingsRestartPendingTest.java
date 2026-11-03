package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

/**
 * Verifies that a pending restart is decided by comparing stored values with the snapshot
 * taken at IDE start, so reverting a change clears the marker and a missing snapshot never
 * pretends something is pending.
 *
 * <p>The restart-scoped preferences are shared with every other test in the surefire JVM,
 * so each value is saved and restored instead of being cleared: wiping them would reset a
 * live setting (the MCP port, for instance) for whatever test runs next.
 */
class PluginSettingsRestartPendingTest {

    private Preferences prefs;
    private final Map<String, String> saved = new HashMap<>();

    @BeforeEach
    void setUp() {
        prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        for (String key : PluginSettings.getRestartScopedKeys()) {
            saved.put(key, prefs.get(key, null));
        }
        clearSnapshots();
        PluginSettings.refreshEffectiveSnapshot();
    }

    @AfterEach
    void tearDown() {
        clearSnapshots();
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            if (entry.getValue() == null) {
                prefs.remove(entry.getKey());
            } else {
                prefs.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Test
    void nothingIsPendingRightAfterTheSnapshot() {
        assertTrue(PluginSettings.getAllPendingRestartKeys().isEmpty());
    }

    @Test
    void changedValueIsPendingAndRevertingClearsIt() {
        int original = prefs.getInt(PreferenceKeys.MCP_SERVER_PORT, 0);
        int changed = original == 8080 ? 8081 : 8080;

        prefs.putInt(PreferenceKeys.MCP_SERVER_PORT, changed);
        assertEquals(List.of(PreferenceKeys.MCP_SERVER_PORT), PluginSettings.getAllPendingRestartKeys());

        prefs.putInt(PreferenceKeys.MCP_SERVER_PORT, original);
        assertTrue(PluginSettings.getAllPendingRestartKeys().isEmpty());
    }

    @Test
    void booleanAndStringKeysAreComparedByValue() {
        boolean enabled = prefs.getBoolean(PreferenceKeys.MCP_SERVER_ENABLED, true);
        String arguments = prefs.get(PreferenceKeys.PROCESS_ARGUMENTS, "acp") + " --verbose";
        prefs.putBoolean(PreferenceKeys.MCP_SERVER_ENABLED, !enabled);
        prefs.put(PreferenceKeys.PROCESS_ARGUMENTS, arguments);

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

    /** Only the test-owned snapshot keys are touched; stored values stay live. */
    private void clearSnapshots() {
        for (String key : PluginSettings.getRestartScopedKeys()) {
            prefs.remove(PreferenceKeys.EFFECTIVE_SNAPSHOT + key);
        }
    }
}
