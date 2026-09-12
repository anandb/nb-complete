package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the {@code file_history} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileHistoryInput(
    String repoDir,
    String path,
    Integer maxCount
) {}
