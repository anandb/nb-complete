package github.anandb.netbeans.mcp.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the {@code update_task} tool. {@code null} fields are left
 * unchanged; empty strings for {@code tags}/{@code projects}/{@code dueDate}
 * clear the value. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record UpdateTaskInput(
    String repoId,
    String taskId,
    String summary,
    String status,
    String priority,
    String projects,
    String tags,
    String dueDate,
    Integer estimate,
    Integer consumed
) {}
