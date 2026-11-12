package github.anandb.netbeans.manager;

import com.fasterxml.jackson.databind.JsonNode;
import github.anandb.netbeans.support.InterjectRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The assembled {@code x.ai/interject} request: the method, the session id, the text,
 * and the content blocks (text first, then attachments). Grok's interject needs no run id.
 */
class ProcessManagerInterjectTest {

    private ProcessManager processManager;
    private AcpProtocolClient client;

    @BeforeEach
    void setUp() throws Exception {
        try {
            processManager = new ProcessManager();
        } catch (Exception e) {
            processManager = null; // headless preferences/lifecycle unavailable
            return;
        }
        client = mock(AcpProtocolClient.class);
        doAnswer(inv -> new CompletableFuture<JsonNode>())
                .when(client).sendRequest(anyString(), any(), anyLong(), any(TimeUnit.class));
        rpcClient().set(client);
    }

    @AfterEach
    void tearDown() {
        if (processManager != null) {
            processManager.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<AcpProtocolClient> rpcClient() throws Exception {
        Field field = ProcessManager.class.getDeclaredField("rpcClient");
        field.setAccessible(true);
        return (AtomicReference<AcpProtocolClient>) field.get(processManager);
    }

    @Test
    void interjectRequestCarriesSessionTextAndBlocks() {
        if (processManager == null) {
            return;
        }
        List<Map<String, Object>> blocks = List.of(
                Map.of("type", "image", "data", "x", "mimeType", "image/png"));

        processManager.interjectMessage("s1", "hello", blocks);

        ArgumentCaptor<Object> params = ArgumentCaptor.forClass(Object.class);
        verify(client).sendRequest(eq(InterjectRequest.METHOD), params.capture(), anyLong(), any(TimeUnit.class));
        Map<?, ?> sent = assertInstanceOf(Map.class, params.getValue());
        assertEquals("s1", sent.get("sessionId"));
        assertEquals("hello", sent.get("text"));
        assertFalse(sent.containsKey("expectedRunId"), "interject needs no run id");
        List<?> content = assertInstanceOf(List.class, sent.get("content"));
        assertEquals(2, content.size(), "the text block, then the attachment block");
        assertEquals(Map.of("type", "text", "text", "hello"), content.get(0));
        assertEquals(blocks.get(0), content.get(1));
    }
}
