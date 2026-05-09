package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import github.anandb.netbeans.support.Logger;

public class McpServer {

    private static final Logger LOG = new Logger(McpServer.class);
    private static final int PORT = 8765;

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/sse", this::handleSse);
        server.createContext("/message", this::handleMessage);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MCP-Server");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        LOG.info("MCP weather server started on port {0}", PORT);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        for (SseClient c : sseClients) {
            c.close();
        }
        sseClients.clear();
        LOG.info("MCP weather server stopped");
    }

    public boolean isRunning() {
        return server != null;
    }

    public String getUrl() {
        return "http://localhost:" + PORT + "/sse";
    }

    private void handleSse(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        try {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            String endpointEvent = "event: endpoint\ndata: /message\n\n";
            out.write(endpointEvent.getBytes(StandardCharsets.UTF_8));
            out.flush();
            SseClient client = new SseClient(out);
            sseClients.add(client);
            while (!client.isClosed()) {
                Thread.sleep(1000);
            }
        } catch (IOException | InterruptedException e) {
            // client disconnected
        } finally {
            exchange.close();
        }
    }

    private void handleMessage(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            JsonNode request = mapper.readTree(body.toString());
            String method = request.get("method").asText();
            JsonNode params = request.get("params");
            long id = request.has("id") ? request.get("id").asLong() : 0;

            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            switch (method) {
                case "tools/list" -> {
                    ObjectNode result = mapper.createObjectNode();
                    ArrayNode tools = mapper.createArrayNode();
                    ObjectNode tool = mapper.createObjectNode();
                    tool.put("name", "get_weather");
                    tool.put("description", "Get the current weather for a location");
                    ObjectNode schema = mapper.createObjectNode();
                    schema.put("type", "object");
                    ObjectNode properties = mapper.createObjectNode();
                    ObjectNode locationProp = mapper.createObjectNode();
                    locationProp.put("type", "string");
                    locationProp.put("description", "City name");
                    properties.set("location", locationProp);
                    schema.set("properties", properties);
                    ArrayNode required = mapper.createArrayNode();
                    required.add("location");
                    schema.set("required", required);
                    tool.set("inputSchema", schema);
                    tools.add(tool);
                    result.set("tools", tools);
                    response.set("result", result);
                }
                case "tools/call" -> {
                    String toolName = params.get("name").asText();
                    JsonNode args = params.get("arguments");
                    String location = args.has("location") ? args.get("location").asText() : "Unknown";
                    int temp = ThreadLocalRandom.current().nextInt(30, 36);
                    String text = "The weather in " + location + " is sunny with a temperature of " + temp + "°C.";
                    ObjectNode result = mapper.createObjectNode();
                    ArrayNode content = mapper.createArrayNode();
                    ObjectNode textContent = mapper.createObjectNode();
                    textContent.put("type", "text");
                    textContent.put("text", text);
                    content.add(textContent);
                    result.set("content", content);
                    result.put("isError", false);
                    response.set("result", result);
                }
                default -> {
                    ObjectNode error = mapper.createObjectNode();
                    error.put("code", -32601);
                    error.put("message", "Method not found: " + method);
                    response.set("error", error);
                }
            }

            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(response);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling MCP message", e);
            try {
                byte[] errorBytes = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, errorBytes.length);
                exchange.getResponseBody().write(errorBytes);
            } catch (IOException ex) {
                // ignore
            }
        } finally {
            exchange.close();
        }
    }

    private static class SseClient {
        private final OutputStream out;
        private volatile boolean closed;

        SseClient(OutputStream out) {
            this.out = out;
        }

        synchronized void send(String data) throws IOException {
            if (!closed) {
                out.write(data.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        }

        void close() {
            closed = true;
            try {
                out.close();
            } catch (IOException e) {
                // ignore
            }
        }

        boolean isClosed() {
            return closed;
        }
    }
}
