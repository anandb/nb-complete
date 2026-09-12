package github.anandb.netbeans.manager;

import java.util.ArrayList;
import java.util.HashSet;
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
    /** Map of sessionId -> agentName that created the session. */
    private final Map<String, String> locallyCreatedSessionToAgentMap = new ConcurrentHashMap<>();

    SessionCacheManager() {
    }

    private String safeAgent(String agentName) {
        return agentName != null && !agentName.isBlank() ? agentName : "default";
    }

    /** Returns the cached session list. */
    List<Session> getCachedSessions() {
        return cachedSessions;
    }

    /**
     * Replaces the cached session list. When {@code preserveLocallyCreated} is true
     * (harnesses without {@code session/list}), locally-created sessions for
     * {@code currentAgent} that the incoming list omitted are kept. List-capable
     * harnesses must pass false so the server list is source of truth.
     */
    void setCachedSessions(List<Session> sessions, String currentAgent, boolean preserveLocallyCreated) {
        List<Session> merged = new ArrayList<>(sessions);
        if (preserveLocallyCreated) {
            String agent = safeAgent(currentAgent);
            for (Session s : cachedSessions) {
                String sessionAgent = locallyCreatedSessionToAgentMap.get(s.id());
                if (agent.equals(sessionAgent) && merged.stream().noneMatch(m -> m.id().equals(s.id()))) {
                    merged.add(s);
                }
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

    /** Marks a session as locally created for the given agent and adds it to the cached list. */
    void addLocallyCreated(Session session, String agentName) {
        if (session == null || session.id() == null) return;
        locallyCreatedSessionToAgentMap.put(session.id(), safeAgent(agentName));
        cacheSession(session);
        List<Session> updated = new ArrayList<>(cachedSessions);
        if (updated.stream().noneMatch(s -> s.id().equals(session.id()))) {
            updated.add(0, session);
            cachedSessions = new CopyOnWriteArrayList<>(updated);
        }
    }

    /** Returns cached sessions that were created locally for the given agent. */
    List<Session> getLocallyCreatedSessions(String agentName) {
        List<Session> result = new ArrayList<>();
        String agent = safeAgent(agentName);
        for (Session s : cachedSessions) {
            String sessionAgent = locallyCreatedSessionToAgentMap.get(s.id());
            if (agent.equals(sessionAgent)) {
                result.add(s);
            }
        }
        return result;
    }

    /** Returns the set of locally-created session IDs for the given agent. */
    Set<String> getLocallyCreatedIds(String agentName) {
        Set<String> result = new HashSet<>();
        String agent = safeAgent(agentName);
        for (Map.Entry<String, String> entry : locallyCreatedSessionToAgentMap.entrySet()) {
            if (agent.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** Returns the session for the given ID, or null. */
    Session getCachedSession(String sessionId) {
        return sessionCacheMap.get(sessionId);
    }

    /** Checks if the session is a descendant of the current active session. */
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
