package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import org.openide.util.NbBundle;

public class McpTools {

    private static final Logger LOG = Logger.from(McpTools.class);
    private static final ObjectMapper MAPPER = MapperSupplier.get();
    private final Map<String, McpToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    public McpTools() {
    }

    public <T, R> void registerTool(String name, String description, ObjectNode inputSchema, ToolExecutor<T, R> executor) {
        tools.put(name, new McpToolDefinition(name, description, inputSchema));
        executors.put(name, executor);
        LOG.info("Registered MCP tool: {0}", name);
    }

    public void unregisterTool(String name) {
        tools.remove(name);
        executors.remove(name);
        LOG.info("Unregistered MCP tool: {0}", name);
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

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public List<String> getToolNames() {
        return List.copyOf(tools.keySet());
    }

    public JsonNode callTool(String name, JsonNode arguments) throws Exception {
        ToolExecutor<?, ?> executor = executors.get(name);
        if (executor == null) {
            throw new IllegalArgumentException(NbBundle.getMessage(McpTools.class, "ERR_ToolNotFound", name));
        }

        return callToolInternal(executor, arguments);
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
