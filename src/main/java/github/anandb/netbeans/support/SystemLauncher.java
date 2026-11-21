package github.anandb.netbeans.support;

import java.util.concurrent.CompletableFuture;

/**
 * Runs blocking OS launches off the EDT: {@code Desktop.open/browse}, browser
 * process starts and similar fork/exec work. A launch can stall while the
 * desktop environment resolves the handler, so it must never hold the UI thread.
 *
 * <p>Failures are logged, never propagated — the task runs on a background
 * thread with no caller left to handle an exception.</p>
 *
 * <p>Layer: support leaf — JDK only.</p>
 */
public final class SystemLauncher {

    private static final Logger LOG = Logger.from(SystemLauncher.class);

    private SystemLauncher() {
    }

    /**
     * Runs {@code task} asynchronously.
     *
     * @param what short description used in the failure log, e.g. {@code "open the working directory"}
     * @param task the blocking launch
     * @return a handle that completes when the task ends, so callers and tests can await it
     */
    public static CompletableFuture<Void> launch(String what, Runnable task) {
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                LOG.warn("Failed to {0}: {1}", what, ex);
            }
        });
    }
}
