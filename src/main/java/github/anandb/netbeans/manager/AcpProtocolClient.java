package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.JsonParser;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.openide.util.RequestProcessor;

import github.anandb.netbeans.contract.RequestHandler;
import github.anandb.netbeans.support.Logger;

import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.WireLogger;

import static github.anandb.netbeans.manager.AgentUtils.closeQuietly;

public class AcpProtocolClient implements Closeable {
    private static final Logger LOG = new Logger(AcpProtocolClient.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private Consumer<Throwable> connectionErrorHandler;
    private Runnable disconnectionHandler;
    private RequestProcessor.Task readerTask;
    private RequestProcessor.Task errorReaderTask;
    private RequestProcessor watchdogRP;

    private final AtomicLong nextId = new AtomicLong(0);
    private final BufferedReader errorReader;
    private final InputStream inputStream;
    private final InputStream errorStream; // raw error stream from process
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> notificationListeners = new ConcurrentHashMap<>();
    private final Map<String, RequestHandler> requestHandlers = new ConcurrentHashMap<>();
    private final PrintWriter writer;
    private final WireLogger wireLogger;

    private volatile boolean running = true;
    private volatile long lastDataTime;

    public AcpProtocolClient(Process process) throws IOException {
        this.writer = new PrintWriter(process.getOutputStream(), true, StandardCharsets.UTF_8);
        this.inputStream = process.getInputStream();
        this.errorStream = process.getErrorStream();
        this.errorReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
        this.wireLogger = new WireLogger();
    }

    public void start() {
        readerTask = RequestProcessor.getDefault().post(this::readLoop);
        errorReaderTask = RequestProcessor.getDefault().post(this::readErrorLoop);
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
        return sendRequest(method, params, PluginSettings.getSessionIdleTimeout());
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
                    if (error instanceof TimeoutException) {
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
        try (JsonParser parser = MAPPER.getFactory().createParser(this.inputStream)) {
            MappingIterator<JsonNode> iterator = MAPPER.readValues(parser, JsonNode.class);
            while (running && iterator.hasNext()) {
                JsonNode node = iterator.next();
                lastDataTime = System.nanoTime();
                handleMessage(node);
            }
        } catch (Exception e) {
            if (running) {
                LOG.severe("JSON-RPC reader thread error", e);
                notifyConnectionError(e);
            }
        } finally {
            notifyDisconnection();
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
        if (pendingRequests.isEmpty()) {
            scheduleWatchdogCheck();
            return;
        }
        long timeout = PluginSettings.getSessionIdleTimeout();
        if (timeout <= 0) {
            scheduleWatchdogCheck();
            return;
        }
        long idleNanos = System.nanoTime() - lastDataTime;
        if (idleNanos >= TimeUnit.SECONDS.toNanos(timeout)) {
            LOG.warn("Connection idle for {0}s with pending requests, closing", TimeUnit.NANOSECONDS.toSeconds(idleNanos));
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

        // Close streams first to unblock reader loops, then cancel tasks.
        // Order matters: close underlying errorStream before errorReader to
        // avoid deadlock (readErrorLoop holds BufferedReader lock inside readLine()).
        closeQuietly(writer);
        closeQuietly(inputStream);
        closeQuietly(errorStream);
        closeQuietly(errorReader);

        if (readerTask != null) {
            readerTask.cancel();
        }
        if (errorReaderTask != null) {
            errorReaderTask.cancel();
        }

        pendingRequests.values().forEach(future -> {
            if (!future.isDone()) {
                future.completeExceptionally(new IOException("Client closed"));
            }
        });
        pendingRequests.clear();
    }
}
