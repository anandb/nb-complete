package github.anandb.netbeans.support;

import org.openide.util.RequestProcessor;

/**
 * One coalescing task shared by burst-prone triggers. Every {@link #schedule()}
 * restarts the window, so a burst runs the body exactly once, {@code delayMs}
 * after the last trigger — the "sliding task" pattern from the
 * {@link RequestProcessor} javadoc.
 * <p>
 * NetBeans API listeners (notably {@code OpenProjects}) fire per project on
 * startup and on project-group switches. Posting a fresh runnable per event
 * queues one full run per event; rescheduling this single task collapses them.
 * <p>
 * The body runs on the caller-supplied processor, never on the EDT — Swing work
 * must marshal itself.
 */
public final class SlidingTask {

    private final RequestProcessor.Task task;
    private final int delayMs;

    /**
     * @param rp      processor that runs the body
     * @param delayMs coalescing window restarted by every {@link #schedule()}
     * @param body    work to run once the burst settles
     */
    public SlidingTask(RequestProcessor rp, int delayMs, Runnable body) {
        this.delayMs = delayMs;
        this.task = rp.create(body);
    }

    /** Restarts the coalescing window; the body runs once the burst settles. */
    public void schedule() {
        task.schedule(delayMs);
    }

    /** Runs the body on the next processor turn, skipping the window (first build). */
    public void scheduleNow() {
        task.schedule(0);
    }

    /** Drops a pending run. Does nothing if none is scheduled. */
    public void cancel() {
        task.cancel();
    }
}
