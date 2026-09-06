package github.anandb.netbeans.manager;

import github.anandb.netbeans.support.PreferenceKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.NbPreferences;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinnedMessageStoreTest {

    private PinnedMessageStore store;
    private static final String SESSION = "test-session-" + System.nanoTime();

    @BeforeEach
    void setUp() {
        store = new PinnedMessageStore();
        // Clean up any leftover prefs for this session
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .remove("pinnedMessages." + SESSION);
    }

    @AfterEach
    void tearDown() {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .remove("pinnedMessages." + SESSION);
    }

    @Test
    void isPinnedReturnsFalseForNullArgs() {
        assertFalse(store.isPinned(null, "msg1"));
        assertFalse(store.isPinned(SESSION, null));
        assertFalse(store.isPinned(null, null));
    }

    @Test
    void setPinnedAndCheck() {
        store.setPinned(SESSION, "msg1", true);
        assertTrue(store.isPinned(SESSION, "msg1"));
    }

    @Test
    void unpinRemovesMessage() {
        store.setPinned(SESSION, "msg1", true);
        assertTrue(store.isPinned(SESSION, "msg1"));

        store.setPinned(SESSION, "msg1", false);
        assertFalse(store.isPinned(SESSION, "msg1"));
    }

    @Test
    void setPinnedNullArgsAreIgnored() {
        store.setPinned(null, "msg1", true);
        store.setPinned(SESSION, null, true);
        // No exception, no side effects
    }

    @Test
    void loadSessionRestoresFromPrefs() {
        // Simulate persisted state
        String json = "[\"msg1\",\"msg2\"]";
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .put("pinnedMessages." + SESSION, json);

        store.loadSession(SESSION);
        assertTrue(store.isPinned(SESSION, "msg1"));
        assertTrue(store.isPinned(SESSION, "msg2"));
    }

    @Test
    void loadSessionNullIdIsIgnored() {
        store.loadSession(null);
        // No exception
    }

    @Test
    void loadSessionWithEmptyPrefs() {
        // No pref stored — should not throw
        store.loadSession(SESSION);
        assertFalse(store.isPinned(SESSION, "msg1"));
    }

    @Test
    void loadSessionWithMalformedJson() {
        NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .put("pinnedMessages." + SESSION, "NOT_JSON!!!");
        // Should not throw — logs warning and returns empty list
        store.loadSession(SESSION);
        assertFalse(store.isPinned(SESSION, "msg1"));
    }

    @Test
    void retainPinnedRemovesStaleEntries() {
        store.setPinned(SESSION, "msg1", true);
        store.setPinned(SESSION, "msg2", true);

        // Retain only msg1
        store.retainPinned(SESSION, Set.of("msg1"));
        assertTrue(store.isPinned(SESSION, "msg1"));
        assertFalse(store.isPinned(SESSION, "msg2"));
    }

    @Test
    void retainPinnedNullArgsAreIgnored() {
        store.retainPinned(null, Set.of("msg1"));
        store.retainPinned(SESSION, null);
        // No exception
    }

    @Test
    void retainPinnedWithNoCachedSession() {
        // Calling retain on a session that was never loaded — no-op
        store.retainPinned("never-loaded", Set.of("msg1"));
    }

    @Test
    void unloadSessionRemovesFromCache() {
        store.setPinned(SESSION, "msg1", true);
        assertTrue(store.isPinned(SESSION, "msg1"));

        store.unloadSession(SESSION);
        assertFalse(store.isPinned(SESSION, "msg1"));
    }

    @Test
    void unloadSessionNullIdIsIgnored() {
        store.unloadSession(null);
        // No exception
    }

    @Test
    void persistToPrefsAndReload() {
        store.setPinned(SESSION, "msg1", true);
        store.setPinned(SESSION, "msg2", true);

        // Create a new store instance and load the same session
        PinnedMessageStore store2 = new PinnedMessageStore();
        store2.loadSession(SESSION);
        assertTrue(store2.isPinned(SESSION, "msg1"));
        assertTrue(store2.isPinned(SESSION, "msg2"));
    }

    @Test
    void removingAllPinsClearsPrefsKey() {
        store.setPinned(SESSION, "msg1", true);
        store.setPinned(SESSION, "msg1", false);

        String json = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                .get("pinnedMessages." + SESSION, null);
        // Key should be removed when set is empty
        assertTrue(json == null || json.isEmpty());
    }
}
