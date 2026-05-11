package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import github.anandb.netbeans.support.Logger;

public class McpServer {

    private static final Logger LOG = new Logger(McpServer.class);
    private static final int PORT = 8765;
    private static final int MAX_THREADS = 10;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private HttpServer server;
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService heartbeatExecutor;

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/sse", this::handleSse);
        server.createContext("/message", this::handleMessage);
        server.setExecutor(new ThreadPoolExecutor(
                2, MAX_THREADS, 60L, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "MCP-Server");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        ));
        server.start();

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MCP-Heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::purgeDeadClients, 5, 5, TimeUnit.SECONDS);

        LOG.info("MCP server started on port {0}", PORT);
    }

    public synchronized void stop() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
            heartbeatExecutor = null;
        }
        if (server != null) {
            server.stop(1);
            server = null;
        }
        for (SseClient c : sseClients) {
            c.close();
        }
        sseClients.clear();
        LOG.info("MCP server stopped");
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
        SseClient client = null;
        CountDownLatch latch = new CountDownLatch(1);
        try {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            String endpointEvent = "event: endpoint\ndata: /message\n\n";
            out.write(endpointEvent.getBytes(StandardCharsets.UTF_8));
            out.flush();
            client = new SseClient(out, latch);
            sseClients.add(client);
            latch.await();
        } catch (IOException | InterruptedException e) {
            // client disconnected
            Thread.currentThread().interrupt();
        } finally {
            try (exchange) {
                if (client != null) {
                    sseClients.remove(client);
                }
            }
        }
    }

    private void purgeDeadClients() {
        Iterator<SseClient> it = sseClients.iterator();
        while (it.hasNext()) {
            SseClient c = it.next();
            if (c.isClosed()) {
                it.remove();
            }
        }
    }

    private void handleMessage(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try (exchange) {
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
                    result.set("tools", mapper.createArrayNode());
                    response.set("result", result);
                }
                default -> {
                    ObjectNode error = mapper.createObjectNode();
                    error.put("code", -32601);
                    error.put("message", "Method not found: " + method);
                    response.set("error", error);
                }
            }

            byte[] bytes = mapper.writeValueAsBytes(response);
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
        }
    }

    private static class SseClient {
        private final OutputStream out;
        private final CountDownLatch latch;
        private volatile boolean closed;

        SseClient(OutputStream out, CountDownLatch latch) {
            this.out = out;
            this.latch = latch;
        }

        synchronized void send(String data) throws IOException {
            if (!closed) {
                try {
                    out.write(data.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    closed = true;
                    latch.countDown();
                    throw e;
                }
            }
        }

        void close() {
            closed = true;
            latch.countDown();
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
