package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.contract.ToolExecutor;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.SteerEchoes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Every server stop — restart button, preference-driven restart, and the project-switch
 * rebind that calls {@code restartServer()} straight on the lifecycle — goes through
 * {@link ServerProcessLifecycle#stopServer()}. Run ids and pending steer echoes belong to
 * the process being torn down, so that method must forget them: a stale
 * {@code expectedRunId} makes the first steer after the new process starts fail.
 */
class ServerProcessLifecycleStopTest {

    @BeforeEach
    void setUp() {
        ActiveRunTracker.clear();
        SteerEchoes.clear();
    }

    @Test
    void stoppingTheServerForgetsRunIdsAndPendingSteerEchoes() {
        ActiveRunTracker.update("s1", runUpdate("run-1"));
        SteerEchoes.expect("s1", "steer_1");

        lifecycle().stopServer();

        assertNull(ActiveRunTracker.get("s1"), "a stopped process cannot still have a live run");
        assertFalse(SteerEchoes.consume("s1", "steer_1"), "nor an echo the new process will never send");
    }

    private static ServerProcessLifecycle lifecycle() {
        return new ServerProcessLifecycle(new AtomicReference<>(), mock(ToolExecutor.class),
                () -> { }, update -> { }, () -> { }, error -> { }, null, null, null);
    }

    /** The raw {@code session_info_update} node goose publishes the run id on. */
    private static JsonNode runUpdate(String runId) {
        ObjectNode root = MapperSupplier.get().createObjectNode();
        root.putObject("_meta").putObject("goose").put("activeRunId", runId);
        return root;
    }
}
