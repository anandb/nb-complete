package github.anandb.netbeans.mcp.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

/**
 * Input for {@code diff_range}: either an explicit revision pair or the number
 * of most recent commits to show.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record DiffRangeInput(String repoDir, String fromRevision, String toRevision, Integer lastCommits) {
}
