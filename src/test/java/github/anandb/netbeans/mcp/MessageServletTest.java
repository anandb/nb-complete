package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openide.util.RequestProcessor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for {@link MessageServlet}: the token-auth gate (constant-time
 * compare, 403 on missing/invalid token) and the localhost-only CORS policy.
 * The authorized request path is covered end-to-end by
 * {@link McpServerHttpTest}.
 */
class MessageServletTest {

    private static final String TOKEN = "test-secret-token";

    private RequestProcessor asyncExecutor;
    private McpTools mcpTools;

    @BeforeEach
    void setUp() {
        asyncExecutor = new RequestProcessor(MessageServletTest.class);
        mcpTools = new McpTools();
    }

    @AfterEach
    void tearDown() {
        asyncExecutor.stop();
    }

    private MessageServlet servlet(boolean authRequired) {
        return new MessageServlet(asyncExecutor, mcpTools, TOKEN, () -> authRequired);
    }

    private static class Captured {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final StringWriter body = new StringWriter();

        Captured() throws Exception {
            when(response.getWriter()).thenReturn(new PrintWriter(body, true));
            when(request.getRequestURI()).thenReturn("/mcp");
        }
    }

    // ---- token auth ---------------------------------------------------------

    @Test
    void rejectsRequestWithoutTokenWhenAuthRequired() throws Exception {
        Captured c = new Captured();
        when(c.request.getParameter("token")).thenReturn(null);

        servlet(true).doPost(c.request, c.response);

        verify(c.response).setStatus(403);
        verify(c.response).setContentType("application/json");
        assertTrue(c.body.toString().contains("\"code\":-32001"),
                "403 body should carry the JSON-RPC unauthorized code: " + c.body);
        assertTrue(c.body.toString().contains("Unauthorized"),
                "403 body should say Unauthorized: " + c.body);
    }

    @Test
    void rejectsRequestWithWrongToken() throws Exception {
        Captured c = new Captured();
        when(c.request.getParameter("token")).thenReturn("wrong-token");

        servlet(true).doPost(c.request, c.response);

        verify(c.response).setStatus(403);
        assertTrue(c.body.toString().contains("Unauthorized"));
    }

    @Test
    void skipsAuthWhenNotRequired() throws Exception {
        Captured c = new Captured();
        when(c.request.getParameter("token")).thenReturn(null);
        // The authorized path continues into the async pipeline; we only need
        // to prove the 403 gate was NOT taken. startAsync must be reached.
        jakarta.servlet.AsyncContext ctx = mock(jakarta.servlet.AsyncContext.class);
        when(c.request.startAsync()).thenReturn(ctx);
        when(ctx.getResponse()).thenReturn(c.response);
        when(c.request.getReader()).thenReturn(
                new java.io.BufferedReader(new java.io.StringReader("{}")));

        servlet(false).doPost(c.request, c.response);

        verify(c.request).startAsync();
        verify(c.response, org.mockito.Mockito.never()).setStatus(403);
        verify(ctx, org.mockito.Mockito.timeout(5000)).complete();
        verify(c.response, org.mockito.Mockito.timeout(5000)).setStatus(400);
    }

    // ---- tool errors --------------------------------------------------------

    @Test
    void marksFatalToolErrorsAsMcpErrors() throws Exception {
        mcpTools.registerTool("boom", "always fails", ToolSchema.object().build(),
                new ToolExecutor<AddTaskInput, Map<String, Object>>(AddTaskInput.class) {
                    @Override
                    Map<String, Object> execute(AddTaskInput args) {
                        return Map.of("status", "error", "message", "no repository", "fatal", true);
                    }
                });

        String body = callTool("boom");

        assertTrue(body.contains("\"isError\":true"), body);
        assertTrue(body.contains("no repository"), body);
    }

    @Test
    void leavesValidationErrorsWithoutTheErrorFlag() throws Exception {
        mcpTools.registerTool("validate", "rejects input", ToolSchema.object().build(),
                new ToolExecutor<AddTaskInput, Map<String, Object>>(AddTaskInput.class) {
                    @Override
                    Map<String, Object> execute(AddTaskInput args) {
                        return Map.of("status", "error", "message", "summary is required");
                    }
                });

        String body = callTool("validate");

        assertTrue(body.contains("summary is required"), body);
        assertFalse(body.contains("isError"), body);
    }

    @Test
    void leavesSuccessfulToolResultsWithoutTheErrorFlag() throws Exception {
        mcpTools.registerTool("fine", "works", ToolSchema.object().build(),
                new ToolExecutor<AddTaskInput, Map<String, Object>>(AddTaskInput.class) {
                    @Override
                    Map<String, Object> execute(AddTaskInput args) {
                        return Map.of("status", "ok");
                    }
                });

        String body = callTool("fine");

        assertTrue(body.contains("\"status\":\"ok\""), body);
        assertFalse(body.contains("isError"), body);
    }

    /** Runs one JSON-RPC tools/call through the servlet and returns the written body. */
    private String callTool(String name) throws Exception {
        Captured c = new Captured();
        jakarta.servlet.AsyncContext ctx = mock(jakarta.servlet.AsyncContext.class);
        when(c.request.startAsync()).thenReturn(ctx);
        when(ctx.getResponse()).thenReturn(c.response);
        when(ctx.getRequest()).thenReturn(c.request);
        when(c.request.getReader()).thenReturn(new java.io.BufferedReader(
                new java.io.StringReader("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"" + name + "\",\"arguments\":{}}}")));

        servlet(false).doPost(c.request, c.response);

        verify(ctx, org.mockito.Mockito.timeout(5000)).complete();
        return c.body.toString();
    }

    // ---- CORS ---------------------------------------------------------------

    @Test
    void doOptionsAllowsLocalhostOrigin() throws Exception {
        Captured c = new Captured();
        when(c.request.getHeader("Origin")).thenReturn("http://localhost:4200");

        servlet(false).doOptions(c.request, c.response);

        verify(c.response).setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        verify(c.response).setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    }

    @Test
    void doOptionsAllowsLoopbackOrigin() throws Exception {
        Captured c = new Captured();
        when(c.request.getHeader("Origin")).thenReturn("http://127.0.0.1:8080");

        servlet(false).doOptions(c.request, c.response);

        verify(c.response).setHeader("Access-Control-Allow-Origin", "http://127.0.0.1:8080");
    }

    @Test
    void doOptionsOmitsCorsHeaderForRemoteOrigin() throws Exception {
        Captured c = new Captured();
        when(c.request.getHeader("Origin")).thenReturn("https://evil.example.com");

        servlet(false).doOptions(c.request, c.response);

        verify(c.response, org.mockito.Mockito.never())
                .setHeader(org.mockito.ArgumentMatchers.eq("Access-Control-Allow-Origin"), any());
    }

    @Test
    void doOptionsAllowsMissingOriginHeader() throws Exception {
        Captured c = new Captured();
        when(c.request.getHeader("Origin")).thenReturn(null);

        servlet(false).doOptions(c.request, c.response);

        verify(c.response).setHeader("Access-Control-Allow-Origin", "*");
    }
}
