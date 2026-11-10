package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.io.InputStream;
import java.io.OutputStream;

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

    @Test
    void windowsPathFallsBackToForciblyDestroyingProcessWhenTaskkillIsUnavailable() {
        assumeFalse(WINDOWS, "this exercises the Windows branch from a non-Windows JVM");
        String previous = System.getProperty("os.name");
        FakeProcess proc = new FakeProcess();
        try {
            System.setProperty("os.name", "Windows");
            ProcessTerminator.terminate(proc);
        } finally {
            if (previous == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", previous);
            }
        }
        assertTrue(proc.forciblyDestroyed);
    }

    private static final class FakeProcess extends Process {
        private boolean alive = true;
        private boolean forciblyDestroyed;

        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return alive ? 1 : 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return false; }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public int exitValue() { return alive ? 1 : 0; }
        @Override public void destroy() { }
        @Override public Process destroyForcibly() {
            forciblyDestroyed = true;
            alive = false;
            return this;
        }
        @Override public boolean isAlive() { return alive; }
        @Override public long pid() { return 123; }
    }
}
