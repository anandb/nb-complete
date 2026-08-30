package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the {@code close_task} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CloseTaskInput(
    String repoId,
    String taskId
) {}
