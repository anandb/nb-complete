package github.anandb.netbeans.manager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import github.anandb.netbeans.model.Session;
import github.anandb.netbeans.support.Logger;

/**
 * Manages session cache and parent-child hierarchy lookups.
 * Extracted from SessionManager to isolate cache concerns.
 */
final class SessionCacheManager {

    private static final Logger LOG = Logger.from(SessionCacheManager.class);

    private List<Session> cachedSessions = new CopyOnWriteArrayList<>();
    private final Map<String, Session> sessionCacheMap = new ConcurrentHashMap<>();

    SessionCacheManager() {
    }

    /** Returns the cached session list. */
    List<Session> getCachedSessions() {
        return cachedSessions;
    }

    /** Replaces the cached session list atomically. */
    void setCachedSessions(List<Session> sessions) {
        cachedSessions = new CopyOnWriteArrayList<>(sessions);
    }

    /** Puts a session into the ID-keyed cache map. */
    void cacheSession(Session session) {
        if (session != null && session.id() != null) {
            sessionCacheMap.put(session.id(), session);
        }
    }

    /** Returns the session for the given ID, or null. */
    Session getCachedSession(String sessionId) {
        return sessionCacheMap.get(sessionId);
    }

    /** Clears the ID-keyed cache map. */
    void clearSessionMap() {
        sessionCacheMap.clear();
    }

    /**
     * Determines whether {@code sessionId} is a descendant of the current session
     * by walking the parentID chain in the cache.
     */
    boolean isDescendantOfCurrent(String sessionId, String currentSessionId) {
        if (sessionId == null || currentSessionId == null) return false;
        String walk = sessionId;
        int depth = 0;
        while (walk != null && depth < 50) {
            if (currentSessionId.equals(walk)) return true;
            Session s = sessionCacheMap.get(walk);
            walk = (s != null) ? s.parentID() : null;
            depth++;
        }
        return false;
    }
}
