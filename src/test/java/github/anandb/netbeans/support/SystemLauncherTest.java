package github.anandb.netbeans.support;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OS launches (desktop open, browser fork) must never run on the EDT, and a failed
 * launch must not surface as an unhandled exception on the background thread.
 */
class SystemLauncherTest {

    @Test
    void launchRunsOffTheEdt() throws Exception {
        AtomicReference<Boolean> onEdt = new AtomicReference<>();

        SystemLauncher.launch("open a directory", () -> onEdt.set(SwingUtilities.isEventDispatchThread()))
                .join();

        assertFalse(onEdt.get(), "the launch ran on the EDT");
    }

    @Test
    void launchRunsTheTaskAndCompletes() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();

        SystemLauncher.launch("open a directory", () -> ran.set(true)).join();

        assertTrue(ran.get(), "the launch task never ran");
    }

    @Test
    void failingLaunchIsLoggedNotRethrown() throws Exception {
        assertDoesNotThrow(() -> SystemLauncher.launch("open a directory", () -> {
            throw new IllegalStateException("desktop unavailable");
        }).join());
    }

    @Test
    void launchedFromTheEdtStillRunsOffIt() throws Exception {
        AtomicReference<Boolean> onEdt = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() ->
                SystemLauncher.launch("open a directory", () -> onEdt.set(SwingUtilities.isEventDispatchThread())));

        // The handle is not collected here; wait for the task to publish its answer.
        for (int i = 0; i < 200 && onEdt.get() == null; i++) {
            Thread.sleep(5);
        }
        assertFalse(onEdt.get(), "an EDT-initiated launch ran on the EDT");
    }
}
