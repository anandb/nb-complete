package github.anandb.netbeans.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** Session IDs created locally via session/new (not discovered via session/list). */
    private final Set<String> locallyCreatedIds = ConcurrentHashMap.newKeySet();

    SessionCacheManager() {
    }

    /** Returns the cached session list. */
    List<Session> getCachedSessions() {
        return cachedSessions;
    }

    /** Replaces the cached session list atomically, preserving locally-created sessions. */
    void setCachedSessions(List<Session> sessions) {
        List<Session> merged = new ArrayList<>(sessions);
        // Re-add locally-created sessions that the server list didn't include
        for (Session s : cachedSessions) {
            if (locallyCreatedIds.contains(s.id()) && merged.stream().noneMatch(m -> m.id().equals(s.id()))) {
                merged.add(s);
            }
        }
        cachedSessions = new CopyOnWriteArrayList<>(merged);
    }

    /** Puts a session into the ID-keyed cache map. */
    void cacheSession(Session session) {
        if (session != null && session.id() != null) {
            sessionCacheMap.put(session.id(), session);
        }
    }

    /** Marks a session as locally created and adds it to the cached list. */
    void addLocallyCreated(Session session) {
        if (session == null || session.id() == null) return;
        locallyCreatedIds.add(session.id());
        cacheSession(session);
        List<Session> updated = new ArrayList<>(cachedSessions);
        if (updated.stream().noneMatch(s -> s.id().equals(session.id()))) {
            updated.add(0, session);
            cachedSessions = new CopyOnWriteArrayList<>(updated);
        }
    }

    /** Returns cached sessions that were created locally (not from session/list). */
    List<Session> getLocallyCreatedSessions() {
        List<Session> result = new ArrayList<>();
        for (Session s : cachedSessions) {
            if (locallyCreatedIds.contains(s.id())) {
                result.add(s);
            }
        }
        return result;
    }

    /** Returns the set of locally-created session IDs (for persistence). */
    Set<String> getLocallyCreatedIds() {
        return Set.copyOf(locallyCreatedIds);
    }

    /** Restores locally-created session IDs from persisted state. */
    void restoreLocallyCreatedIds(Set<String> ids) {
        locallyCreatedIds.addAll(ids);
    }

    /** Returns the session for the given ID, or null. */
    Session getCachedSession(String sessionId) {
        return sessionCacheMap.get(sessionId);
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
