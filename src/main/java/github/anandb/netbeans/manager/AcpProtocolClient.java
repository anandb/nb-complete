package github.anandb.netbeans.manager;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import github.anandb.netbeans.contract.RequestHandler;
import github.anandb.netbeans.support.Logger;

import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.WireLogger;

import static github.anandb.netbeans.manager.AgentUtils.closeQuietly;

public class AcpProtocolClient implements Closeable {
    private static final Logger LOG = new Logger(AcpProtocolClient.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 30; // seconds, applied to all requests
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private final PrintWriter writer;
    private final BufferedReader reader;
    private final BufferedReader errorReader;
    private final AtomicLong nextId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> notificationListeners = new ConcurrentHashMap<>();
    private final Map<String, RequestHandler> requestHandlers = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private Thread readerThread;
    private Thread errorReaderThread;
    private Consumer<Throwable> connectionErrorHandler;
    private Runnable disconnectionHandler;
    private final WireLogger wireLogger;

    private volatile long lastDataTime;
    private ScheduledExecutorService watchdogExecutor;
    private ScheduledFuture<?> watchdogFuture;

    public AcpProtocolClient(Process process) {
        this.writer = new PrintWriter(process.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        this.wireLogger = new WireLogger();
    }

    public void start() {
        readerThread = new Thread(this::readLoop, "ACP-JSONRPC-Reader");
        readerThread.setDaemon(true);
        readerThread.start();

        errorReaderThread = new Thread(this::readErrorLoop, "ACP-JSONRPC-ErrorReader");
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();

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
        return sendRequest(method, params, DEFAULT_TIMEOUT_SECONDS);
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params, long timeoutSeconds) {
        long id = nextId.getAndIncrement();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        
        long sendStart = System.nanoTime();

        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", MAPPER.valueToTree(params));

        try {
            String json = MAPPER.writeValueAsString(request);
            LOG.fine("[ACP] Sending request: {0}", method);
            writer.println(json);
            long sendEnd = System.nanoTime();
            long sendMs = (sendEnd - sendStart) / 1_000_000;
            LOG.info("[ACP] Request {0} (id={1}) sent in {2}ms", method, id, sendMs);
            if (writer.checkError()) {
                pendingRequests.remove(id);
                IOException ex = new IOException("Failed to write request");
                future.completeExceptionally(ex);
                notifyConnectionError(ex);
            }

            wireLogger.log(json);
        } catch (IOException e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }

        if (timeoutSeconds > 0) {
            return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .whenComplete((result, error) -> {
                        if (error instanceof java.util.concurrent.TimeoutException) {
                            pendingRequests.remove(id);
                            long totalMs = timeoutSeconds * 1000;
                            LOG.warn("Request timed out: method={0}, id={1} after {2}ms", new Object[]{method, id, totalMs});
                        } else if (error != null) {
                            long totalMs = (System.nanoTime() - sendStart) / 1_000_000;
                            LOG.warn("Request failed: method={0}, id={1} after {2}ms - {3}", new Object[]{method, id, totalMs, error.getMessage()});
                        } else {
                            long totalMs = (System.nanoTime() - sendStart) / 1_000_000;
                            LOG.info("Request completed: method={0}, id={1} in {2}ms", new Object[]{method, id, totalMs});
                        }
                    });
        }

        return future;
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params, long timeout, TimeUnit unit) {
        long timeoutSeconds = unit.toSeconds(timeout);
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
        return sendRequest(method, params, timeoutSeconds);
    }

    public void sendNotification(String method, Object params) {
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", MAPPER.valueToTree(params));

        try {
            String json = MAPPER.writeValueAsString(notification);
            LOG.fine("[ACP] Sending notification: {0}", method);
            writer.println(json);
            if (writer.checkError()) {
                LOG.severe("Failed to write notification");
                notifyConnectionError(new IOException("Failed to write notification"));
            }

            wireLogger.log(json);
        } catch (IOException e) {
            LOG.severe("Failed to serialize notification", e);
        }
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                lastDataTime = System.nanoTime();
                // Fast path: skip empty lines without creating trimmed string
                if (line.isEmpty()) {
                    continue;
                }
                // Check raw line first - avoid trim() if possible
                char firstChar = line.charAt(0);
                char lastChar = line.charAt(line.length() - 1);
                // Skip whitespace-only lines by checking first/last char
                if ((firstChar <= ' ' && StringUtils.isBlank(line)) || (lastChar <= ' ' && StringUtils.isBlank(line))) {
                    continue;
                }
                // Quick check for JSON object start on raw line
                int idx = 0;
                while (idx < line.length() && line.charAt(idx) <= ' ') {
                    idx++;
                }
                if (idx >= line.length() || line.charAt(idx) != '{') {
                    LOG.fine("Ignoring non-JSON process output: {0}", line);
                    continue;
                }

                try {
                    // Direct readTree is faster than creating JsonParser + MappingIterator
                    JsonNode node = MAPPER.readTree(line);
                    handleMessage(node);
                } catch (Exception e) {
                    LOG.warn("Error parsing JSON-RPC line: {0}", new Object[]{line});
                    LOG.fine("Parse exception", e);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOG.severe("JSON-RPC reader thread error", e);
                notifyConnectionError(e);
            }
        } finally {
            if (running) {
                notifyDisconnection();
            }
        }
    }

    private void readErrorLoop() {
        try {
            String line;
            while (running && (line = errorReader.readLine()) != null) {
                LOG.warn("Process stderr: {0}", line);
            }
        } catch (IOException e) {
            if (running) {
                LOG.fine("Error reader thread error", e);
            }
        }
    }

    private void startWatchdog() {
        lastDataTime = System.nanoTime();
        watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ACP-IdleWatchdog");
            t.setDaemon(true);
            return t;
        });
        watchdogFuture = watchdogExecutor.scheduleWithFixedDelay(
                this::checkIdleTimeout, 5, 5, TimeUnit.SECONDS);
    }

    private void checkIdleTimeout() {
        if (!running) return;
        if (pendingRequests.isEmpty()) return;
        long timeout = PluginSettings.getSessionIdleTimeout();
        if (timeout <= 0) return;
        long idleNanos = System.nanoTime() - lastDataTime;
        if (idleNanos >= TimeUnit.SECONDS.toNanos(timeout)) {
            LOG.warn("Connection idle for {0}s with pending requests, closing", TimeUnit.NANOSECONDS.toSeconds(idleNanos));
            close();
            notifyDisconnection();
        }
    }

    public void touch() {
        lastDataTime = System.nanoTime();
    }

    private void stopWatchdog() {
        if (watchdogFuture != null) {
            watchdogFuture.cancel(false);
            watchdogFuture = null;
        }
        if (watchdogExecutor != null) {
            watchdogExecutor.shutdown();
            watchdogExecutor = null;
        }
    }

    private void handleMessage(JsonNode node) {
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
                long recvTime = System.nanoTime();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    if (node.has("error")) {
                        JsonNode errNode = node.get("error");
                        String errMsg = errNode.has("message") ? errNode.get("message").asText() : errNode.toString();
                        LOG.info("[ACP] Received error response for id={0}: {1}", id, errMsg);
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
                    LOG.warn("Notification handler error for method: {0} {1}", method, e);
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
                        sendError(id, -32603, ex.getMessage());
                        return null;
                    });
        } else {
            LOG.warn("No handler for request method: {0}", method);
            sendError(id, -32601, "Method not found: " + method);
        }
    }

    private void sendResponse(long id, JsonNode result) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result != null ? result : MAPPER.createObjectNode());

        try {
            String json = MAPPER.writeValueAsString(response);
            LOG.fine("[ACP] Sending response: {0}", json);
            writer.println(json);
        } catch (JsonProcessingException e) {
            LOG.severe("Failed to serialize response", e);
        }
    }

    private void sendError(long id, int code, String message) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);

        try {
            String json = MAPPER.writeValueAsString(response);
            LOG.fine("[ACP] Sending error response: {0}", message);
            writer.println(json);
        } catch (JsonProcessingException e) {
            LOG.severe("Failed to serialize error response", e);
        }
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
        running = false;
        stopWatchdog();
        closeQuietly(wireLogger);

        pendingRequests.values().forEach(future -> {
            if (!future.isDone()) {
                future.completeExceptionally(new IOException("Client closed"));
            }
        });
        pendingRequests.clear();

        if (readerThread != null) {
            readerThread.interrupt();
        }
        if (errorReaderThread != null) {
            errorReaderThread.interrupt();
        }

        closeQuietly(writer);
        closeQuietly(reader);
        closeQuietly(errorReader);
    }
}
