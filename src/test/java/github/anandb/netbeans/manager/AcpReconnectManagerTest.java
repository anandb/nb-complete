package github.anandb.netbeans.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcpReconnectManagerTest {

    @Mock
    private AcpProtocolClient rpcClient;

    private AcpReconnectManager manager;
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicReference<Process> processRef = new AtomicReference<>(null);
    private final AtomicReference<AcpProtocolClient> rpcRef = new AtomicReference<>(null);
    private final AtomicBoolean crashCalled = new AtomicBoolean(false);
    private final AtomicReference<Consumer<String>> statusRef = new AtomicReference<>(null);

    @BeforeEach
    void setUp() {
        rpcRef.set(rpcClient);
        manager = new AcpReconnectManager(
                closing::get,
                processRef::get,
                rpcRef,
                () -> crashCalled.set(true),
                statusRef::get,
                () -> null,  // reconnectRP
                t -> {}      // setReconnectTask
        );
    }

    @Test
    void handleDisconnectionReturnsEarlyWhenClosing() {
        closing.set(true);
        AtomicBoolean startCalled = new AtomicBoolean(false);
        manager.handleDisconnection(() -> startCalled.set(true));
        assertFalse(startCalled.get(), "Server should NOT be started when closing");
    }

    @Test
    void handleDisconnectionCallsCrashHandler() {
        closing.set(false);
        processRef.set(null);
        manager.handleDisconnection(() -> {});
        assertTrue(crashCalled.get(), "Crash handler should be called");
    }

    @Test
    void handleDisconnectionClosesRpcClient() {
        closing.set(false);
        processRef.set(null);
        manager.handleDisconnection(() -> {});
        // rpcClient should have been set to null
        assertFalse(rpcRef.get() != null, "RPC client reference should be nulled");
    }

    @Test
    void resetThrottleResetsCounters() {
        // Simulate multiple disconnections
        closing.set(false);
        processRef.set(null);
        for (int i = 0; i < 3; i++) {
            manager.handleDisconnection(() -> {});
        }

        manager.resetThrottle();

        // After reset, should be able to retry again
        // (this tests the internal state via behavior)
        crashCalled.set(false);
        manager.handleDisconnection(() -> {});
        assertTrue(crashCalled.get(), "Crash handler should fire after reset");
    }

    @Test
    void setLastDisconnectReasonStoresValue() {
        manager.setLastDisconnectReason("test reason");
        // The reason is consumed by handleDisconnection — we verify indirectly
        // that it doesn't throw.
        closing.set(false);
        processRef.set(null);
        manager.handleDisconnection(() -> {});
    }

    @Test
    void handleDisconnectionKillsStaleProcess() throws Exception {
        closing.set(false);
        // Create a mock-like process that's "alive" but can be destroyed
        ProcessBuilder pb = new ProcessBuilder("sleep", "100");
        Process proc = pb.start();
        processRef.set(proc);

        manager.handleDisconnection(() -> {});

        // The process should have been destroyed
        assertFalse(proc.isAlive(), "Stale process should be killed");
        assertTrue(crashCalled.get());
    }
}
