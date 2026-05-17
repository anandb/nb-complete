package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenFileInput(String filePath, int line) {}
