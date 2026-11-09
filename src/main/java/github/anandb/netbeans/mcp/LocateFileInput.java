package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Input for the {@code locate_file} MCP tool.
 *
 * @param query file name, path fragment or glob
 * @param scope {@code session} (default: only the session's working directory) or
 *              {@code projects} (every open project)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocateFileInput(String query, String scope) {
}
