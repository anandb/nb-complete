package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the {@code search_task} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchTaskInput(
    String repoId,
    String query
) {}