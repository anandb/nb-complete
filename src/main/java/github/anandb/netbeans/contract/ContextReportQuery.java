package github.anandb.netbeans.contract;

import java.util.concurrent.CompletableFuture;

import github.anandb.netbeans.model.ContextReport;

/**
 * Port for a harness's live context-window breakdown. Only a harness that implements
 * goose's {@code _goose/unstable/context/report} can answer; for any other agent
 * {@link #canReportContext()} is false and callers show nothing.
 */
public interface ContextReportQuery {

    /** True when the harness can break down what fills the session's context window. */
    boolean canReportContext();

    /**
     * Breaks down what the next request would send. Completes with {@code null} when the
     * harness cannot report or the call failed — a diagnostic panel never fails a refresh.
     */
    CompletableFuture<ContextReport> reportContext(String sessionId);
}
