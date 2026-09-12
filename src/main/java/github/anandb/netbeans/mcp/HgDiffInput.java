package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the hg_diff tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HgDiffInput(String repoDir, String target) {}
