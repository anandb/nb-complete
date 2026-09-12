package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the hg_status tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HgStatusInput(String repoDir) {}
