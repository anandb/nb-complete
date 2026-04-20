package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonRpcClient {
    private static final Logger LOG = Logger.getLogger(JsonRpcClient.class.getName());
    private static final long DEFAULT_TIMEOUT_SECONDS = 0; // 0 means no timeout by default

    private final ObjectMapper mapper = new ObjectMapper();
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

    public JsonRpcClient(Process process) {
        this.writer = new PrintWriter(process.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
    }

    public void start() {
        readerThread = new Thread(this::readLoop, "ACP-JSONRPC-Reader");
        readerThread.setDaemon(true);
        readerThread.start();

        errorReaderThread = new Thread(this::readErrorLoop, "ACP-JSONRPC-ErrorReader");
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();
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

        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", mapper.valueToTree(params));

        try {
            String json = mapper.writeValueAsString(request);
            LOG.log(Level.FINE, "Sending request: {0}", json);
            writer.println(json);
            if (writer.checkError()) {
                pendingRequests.remove(id);
                IOException ex = new IOException("Failed to write request");
                future.completeExceptionally(ex);
                notifyConnectionError(ex);
            }
        } catch (JsonProcessingException e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }

        if (timeoutSeconds > 0) {
            return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .whenComplete((result, error) -> {
                        if (error instanceof java.util.concurrent.TimeoutException) {
                            pendingRequests.remove(id);
                            LOG.log(Level.WARNING, "Request timed out: method={0}, id={1}", new Object[]{method, id});
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
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", mapper.valueToTree(params));

        try {
            String json = mapper.writeValueAsString(notification);
            LOG.log(Level.FINE, "Sending notification: {0}", json);
            writer.println(json);
            if (writer.checkError()) {
                LOG.log(Level.SEVERE, "Failed to write notification");
                notifyConnectionError(new IOException("Failed to write notification"));
            }
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "Failed to serialize notification", e);
        }
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!trimmed.startsWith("{")) {
                    LOG.log(Level.FINE, "Ignoring non-JSON process output: {0}", line);
                    continue;
                }
                LOG.log(Level.FINE, "Received JSON-RPC line: {0}", line);
                try {
                    JsonNode node = mapper.readTree(line);
                    handleMessage(node);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error parsing JSON-RPC line: {0}", new Object[]{line});
                    LOG.log(Level.FINE, "Parse exception", e);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOG.log(Level.SEVERE, "JSON-RPC reader thread error", e);
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
                LOG.log(Level.WARNING, "Process stderr: {0}", line);
            }
        } catch (IOException e) {
            if (running) {
                LOG.log(Level.FINE, "Error reader thread error", e);
            }
        }
    }

    private void handleMessage(JsonNode node) {
        if (node.has("id")) {
            long id = node.get("id").asLong();
            if (node.has("method")) {
                // Incoming Request
                String method = node.get("method").asText();
                JsonNode params = node.has("params") ? node.get("params") : mapper.createObjectNode();
                handleIncomingRequest(id, method, params);
            } else {
                // Response to Outgoing Request
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    if (node.has("error")) {
                        JsonNode errNode = node.get("error");
                        String errMsg = errNode.has("message") ? errNode.get("message").asText() : errNode.toString();
                        future.completeExceptionally(new RuntimeException(errMsg));
                    } else if (node.has("result")) {
                        future.complete(node.get("result"));
                    } else {
                        future.complete(null);
                    }
                } else {
                    LOG.log(Level.WARNING, "Received response for unknown request id: {0}", id);
                }
            }
        } else if (node.has("method")) {
            // Incoming Notification
            String method = node.get("method").asText();
            Consumer<JsonNode> listener = notificationListeners.get(method);
            if (listener != null) {
                try {
                    JsonNode params = node.has("params") ? node.get("params") : mapper.createObjectNode();
                    listener.accept(params);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Notification handler error for method: {0}", method);
                    LOG.log(Level.FINE, "Handler exception", e);
                }
            } else {
                LOG.log(Level.FINE, "No listener for notification method: {0}", method);
            }
        }
    }

    private void handleIncomingRequest(long id, String method, JsonNode params) {
        RequestHandler handler = requestHandlers.get(method);
        if (handler != null) {
            handler.handle(params)
                    .thenAccept(result -> sendResponse(id, result))
                    .exceptionally(ex -> {
                        LOG.log(Level.SEVERE, "Error handling request " + method + " (" + id + ")", ex);
                        sendError(id, -32603, ex.getMessage());
                        return null;
                    });
        } else {
            LOG.log(Level.WARNING, "No handler for request method: {0}", method);
            sendError(id, -32601, "Method not found: " + method);
        }
    }

    private void sendResponse(long id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result != null ? result : mapper.createObjectNode());

        try {
            String json = mapper.writeValueAsString(response);
            LOG.log(Level.FINE, "Sending response: {0}", json);
            writer.println(json);
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "Failed to serialize response", e);
        }
    }

    private void sendError(long id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);

        try {
            String json = mapper.writeValueAsString(response);
            LOG.log(Level.FINE, "Sending error response: {0}", json);
            writer.println(json);
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "Failed to serialize error response", e);
        }
    }

    @FunctionalInterface
    public interface RequestHandler {
        CompletableFuture<JsonNode> handle(JsonNode params);
    }

    private void notifyConnectionError(Throwable t) {
        if (connectionErrorHandler != null) {
            try {
                connectionErrorHandler.accept(t);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Connection error handler threw exception", e);
            }
        }
    }

    private void notifyDisconnection() {
        if (disconnectionHandler != null) {
            try {
                disconnectionHandler.run();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Disconnection handler threw exception", e);
            }
        }
    }

    public void close() {
        running = false;

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

        writer.close();
        try {
            reader.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error closing reader", e);
        }
        try {
            errorReader.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Error closing error reader", e);
        }
    }
}