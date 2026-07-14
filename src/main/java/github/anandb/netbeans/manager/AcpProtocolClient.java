package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.openide.util.RequestProcessor;

import org.apache.commons.lang3.exception.ExceptionUtils;

import github.anandb.netbeans.contract.RequestHandler;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.WireLogger;

import static github.anandb.netbeans.support.AgentUtils.closeQuietly;

public class AcpProtocolClient implements Closeable {
    private static final Logger LOG = Logger.from(AcpProtocolClient.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private volatile Consumer<Throwable> connectionErrorHandler;
    private volatile Runnable disconnectionHandler;
    private volatile Thread readerThread;
    private volatile RequestProcessor watchdogRP;

    private final AtomicLong nextId = new AtomicLong(0);
    private final InputStream inputStream;
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    /** Per-request idle timeout in seconds. A request times out when no data arrives on the connection for this duration. */
    private final Map<Long, Long> pendingRequestIdleTimeouts = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> notificationListeners = new ConcurrentHashMap<>();
    private final Map<String, RequestHandler> requestHandlers = new ConcurrentHashMap<>();
    private final BufferedWriter writer;
    private final WireLogger wireLogger;

    private volatile boolean running = true;
    private volatile boolean closed = false;
    private volatile long lastDataTime;
    private volatile String closeReason;

    public AcpProtocolClient(Process process) throws IOException {
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.inputStream = process.getInputStream();
        this.wireLogger = new WireLogger();
    }

    public void start() {
        readerThread = new Thread(this::readLoop, "ACP-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
        startWatchdog();
    }

    public void onNotification(String method, Consumer<JsonNode> listener) {
        notificationListeners.put(method, listener);
    }

    public void onRequest(String method, RequestHandler handler) {
        requestHandlers.put(method, handler);
    }

    public void setConnectionErrorHandler(Consumer<Throwable> handler) {
        this.connectionErrorHandler = handler;
    }

    public void setDisconnectionHandler(Runnable handler) {
        this.disconnectionHandler = handler;
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params) {
        return sendRequest(method, params, 0);
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params, long idleTimeoutSeconds) {
        if (closed) {
            return CompletableFuture.failedFuture(new IOException("Client closed"));
        }
        long id = nextId.getAndIncrement();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        // Re-check closed after put — if close() drained the map between
        // our top-level guard and this put, remove the orphaned future.
        if (closed) {
            pendingRequests.remove(id);
            future.completeExceptionally(new IOException("Client closed"));
            return future;
        }

        long sendStart = System.nanoTime();

        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", MAPPER.valueToTree(params));

        try {
            String json = MAPPER.writeValueAsString(request);
            LOG.fine("[ACP] Sending request: {0}", method);
            synchronized (writer) {
                writer.write(json);
                writer.newLine();
                writer.flush();
            }
            touch();
            long sendEnd = System.nanoTime();
            long sendMs = (sendEnd - sendStart) / 1_000_000;
            LOG.info("[ACP] Request {0} (id={1}) sent in {2}ms", method, id, sendMs);

            wireLogger.log(json);
        } catch (IOException e) {
            pendingRequests.remove(id);
            LOG.severe("IOException sending request method={0}, id={1}", method, id, e);
            future.completeExceptionally(e);
            notifyConnectionError(e);
        }

        if (idleTimeoutSeconds > 0) {
            pendingRequestIdleTimeouts.put(id, idleTimeoutSeconds);
        }

        return future;
    }

    public void sendNotification(String method, Object params) {
        if (closed) return;
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", MAPPER.valueToTree(params));

        try {
            String json = MAPPER.writeValueAsString(notification);
            LOG.fine("[ACP] Sending notification: {0}", method);
            synchronized (writer) {
                writer.write(json);
                writer.newLine();
                writer.flush();
            }
            touch();

            wireLogger.log(json);
        } catch (IOException e) {
            LOG.severe("Failed to send notification: {0}", method, e);
            notifyConnectionError(e);
        }
    }

    private void readLoop() {
        try (JsonParser parser = MAPPER.getFactory().createParser(this.inputStream)) {
            MappingIterator<JsonNode> iterator = MAPPER.readValues(parser, JsonNode.class);
            while (running && iterator.hasNext()) {
                try {
                    JsonNode node = iterator.next();
                    lastDataTime = System.nanoTime();
                    handleMessage(node);
                } catch (Exception e) {
                    // Recover from a single malformed message instead of
                    // shutting down the entire connection.
                    if (running) {
                        LOG.warn("Skipping malformed message: {0}", e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                LOG.severe("JSON-RPC reader thread error", e);
                notifyConnectionError(e);
            } else {
                LOG.warn("JSON-RPC reader thread error after close: {0}", e.getMessage());
            }
        } finally {
            if (!closed) {
                notifyDisconnection();
            }
        }
    }

    private void startWatchdog() {
        lastDataTime = System.nanoTime();
        watchdogRP = new RequestProcessor("ACP-Watchdog", 1, true);
        scheduleWatchdogCheck();
    }

    private void scheduleWatchdogCheck() {
        if (running && watchdogRP != null) {
            watchdogRP.post(this::checkIdleTimeout, 5000);
        }
    }

    private void checkIdleTimeout() {
        if (!running) return;

        long now = System.nanoTime();
        long idleNanos = now - lastDataTime;

        // Check per-request idle timeouts — fail individual requests when
        // no data arrives on the connection for the specified duration.
        // Uses connection-level lastDataTime so ANY inbound data resets
        // the idle timer for all pending requests.
        if (!pendingRequestIdleTimeouts.isEmpty()) {
            List<Long> timedOut = new ArrayList<>();
            for (Map.Entry<Long, Long> entry : pendingRequestIdleTimeouts.entrySet()) {
                long id = entry.getKey();
                long idleTimeoutSecs = entry.getValue();
                if (idleNanos >= TimeUnit.SECONDS.toNanos(idleTimeoutSecs)) {
                    timedOut.add(id);
                }
            }
            for (Long id : timedOut) {
                pendingRequestIdleTimeouts.remove(id);
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null && !future.isDone()) {
                    long idleSecs = TimeUnit.NANOSECONDS.toSeconds(idleNanos);
                    LOG.warn("Request id={0} idle timeout after {1}s", id, idleSecs);
                    future.completeExceptionally(
                            new TimeoutException("No response received for " + idleSecs + "s"));
                }
            }
        }

        // Global connection idle timeout (existing behavior)
        if (pendingRequests.isEmpty()) {
            scheduleWatchdogCheck();
            return;
        }
        long timeout = PluginSettings.getSessionIdleTimeout();
        if (timeout <= 0) {
            scheduleWatchdogCheck();
            return;
        }
        if (idleNanos >= TimeUnit.SECONDS.toNanos(timeout)) {
            long idleSecs = TimeUnit.NANOSECONDS.toSeconds(idleNanos);
            int pending = pendingRequests.size();
            LOG.warn("Connection idle for {0}s with {1} pending request(s), closing",
                    idleSecs, pending);
            closeReason = "Connection idle for " + idleSecs + "s with "
                    + pending + " pending request(s)";
            close();
            notifyDisconnection();
            return;
        }
        scheduleWatchdogCheck();
    }

    public void touch() {
        lastDataTime = System.nanoTime();
    }

    private void stopWatchdog() {
        if (watchdogRP != null) {
            watchdogRP.stop();
            watchdogRP = null;
        }
    }

    private void handleMessage(JsonNode node) {
        // Any incoming message proves the connection is alive
        touch();
        wireLogger.log(node);
        if (node.has("id")) {
            long id = node.get("id").asLong();
            if (node.has("method")) {
                // Incoming Request
                String method = node.get("method").asText();
                JsonNode params = node.has("params") ? node.get("params") : MAPPER.createObjectNode();
                handleIncomingRequest(id, method, params);
            } else {
                // Response to Outgoing Request
                pendingRequestIdleTimeouts.remove(id);
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    if (node.has("error")) {
                        JsonNode errNode = node.get("error");
                        String errMsg = errNode.has("message") ? errNode.get("message").asText() : errNode.toString();
                        LOG.warn("[ACP] Error response for id={0}: {1}", id, errMsg);
                        future.completeExceptionally(new RuntimeException(errMsg));
                    } else if (node.has("result")) {
                        LOG.fine("[ACP] Received response for id={0}", id);
                        future.complete(node.get("result"));
                    } else {
                        future.complete(null);
                    }
                } else {
                    LOG.warn("Received response for unknown request id: {0}", id);
                }
            }
        } else if (node.has("method")) {
            // Incoming Notification
            String method = node.get("method").asText();
            Consumer<JsonNode> listener = notificationListeners.get(method);
            if (listener != null) {
                try {
                    JsonNode params = node.has("params") ? node.get("params") : MAPPER.createObjectNode();
                    listener.accept(params);
                } catch (Exception e) {
                    LOG.warn("Notification handler error for method: {0}", method, e);
                }
            } else {
                LOG.fine("No listener for notification method: {0}", method);
            }
        } else {
            LOG.fine("Received unknown JSON payload: {0}", node);
        }
    }

    private void handleIncomingRequest(long id, String method, JsonNode params) {
        RequestHandler handler = requestHandlers.get(method);
        if (handler != null) {
            handler.handle(params)
                    .thenAccept(result -> sendResponse(id, result))
                    .exceptionally(ex -> {
                        LOG.severe("Error handling request {0} ({1})", method, id, ex);
                        sendError(id, -32603, ExceptionUtils.getRootCauseMessage(ex));
                        return null;
                    });
        } else {
            LOG.warn("No handler for request method: {0}", method);
            sendError(id, -32601, "Method not found: " + method);
        }
    }

    private void sendJsonMessage(ObjectNode message) {
        try {
            String json = MAPPER.writeValueAsString(message);
            LOG.fine("[ACP] Sending: {0}", json);
            synchronized (writer) {
                writer.write(json);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            LOG.severe("Failed to write message", e);
        }
    }

    private void sendResponse(long id, JsonNode result) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result != null ? result : MAPPER.createObjectNode());
        sendJsonMessage(response);
    }

    private void sendError(long id, int code, String message) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        sendJsonMessage(response);
    }

    private void notifyConnectionError(Throwable t) {
        if (connectionErrorHandler != null) {
            try {
                connectionErrorHandler.accept(t);
            } catch (Exception e) {
                LOG.warn("Connection error handler threw exception", e);
            }
        }
    }

    private void notifyDisconnection() {
        if (disconnectionHandler != null) {
            try {
                disconnectionHandler.run();
            } catch (Exception e) {
                LOG.warn("Disconnection handler threw exception", e);
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        running = false;
        stopWatchdog();
        closeQuietly(wireLogger);

        // Close streams first to unblock reader loops, then interrupt threads.
        // Order matters: close underlying errorStream before errorReader to
        // avoid deadlock (readErrorLoop holds BufferedReader lock inside readLine()).
        // Synchronize on writer to prevent concurrent write/close race.
        // BufferedWriter is not thread-safe for concurrent close + write.
        synchronized (writer) {
            closeQuietly(writer);
        }
        closeQuietly(inputStream);

        // Interrupt reader thread — cancel() on RequestProcessor tasks does not
        // stop running threads, leaving them hung on blocking I/O indefinitely.
        if (readerThread != null) {
            readerThread.interrupt();
        }

        String reason = closeReason;
        int drainCount = (int) pendingRequests.values().stream().filter(f -> !f.isDone()).count();
        if (drainCount > 0) {
            LOG.warn("Closing client with {0} pending request(s)", drainCount);
        }
        pendingRequests.values().forEach(future -> {
            if (!future.isDone()) {
                future.completeExceptionally(new IOException(
                        reason != null ? reason : "Client closed"));
            }
        });
        pendingRequests.clear();
        pendingRequestIdleTimeouts.clear();
        notificationListeners.clear();
        requestHandlers.clear();
    }
}
