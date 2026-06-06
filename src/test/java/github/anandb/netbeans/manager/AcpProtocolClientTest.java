package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import github.anandb.netbeans.support.MapperSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcpProtocolClientTest {

    @Mock
    private Process process;

    private PipedOutputStream processInput;
    private PipedInputStream processOutput;

    private PipedOutputStream clientInputEmulator; // What the client reads from
    private PipedInputStream clientOutputEmulator; // What the client writes to

    private AcpProtocolClient client;
    private final ObjectMapper mapper = MapperSupplier.get();

    @BeforeEach
    void setUp() throws IOException {
        processInput = new PipedOutputStream();
        clientOutputEmulator = new PipedInputStream(processInput);

        clientInputEmulator = new PipedOutputStream();
        processOutput = new PipedInputStream(clientInputEmulator);

        when(process.getOutputStream()).thenReturn(processInput);
        when(process.getInputStream()).thenReturn(processOutput);

        client = new AcpProtocolClient(process);
        client.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Close write ends first so PipedInputStream detects EOF and unblocks reader loops
        clientInputEmulator.close();
        client.close();
    }

    @Test
    void testSendRequestAndReceiveResponse() throws Exception {
        CompletableFuture<JsonNode> future = client.sendRequest("testMethod", "testParams");

        // Read the sent request from the emulator
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientOutputEmulator, StandardCharsets.UTF_8));
        String sentJson = reader.readLine();
        assertNotNull(sentJson);
        JsonNode sentNode = mapper.readTree(sentJson);
        assertEquals("testMethod", sentNode.get("method").asText());
        long id = sentNode.get("id").asLong();

        // Simulate a response
        String responseJson = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"status\":\"ok\"}}\n";
        clientInputEmulator.write(responseJson.getBytes(StandardCharsets.UTF_8));
        clientInputEmulator.flush();

        JsonNode result = future.get(5, TimeUnit.SECONDS);
        assertEquals("ok", result.get("status").asText());
    }

    @Test
    void testReceiveNotification() throws Exception {
        AtomicReference<JsonNode> receivedParams = new AtomicReference<>();
        client.onNotification("testNotify", receivedParams::set);

        String notificationJson = "{\"jsonrpc\":\"2.0\",\"method\":\"testNotify\",\"params\":{\"key\":\"value\"}}\n";
        clientInputEmulator.write(notificationJson.getBytes(StandardCharsets.UTF_8));
        clientInputEmulator.flush();

        // Give some time for the reader thread
        Thread.sleep(200);

        assertNotNull(receivedParams.get());
        assertEquals("value", receivedParams.get().get("key").asText());
    }

    @Test
    void testErrorResponse() throws Exception {
        CompletableFuture<JsonNode> future = client.sendRequest("failMethod", null);

        BufferedReader reader = new BufferedReader(new InputStreamReader(clientOutputEmulator, StandardCharsets.UTF_8));
        String sentJson = reader.readLine();
        long id = mapper.readTree(sentJson).get("id").asLong();

        String errorJson = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}\n";
        clientInputEmulator.write(errorJson.getBytes(StandardCharsets.UTF_8));
        clientInputEmulator.flush();

        Exception ex = assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(ex.getMessage().contains("Internal error"));
    }
}
