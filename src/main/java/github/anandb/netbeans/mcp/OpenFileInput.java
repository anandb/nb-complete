package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import github.anandb.netbeans.stereotype.McpToolInput;

@JsonIgnoreProperties(ignoreUnknown = true)
@McpToolInput
public record OpenFileInput(String filePath, int line, boolean focus) {}
