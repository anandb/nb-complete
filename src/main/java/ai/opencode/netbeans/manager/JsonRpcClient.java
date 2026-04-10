package ai.opencode.netbeans.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JsonRpcClient {
    private static final Logger LOG = Logger.getLogger(JsonRpcClient.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private final PrintWriter writer;
    private final BufferedReader reader;
    private final AtomicLong nextId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonNode>> notificationListeners = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private final Thread readerThread;

    public JsonRpcClient(Process process) {
        this.writer = new PrintWriter(process.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        
        readerThread = new Thread(this::readLoop, "OpenCode-JSONRPC-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void onNotification(String method, Consumer<JsonNode> listener) {
        notificationListeners.put(method, listener);
    }

    public CompletableFuture<JsonNode> sendRequest(String method, Object params) {
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
            LOG.log(Level.INFO, "Sending request: {0}", json);
            writer.println(json);
        } catch (JsonProcessingException e) {
            pendingRequests.remove(id);
            future.completeExceptionally(e);
        }

        return future;
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
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "Failed to serialize notification", e);
        }
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                LOG.log(Level.INFO, "Received line: {0}", line);
                try {
                    JsonNode node = mapper.readTree(line);
                    handleMessage(node);
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Error parsing JSON-RPC line: " + line, e);
                }
            }
        } catch (IOException e) {
            if (running) {
                LOG.log(Level.SEVERE, "JSON-RPC reader thread error", e);
            }
        }
    }

    private void handleMessage(JsonNode node) {
        if (node.has("id")) {
            // Response
            long id = node.get("id").asLong();
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
            }
        } else if (node.has("method")) {
            // Notification
            String method = node.get("method").asText();
            Consumer<JsonNode> listener = notificationListeners.get(method);
            if (listener != null) {
                listener.accept(node.get("params"));
            }
        }
    }

    public void close() {
        running = false;
        if (readerThread != null) {
            readerThread.interrupt();
        }
        writer.close();
        try {
            reader.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
