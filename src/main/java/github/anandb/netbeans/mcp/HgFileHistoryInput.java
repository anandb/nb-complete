package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/** MCP input for the hg_file_hist tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record HgFileHistoryInput(String repoDir, String path, Integer maxCount) {}
