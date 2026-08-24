package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the {@code add_task} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
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