package github.anandb.netbeans.mcp.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the {@code add_task} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record AddTaskInput(
    String repoId,
    String summary,
    String status,
    String priority,
    String projects,
    String tags,
    String dueDate,
    Integer estimate,
    Integer consumed
) {}
