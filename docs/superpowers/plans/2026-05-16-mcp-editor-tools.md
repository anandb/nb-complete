# MCP Editor Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable MCP server by default, replace auto-injected file context with on-demand MCP tools (`get_current_file_context` and `get_opened_files`).

**Architecture:** New `EditorToolProvider` class registers two `ToolExecutor` implementations that bridge to EDT via `SwingUtilities.invokeAndWait()`. `McpServer.start()` calls the provider instead of registering the weather demo tool. `ProcessManager.sendMessage()` drops the context→XML block building.

**Tech Stack:** Java 17, NetBeans Editor API, Jackson, Jetty (MCP)

---

### Task 1: Enable MCP Server by Default

**Files:**
- Modify: `src/main/java/github/anandb/netbeans/mcp/McpManager.java:17`

- [ ] **Step 1: Change default from `true` to `false`**

Change line 17:
```java
private volatile boolean mcpDisabled = true;
```
to:
```java
private volatile boolean mcpDisabled = false;
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/github/anandb/netbeans/mcp/McpManager.java
git commit -m "feat: enable MCP server by default"
```

---

### Task 2: Create `EditorToolProvider` with Two MCP Tools

**Files:**
- Create: `src/main/java/github/anandb/netbeans/mcp/EditorToolProvider.java`
- Test: No dedicated test (tools require running NetBeans; verify via build)

- [ ] **Step 1: Create the `EmptyToolInput` record class**

This is used for tools that take no parameters:
```java
package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EmptyToolInput() {}
```

- [ ] **Step 2: Create the `EditorToolProvider` class**

```java
package github.anandb.netbeans.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.ui.EditorContextCapture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;

public class EditorToolProvider {

    private static final Logger LOG = new Logger(EditorToolProvider.class);
    private final ObjectMapper mapper = MapperSupplier.get();

    public void registerTools(McpTools mcpTools) {
        registerGetCurrentFileContext(mcpTools);
        registerGetOpenedFiles(mcpTools);
    }

    private void registerGetCurrentFileContext(McpTools mcpTools) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        mcpTools.registerTool(
                "get_current_file_context",
                "Returns the current file path, cursor position, and any text selection from the active editor tab",
                schema,
                new ToolExecutor<EmptyToolInput, Map<String, Object>>(EmptyToolInput.class) {
                    @Override
                    public Map<String, Object> execute(EmptyToolInput args) throws Exception {
                        Map<String, Object>[] result = new Map[1];
                        SwingUtilities.invokeAndWait(() -> {
                            result[0] = EditorContextCapture.capture();
                        });
                        return result[0];
                    }
                });
    }

    private void registerGetOpenedFiles(McpTools mcpTools) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        mcpTools.registerTool(
                "get_opened_files",
                "Returns a list of file paths currently open in editor tabs",
                schema,
                new ToolExecutor<EmptyToolInput, List<String>>(EmptyToolInput.class) {
                    @Override
                    public List<String> execute(EmptyToolInput args) throws Exception {
                        List<String>[] result = new List[1];
                        SwingUtilities.invokeAndWait(() -> {
                            List<String> paths = new ArrayList<>();
                            for (var editor : EditorRegistry.componentList()) {
                                FileObject fo = NbEditorUtilities.getFileObject(editor.getDocument());
                                if (fo != null) {
                                    paths.add(fo.getPath());
                                }
                            }
                            result[0] = paths;
                        });
                        return result[0];
                    }
                });
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `mvn package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/github/anandb/netbeans/mcp/EditorToolProvider.java src/main/java/github/anandb/netbeans/mcp/EmptyToolInput.java
git commit -m "feat: add EditorToolProvider with get_current_file_context and get_opened_files tools"
```

---

### Task 3: Wire `EditorToolProvider` into `McpServer`, Remove Weather

**Files:**
- Modify: `src/main/java/github/anandb/netbeans/mcp/McpServer.java`

- [ ] **Step 1: Replace weather registration with `EditorToolProvider`**

In `McpServer.java`, remove lines 11-12 (WeatherInput/WeatherOutput imports), remove lines 44-58 (weather schema and registration), and add the `EditorToolProvider` call after `asyncExecutor` is created.

Replace lines 44-58:
```java
        ObjectNode weatherSchema = mapper.createObjectNode();
        ObjectNode locationProp = mapper.createObjectNode();
        locationProp.put("type", "string");
        locationProp.put("description", "City or location name");
        weatherSchema.set("location", locationProp);
        weatherSchema.put("type", "object");
        
        mcpTools.registerTool("weather", "Get current weather for a location",
                              weatherSchema, new ToolExecutor<WeatherInput, WeatherOutput>(WeatherInput.class) {
            @Override
            public WeatherOutput execute(WeatherInput args) throws Exception {
                String location = args.location() != null ? args.location() : "Unknown";
                return new WeatherOutput(25.0, "Sunny", 60, location);
            }
        });
```

with:
```java
        EditorToolProvider editorTools = new EditorToolProvider();
        editorTools.registerTools(mcpTools);
```

Also remove the two Weather imports at the top (lines 11-12):
```java
import github.anandb.netbeans.model.WeatherInput;
import github.anandb.netbeans.model.WeatherOutput;
```

- [ ] **Step 2: Build to verify it compiles**

Run: `mvn package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/github/anandb/netbeans/mcp/McpServer.java
git commit -m "feat: wire EditorToolProvider, remove weather demo tool"
```

---

### Task 4: Remove Auto-Injected File Context from Message Flow

**Files:**
- Modify: `src/main/java/github/anandb/netbeans/ui/MessageSender.java:153-154`
- Modify: `src/main/java/github/anandb/netbeans/manager/ProcessManager.java:439-488`

- [ ] **Step 1: Remove context capture from `MessageSender`**

In `MessageSender.java`, remove lines 153-154:
```java
        // Editor Context
        Map<String, Object> context = isForwardedSlash ? null : EditorContextCapture.capture();
```

Update the `sendMessage` call at line 157 to pass `null` instead of `context`:
```java
        ProcessManager.getInstance().sendMessage(currentSessionId, messageText, context, fileBlocks)
```
→
```java
        ProcessManager.getInstance().sendMessage(currentSessionId, messageText, null, fileBlocks)
```

Also remove the unused `EditorContextCapture` import if it becomes unused.

- [ ] **Step 2: Remove context processing from `ProcessManager.sendMessage()`**

In `ProcessManager.java`, remove the entire context-to-blocks block (lines 439-488):

Remove:
```java
        // 1. Context & Metadata (Start with this so model has environment context first)
        if (context != null) {
            String filePath = (String) context.get("filePath");
            if (filePath != null) {
                File file = new File(filePath);
                String lang = LanguageResolver.fromPath(filePath);
                String fileName = file.getName();

                // Metadata XML Block (For the AI)
                StringBuilder xml = new StringBuilder();
                xml.append("<metadata>\n");
                xml.append("  <purpose>reference</purpose>\n");
                xml.append("  <note>The file path, cursor, and selection below are reference-only")
                   .append(" context about the user's editor state. The user's text message")
                   .append(" that follows is the primary instruction.</note>\n");
                xml.append("  <language>").append(lang).append("</language>\n");
                xml.append("  <file_path>").append(filePath).append("</file_path>\n");

                Object cursorObj = context.get("cursor");
                if (cursorObj != null) {
                    xml.append("  <cursor>").append(cursorObj.toString()).append("</cursor>\n");
                }

                Object selObj = context.get("selection");
                if (selObj != null) {
                    xml.append("  <selection>").append(selObj.toString()).append("</selection>\n");
                }
                xml.append("</metadata>");

                Map<String, Object> metadataPart = new HashMap<>();
                metadataPart.put("type", "text");
                metadataPart.put("text", xml.toString());

                // Add annotations for assistant audience (OpenCode internal)
                Map<String, Object> annotations = new HashMap<>();
                annotations.put("audience", List.of("assistant"));
                metadataPart.put("annotations", annotations);

                promptBlocks.add(metadataPart);

                // Selection Content Block (if any)
                String selectionContent = (String) context.get("selectionContent");
                if (selectionContent != null && !selectionContent.isEmpty()) {
                    Map<String, Object> selectionPart = new HashMap<>();
                    selectionPart.put("type", "text");
                    selectionPart.put("text", "\nSelection from `" + fileName + "`:\n```" + lang + "\n" + selectionContent + "\n```\n");
                    promptBlocks.add(selectionPart);
                }
            }
        }
```

Keep the comment numbering consistent — change `// 1b. Additional blocks` to `// 1. Additional blocks`.

Also check for unused imports after the change (`File`, `LanguageResolver`, the removed imports below) and remove them.

The `ProcessManager.java` imports section (lines 1-50) should be checked for these now-unused imports after removing lines 439-488:
- `java.io.File` (only used for `new File(filePath)` in removed code)
- Any imports only used in the removed section

- [ ] **Step 3: Build to verify it compiles**

Run: `mvn package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/github/anandb/netbeans/ui/MessageSender.java src/main/java/github/anandb/netbeans/manager/ProcessManager.java
git commit -m "feat: remove auto-injected file context, AI now uses MCP tools"
```

---

### Task 5: Clean Up Deleted Weather Model Files

**Files:**
- Delete: `src/main/java/github/anandb/netbeans/model/WeatherInput.java`
- Delete: `src/main/java/github/anandb/netbeans/model/WeatherOutput.java`

- [ ] **Step 1: Delete both files**

```bash
rm src/main/java/github/anandb/netbeans/model/WeatherInput.java src/main/java/github/anandb/netbeans/model/WeatherOutput.java
```

- [ ] **Step 2: Build to verify everything still compiles**

Run: `mvn package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Full build with tests**

Run: `mvn package`
Expected: BUILD SUCCESS, all 106 tests pass

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: remove weather demo tool model files"
```

---

### Task 6: Final Verification

- [ ] **Step 1: Run full build**

Run: `mvn package`
Expected: BUILD SUCCESS, all tests pass, 0 checkstyle violations, javadoc OK

- [ ] **Step 2: Verify no stale references to `context` param**

```bash
rg "Map.*context" src/main/java/
```
Only the method signature in `ProcessManager.java` should remain (the `context` parameter is kept for backward compatibility but not used).
