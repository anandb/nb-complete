package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the {@code search_task} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record SearchTaskInput(
    String repoId,
    String query
) {}
