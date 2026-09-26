package github.anandb.netbeans.manager;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.support.MapperSupplier;
import java.io.EOFException;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Scripted ACP peer for headless protocol tests: a Mockito-mocked {@code Process} whose
 * pipes are wired to a real {@link AcpProtocolClient}, so tests can push agent-side
 * wire bytes at the client and read the client's outbound JSON-RPC messages.
 *
 * <p>Reuses the pipe-emulation pattern proven in {@code AcpProtocolClientTest}.
 * Outbound reads use Jackson's streaming parser (never {@code BufferedReader.readLine()}),
 * mirroring the production {@code readLoop} framing: whitespace-separated JSON objects.</p>
 */
final class MockAcpServer implements AutoCloseable {

    private static final int PIPE_SIZE = 64 * 1024;
    private final ObjectMapper mapper = MapperSupplier.get();
    /** Test writes here; the client reads it as the process stdin. */
    private final PipedOutputStream toClient = new PipedOutputStream();
    /** Client writes here; the test reads its outbound JSON-RPC messages. */
    private final PipedInputStream fromClient = new PipedInputStream(PIPE_SIZE);
    /** Lazily created streaming parser over {@link #fromClient}: Jackson sniffs the
     *  first bytes during construction, which would block on an empty pipe. */
    private JsonParser clientParser;
    private final AcpProtocolClient client;

    MockAcpServer() throws IOException {
        PipedInputStream processStdin = new PipedInputStream(toClient, PIPE_SIZE);
        PipedOutputStream processStdout = new PipedOutputStream(fromClient);
        // The mock process hands the client its ends of the two pipes: stdin is fed
        // by what the test writes to toClient, stdout drains into fromClient.
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(processStdin);
        when(process.getOutputStream()).thenReturn(processStdout);
        this.client = new AcpProtocolClient(process);
        this.client.start();
    }

    AcpProtocolClient client() {
        return client;
    }

    /** Pushes one wire message (or a fixture blob containing several) toward the client. */
    void sendToClient(String json) throws IOException {
        try {
            toClient.write(json.getBytes(StandardCharsets.UTF_8));
            toClient.flush();
        } catch (IOException e) {
            throw new IOException("MockAcpServer failed to send payload: " + json, e);
        }
    }

    /**
     * Reads the next complete JSON object the client sent (fails fast after 5s instead of hanging forever).
     *
     * @throws EOFException when the client closed its output stream first
     * @throws IOException when the bytes are not a parseable JSON object or the wait times out
     */
    JsonNode readFromClient() throws IOException {
        return readFromClient(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    JsonNode readFromClient(long timeout, java.util.concurrent.TimeUnit unit) throws IOException {
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "mock-acp-read");
            t.setDaemon(true);
            return t;
        });
        java.util.concurrent.Future<JsonNode> future = exec.submit(() -> {
            JsonToken token;
            synchronized (MockAcpServer.this) {
                if (clientParser == null) {
                    clientParser = mapper.getFactory().createParser(fromClient);
                }
            }
            try {
                while ((token = clientParser.nextToken()) != null && token != JsonToken.START_OBJECT) {
                    // skip separators/whitespace between messages
                }
            } catch (JsonProcessingException e) {
                throw new IOException("MockAcpServer failed to parse client message", e);
            }
            if (token == null) {
                throw new EOFException("MockAcpServer: client stream closed before a JSON object arrived");
            }
            return mapper.readTree(clientParser);
        });
        try {
            return future.get(timeout, unit);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new IOException("MockAcpServer: timed out after " + timeout + " " + unit + " waiting for client", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("MockAcpServer: client read failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MockAcpServer: interrupted waiting for client", e);
        } finally {
            exec.shutdownNow();
        }
    }

    @Override
    public void close() throws IOException {
        // Close the write end first so the client's reader sees EOF and exits its loop.
        toClient.close();
        client.close();
        if (clientParser != null) {
            clientParser.close();
        }
        fromClient.close();
    }
}
