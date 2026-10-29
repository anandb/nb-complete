package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * {@link ProcessTerminator} against real child processes. Skips on Windows (the
 * taskkill path cannot be exercised from a POSIX test JVM).
 */
class ProcessTerminatorTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    @Test
    void nullProcessIsANoOp() {
        ProcessTerminator.terminate(null);
    }

    @Test
    void alreadyDeadProcessIsANoOp() throws Exception {
        Process proc = new ProcessBuilder("sh", "-c", "exit 0").start();
        proc.waitFor(5, TimeUnit.SECONDS);

        ProcessTerminator.terminate(proc);

        assertFalse(proc.isAlive());
    }

    @Test
    void terminatesARunningProcess() throws Exception {
        assumeFalse(WINDOWS, "taskkill path is not exercised here");
        Process proc = new ProcessBuilder("sh", "-c", "sleep 60").start();
        assertTrue(proc.isAlive(), "child should be running before termination");

        ProcessTerminator.terminate(proc);

        assertTrue(proc.waitFor(10, TimeUnit.SECONDS), "process should exit after termination");
        assertFalse(proc.isAlive());
    }

    @Test
    void forcesProcessesThatIgnoreSigterm() throws Exception {
        assumeFalse(WINDOWS, "taskkill path is not exercised here");
        // Ignore SIGTERM in the shell, then sleep: only SIGKILL ends this one.
        Process proc = new ProcessBuilder("sh", "-c", "trap '' TERM; sleep 60").start();
        assertTrue(proc.isAlive());

        long start = System.nanoTime();
        ProcessTerminator.terminate(proc);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(proc.waitFor(5, TimeUnit.SECONDS), "process should be killed");
        assertFalse(proc.isAlive());
        // The SIGTERM wait is 3s; a forced kill must therefore take at least that long.
        assertTrue(elapsedMs >= 2_000, "expected the SIGTERM grace period, took " + elapsedMs + "ms");
    }
}
