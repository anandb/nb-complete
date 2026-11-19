package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/**
 * Input for the {@code find_java_type} MCP tool.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record FindTypeInput(String query, String kind, String scope, String projectPath, Integer limit) {
}
