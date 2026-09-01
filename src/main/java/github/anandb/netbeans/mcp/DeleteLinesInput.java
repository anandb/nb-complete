package github.anandb.netbeans.mcp;

/**
 * Input record for the {@code delete_lines} MCP tool.
 *
 * @param filePath  absolute path to the file
 * @param startLine first line to delete (1-indexed, inclusive)
 * @param endLine   last line to delete (1-indexed, inclusive)
 */
public record DeleteLinesInput(String filePath, int startLine, int endLine) {}
