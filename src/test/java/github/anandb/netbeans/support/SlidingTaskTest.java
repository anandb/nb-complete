package github.anandb.netbeans.support;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.RequestProcessor;

class SlidingTaskTest {

    private static final int WINDOW_MS = 100;

    private RequestProcessor rp;

    @BeforeEach
    void setUp() {
        rp = new RequestProcessor("SlidingTaskTest", 1);
    }

    @AfterEach
    void tearDown() {
        rp.stop();
    }

    @Test
    void scheduleWaitsForTheWindow() {
        AtomicInteger runs = new AtomicInteger();
        SlidingTask task = new SlidingTask(rp, WINDOW_MS, runs::incrementAndGet);

        task.schedule();
        await().atMost(2, TimeUnit.SECONDS).until(() -> runs.get() == 1);
    }

    @Test
    void burstRunsBodyExactlyOnce() {
        AtomicInteger runs = new AtomicInteger();
        SlidingTask task = new SlidingTask(rp, WINDOW_MS, runs::incrementAndGet);

        for (int i = 0; i < 5; i++) {
            task.schedule();
        }

        // Still deferred: a trigger may never run before the window elapses.
        assertEquals(0, runs.get(), "burst must not run before the coalescing window");

        await().atMost(2, TimeUnit.SECONDS).until(() -> runs.get() == 1);

        // And exactly one run: the burst must not queue extra runs behind it.
        await().during(WINDOW_MS * 3L, TimeUnit.MILLISECONDS)
                .atMost(WINDOW_MS * 6L, TimeUnit.MILLISECONDS)
                .until(() -> runs.get() == 1);
    }

    @Test
    void cancelDropsThePendingRun() {
        AtomicInteger runs = new AtomicInteger();
        SlidingTask task = new SlidingTask(rp, WINDOW_MS, runs::incrementAndGet);

        task.schedule();
        task.cancel();

        await().during(WINDOW_MS * 3L, TimeUnit.MILLISECONDS)
                .atMost(WINDOW_MS * 6L, TimeUnit.MILLISECONDS)
                .until(() -> runs.get() == 0);
    }

    @Test
    void reschedulingAfterCancelStillRuns() {
        AtomicInteger runs = new AtomicInteger();
        SlidingTask task = new SlidingTask(rp, WINDOW_MS, runs::incrementAndGet);

        task.schedule();
        task.cancel();
        task.schedule();

        await().atMost(2, TimeUnit.SECONDS).until(() -> runs.get() == 1);
    }
}
