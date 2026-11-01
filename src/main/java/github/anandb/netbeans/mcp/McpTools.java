package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import github.anandb.netbeans.contract.ToolCallRecorder;
import github.anandb.netbeans.model.UsageRecords.ToolCallStatus;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

public class McpTools {

    private static final Logger LOG = Logger.from(McpTools.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private final Map<String, McpToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();
    private volatile ToolCallRecorder recorder;

    public McpTools() {
    }

    public <T, R> void registerTool(String name, String description, ObjectNode inputSchema, ToolExecutor<T, R> executor) {
        tools.put(name, new McpToolDefinition(name, description, inputSchema));
        executors.put(name, executor);
        LOG.info("Registered MCP tool: {0}", name);
    }

    public ArrayNode getToolList() {
        ArrayNode arr = MAPPER.createArrayNode();
        for (McpToolDefinition def : tools.values()) {
            ObjectNode tool = MAPPER.createObjectNode();
            tool.put("name", def.name());
            tool.put("description", def.description());
            tool.set("inputSchema", def.inputSchema() != null ? def.inputSchema() : MAPPER.createObjectNode());
            arr.add(tool);
        }
        return arr;
    }

    public JsonNode callTool(String name, JsonNode arguments) throws Exception {
        long startNanos = System.nanoTime();
        ToolCallStatus status = ToolCallStatus.ERROR;
        try {
            ToolExecutor<?, ?> executor = name != null ? executors.get(name) : null;
            if (executor == null) {
                status = ToolCallStatus.NOT_FOUND;
                throw new IllegalArgumentException(NbBundle.getMessage(McpTools.class, "ERR_ToolNotFound", name));
            }
            JsonNode response = callToolInternal(executor, arguments);
            status = "error".equals(response.path("status").asText("")) ? ToolCallStatus.ERROR : ToolCallStatus.OK;
            return response;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            recordExecution(name, System.currentTimeMillis(), durationMs, status);
        }
    }

    /** Records one finished execution. Capture must never fail the tool call. */
    private void recordExecution(String name, long capturedAt, long durationMs, ToolCallStatus status) {
        try {
            ToolCallRecorder target = recorder();
            if (target != null) {
                target.record(name, capturedAt, durationMs, status);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Tool-call capture failed for {0}", name, e);
        }
    }

    /** The recorder seam, resolved from Lookup once and then cached. */
    private ToolCallRecorder recorder() {
        ToolCallRecorder current = recorder;
        if (current == null) {
            current = Lookup.getDefault().lookup(ToolCallRecorder.class);
            recorder = current;
        }
        return current;
    }

    /** Replaces the Lookup-resolved recorder; tests only. */
    void setRecorderForTest(ToolCallRecorder target) {
        this.recorder = target;
    }

    @SuppressWarnings("unchecked")
    private <T, R> JsonNode callToolInternal(ToolExecutor<T, R> executor, JsonNode arguments) throws Exception {
        T args = MAPPER.treeToValue(arguments, executor.getArgClass());
        R response = executor.execute(args);
        return MAPPER.valueToTree(response);
    }

    public int toolCount() {
        return tools.size();
    }
}
