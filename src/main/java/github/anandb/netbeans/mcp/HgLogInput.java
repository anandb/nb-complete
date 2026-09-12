package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the hg_log tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HgLogInput(String repoDir, Integer maxCount, String since) {}
