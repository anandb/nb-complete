package github.anandb.netbeans.manager.strategy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import github.anandb.netbeans.contract.ToolCallRecorder;
import github.anandb.netbeans.model.SessionUpdate;
import github.anandb.netbeans.model.UsageRecords.ToolCallStatus;
import github.anandb.netbeans.support.MapperSupplier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests {@link ExternalMcpToolCapture}: per-harness MCP attribution, minus the internal server. */
class ExternalMcpToolCaptureTest {

    private RecordingRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new RecordingRecorder();
        ExternalMcpToolCapture.clearCachesForTest();
        ExternalMcpToolCapture.setRecorderForTest(recorder);
    }

    @AfterEach
    void tearDown() {
        ExternalMcpToolCapture.setRecorderForTest(null);
        ExternalMcpToolCapture.setHarnessForTest(null);
        ExternalMcpToolCapture.clearCachesForTest();
    }

    @Test
    void recordsAnOmpExternalMcpExecution() {
        ExternalMcpToolCapture.setHarnessForTest("omp");
        capture(start("s1", "call-1", "xd://mcp__dukeoff_workspace_status"));
        capture(ompDone("s1", "call-1", "dukeoff", "workspace_status", false));

        assertEquals(1, recorder.rows.size());
        assertEquals("dukeoff/workspace_status", recorder.rows.get(0).toolName());
        assertEquals(ToolCallStatus.OK, recorder.rows.get(0).status());
    }

    @Test
    void recordsAGooseExternalMcpExecution() {
        ExternalMcpToolCapture.setHarnessForTest("goose");
        capture(gooseStart("s1", "call-1", "dukeoff", "dukeoff__find_symbol"));
        capture(status("s1", "call-1", "completed"));

        assertEquals(1, recorder.rows.size());
        assertEquals("dukeoff/find_symbol", recorder.rows.get(0).toolName());
    }

    @Test
    void ignoresGooseBuiltInExtensions() {
        ExternalMcpToolCapture.setHarnessForTest("goose");
        capture(gooseStart("s1", "call-1", "developer", "edit"));
        capture(status("s1", "call-1", "completed"));

        assertEquals(List.of(), recorder.rows);
    }

    @Test
    void recordsACodexExternalMcpExecution() {
        ExternalMcpToolCapture.setHarnessForTest("codex");
        capture(codexStart("s1", "call-1", "in_progress", "dukeoff", "workspace_status"));
        capture(codexStart("s1", "call-1", "completed", "dukeoff", "workspace_status"));

        assertEquals(1, recorder.rows.size());
        assertEquals("dukeoff/workspace_status", recorder.rows.get(0).toolName());
    }

    @Test
    void recordsAnOpenCodeExternalMcpExecution() {
        ExternalMcpToolCapture.setHarnessForTest("opencode");
        capture(openCodeStart("s1", "call-1", "dukeoff_workspace_status"));
        capture(status("s1", "call-1", "completed"));

        assertEquals(1, recorder.rows.size());
        assertEquals("dukeoff/workspace_status", recorder.rows.get(0).toolName());
    }

    @Test
    void openCodeSplitsAnUnknownServerAtTheFirstUnderscore() {
        ExternalMcpToolCapture.setHarnessForTest("opencode");
        capture(openCodeStart("s1", "call-1", "youtube_searchVideos"));
        capture(status("s1", "call-1", "completed"));

        assertEquals("youtube/searchVideos", recorder.rows.get(0).toolName());
    }

    @Test
    void ignoresOpenCodeBuiltIns() {
        ExternalMcpToolCapture.setHarnessForTest("opencode");
        capture(openCodeStart("s1", "call-1", "todowrite"));
        capture(status("s1", "call-1", "completed"));

        assertEquals(List.of(), recorder.rows);
    }

    @Test
    void ignoresTheInternalNbServerOnOmp() {
        assertInternalServerIgnored("omp",
                () -> capture(start("s1", "call-1", "xd://mcp__nb_rename_session")),
                () -> capture(ompDone("s1", "call-1", "nb", "rename_session", false)));
    }

    @Test
    void ignoresTheInternalNbServerOnGoose() {
        assertInternalServerIgnored("goose",
                () -> capture(gooseStart("s1", "call-1", "nb", "nb__rename_session")),
                () -> capture(status("s1", "call-1", "completed")));
    }

    @Test
    void ignoresTheInternalNbServerOnCodex() {
        assertInternalServerIgnored("codex",
                () -> capture(codexStart("s1", "call-1", "in_progress", "nb", "rename_session")),
                () -> capture(codexStart("s1", "call-1", "completed", "nb", "rename_session")));
    }

    @Test
    void ignoresTheInternalNbServerOnOpenCode() {
        assertInternalServerIgnored("opencode",
                () -> capture(openCodeStart("s1", "call-1", "nb_rename_session")),
                () -> capture(status("s1", "call-1", "completed")));
    }

    private void assertInternalServerIgnored(String harness, Runnable... steps) {
        ExternalMcpToolCapture.setHarnessForTest(harness);
        for (Runnable step : steps) {
            step.run();
        }
        assertEquals(List.of(), recorder.rows, "internal nb must not be recorded for " + harness);
    }

    @Test
    void ignoresTheOmpSchemaReadWhichCarriesNoXdevDetails() {
        ExternalMcpToolCapture.setHarnessForTest("omp");
        capture(start("s1", "call-1", "xd://mcp__dukeoff_workspace_status"));
        capture(status("s1", "call-1", "completed"));

        assertEquals(List.of(), recorder.rows);
    }

    @Test
    void ignoresANonTerminalUpdate() {
        ExternalMcpToolCapture.setHarnessForTest("omp");
        capture(start("s1", "call-1", "xd://mcp__dukeoff_workspace_status"));
        capture(ompDone("s1", "call-1", "dukeoff", "workspace_status", false, "in_progress"));

        assertEquals(List.of(), recorder.rows);
    }

    @Test
    void reportsAFailedExecutionAsError() {
        ExternalMcpToolCapture.setHarnessForTest("goose");
        capture(gooseStart("s1", "call-1", "dukeoff", "dukeoff__find_symbol"));
        capture(status("s1", "call-1", "failed"));

        assertEquals(1, recorder.rows.size());
        assertEquals(ToolCallStatus.ERROR, recorder.rows.get(0).status());
        assertTrue(recorder.rows.get(0).durationMs() >= 0, "duration must never be negative");
    }

    @Test
    void recordsOnlyOnceForARepeatedTerminalUpdate() {
        ExternalMcpToolCapture.setHarnessForTest("goose");
        capture(gooseStart("s1", "call-1", "dukeoff", "dukeoff__find_symbol"));
        capture(status("s1", "call-1", "completed"));
        capture(status("s1", "call-1", "completed"));

        assertEquals(1, recorder.rows.size());
    }

    private static void capture(SessionUpdate update) {
        ExternalMcpToolCapture.capture(update);
    }

    private static SessionUpdate start(String sessionId, String toolCallId, String path) {
        ObjectNode update = base(toolCallId, "tool_call", "pending");
        update.putObject("rawInput").put("path", path);
        return update(sessionId, update);
    }

    private static SessionUpdate ompDone(String sessionId, String toolCallId, String server,
            String tool, boolean error) {
        return ompDone(sessionId, toolCallId, server, tool, error, "completed");
    }

    private static SessionUpdate ompDone(String sessionId, String toolCallId, String server,
            String tool, boolean error, String status) {
        ObjectNode update = base(toolCallId, "tool_call_update", status);
        update.putObject("rawOutput").putObject("details").putObject("xdev")
                .set("inner", inner(server, tool, error));
        return update(sessionId, update);
    }

    private static SessionUpdate gooseStart(String sessionId, String toolCallId,
            String extension, String toolName) {
        ObjectNode update = base(toolCallId, "tool_call", null);
        update.putObject("_meta").putObject("goose").putObject("toolCall")
                .put("extensionName", extension).put("toolName", toolName);
        return update(sessionId, update);
    }

    private static SessionUpdate codexStart(String sessionId, String toolCallId,
            String status, String server, String tool) {
        ObjectNode update = base(toolCallId, "tool_call", status);
        update.putObject("_meta").put("is_mcp_tool_call", true);
        ObjectNode rawInput = update.putObject("rawInput");
        rawInput.put("server", server);
        rawInput.put("tool", tool);
        return update(sessionId, update);
    }

    private static SessionUpdate openCodeStart(String sessionId, String toolCallId, String name) {
        ObjectNode update = base(toolCallId, "tool_call", "pending");
        update.put("name", name);
        return update(sessionId, update);
    }

    private static SessionUpdate status(String sessionId, String toolCallId, String status) {
        return update(sessionId, base(toolCallId, "tool_call_update", status));
    }

    private static ObjectNode base(String toolCallId, String type, String status) {
        ObjectNode update = MapperSupplier.get().createObjectNode();
        update.put("sessionUpdate", type);
        update.put("toolCallId", toolCallId);
        if (status != null) {
            update.put("status", status);
        }
        return update;
    }

    private static ObjectNode inner(String server, String tool, boolean error) {
        ObjectNode inner = MapperSupplier.get().createObjectNode();
        inner.put("serverName", server);
        inner.put("mcpToolName", tool);
        inner.put("isError", error);
        return inner;
    }

    private static SessionUpdate update(String sessionId, ObjectNode updateNode) {
        SessionUpdate.UpdateData data;
        try {
            data = MapperSupplier.get().treeToValue(updateNode, SessionUpdate.UpdateData.class);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        return new SessionUpdate(SessionUpdate.JSONRPC_VERSION, SessionUpdate.METHOD,
                new SessionUpdate.Params(sessionId, data));
    }

    private record Row(String toolName, long durationMs, ToolCallStatus status) {
    }

    private static final class RecordingRecorder implements ToolCallRecorder {
        private final List<Row> rows = new ArrayList<>();

        @Override
        public void record(String toolName, long capturedAt, long durationMs, ToolCallStatus status) {
            rows.add(new Row(toolName, durationMs, status));
        }
    }
}
