package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests of the embedded MCP server over real HTTP (Jetty on a
 * random loopback port): protocol handshake, tools/list, tools/call, JSON-RPC
 * error mapping, notification handling, token auth enforcement, and the
 * localhost-only CORS policy. Runs fully headless — Jetty binds 127.0.0.1:0.
 */
class McpServerHttpTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpServer server;
    private HttpClient http;

    @BeforeEach
    void setUp() throws Exception {
        server = new McpServer();
        server.setAuthRequired(false);
        server.start();
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String body) throws Exception {
        return post(server.getUrl(), body);
    }

    // ---- protocol handshake -------------------------------------------------

    @Test
    void initializeAdvertisesProtocolAndCapabilities() throws Exception {
        HttpResponse<String> resp = postJson(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals(1, root.get("id").asInt());
        assertEquals("2025-03-26", root.at("/result/protocolVersion").asText());
        assertEquals("nb-mcp", root.at("/result/serverInfo/name").asText());
        assertTrue(root.at("/result/capabilities/tools/listChanged").asBoolean());
    }

    @Test
    void pingReturnsEmptyResult() throws Exception {
        HttpResponse<String> resp = postJson("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertTrue(root.get("result").isObject());
    }

    @Test
    void stringIdsAreEchoedVerbatim() throws Exception {
        // goose sends UUID string ids; coercing them to long breaks correlation.
        HttpResponse<String> resp = postJson(
                "{\"jsonrpc\":\"2.0\",\"id\":\"517ef9d2-aaaa-bbbb-cccc-000000000000\",\"method\":\"ping\"}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals("517ef9d2-aaaa-bbbb-cccc-000000000000", root.get("id").asText());
    }

    // ---- tools --------------------------------------------------------------

    @Test
    void toolsListIncludesFileSystemTools() throws Exception {
        HttpResponse<String> resp = postJson("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");

        assertEquals(200, resp.statusCode());
        JsonNode tools = MAPPER.readTree(resp.body()).at("/result/tools");
        assertTrue(tools.isArray() && tools.size() > 0, "tools/list should return tools: " + tools);
        boolean foundRead = false;
        boolean foundWrite = false;
        for (JsonNode t : tools) {
            foundRead |= "read_file".equals(t.get("name").asText());
            foundWrite |= "write_to_file".equals(t.get("name").asText());
        }
        assertTrue(foundRead, "tools/list should include read_file");
        assertTrue(foundWrite, "tools/list should include write_to_file");
    }

    @Test
    void toolsCallExecutesRegisteredTool() throws Exception {
        server.getMcpTools().registerTool(
                "echo_test",
                "Echoes the message back.",
                ToolSchema.object().str("msg", "Message to echo").require("msg").build(),
                new ToolExecutor<EchoInput, java.util.Map<String, Object>>(EchoInput.class) {
                    @Override
                    public java.util.Map<String, Object> execute(EchoInput args) {
                        return java.util.Map.of("status", "ok", "echo", args.msg());
                    }
                });

        HttpResponse<String> resp = postJson(
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"echo_test\",\"arguments\":{\"msg\":\"pong\"}}}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals("pong", root.at("/result/structuredContent/echo").asText());
        assertEquals("ok", root.at("/result/structuredContent/status").asText());
        assertTrue(root.at("/result/content/0/text").asText().contains("pong"),
                "content text should carry the tool response JSON");
    }

    @Test
    void toolsCallUnknownToolReturnsInvalidParameters() throws Exception {
        HttpResponse<String> resp = postJson(
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"no_such_tool\",\"arguments\":{}}}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals(-32602, root.at("/error/code").asInt());
    }

    @Test
    void toolsCallMissingNameReturnsInvalidParams() throws Exception {
        HttpResponse<String> resp = postJson(
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{}}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals(-32602, root.at("/error/code").asInt());
        assertEquals("Missing required parameter: name", root.at("/error/message").asText());
    }

    // ---- JSON-RPC error mapping ---------------------------------------------

    @Test
    void unknownMethodReturnsMethodNotFound() throws Exception {
        HttpResponse<String> resp = postJson(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"bogus/method\"}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals(-32601, root.at("/error/code").asInt());
        assertTrue(root.at("/error/message").asText().contains("bogus/method"),
                "error should name the method: " + resp.body());
    }

    @Test
    void missingMethodReturns400() throws Exception {
        HttpResponse<String> resp = postJson("{\"jsonrpc\":\"2.0\",\"id\":8}");

        assertEquals(400, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertEquals(-32600, root.at("/error/code").asInt());
    }

    @Test
    void notificationAcceptedWithoutResponseBody() throws Exception {
        HttpResponse<String> resp = postJson(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

        assertEquals(202, resp.statusCode());
    }

    // ---- token auth ---------------------------------------------------------

    @Test
    void authRequiredRejectsMissingToken() throws Exception {
        server.setAuthRequired(true);

        HttpResponse<String> resp = postJson("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");

        assertEquals(403, resp.statusCode());
        assertTrue(resp.body().contains("Unauthorized"), resp.body());
    }

    @Test
    void authRequiredRejectsWrongToken() throws Exception {
        server.setAuthRequired(true);

        HttpResponse<String> resp = post(server.getUrl() + "?token=nope",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");

        assertEquals(403, resp.statusCode());
        assertTrue(resp.body().contains("Unauthorized"), resp.body());
    }

    @Test
    void authRequiredAcceptsValidToken() throws Exception {
        server.setAuthRequired(true);
        String authedUrl = server.getAuthenticatedUrl();
        assertNotNull(authedUrl);
        assertTrue(authedUrl.contains("?token="), authedUrl);

        HttpResponse<String> resp = post(authedUrl,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");

        assertEquals(200, resp.statusCode());
        JsonNode root = MAPPER.readTree(resp.body());
        assertTrue(root.get("result").isObject());
    }

    @Test
    void authenticatedUrlDiffersFromPlainUrl() {
        server.setAuthRequired(true);
        assertFalse(server.getAuthenticatedUrl().equals(server.getUrl()));
        assertTrue(server.getAuthenticatedUrl().startsWith(server.getUrl() + "?token="));
    }

    // ---- CORS over HTTP -----------------------------------------------------

    @Test
    void corsEchoesLocalhostOrigin() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(server.getUrl()))
                        .header("Content-Type", "application/json")
                        .header("Origin", "http://localhost:9999")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertEquals("http://localhost:9999", resp.headers().firstValue("access-control-allow-origin").orElse(null));
    }

    @Test
    void corsOmitsHeaderForRemoteOrigin() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(server.getUrl()))
                        .header("Content-Type", "application/json")
                        .header("Origin", "https://evil.example.com")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // The response is still served (the gate is the browser), but no CORS
        // header is granted to a remote origin.
        assertTrue(resp.headers().firstValue("access-control-allow-origin").isEmpty(),
                "remote origin must not receive a CORS header");
    }

    // ---- misrouted paths ----------------------------------------------------

    @Test
    void bareOriginPathIsStillServed() throws Exception {
        // Many clients are configured with the bare origin; the servlet maps
        // "/" to the same handler and logs the misroute once per start.
        String bareOrigin = server.getUrl().replace("/mcp", "/");
        HttpResponse<String> resp = post(bareOrigin,
                "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"ping\"}");

        assertEquals(200, resp.statusCode());
        assertTrue(MAPPER.readTree(resp.body()).get("result").isObject());
    }

    private record EchoInput(String msg) {}
}
