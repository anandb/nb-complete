package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the hg_file_hist tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HgFileHistoryInput(String repoDir, String path, Integer maxCount) {}
