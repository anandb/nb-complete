package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record ReplaceLinesInput(String filePath, int startLine, int endLine, String content) {}
