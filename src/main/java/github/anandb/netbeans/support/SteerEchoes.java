package github.anandb.netbeans.support;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remembers the server message ids of steers whose text the UI already echoed locally.
 *
 * <p>goose queues a steer and later streams it back as a {@code user_message_chunk} carrying
 * the same message id ({@code _meta.goose.steer}). User bubbles never merge, so that echo would
 * render the text a second time. The id is registered from the steer response and consumed by
 * the matching echo, keeping one bubble per steer.</p>
 */
public final class SteerEchoes {

    private static final ConcurrentMap<String, Set<String>> PENDING = new ConcurrentHashMap<>();

    private SteerEchoes() {
    }

    /** Registers a locally echoed steer; the matching {@code user_message_chunk} is skipped. */
    public static void expect(String sessionId, String messageId) {
        if (sessionId == null || messageId == null || messageId.isEmpty()) {
            return;
        }
        PENDING.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(messageId);
    }

    /**
     * @return true when this echo belongs to a steer that was already drawn locally; the
     *         expectation is removed, so a later identical chunk renders normally.
     */
    public static boolean consume(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) {
            return false;
        }
        Set<String> ids = PENDING.get(sessionId);
        if (ids == null || !ids.remove(messageId)) {
            return false;
        }
        if (ids.isEmpty()) {
            PENDING.remove(sessionId, ids);
        }
        return true;
    }

    /** Forgets every expectation of a session (session switch, restart, tests). */
    public static void clear(String sessionId) {
        if (sessionId != null) {
            PENDING.remove(sessionId);
        }
    }

    /** Forgets every expectation (server restart, tests). */
    public static void clear() {
        PENDING.clear();
    }
}
