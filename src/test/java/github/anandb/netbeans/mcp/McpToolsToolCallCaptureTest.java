package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.anandb.netbeans.contract.ToolCallRecorder;
import github.anandb.netbeans.model.UsageRecords.ToolCallStatus;
import github.anandb.netbeans.support.MapperSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link McpTools} reports every {@code tools/call} exit to the
 * capture seam: success, a {@code status: "error"} payload, an unknown tool, a
 * throwing executor and a null name. The recorder is injected, so nothing here
 * touches the database or Lookup.
 */
class McpToolsToolCallCaptureTest {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    private record Call(String tool, long capturedAt, long durationMs, ToolCallStatus status) {
    }

    /** Collects what the tool registry reports. */
    private static final class RecordingRecorder implements ToolCallRecorder {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public void record(String toolName, long capturedAt, long durationMs, ToolCallStatus status) {
            calls.add(new Call(toolName, capturedAt, durationMs, status));
        }
    }

    private McpTools tools;
    private RecordingRecorder recorder;

    @BeforeEach
    void setUp() {
        tools = new McpTools();
        recorder = new RecordingRecorder();
        tools.setRecorderForTest(recorder);
    }

    private void register(String name, ToolExecutor<EmptyToolInput, Map<String, Object>> executor) {
        tools.registerTool(name, "test tool", ToolSchema.object().build(), executor);
    }

    private JsonNode call(String name) throws Exception {
        return tools.callTool(name, MAPPER.createObjectNode());
    }

    @Test
    void recordsOneSuccessfulExecutionWithDurationAndName() throws Exception {
        register("read_file", new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
            @Override
            Map<String, Object> execute(EmptyToolInput args) {
                return Map.of("status", "ok", "content", "hi");
            }
        });

        JsonNode result = call("read_file");

        assertEquals("hi", result.path("content").asText());
        assertEquals(1, recorder.calls.size(), "exactly one row per execution");
        Call recorded = recorder.calls.get(0);
        assertEquals("read_file", recorded.tool());
        assertEquals(ToolCallStatus.OK, recorded.status());
        assertTrue(recorded.durationMs() >= 0, "duration is never negative");
        assertTrue(recorded.capturedAt() > 0, "the row carries its own capture time");
    }

    @Test
    void recordsAnErrorPayloadAsFailedExecution() throws Exception {
        register("write_to_file", new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
            @Override
            Map<String, Object> execute(EmptyToolInput args) {
                return Map.of("status", "error", "message", "denied");
            }
        });

        call("write_to_file");

        assertEquals(1, recorder.calls.size());
        assertEquals(ToolCallStatus.ERROR, recorder.calls.get(0).status());
    }

    @Test
    void recordsAnUnknownToolAndStillRejectsTheCall() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> call("no_such_tool"));

        assertNotNull(e.getMessage());
        assertEquals(1, recorder.calls.size());
        assertEquals("no_such_tool", recorder.calls.get(0).tool());
        assertEquals(ToolCallStatus.NOT_FOUND, recorder.calls.get(0).status());
    }

    @Test
    void recordsAThrowingExecutorAsFailedExecution() {
        register("ghost", new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
            @Override
            Map<String, Object> execute(EmptyToolInput args) {
                throw new IllegalStateException("boom");
            }
        });

        assertThrows(IllegalStateException.class, () -> call("ghost"));

        assertEquals(1, recorder.calls.size());
        assertEquals(ToolCallStatus.ERROR, recorder.calls.get(0).status());
    }

    @Test
    void aNullToolNameIsReportedAsNotFoundWithoutThrowingNpe() {
        assertThrows(IllegalArgumentException.class, () -> call(null));

        assertEquals(1, recorder.calls.size());
        assertEquals(ToolCallStatus.NOT_FOUND, recorder.calls.get(0).status());
    }

    @Test
    void captureFailureNeverFailsTheToolCall() throws Exception {
        register("read_file", new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
            @Override
            Map<String, Object> execute(EmptyToolInput args) {
                return Map.of("status", "ok");
            }
        });
        tools.setRecorderForTest((toolName, capturedAt, durationMs, status) -> {
            throw new IllegalStateException("capture is down");
        });

        JsonNode result = call("read_file");

        assertEquals("ok", result.path("status").asText(), "the tool result still reaches the harness");
    }
}
