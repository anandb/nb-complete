package github.anandb.netbeans.db;

import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.MessageEvent;
import github.anandb.netbeans.model.UsageRecords.MessageKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-process smoke test for the in-place-upgrade path: H2 refuses a database
 * file another process holds, so the store must park capture without losing rows,
 * and the one explicit retry must resume once that process is gone. A second JVM
 * holds the file because no in-process test can reproduce H2's file lock.
 *
 * <p>Runs as an integration test ({@code *IT}) in its own JVM under
 * {@code mvn verify}, after the unit suite.</p>
 */
class UsageStatsStoreFileLockIT {

    private static final long NOW = 1_700_000_000_000L;
    private static final long READY_TIMEOUT_SECONDS = 30;

    private static MessageEvent event(String sessionId) {
        return new MessageEvent(new Attribution(sessionId, "omp", "m1", "/p"), NOW, MessageKind.USER);
    }

    @Test
    void aLockedFileDisablesCaptureAndTheSingleRetryResumes(@TempDir Path dir) throws Exception {
        String url = "jdbc:h2:" + dir.resolve("usage-stats").toAbsolutePath().toString().replace('\\', '/');

        H2UsageStatsStore seed = new H2UsageStatsStore(url);
        seed.recordMessage(event("s1"));
        assertEquals(1, seed.query(1, null, NOW).messages(), "the seed row must be durable before the lock");
        seed.shutdown();

        Path marker = dir.resolve("holder.locked");
        Process holder = startHolder(url, marker);
        H2UsageStatsStore store = new H2UsageStatsStore(url);
        try {
            awaitLock(holder, marker);
            store.load();

            assertEquals(0, store.query(1, null, NOW).messages(),
                    "capture is disabled while another process holds the file");
            store.recordMessage(event("s2")); // must not throw while unavailable

            stopHolder(holder);
            store.retryAfterFailure();

            assertEquals(1, store.query(1, null, NOW).messages(),
                    "rows written before the lock must survive the failed open");
            store.recordMessage(event("s3"));
            assertEquals(2, store.query(1, null, NOW).messages(),
                    "the single retry resumes capture once the lock is released");
        } finally {
            stopHolder(holder);
            store.shutdown();
        }
    }

    private static Process startHolder(String url, Path marker) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) {
            classpath = System.getProperty("java.class.path");
        }
        return new ProcessBuilder(java, "-cp", classpath, DbLockHolder.class.getName(),
                url, marker.toString())
                .redirectErrorStream(true)
                .start();
    }

    private static void awaitLock(Process holder, Path marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(READY_TIMEOUT_SECONDS);
        while (!Files.exists(marker)) {
            if (!holder.isAlive()) {
                throw new IllegalStateException("Lock holder exited early: " + output(holder));
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException(
                        "Lock holder did not acquire the database within " + READY_TIMEOUT_SECONDS + "s: "
                                + output(holder));
            }
            Thread.sleep(50);
        }
    }

    private static String output(Process process) {
        try {
            process.getOutputStream().close();
            InputStream in = process.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<no output: " + e.getMessage() + ">";
        }
    }

    private static void stopHolder(Process holder) throws InterruptedException {
        if (holder == null) {
            return;
        }
        holder.destroy();
        assertTrue(holder.waitFor(10, TimeUnit.SECONDS), "the lock holder must exit");
    }
}
