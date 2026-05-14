package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class McpToolDefinition {
    
    private final String name;
    private final String description;
    private final ObjectNode inputSchema;
    
    public McpToolDefinition(String name, String description, ObjectNode inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
    
    public String name() {
        return name;
    }
    
    public String description() {
        return description;
    }
    
    public ObjectNode inputSchema() {
        return inputSchema;
    }
}
