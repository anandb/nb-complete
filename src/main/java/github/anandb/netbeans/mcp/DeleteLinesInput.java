package github.anandb.netbeans.mcp;

import github.anandb.netbeans.stereotype.McpToolInput;

/**
 * Input record for the {@code delete_lines} MCP tool.
 *
 * @param filePath  absolute path to the file
 * @param startLine first line to delete (1-indexed, inclusive)
 * @param endLine   last line to delete (1-indexed, inclusive)
 */
@McpToolInput
public record DeleteLinesInput(String filePath, int startLine, int endLine) {}
