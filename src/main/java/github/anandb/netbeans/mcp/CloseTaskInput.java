package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the {@code close_task} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record CloseTaskInput(
    String repoId,
    String taskId
) {}
