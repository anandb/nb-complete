package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the hg_status tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record HgStatusInput(String repoDir) {}
