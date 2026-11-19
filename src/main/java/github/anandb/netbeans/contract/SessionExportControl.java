package github.anandb.netbeans.contract;

import github.anandb.netbeans.stereotype.HexagonalPort;
import github.anandb.netbeans.model.ProcessedMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Full-transcript export without disturbing the displayed thread.
 * <p>
 * The transcript is captured by replaying {@code session/load} in the background:
 * the replayed {@code session/update} notifications are collected and converted to
 * {@link ProcessedMessage}s instead of being routed to the chat panel, so the
 * display keeps its trimmed state, scroll position and pinned messages. The
 * session must be the current one and no turn may be in flight; the load closes
 * and re-opens the session on the server, which would cancel a running turn.
 * <p>
 * While a capture is active, prompt sends are refused
 * ({@link #isTranscriptCaptureActive()}) — a live turn's updates would be
 * swallowed by the collector. A turn that starts anyway aborts the capture:
 * the session service fails it in {@code onTurnStarted()} under its admission
 * lock, so a send that raced the gate can never share the session with the replay.
 */
@HexagonalPort
public interface SessionExportControl {

    /**
     * Captures the full transcript of the given session via a background
     * {@code session/load}. The future fails when the session is not the
     * current one, a turn is in flight, the harness does not implement
     * {@code session/load}, or the load itself fails.
     *
     * @param sessionId the session to capture; must be the current session
     * @return the replayed transcript in message order, never null on success
     */
    CompletableFuture<List<ProcessedMessage>> captureTranscript(String sessionId);

    /** True while a transcript capture is in flight; sends must wait. */
    boolean isTranscriptCaptureActive();

    /** True when the active capture owns the supplied session. */
    default boolean isTranscriptCaptureActive(String sessionId) {
        return isTranscriptCaptureActive();
    }

}
