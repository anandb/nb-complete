package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** MCP input for the {@code git_log} tool. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLogInput(
    String repoDir,
    Integer maxCount,
    String since
) {}
