package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import github.anandb.netbeans.support.PluginSettings;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

import github.anandb.netbeans.support.Logger;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class MessageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.from(MessageServlet.class);
    private static final String MCP_PROTOCOL_VERSION = "2025-03-26";
    private static final String SERVER_NAME = "nb-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    private final ObjectMapper mapper;
    private final transient RequestProcessor asyncExecutor;
    private final transient McpTools mcpTools;
    private final String token;

    MessageServlet(ObjectMapper mapper, RequestProcessor asyncExecutor, McpTools mcpTools, String token) {
        this.mapper = mapper;
        this.asyncExecutor = asyncExecutor;
        this.mcpTools = mcpTools;
        this.token = token;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Verify auth token
        String reqToken = request.getParameter("token");
        if (reqToken == null || !reqToken.equals(token)) {
            LOG.warn("MCP request rejected: missing or invalid token");
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32001,\"message\":\"Unauthorized\"}}");
            return;
        }

        long start = System.nanoTime();
        LOG.info("MCP request received: {0} {1}", request.getMethod(), request.getRequestURI());
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(PluginSettings.getSessionIdleTimeout() * 1000L);

        asyncExecutor.post(() -> {
            boolean isToolsCall = false;
            try {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = request.getReader().readLine()) != null) {
                    body.append(line);
                }

                JsonNode req = mapper.readTree(body.toString());
                String method = req.get("method").asText();
                JsonNode params = req.get("params");
                boolean isNotification = !req.has("id");
                long id = isNotification ? -1 : req.get("id").asLong();

                LOG.info("MCP method: {0} (id={1})", method, id);

                isToolsCall = !isNotification && "tools/call".equals(method);

                if (!isNotification) {
                    ObjectNode resp = mapper.createObjectNode();
                    resp.put("jsonrpc", "2.0");
                    resp.put("id", id);

                    if ("tools/call".equals(method)) {
                        // tools/call handles response asynchronously with minimum delay
                        handleToolsCallAsync(params, resp, asyncContext, start);
                        return; // response will be sent by handleToolsCallAsync, which must complete the context
                    }

                    try {
                        handleRequest(method, params, resp);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Error handling MCP method: {0}", method);
                        LOG.log(Level.SEVERE, "Exception: {0}", e.getMessage());
                        ObjectNode error = mapper.createObjectNode();
                        error.put("code", -32603);
                        error.put("message", NbBundle.getMessage(MessageServlet.class, "MSG_InternalError", e.getMessage()));
                        resp.set("error", error);
                    }

                    writeResponse(asyncContext, resp);
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    LOG.info("MCP response sent: {0} in {1}ms", method, durationMs);
                } else {
                    handleNotification(method, params);
                }

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error handling MCP message", e);
                try {
                    HttpServletResponse httpResp = (HttpServletResponse) asyncContext.getResponse();
                    httpResp.setContentType("application/json");
                    httpResp.setStatus(500);
                    httpResp.getWriter().write("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}");
                } catch (IOException ex) {
                    // ignore
                }
            } finally {
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                LOG.info("MCP request completed in {0}ms", durationMs);
                if (!isToolsCall) {
                    asyncContext.complete();
                }
            }
        });
    }

    /**
     * Handles tools/call with a scheduled minimum latency (5000ms) instead of blocking Thread.sleep.
     * The tool executes immediately on the RequestProcessor thread, but the response is delayed
     * to ensure a minimum elapsed time. No thread is blocked during the delay.
     */
    private void handleToolsCallAsync(JsonNode params, ObjectNode resp, AsyncContext asyncContext, long requestStart) {
        if (params == null || !params.has("name")) {
            try {
                writeError(asyncContext, resp, -32602, "Missing required parameter: name");
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Failed to write error response", e);
            }
            return;
        }

        String name = params.get("name").asText();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        asyncExecutor.post(() -> {
            try {
                long toolStart = System.nanoTime();
                JsonNode toolResponse = mcpTools.callTool(name, arguments);
                long toolElapsed = (System.nanoTime() - toolStart) / 1_000_000;
                long totalElapsed = (System.nanoTime() - requestStart) / 1_000_000;
                long remainingDelay = Math.max(0, 5000 - totalElapsed);

                // Build result (cheap, no blocking)
                ObjectNode result = mapper.createObjectNode();
                // structuredContent expects a record (object), not an array.
                // Wrap array-type tool responses so the content is always an object.
                if (toolResponse.isArray()) {
                    ObjectNode wrapped = mapper.createObjectNode();
                    wrapped.set("items", toolResponse);
                    result.set("structuredContent", wrapped);
                } else {
                    result.set("structuredContent", toolResponse);
                }
                ArrayNode contentNode = mapper.createArrayNode();
                ObjectNode contentElement = mapper.createObjectNode();
                contentElement.put("type", "text");
                contentElement.put("text", mapper.writeValueAsString(toolResponse));
                contentNode.add(contentElement);
                result.set("content", contentNode);
                resp.set("result", result);

                Runnable sendResponse = () -> {
                    try {
                        writeResponse(asyncContext, resp);
                        LOG.info("MCP tools/call completed: {0} (tool={1}ms, delay={2}ms)", name, toolElapsed, remainingDelay);
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Failed to write tools/call response", e);
                    } finally {
                        asyncContext.complete();
                    }
                };

                if (remainingDelay > 0) {
                    asyncExecutor.post(sendResponse, (int) remainingDelay);
                } else {
                    sendResponse.run();
                }
            } catch (IllegalArgumentException e) {
                scheduleErrorResponse(resp, asyncContext, -32602, e.getMessage());
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Tool execution failed: {0}", e.getMessage());
                scheduleErrorResponse(resp, asyncContext, -32603, "Tool execution failed: " + e.getMessage());
            }
        });
    }

    private void scheduleErrorResponse(ObjectNode resp, AsyncContext ctx, int code, String message) {
        asyncExecutor.post(() -> {
            try {
                writeError(ctx, resp, code, message);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Failed to write error response", e);
            }
        });
    }

    private void writeError(AsyncContext ctx, ObjectNode resp, int code, String message) throws IOException {
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        resp.set("error", error);
        writeResponse(ctx, resp);
        ctx.complete();
    }

    private void handleRequest(String method, JsonNode params, ObjectNode resp) {
        switch (method) {
            case "initialize" -> handleInitialize(params, resp);
            case "tools/list" -> handleToolsList(resp);
            case "ping" -> resp.set("result", mapper.createObjectNode());
            case "resources/list" -> handleResourcesList(resp);
            case "resources/subscribe" -> resp.set("result", mapper.createObjectNode());
            case "resources/unsubscribe" -> resp.set("result", mapper.createObjectNode());
            default -> {
                ObjectNode error = mapper.createObjectNode();
                error.put("code", -32601);
                error.put("message", NbBundle.getMessage(MessageServlet.class, "MSG_MethodNotFound", method));
                resp.set("error", error);
            }
        }
    }

    private void handleNotification(String method, JsonNode params) {
        switch (method) {
            case "notifications/initialized" ->
                LOG.info("MCP client initialized");
            case "notifications/cancelled" ->
                LOG.info("MCP client cancelled: {0}", params);
            case "notifications/tools/list_changed" ->
                LOG.info("MCP client notified tool list changed");
            default ->
                LOG.info("MCP unhandled notification: {0}", method);
        }
    }

    private void handleInitialize(JsonNode params, ObjectNode resp) {
        String clientName = "unknown";
        String clientVersion = "unknown";
        if (params != null && params.has("clientInfo")) {
            JsonNode info = params.get("clientInfo");
            clientName = info.has("name") ? info.get("name").asText() : clientName;
            clientVersion = info.has("version") ? info.get("version").asText() : clientVersion;
        }
        LOG.info("MCP initialize from {0} v{1}", clientName, clientVersion);

        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);

        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode tools = mapper.createObjectNode();
        tools.put("listChanged", true);
        capabilities.set("tools", tools);
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.set("serverInfo", serverInfo);

        resp.set("result", result);
    }

    private void handleToolsList(ObjectNode resp) {
        long start = System.nanoTime();
        LOG.info("MCP tools/list request");
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", mcpTools.getToolList());
        resp.set("result", result);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        LOG.info("MCP tools/list completed in {0}ms, tools={1}", durationMs, mcpTools.toolCount());
    }

    private void handleResourcesList(ObjectNode resp) {
        ObjectNode result = mapper.createObjectNode();
        result.set("resources", mapper.createArrayNode());
        resp.set("result", result);
    }

    private void writeResponse(AsyncContext asyncContext, ObjectNode resp) throws IOException {
        HttpServletResponse httpResp = (HttpServletResponse) asyncContext.getResponse();
        httpResp.setContentType("application/json");
        httpResp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResp.setHeader("Access-Control-Allow-Origin", "*");
        httpResp.getWriter().write(mapper.writeValueAsString(resp));
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}
