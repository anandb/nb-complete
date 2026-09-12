package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the {@code update_task} tool. {@code null} fields are left
 * unchanged; empty strings for {@code tags}/{@code projects}/{@code dueDate}
 * clear the value. */
@JsonIgnoreProperties(ignoreUnknown = true)
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
