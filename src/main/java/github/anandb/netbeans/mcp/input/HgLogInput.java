package github.anandb.netbeans.mcp.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the hg_log tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record HgLogInput(String repoDir, Integer maxCount, String since) {}
