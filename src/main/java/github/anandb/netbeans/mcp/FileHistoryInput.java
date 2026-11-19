package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the {@code file_history} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record FileHistoryInput(
    String repoDir,
    String path,
    Integer maxCount
) {}
