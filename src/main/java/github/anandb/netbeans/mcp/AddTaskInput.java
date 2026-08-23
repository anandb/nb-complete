package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the {@code add_task} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddTaskInput(
    String repoId,
    String summary,
    String description,
    String status,
    String priority,
    String filePath,
    String tags,
    String dueDate
) {}