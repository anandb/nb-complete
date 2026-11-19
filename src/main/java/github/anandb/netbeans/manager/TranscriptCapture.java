package github.anandb.netbeans.manager;

import github.anandb.netbeans.model.ProcessedMessage;
import github.anandb.netbeans.model.SessionUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Collector state for one background transcript capture
 * ({@link SessionManager#captureTranscript}). Registered in
 * {@link SessionManager#handleSseUpdate} while the capture's
 * {@code session/load} replays the session: replayed updates for the target
 * session (and its sub-agents) are appended here instead of being routed to
 * the display pipeline.
 * <p>
 * Thread-safe: {@link #collect} runs on the SSE reader thread while the drain
 * loop and the abort paths run on completable-future executors.
 */
final class TranscriptCapture {

    /** Replay may straddle the load response (goose LOADING), so wait for a quiet window. */
    static final long DRAIN_QUIET_MS = 300;

    /** How often the drain loop re-checks the quiet window. */
    static final long DRAIN_POLL_MS = 100;

    /** Hard cap on the drain wait; a harness that keeps trickling must not hang the export. */
    static final long DRAIN_MAX_MS = 10_000;

    private final String sessionId;
    private final long serverGeneration;
    private final Predicate<String> descendantOfTarget;
    private final long drainMaxMs;
    private final long startNanos = System.nanoTime();

    private final List<SessionUpdate> updates = new ArrayList<>();
    private volatile long lastCollectNanos = startNanos;

    /** Completed exactly once: by the drain loop, a load failure, or an abort. */
    private final CompletableFuture<List<ProcessedMessage>> result = new CompletableFuture<>();
    private volatile CompletableFuture<?> loadFuture;
    private volatile boolean aborted;

    void setLoadFuture(CompletableFuture<?> future) {
        loadFuture = future;
        if (aborted && future != null) {
            future.cancel(true);
        }
    }

    void cancelLoad() {
        aborted = true;
        CompletableFuture<?> future = loadFuture;
        if (future != null) {
            future.cancel(true);
        }
    }

    /** True once an abort cancelled the load; a retry must not re-issue it. */
    boolean isAborted() {
        return aborted;
    }

    TranscriptCapture(String sessionId, long serverGeneration, Predicate<String> descendantOfTarget) {
        this(sessionId, serverGeneration, descendantOfTarget, DRAIN_MAX_MS);
    }

    TranscriptCapture(String sessionId, long serverGeneration, Predicate<String> descendantOfTarget,
            long drainMaxMs) {
        this.sessionId = sessionId;
        this.serverGeneration = serverGeneration;
        this.descendantOfTarget = descendantOfTarget;
        this.drainMaxMs = drainMaxMs;
    }

    String sessionId() {
        return sessionId;
    }

    long serverGeneration() {
        return serverGeneration;
    }

    CompletableFuture<List<ProcessedMessage>> result() {
        return result;
    }

    /** True for updates of the target session or one of its sub-agents. */
    boolean accepts(String updateSessionId) {
        return updateSessionId != null
                && (updateSessionId.equals(sessionId) || descendantOfTarget.test(updateSessionId));
    }

    void collect(SessionUpdate update) {
        synchronized (updates) {
            updates.add(update);
        }
        lastCollectNanos = System.nanoTime();
    }

    /** Drops updates from a failed load attempt before a full retry. */
    void resetForRetry() {
        synchronized (updates) {
            updates.clear();
        }
        lastCollectNanos = System.nanoTime();
    }

    /** Starts the post-response quiet window; replay may continue after the RPC result. */
    void markResponseReceived() {
        lastCollectNanos = System.nanoTime();
    }

    /** True when no update arrived within the last {@link #DRAIN_QUIET_MS}. */
    boolean isQuiet() {
        return System.nanoTime() - lastCollectNanos >= TimeUnit.MILLISECONDS.toNanos(DRAIN_QUIET_MS);
    }

    /** True when the drain window's hard cap is exhausted. */
    boolean isExhausted() {
        return System.nanoTime() - startNanos >= TimeUnit.MILLISECONDS.toNanos(drainMaxMs);
    }

    /** Snapshot of the collected updates; the collector is spent after this. */
    List<SessionUpdate> drain() {
        synchronized (updates) {
            return new ArrayList<>(updates);
        }
    }

    void succeed(List<ProcessedMessage> messages) {
        result.complete(messages);
    }

    void fail(Throwable cause) {
        result.completeExceptionally(cause);
    }
}
