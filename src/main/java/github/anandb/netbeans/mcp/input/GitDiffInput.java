package github.anandb.netbeans.mcp.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record GitDiffInput(String repoDir, String target) {}
