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
 *
 * <p>The {@code _session/steering} extension answers with an outcome, not a message id, so those
 * steers are matched by their exact text instead ({@link #expectText}). claude-agent-acp streams
 * the injected message back as a {@code user_message_chunk}; adapters that do not echo simply
 * leave the expectation unused until the session is cleared.</p>
 */
public final class SteerEchoes {

    private static final ConcurrentMap<String, Set<String>> PENDING = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Set<String>> PENDING_TEXT = new ConcurrentHashMap<>();

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
     * Registers the text of a locally echoed steer that was sent through an extension which
     * reports no message id, so its echo is matched by text.
     */
    public static void expectText(String sessionId, String text) {
        if (sessionId == null || text == null || text.isBlank()) {
            return;
        }
        PENDING_TEXT.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(text);
    }

    /**
     * @return true when this echo belongs to a steer that was already drawn locally; the
     *         expectation is removed, so a later identical chunk renders normally.
     */
    public static boolean consume(String sessionId, String messageId) {
        if (sessionId == null || messageId == null) {
            return false;
        }
        return consume(PENDING, sessionId, messageId);
    }

    /** Text counterpart of {@link #consume(String, String)} for steers without a message id. */
    public static boolean consumeText(String sessionId, String text) {
        if (sessionId == null || text == null) {
            return false;
        }
        return consume(PENDING_TEXT, sessionId, text);
    }

    private static boolean consume(ConcurrentMap<String, Set<String>> pending, String sessionId,
                                   String value) {
        Set<String> values = pending.get(sessionId);
        if (values == null || !values.remove(value)) {
            return false;
        }
        if (values.isEmpty()) {
            pending.remove(sessionId, values);
        }
        return true;
    }

    /** Forgets every expectation of a session (session switch, restart, tests). */
    public static void clear(String sessionId) {
        if (sessionId != null) {
            PENDING.remove(sessionId);
            PENDING_TEXT.remove(sessionId);
        }
    }

    /** Forgets every expectation (server restart, tests). */
    public static void clear() {
        PENDING.clear();
        PENDING_TEXT.clear();
    }
}
