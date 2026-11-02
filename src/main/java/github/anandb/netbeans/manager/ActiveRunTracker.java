package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import github.anandb.netbeans.support.SteerEchoes;

/**
 * Tracks goose's active run id per session.
 *
 * <p>goose publishes it on {@code session_info_update} as {@code _meta.goose.activeRunId}
 * (null once the run ends) and requires it as {@code expectedRunId} when a turn is steered.
 * A session without an entry has no active run, so a steer cannot be issued.</p>
 */
final class ActiveRunTracker {

    private static final ConcurrentMap<String, String> ACTIVE_RUNS = new ConcurrentHashMap<>();

    private ActiveRunTracker() {
    }

    /** Applies the run id carried by a raw {@code update} node; non-run updates are ignored. */
    static void update(String sessionId, JsonNode update) {
        if (sessionId == null || update == null || !update.isObject()) {
            return;
        }
        JsonNode meta = update.get("_meta");
        JsonNode goose = meta != null ? meta.get("goose") : null;
        if (goose == null || !goose.has("activeRunId")) {
            return;
        }
        JsonNode runId = goose.get("activeRunId");
        // Null, non-textual and empty all mean "no run": keeping a dead id would make the
        // next steer fail with a stale expectedRunId.
        String value = runId != null && runId.isTextual() ? runId.asText().trim() : "";
        if (value.isEmpty()) {
            ACTIVE_RUNS.remove(sessionId);
            // The run is over, so a pending steer can never be echoed.
            SteerEchoes.clear(sessionId);
        } else {
            ACTIVE_RUNS.put(sessionId, value);
        }
    }

    /** Records a run id learnt from a rejected steer ({@code data.actualRunId}). */
    static void set(String sessionId, String runId) {
        if (sessionId == null || runId == null || runId.isBlank()) {
            return;
        }
        ACTIVE_RUNS.put(sessionId, runId);
    }

    static String get(String sessionId) {
        return sessionId != null ? ACTIVE_RUNS.get(sessionId) : null;
    }

    static void clear(String sessionId) {
        if (sessionId != null) {
            ACTIVE_RUNS.remove(sessionId);
        }
    }

    static void clear() {
        ACTIVE_RUNS.clear();
    }
}
