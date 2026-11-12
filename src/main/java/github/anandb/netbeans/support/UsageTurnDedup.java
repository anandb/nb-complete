package github.anandb.netbeans.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;

/**
 * Dedupes per-turn usage so one turn recorded from two wire shapes — the
 * {@code _x.ai/session_notification turn_completed} frame and the
 * {@code session/prompt} result {@code _meta.usage} — writes a single row.
 * Keyed by {@code sessionId|turnId}; bounded like the other capture caches.
 */
public final class UsageTurnDedup {
    private static final Cache<String, Boolean> SEEN = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(20_000)
            .build();

    private UsageTurnDedup() {
    }

    /** True the first time this (session, turn) pair is seen; true when the turn id is blank. */
    public static boolean firstCapture(String sessionId, String turnId) {
        if (sessionId == null || sessionId.isBlank() || turnId == null || turnId.isBlank()) {
            return true;
        }
        return SEEN.asMap().putIfAbsent(sessionId + '|' + turnId, Boolean.TRUE) == null;
    }

    /** Test seam: forget every recorded turn. */
    public static void clear() {
        SEEN.invalidateAll();
    }
}
