package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class VcsUtilsRunCapturedTest {

    private static boolean canRun(String... cmd) {
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor(10, TimeUnit.SECONDS) && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void runCapturedTimesOutHungProcess() throws Exception {
        assumeTrue(canRun("sleep", "0"), "sleep not available");
        long start = System.nanoTime();
        VcsUtils.CapturedCommand captured = VcsUtils.runCaptured(new File("."), 1, "sleep", "30");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(captured.timedOut());
        assertTrue(elapsedMs < 10_000, "timeout must apply during hang, not after stdout EOF: " + elapsedMs);
    }

    @Test
    void runCapturedCapsOversizedOutput() throws Exception {
        assumeTrue(canRun("sh", "-c", "yes | head -n 1"), "yes/head not available");
        VcsUtils.CapturedCommand captured = VcsUtils.runCaptured(new File("."), 10, "yes", "xxxxxxxxxxxxxxxxxxxx");
        assertTrue(captured.output().length() <= VcsUtils.CAPTURE_MAX_CHARS);
        assertTrue(captured.truncated());
    }
}
