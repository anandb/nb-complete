package github.anandb.netbeans.manager;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.awt.NotificationDisplayer;

import javax.swing.SwingUtilities;

import github.anandb.netbeans.support.Logger;

class AcpReconnectManager {

    private static final Logger LOG = Logger.from(AcpReconnectManager.class);

    private int restartCount = 0;
    private long lastRestartTime = 0;
    private static final int MAX_RESTARTS = 3;
    private static final long RESTART_RESET_INTERVAL = 300000;

    private final BooleanSupplier isClosing;
    private final Supplier<Process> serverProcess;
    private final AtomicReference<AcpProtocolClient> rpcClient;
    private final Runnable crashHandler;
    private final Supplier<Consumer<String>> statusListener;
    private final Supplier<RequestProcessor> reconnectRP;
    private final Consumer<RequestProcessor.Task> setReconnectTask;
    private volatile String lastDisconnectReason;

    AcpReconnectManager(
            BooleanSupplier isClosing,
            Supplier<Process> serverProcess,
            AtomicReference<AcpProtocolClient> rpcClient,
            Runnable crashHandler,
            Supplier<Consumer<String>> statusListener,
            Supplier<RequestProcessor> reconnectRP,
            Consumer<RequestProcessor.Task> setReconnectTask) {
        this.isClosing = isClosing;
        this.serverProcess = serverProcess;
        this.rpcClient = rpcClient;
        this.crashHandler = crashHandler;
        this.statusListener = statusListener;
        this.reconnectRP = reconnectRP;
        this.setReconnectTask = setReconnectTask;
    }

    synchronized void handleDisconnection(Runnable onStartServer) {
        if (isClosing.getAsBoolean()) {
            // Double-get is safe here: serverProcess Supplier wraps an AtomicReference
            // that is written only once (ServerProcessLifecycle.start()) and cleared
            // only after destroy. In the synchronized context of this method the
            // value cannot change between the null check and pid().
            Process p = serverProcess.get();
            LOG.warn("handleDisconnection called while closing — returning early (PID: {0})",
                    p != null ? p.pid() : "unknown");
            return;
        }

        Process proc = serverProcess.get();
        LOG.warn("ACP server disconnected unexpectedly (PID: {0})",
                proc != null ? proc.pid() : "unknown");

        if (proc != null && proc.isAlive()) {
            LOG.warn("Stale process PID {0} is still alive but pipes are broken — killing it",
                    proc.pid());
            List<ProcessHandle> descendants = proc.descendants().toList();
            for (ProcessHandle h : descendants) {
                if (h.isAlive()) {
                    h.destroyForcibly();
                }
            }
            proc.destroyForcibly();
            try {
                proc.waitFor(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        AcpProtocolClient staleClient = rpcClient.getAndSet(null);
        if (staleClient != null) {
            staleClient.close();
        } else {
            LOG.warn("rpcClient was already null in handleDisconnection — pending futures will never complete");
        }

        if (crashHandler != null) {
            try {
                crashHandler.run();
            } catch (Exception ex) {
                LOG.warn("Crash handler failed", ex);
            }
        }

        SwingUtilities.invokeLater(() -> {
            String reason = lastDisconnectReason;
            lastDisconnectReason = null;
            String detail = (reason != null && !reason.isEmpty()) ? reason : "Unknown error";
            NotificationDisplayer.getDefault().notify(
                "ACP Server Disconnected",
                NotificationDisplayer.Priority.HIGH.getIcon(),
                detail + " — Attempting to reconnect...",
                null,
                NotificationDisplayer.Priority.HIGH
            );
        });

        long now = System.currentTimeMillis();
        if (now - lastRestartTime > RESTART_RESET_INTERVAL) {
            restartCount = 0;
        }

        if (restartCount < MAX_RESTARTS) {
            restartCount++;
            lastRestartTime = now;
            long delay = restartCount * 3000L;
            LOG.fine("Respawning ACP server in {0}ms (attempt {1}/{2})...",
                    new Object[]{delay, restartCount, MAX_RESTARTS});

            RequestProcessor rp = reconnectRP.get();
            if (rp != null) {
                RequestProcessor.Task task = rp.post(onStartServer, (int) delay);
                setReconnectTask.accept(task);
            }
        } else {
            LOG.severe("ACP server crashed {0} times within {1}ms. Giving up.",
                       new Object[]{MAX_RESTARTS, RESTART_RESET_INTERVAL});
            Consumer<String> listener = statusListener.get();
            if (listener != null) {
                listener.accept(NbBundle.getMessage(ProcessManager.class, "ERR_ServerCrashed", MAX_RESTARTS));
            }
        }
    }

    synchronized void resetThrottle() {
        restartCount = 0;
        lastRestartTime = 0;
    }

    void setLastDisconnectReason(String reason) {
        this.lastDisconnectReason = reason;
    }
}
