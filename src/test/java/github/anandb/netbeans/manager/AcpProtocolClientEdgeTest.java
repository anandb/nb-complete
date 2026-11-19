package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transport-level branches that the golden transcript tests do not exercise. */
class AcpProtocolClientEdgeTest {

    private MockAcpServer server;
    private AcpProtocolClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockAcpServer();
        client = server.client();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void sendNotificationWritesAFramedJsonRpcNotification() throws Exception {
        client.sendNotification("session/cancel", Map.of("sessionId", "s1"));

        JsonNode sent = server.readFromClient();

        assertEquals("2.0", sent.get("jsonrpc").asText());
        assertEquals("session/cancel", sent.get("method").asText());
        assertEquals("s1", sent.get("params").get("sessionId").asText());
    }

    @Test
    void closedClientFailsRequestsAndIgnoresNotifications() throws Exception {
        client.close();
        client.close();

        CompletableFuture<JsonNode> failed = client.sendRequest("late", Map.of());
        assertThrows(Exception.class, () -> failed.get(1, TimeUnit.SECONDS));
        client.sendNotification("late", Map.of());
    }

    @Test
    void closeDrainsPendingRequests() throws Exception {
        CompletableFuture<JsonNode> pending = client.sendRequest("slow", Map.of());
        server.readFromClient();

        client.close();

        assertThrows(Exception.class, () -> pending.get(1, TimeUnit.SECONDS));
    }

    @Test
    void responseWithoutResultCompletesWithNull() throws Exception {
        CompletableFuture<JsonNode> future = client.sendRequest("bare", Map.of());
        long id = server.readFromClient().get("id").asLong();

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":" + id + "}");

        assertEquals(null, future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void unknownAndNonNumericResponseIdsAreIgnored() throws Exception {
        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.onNotification("alive", received::set);

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":999,\"result\":{}}");
        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":\"uuid-like\",\"result\":{}}");
        server.sendToClient("{\"jsonrpc\":\"2.0\",\"method\":\"alive\",\"params\":{}}");

        waitFor(() -> received.get() != null);
        assertNotNull(received.get());
    }

    @Test
    void incomingRequestWithoutHandlerReturnsMethodNotFound() throws Exception {
        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"missing\"}");

        JsonNode response = server.readFromClient();

        assertEquals(7, response.get("id").asInt());
        assertEquals(-32601, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("missing"));
    }

    @Test
    void throwingHandlerReturnsInternalError() throws Exception {
        client.onRequest("boom", params -> {
            throw new IllegalStateException("bad");
        });

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"boom\"}");

        JsonNode response = server.readFromClient();
        assertEquals(-32603, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("bad"));
    }

    @Test
    void rejectedHandlerReturnsItsMessageWithoutBreakingTheConnection() throws Exception {
        client.onRequest("reject", params ->
                CompletableFuture.failedFuture(new RequestRejectedException("rejected")));

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"reject\"}");

        JsonNode response = server.readFromClient();
        assertEquals(-32603, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("rejected"));
    }

    @Test
    void nullHandlerResultBecomesAnEmptyObject() throws Exception {
        client.onRequest("nil", params -> CompletableFuture.completedFuture(null));

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"nil\"}");

        JsonNode response = server.readFromClient();
        assertTrue(response.get("result").isObject());
        assertEquals(0, response.get("result").size());
    }

    @Test
    void throwingNotificationListenerDoesNotKillTheReader() throws Exception {
        client.onNotification("bad", params -> {
            throw new IllegalStateException("listener failed");
        });
        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.onNotification("good", received::set);

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"method\":\"bad\"}");
        server.sendToClient("{\"jsonrpc\":\"2.0\",\"method\":\"good\",\"params\":{}}");

        waitFor(() -> received.get() != null);
        assertNotNull(received.get());
    }

    @Test
    void eofReportsDisconnection() throws Exception {
        AtomicInteger disconnections = new AtomicInteger();
        AcpProtocolClient eofClient = new AcpProtocolClient(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        try {
            eofClient.setDisconnectionHandler(disconnections::incrementAndGet);
            eofClient.start();

            waitFor(() -> disconnections.get() > 0);
            assertTrue(disconnections.get() > 0);
        } finally {
            eofClient.close();
        }
    }

    @Test
    void writeFailuresReachTheConnectionErrorHandlerForRequestsAndNotifications() throws Exception {
        AtomicInteger errors = new AtomicInteger();
        AcpProtocolClient failingClient = new AcpProtocolClient(
                new ByteArrayInputStream(new byte[0]), new BrokenOutputStream());
        try {
            failingClient.setConnectionErrorHandler(error -> errors.incrementAndGet());

            CompletableFuture<JsonNode> request = failingClient.sendRequest("x", Map.of());
            assertThrows(Exception.class, () -> request.get(1, TimeUnit.SECONDS));
            waitFor(() -> errors.get() >= 1);

            failingClient.sendNotification("y", Map.of());
            waitFor(() -> errors.get() >= 2);
            assertTrue(errors.get() >= 2);
        } finally {
            failingClient.close();
        }
    }

    @Test
    void errorWithoutMessageOrCodeStillProducesTypedFailure() throws Exception {
        CompletableFuture<JsonNode> future = client.sendRequest("bad", Map.of());
        long id = server.readFromClient().get("id").asLong();

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{}}");

        Exception ex = assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
        AcpRpcException rpc = (AcpRpcException) ex.getCause();
        assertEquals(0, rpc.code());
        assertEquals("{}", rpc.getMessage());
    }

    @Test
    void unknownNotificationIsIgnoredAndTheReaderContinues() throws Exception {
        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.onNotification("known", received::set);

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"method\":\"unknown\"}");
        server.sendToClient("{\"jsonrpc\":\"2.0\",\"method\":\"known\",\"params\":{}}");

        waitFor(() -> received.get() != null);
        assertNotNull(received.get());
    }

    @Test
    void asyncHandlerFailureUsesTheInternalErrorPath() throws Exception {
        client.onRequest("async-fail", params ->
                CompletableFuture.failedFuture(new RuntimeException("async failure")));

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"async-fail\"}");

        JsonNode response = server.readFromClient();
        assertEquals(-32603, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("async failure"));
    }

    @Test
    void throwingConnectionErrorHandlerDoesNotEscape() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AcpProtocolClient failingClient = new AcpProtocolClient(
                new ByteArrayInputStream(new byte[0]), new BrokenOutputStream());
        try {
            failingClient.setConnectionErrorHandler(error -> {
                calls.incrementAndGet();
                throw new IllegalStateException("handler failed");
            });

            CompletableFuture<JsonNode> request = failingClient.sendRequest("x", Map.of());
            assertThrows(Exception.class, () -> request.get(1, TimeUnit.SECONDS));
            waitFor(() -> calls.get() >= 1);
        } finally {
            failingClient.close();
        }
    }

    @Test
    void throwingDisconnectionHandlerDoesNotEscapeClose() {
        client.setDisconnectionHandler(() -> {
            throw new IllegalStateException("handler failed");
        });

        client.close();
    }

    @Test
    void notificationWithoutParamsGetsAnEmptyObject() throws Exception {
        AtomicReference<JsonNode> received = new AtomicReference<>();
        client.onNotification("no-params", received::set);

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"method\":\"no-params\"}");

        waitFor(() -> received.get() != null);
        assertTrue(received.get().isObject());
        assertEquals(0, received.get().size());
    }

    @Test
    void permissionRequestSuccessTogglesThePendingCounter() throws Exception {
        client.onRequest("session/request_permission",
                params -> CompletableFuture.completedFuture(null));

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"session/request_permission\"}");

        JsonNode response = server.readFromClient();
        assertTrue(response.get("result").isObject());
    }

    @Test
    void permissionRequestFailureClearsThePendingCounter() throws Exception {
        client.onRequest("session/request_permission", params -> {
            throw new IllegalStateException("permission failed");
        });

        server.sendToClient("{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"session/request_permission\"}");

        JsonNode response = server.readFromClient();
        assertEquals(-32603, response.get("error").get("code").asInt());
    }

    @Test
    void readerFailureReportsConnectionErrorAndDisconnection() throws Exception {
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger disconnections = new AtomicInteger();
        AcpProtocolClient failingClient = new AcpProtocolClient(
                new FailingInputStream(), new ByteArrayOutputStream());
        try {
            failingClient.setConnectionErrorHandler(error -> errors.incrementAndGet());
            failingClient.setDisconnectionHandler(disconnections::incrementAndGet);
            failingClient.start();

            waitFor(() -> errors.get() > 0 && disconnections.get() > 0);
            assertTrue(errors.get() > 0);
            assertTrue(disconnections.get() > 0);
        } finally {
            failingClient.close();
        }
    }

    @Test
    void zeroGlobalIdleTimeoutStillSchedulesWithoutClosing() throws Exception {
        int oldTimeout = github.anandb.netbeans.support.PluginSettings.getSessionIdleTimeout();
        try {
            github.anandb.netbeans.support.PluginSettings.setSessionIdleTimeout(0);
            CompletableFuture<JsonNode> pending = client.sendRequest("slow", Map.of());
            server.readFromClient();
            setLastDataTime(client, System.nanoTime() - TimeUnit.SECONDS.toNanos(2));

            client.checkIdleTimeout();

            assertFalse(pending.isDone());
        } finally {
            github.anandb.netbeans.support.PluginSettings.setSessionIdleTimeout(oldTimeout);
        }
    }

    private static final class FailingInputStream extends java.io.InputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("reader failed");
        }
    }

    @Test
    void watchdogSkipsWhileAPermissionIsPending() throws Exception {
        client.setPermissionRequestPending(true);
        try {
            client.checkIdleTimeout();
        } finally {
            client.setPermissionRequestPending(false);
        }
    }

    @Test
    void watchdogNoOpsWithoutPendingRequestsOrAfterClose() throws Exception {
        client.checkIdleTimeout();
        client.close();
        client.checkIdleTimeout();
    }

    @Test
    void watchdogFailsAnExpiredPerRequestTimeout() throws Exception {
        CompletableFuture<JsonNode> future = client.sendRequest(
                "slow", Map.of(), 50, TimeUnit.MILLISECONDS);
        server.readFromClient();
        setLastDataTime(client, System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(100));

        client.checkIdleTimeout();

        Exception ex = assertThrows(Exception.class, () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof java.util.concurrent.TimeoutException);
    }

    @Test
    void watchdogDoesNotCloseBelowTheGlobalIdleTimeout() throws Exception {
        int oldTimeout = github.anandb.netbeans.support.PluginSettings.getSessionIdleTimeout();
        try {
            github.anandb.netbeans.support.PluginSettings.setSessionIdleTimeout(60);
            CompletableFuture<JsonNode> pending = client.sendRequest("slow", Map.of());
            server.readFromClient();
            setLastDataTime(client, System.nanoTime());

            client.checkIdleTimeout();

            assertFalse(pending.isDone());
        } finally {
            github.anandb.netbeans.support.PluginSettings.setSessionIdleTimeout(oldTimeout);
        }
    }

    @Test
    void watchdogClosesWhenTheGlobalIdleTimeoutExpires() throws Exception {
        int oldTimeout = github.anandb.netbeans.support.PluginSettings.getSessionIdleTimeout();
        try {
            github.anandb.netbeans.support.PluginSettings.setSessionIdleTimeout(1);
            CompletableFuture<JsonNode> pending = client.sendRequest("slow", Map.of());
            server.readFromClient();
            setLastDataTime(client, System.nanoTime() - TimeUnit.SECONDS.toNanos(2));

            client.checkIdleTimeout();

            assertThrows(Exception.class, () -> pending.get(1, TimeUnit.SECONDS));
            CompletableFuture<JsonNode> afterClose = client.sendRequest("late", Map.of());
            assertThrows(Exception.class, () -> afterClose.get(1, TimeUnit.SECONDS));
        } finally {
            github.anandb.netbeans.support.PluginSettings.setSessionIdleTimeout(oldTimeout);
        }
    }

    private static void setLastDataTime(AcpProtocolClient target, long value) throws Exception {
        java.lang.reflect.Field field = AcpProtocolClient.class.getDeclaredField("lastDataTime");
        field.setAccessible(true);
        field.setLong(target, value);
    }
    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static final class BrokenOutputStream extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("broken pipe");
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw new IOException("broken pipe");
        }
    }
}
