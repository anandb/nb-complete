package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCacheManagerTest {

    private SessionCacheManager cache;

    @BeforeEach
    void setUp() {
        cache = new SessionCacheManager();
    }

    /** Creates a Session with sensible defaults for test convenience. */
    private static Session sess(String id, String title, String parentID) {
        return new Session(id, title, null, null, parentID, null, null, null, null, null);
    }

    @Test
    void getCachedSessionsReturnsEmptyListByDefault() {
        assertTrue(cache.getCachedSessions().isEmpty());
    }

    @Test
    void setCachedSessionsReplacesList() {
        cache.setCachedSessions(List.of(sess("id1", "t1", null), sess("id2", "t2", null)), "test-agent");

        List<Session> sessions = cache.getCachedSessions();
        assertEquals(2, sessions.size());
        assertEquals("id1", sessions.get(0).id());
        assertEquals("id2", sessions.get(1).id());
    }

    @Test
    void setCachedSessionsWithEmptyList() {
        cache.setCachedSessions(List.of(sess("x", "t", null)), "test-agent");
        cache.setCachedSessions(List.of(), "test-agent");
        assertTrue(cache.getCachedSessions().isEmpty());
    }

    @Test
    void cacheSessionAndGetCachedSession() {
        Session s = sess("abc", "My Session", null);
        cache.cacheSession(s);
        assertEquals(s, cache.getCachedSession("abc"));
    }

    @Test
    void getCachedSessionReturnsNullForUnknownId() {
        assertNull(cache.getCachedSession("nonexistent"));
    }

    @Test
    void cacheSessionWithNullIdDoesNotThrow() {
        // Null id is rejected by the guard, so nothing is cached.
        cache.cacheSession(sess(null, "title", null));
        // the cache should still be empty (no entry was stored).
        assertTrue(cache.getCachedSessions().isEmpty());
    }

    @Test
    void cacheSessionWithNullSessionIsIgnored() {
        cache.cacheSession(null);
        assertTrue(cache.getCachedSessions().isEmpty());
    }

    @Test
    void isDescendantOfCurrentReturnsFalseForNullArgs() {
        assertFalse(cache.isDescendantOfCurrent(null, "current"));
        assertFalse(cache.isDescendantOfCurrent("child", null));
        assertFalse(cache.isDescendantOfCurrent(null, null));
    }

    @Test
    void isDescendantOfCurrentReturnsTrueForDirectChild() {
        cache.cacheSession(sess("parent", "Parent", null));
        cache.cacheSession(sess("child", "Child", "parent"));
        assertTrue(cache.isDescendantOfCurrent("child", "parent"));
    }

    @Test
    void isDescendantOfCurrentReturnsTrueForGrandchild() {
        cache.cacheSession(sess("root", "Root", null));
        cache.cacheSession(sess("mid", "Mid", "root"));
        cache.cacheSession(sess("leaf", "Leaf", "mid"));
        assertTrue(cache.isDescendantOfCurrent("leaf", "root"));
    }

    @Test
    void isDescendantOfCurrentReturnsFalseForUnrelated() {
        cache.cacheSession(sess("a", "A", null));
        cache.cacheSession(sess("b", "B", null));
        assertFalse(cache.isDescendantOfCurrent("a", "b"));
    }

    @Test
    void isDescendantOfCurrentReturnsTrueForSelf() {
        cache.cacheSession(sess("s", "S", null));
        assertTrue(cache.isDescendantOfCurrent("s", "s"));
    }

    @Test
    void isDescendantOfCurrentReturnsFalseForBrokenChain() {
        cache.cacheSession(sess("orphan", "Orphan", "missing"));
        assertFalse(cache.isDescendantOfCurrent("orphan", "root"));
    }

    @Test
    void setCachedSessionsDefendsAgainstExternalMutation() {
        List<Session> external = new ArrayList<>();
        external.add(sess("x", "X", null));
        cache.setCachedSessions(external, "test-agent");

        // Mutating the original list after set should not affect the cache
        external.clear();
        assertEquals(1, cache.getCachedSessions().size());
    }

    @Test
    void testAgentIsolationForLocallyCreatedSessions() {
        Session s1 = sess("id-gemini", "Gemini Session", null);
        Session s2 = sess("id-pi", "Pi Session", null);

        // Add s1 locally for gemini, s2 locally for pi
        cache.addLocallyCreated(s1, "gemini");
        cache.addLocallyCreated(s2, "pi");

        // Verify loaded sessions and IDs are isolated per agent
        List<Session> geminiSessions = cache.getLocallyCreatedSessions("gemini");
        assertEquals(1, geminiSessions.size());
        assertEquals("id-gemini", geminiSessions.get(0).id());

        List<Session> piSessions = cache.getLocallyCreatedSessions("pi");
        assertEquals(1, piSessions.size());
        assertEquals("id-pi", piSessions.get(0).id());

        Set<String> geminiIds = cache.getLocallyCreatedIds("gemini");
        assertEquals(1, geminiIds.size());
        assertTrue(geminiIds.contains("id-gemini"));

        Set<String> piIds = cache.getLocallyCreatedIds("pi");
        assertEquals(1, piIds.size());
        assertTrue(piIds.contains("id-pi"));

        // Verify setCachedSessions for "gemini" preserves ONLY s1 and not s2
        cache.setCachedSessions(List.of(), "gemini");
        List<Session> cachedForGemini = cache.getCachedSessions();
        assertEquals(1, cachedForGemini.size());
        assertEquals("id-gemini", cachedForGemini.get(0).id());

        // Verify setCachedSessions for "pi" preserves ONLY s2 and not s1 (with reload simulation)
        cache.addLocallyCreated(s2, "pi");
        cache.setCachedSessions(List.of(), "pi");
        List<Session> cachedForPi = cache.getCachedSessions();
        assertEquals(1, cachedForPi.size());
        assertEquals("id-pi", cachedForPi.get(0).id());
    }
}
