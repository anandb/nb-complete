# MCP Editor Tools: Replace Auto-Injected Context with On-Demand Tools

## Summary

Replace the current automatic file-context injection (file path, cursor, selection) on every user message with two on-demand MCP tools. The MCP server is enabled by default so the AI can request editor context only when needed.

## Changes

### 1. Enable MCP Server by Default

- `McpManager.mcpDisabled`: change default from `true` to `false`
- MCP server starts on ephemeral port (as before) and is wired to the ACP server via `getServerConfig()`
- The existing `checkServerSupport()` logic still disables MCP if the ACP server doesn't advertise support

### 2. Remove Weather Demo Tool

- Delete `McpServer.start()` registration of `"weather"` tool
- Delete `WeatherInput.java`, `WeatherOutput.java`

### 3. Remove Auto-Injected File Context

- `MessageSender.sendMessage()`: stop calling `EditorContextCapture.capture()` (remove line ~154 and the `isForwardedSlash` context-skip logic)
- `ProcessManager.sendMessage()`: remove the `context` → `promptBlocks` XML metadata + selection content block building (lines ~440-488); simplify the prompt to just user text + file attachments
- Keep the `promptBlocks` structure and `mcpServers` wiring intact

### 4. New File: `EditorToolProvider.java`

**Package:** `github.anandb.netbeans.mcp`

**Method:** `registerTools(McpTools mcpTools)` — registers two tools:

#### Tool: `get_current_file_context`
- **Description:** `"Returns the current file path, cursor position, and any text selection from the active editor tab"`
- **Input:** None (`EmptyToolInput`)
- **Output:** JSON object
  - `filePath` (string): project-relative file path
  - `cursor` (int): current line number (1-indexed)
  - `selectionContent` (string|null): selected text if any
  - `selection` (string|null): line range as `"startLine:endLine"` if selection exists
- **Implementation:** Calls `EditorContextCapture.capture()` via `SwingUtilities.invokeAndWait()`

#### Tool: `get_opened_files`
- **Description:** `"Returns a list of file paths currently open in editor tabs"`
- **Input:** None (`EmptyToolInput`)
- **Output:** JSON array of strings (file paths)
- **Implementation:** Enumerates open documents via `EditorRegistry.getRegisteredDocuments()` via `SwingUtilities.invokeAndWait()`, extracts `FileObject` paths

#### EDT Bridging
Both tool executors run on the MCP async thread pool. They capture editor state by submitting to EDT:
```java
SwingUtilities.invokeAndWait(() -> { /* capture */ });
```

### 5. Modified Files Summary

| File | Change |
|------|--------|
| `McpManager.java` | `mcpDisabled = true` → `false` |
| `McpServer.java` | Replace `weather` registration → `editorToolProvider.registerTools(mcpTools)` |
| `EditorToolProvider.java` | **New** — tool registration + executors |
| `EditorContextCapture.java` | No changes (reused as-is) |
| `MessageSender.java` | Remove `EditorContextCapture.capture()` call and `context` variable |
| `ProcessManager.java` | Remove context→XML/selection block building from `sendMessage()` |
| `WeatherInput.java` | **Delete** |
| `WeatherOutput.java` | **Delete** |

### 6. Data Flow (After)

```
User types message → MessageSender (no context capture)
  → ProcessManager.sendMessage() (no metadata/selection blocks)
  → ACP server → AI
    → AI decides: call get_current_file_context
    → HTTP POST to MCP server
    → EditorToolProvider → EDT → EditorContextCapture.capture()
    → Returns context JSON → AI generates response
```
