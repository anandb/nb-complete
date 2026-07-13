package github.anandb.netbeans.support;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Graceful process tree termination utility.
 * Same SIGTERM → wait → SIGKILL pattern used by ServerProcessLifecycle.
 */
public final class ProcessTerminator {

    private static final Logger LOG = Logger.from(ProcessTerminator.class);

    private ProcessTerminator() { }

    /**
     * Gracefully terminates a process and its entire tree.
     * <ol>
     *   <li>Captures descendants</li>
     *   <li>Sends SIGTERM to descendants then parent, waits 3s</li>
     *   <li>If still alive, sends SIGKILL to descendants then parent</li>
     * </ol>
     *
     * @param proc the process to terminate (no-op if null or not alive)
     */
    public static void terminate(Process proc) {
        if (proc == null || !proc.isAlive()) return;

        long pid = proc.pid();
        LOG.fine("Terminating process tree PID {0}...", pid);

        List<ProcessHandle> descendants = proc.descendants().toList();

        // SIGTERM
        for (ProcessHandle h : descendants) {
            if (h.isAlive()) {
                LOG.fine("SIGTERM descendant PID {0}", h.pid());
                h.destroy();
            }
        }
        if (proc.isAlive()) {
            proc.destroy();
        }

        try {
            if (proc.waitFor(3, TimeUnit.SECONDS)) {
                // If parent exited, check if any descendants are still alive
                boolean childrenAlive = false;
                for (ProcessHandle h : descendants) {
                    if (h.isAlive()) {
                        childrenAlive = true;
                        break;
                    }
                }
                if (!childrenAlive) {
                    LOG.fine("Process tree PID {0} exited gracefully.", pid);
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // SIGKILL
        LOG.log(Level.WARNING, "Process tree PID {0} still alive after SIGTERM, forcing SIGKILL...", pid);
        for (ProcessHandle h : descendants) {
            if (h.isAlive()) {
                LOG.fine("SIGKILL descendant PID {0}", h.pid());
                h.destroyForcibly();
            }
        }
        if (proc.isAlive()) {
            proc.destroyForcibly();
        }
    }
}
