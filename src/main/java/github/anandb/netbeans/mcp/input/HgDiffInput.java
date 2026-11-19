package github.anandb.netbeans.mcp.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the hg_diff tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record HgDiffInput(String repoDir, String target) {}
