# Agent Instructions for coding-assistant

## Project Overview
- **Project**: Coding Assistant (NetBeans IDE plugin, Java 17, Maven)
- **Current Stable Version**: 1.10.0
- **Key Tech**: NetBeans API (RELEASE220), Flexmark, Jackson, RSyntaxTextArea, JUnit 5.

## Build Commands
- Build: `mvn package` (runs checks & tests) | Clean: `mvn clean` | Quick: `mvn package -DskipTests`

## Architecture & UI
- **ACP & JSON-RPC**: Compliant with ACP v1. Communication via `AcpProtocolClient`
  (JSON-RPC + SSE Streams).
- **Stop Mechanism**: `session/cancel` MUST be a **notification** (no ID).
- **UI Structure**:
    - `AssistantTopComponent`: Main chat panel.
    - `ChatThreadPanel`: Message thread container.
    - `MessageBubble`: Chat turns (thought/tool/code/text bubbles).
    - `CollapsibleCodePane`: Syntax highlighted code block rendering.

## Important Coding Rules
- **Logging**: Use index-based placeholders (`{0}`). NO `warning()` method (use
  `LOG.log(Level.WARNING, ...)` or `severe/warn/info/fine`). Pass exceptions as the LAST argument.
- **Theming**: Use `ThemeManager.getCurrentTheme()`. Dark mode icons require `_dark.svg` suffix.
- **Asynchrony / EDT**: Wrap all background thread UI updates in `SwingUtilities.invokeLater()`.
- **Regex in hot paths**: NEVER use `String.matches()` or `String.replaceAll()` in loops or
  per-tick callbacks. Pre-compile with `static final Pattern` and reuse via `matcher()`.
- **Swing Layout Loops**: NEVER call `revalidate()` synchronously inside sizing methods like `getPreferredSize()` or `setBounds()`. Doing so re-adds the component to Swing's invalidation queue during a layout pass, causing an infinite 100% CPU layout loop. Use a boolean guard (e.g. `suppressRevalidate`) to ignore or defer revalidation during internal size calculations.
- **NbPreferences caching**: If a preference is read in a hot path, cache it in a `static volatile`
  field and register a `PreferenceChangeListener` to update on change. Do NOT call
  `NbPreferences.forModule().getBoolean()` on every invocation.
- **Scan ranges**: When scanning large strings for patterns (e.g. ASCII art detection), limit
  the scan to a bounded prefix (e.g. first 512 chars) instead of the full string.
- **ACPProjectManager cache**: `getAllOpenProjects()` returns the cached `currentProjects` field,
  not `OpenProjects.getDefault().getOpenProjects()`. The cache is populated by `start()` and
  updated via `propertyChange()` on project open/close. Do NOT bypass the cache.
- **New session debounce**: The toolbar New Session button has a 300ms debounce timer to prevent
  accidental double-clicks. The keyboard shortcut (`Ctrl+L` via `NewSessionAction`) bypasses the
  debounce and calls `createNewSession()` directly.
- **contract/ → manager/ singletons**: `contract/` classes must use `Lookup.getDefault().lookup()`
  to access services, never direct `Manager.getInstance()` calls. The `SlashCommandInterceptor`
  violation was fixed by injecting `ProcessControl` via Lookup.
- **Request idle timeouts, not absolute**: Use per-request idle timeouts tied to
  `AcpProtocolClient`'s connection-level `lastDataTime`. Only fail requests when the connection
  is idle (no inbound data) beyond their timeout. Do NOT use `future.orTimeout()` — that is an
  absolute deadline that kills requests even when data is flowing. Set timeouts by passing
  `(method, params, timeout, unit)` to `ProcessManager.sendRequest()` or
  `SessionRpcClient` methods.
- **Process I/O pattern**: When reading a subprocess's stdout/stdin with a timeout, read in a
  `RequestProcessor` background task, then use `proc.waitFor(timeout, unit)` on the caller
  thread as the timeout mechanism. On timeout, `proc.destroyForcibly()` closes the pipe,
  unblocking the reader. Always call `readerTask.waitFinished(timeoutMs)` before consuming
  output and in a `finally` block to guarantee the reader exits. Example: `StashDiffAction.runGit()`.

## Token Efficiency Rules
- **Targeted File Reads**: NEVER read full files unless they are <100 lines. Use `view_file`
  with precise `StartLine` and `EndLine` parameters.
- **Concise Edits**: Prefer `replace_file_content` over `write_to_file`. Only replace the
  specific lines of interest to keep token counts low.
- **Avoid Redundant Builds**: Run checks and test suites only when validating completed features.
- **Component Reuse**: Reuse lightweight rendering components where possible. Avoid custom
  layout managers or deep containers.

## Hexagonal Architecture Rules

### Layer Model (dependencies flow downward only)
```
        ┌─────────┐
        │   ui/   │  ← presentation (highest)
        └────┬────┘
             │
        ┌────▼────┐
        │manager/ │  ← business logic
        └────┬────┘
             │
    ┌────────┼────────┐
    ▼        ▼        ▼
┌────────┐ ┌────────┐ ┌────────┐
│ model/ │ │contract│ │support/│  ← data, interfaces, utils (lowest)
└────────┘ └────────┘ └────────┘
```

### Forbidden Patterns (do NOT introduce these)
- **manager/ → ui/**: Never import from `ui/` in `manager/`. Use `contract/`
  interfaces or `support/` classes instead.
- **project/ → ui/**: Use `WindowManager.findTopComponent("...")` by string ID,
  never direct class references.
- **ui/ bypassing contract/**: `ui/` should call `contract/` interfaces, not
  concrete `manager/` classes directly. This keeps UI testable and swappable.
- **Lower layer → Higher layer**: `model/`, `contract/`, `support/` must never
  import from `manager/` or `ui/`.

### Adding New Code
- **New manager/ class**: Depends only on `contract/`, `model/`, `support/`.
  Inject `contract/` interfaces, not concrete managers.
- **New ui/ class**: Depends on `contract/` interfaces. If you need session
  state, use `contract/SessionQuery`, not `manager/SessionManager` directly.
- **New support/ class**: Zero dependencies. Pure utilities, constants, loggers.
- **New contract/ interface**: Only imports from `model/`. Defines ports that
  `manager/` implements and `ui/` consumes.

### Fixing Existing Violations
If you find an upward dependency, extract a neutral class in a lower layer:
```java
// BAD: manager/ProcessManager.java imports ui/ACPOptionsPanel
import github.anandb.netbeans.ui.ACPOptionsPanel;
NbPreferences.forModule(ACPOptionsPanel.class)

// GOOD: manager/ProcessManager.java uses support/PreferenceKeys
import github.anandb.netbeans.support.PreferenceKeys;
NbPreferences.forModule(PreferenceKeys.class)
```

### What Belongs Where
| Concern | Location | Examples |
|---------|----------|----------|
| Swing components, layout | `ui/` | Panels, buttons, dialogs |
| Session state machine | `manager/` | `SessionManager` |
| Network protocol | `manager/` | `AcpProtocolClient` |
| Domain interfaces | `contract/` | `SessionQuery`, `ProcessControl` |
| Data classes | `model/` | `Session`, `ProcessedMessage` |
| Utilities, constants | `support/` | `Logger`, `PreferenceKeys` |

### Architectural Debt (Known Violations — All Resolved)
- **ui/ → manager/ singletons (RESOLVED)**: All `ui/` → `manager/SessionManager` and
  `ProcessManager` imports have been eliminated. UI now accesses services via
  `Lookup.getDefault().lookup(SessionControl.class)` and
  `Lookup.getDefault().lookup(ProcessControl.class)`. Do NOT reintroduce
  `SessionManager.getInstance()` or `ProcessManager.getInstance()` calls in `ui/`.
- **project/ACPStartup → manager/ (RESOLVED)**: `ACPStartup` was importing
  `manager/UpdateCheckerService` directly. Fixed via `contract/UpdateCheckerControl`
  interface with `Lookup.getDefault().lookup()`. Do NOT reintroduce direct
  `UpdateCheckerService.getInstance()` calls in `project/`.
- **project/ACPShutdown → ui/ (RESOLVED)**: `ACPShutdown` was importing
  `ui/ImagePasteTransferHandler` to shut down the image paste I/O thread pool.
  Fixed by extracting the `RequestProcessor` to `support/ImagePasteIoProcessor`.
- **ui/GoToFileDialog → manager/FileCacheManager (RESOLVED)**: `GoToFileDialog` was importing
  `manager/FileCacheManager` directly instead of using `contract/FileCacheQuery` via Lookup.
  Fixed by accessing `Lookup.getDefault().lookup(FileCacheQuery.class)`. Do NOT reintroduce
  `FileCacheManager.getDefault()` calls in `ui/`.
- **New extraction pattern**: When extracting utilities from `ui/` god components, place
  pure logic in `support/` (e.g. `ToolContextExtractor`, `ShortcutUtils`). Keep Swing-coupled code in `ui/`.

## Critical Technical Details

### Notifications & Streaming
- `session/update` pipeline: `AcpProtocolClient.handleMessage()` → `ProcessManager` →
  `SessionManager` → `SessionLifecycleHandler.onSessionUpdate()` → `StrategyRegistry.handle()`
  → UI. Returning early anywhere drops the message.
- Streaming is finalized via `ChatThreadPanel.stopStreaming()` → `finalizeStreaming()`.
- End-of-turn signals: `responding_finished`, `end_turn`, or `available_commands_update` set
  `turnEnded=true` and start a flush timer (`TimingConstants.STREAM_FLUSH_MS`, currently 300ms).
  `session/load` configOptions also triggers a flush.
- `MessageType` enum contains all valid session updates (e.g. `agent_message_chunk`,

(Showing lines 110-122 of 153. Use offset=112 to continue.)
  `agent_thought_chunk`, `plan`, `tool_call`). Check this enum before adding message types.

### Connection & Lifecycle
- `AcpProtocolClient.setConnectionErrorHandler()` is a noop. Disconnection handles
  IOExceptions silently; pending futures receive a generic client closed exception.
- `@OnStart` handler (`ACPStartup.java`) triggers `componentOpened()`. Defer heavy tasks
  (server start, session load) via `SwingUtilities.invokeLater()` to avoid blocking plugin
  installation.

### UI Sizing & Document Re-use
- Bubbles use a cheap `JTextArea` during streaming (timer flush every `TimingConstants.STREAM_FLUSH_MS` ms).
- `finalizeStreaming(expanded, immediate=false)` starts a cooldown timer (`TimingConstants.STREAM_FLUSH_MS` ms). New content
  resets the timer. Full update runs once on cooldown expiry. `stopStreaming()` calls it with
  `immediate=true`.
- `FitEditorPane.setText(String)` reuses the HTML Document via `doc.remove()` + `kit.read()`
  to prevent layout tree rebuilds.
- `HtmlContentPreparer` uses an LRU cache (256 entries) for markdown->HTML conversion. Content
  >32 KB bypasses the cache.
- UI elements use `RoundedPanel(16)` for assistant/user bubbles, and `RoundedPanel(12)` for
  tables/tool blocks.
- `removeNotify()` in UI components MUST stop `javax.swing.Timer` instances and finalize
  streaming to prevent memory leaks.
- `BaseCollapsiblePane` does NOT remove its MouseListeners (`toggleListener`, `copyButtonHoverListener`)
  in `removeNotify()`. These listeners only capture `this` (the component itself) — they die with the
  component tree. Removing and re-adding them caused duplicate listeners (click fired toggle twice).
  Do NOT add matching `removeMouseListener` calls here.
- Clamping: `McpServer` connector idle timeout is clamped to a minimum of 30s.
- MCP tools/call: Response is sent immediately after tool execution (no artificial delay). The old 5,000ms minimum latency was removed in v1.7.2 because it caused client abort errors on macOS.

### SSE → EDT Session Race Guard
`SessionLifecycleHandler.displayMessage()` captures `update.params().sessionId()` before scheduling the EDT callback via `invokeLater`. Inside the runnable, it re-checks against `SessionControl.getCurrentSessionId()` and silently drops the message if the session no longer matches. This prevents stale SSE messages from appearing in the wrong session's chat panel when the user switches sessions during streaming.

### Editor Context Capture
`EditorContextCapture.capture()` returns `null` when no real file is open (no `FileObject` associated with the editor document). This prevents phantom paths like `/tmp/xxx` from being sent to the AI as context. The return value is passed as `Map<String, Object> context` in `MessageSender`, where `null` means "no editor context available" — the caller skips attaching context entirely.

### Auto-Scroll Contract
`ScrollController.isAtBottom()` uses a 50px tolerance (not 400px). All content-modifying calls in `ChatThreadPanel` (`addSingleBubble`, `processMessageSections`, `stopStreaming`, stream timer) capture `wasAtBottom` BEFORE touching content. Auto-scroll only fires when `wasAtBottom` is true. The stream timer path (`Timer TimingConstants.STREAM_FLUSH_MS` ms) already captured `wasAtBottom` before `flushUpdate()` — this is the correct pattern; new callers must follow it.

### Renderer Selection
- `MarkdownStyledRenderer` bypasses the Swing HTML engine entirely by inserting text ranges
  with `SimpleAttributeSet` into a `JTextPane`. Fast and lightweight; use for streaming
  collapsible activity/thought panes.
- `FitEditorPane` renders complete HTML/CSS via Swing's native rendering engine. Heavy and
  complex; reserved for main chat bubbles needing full list, link, and nested styling support.

### Ctrl+L Toggle / Shortcut Registration (DO NOT BREAK)
The Ctrl+L shortcut and Window > Assistant menu item depend on a specific combination of
annotations and layer.xml entries. This is the ONLY working configuration:

1. **`AssistantTopComponent` annotations** (must all be present together):
```java
@TopComponent.Registration(mode = "properties", openAtStartup = true, position = 1001)
@ActionID(category = "Window", id = "github.anandb.netbeans.ui.AssistantTopComponent")
@TopComponent.OpenActionRegistration(
    displayName = "#CTL_AssistantAction",
    preferredID = "AssistantTopComponent"
)
```
   - `@TopComponent.OpenActionRegistration` generates a built-in open action at
     `Actions/Window/github-anandb-netbeans-ui-AssistantTopComponent.instance`.
   - Do NOT remove `@ActionID` or `@TopComponent.OpenActionRegistration` — other
     combinations cause the shortcut or menu to stop working.
   - `@TopComponent.OpenActionRegistration` does NOT support a `shortcuts` attribute in
     NetBeans RELEASE220 — the shortcut must be registered in `layer.xml`.

2. **`layer.xml`** — manual `ToggleAssistantAction` registration (handles toggle/close):
```xml
<!-- Action instance -->
<folder name="Actions">
    <folder name="Window">
        <file name="github-anandb-netbeans-ui-ToggleAssistantAction.instance">
            <attr name="instanceCreate" methodvalue="org.openide.awt.Actions.alwaysEnabled"/>
            <attr name="delegate" newvalue="github.anandb.netbeans.ui.ToggleAssistantAction"/>
            <attr name="displayName" bundlevalue="github/anandb/netbeans/ui/Bundle#CTL_ToggleAssistantAction"/>
            <attr name="iconBase" stringvalue="github/anandb/netbeans/ui/icons/logo_window.svg"/>
        </file>
    </folder>
</folder>
<!-- Menu entry -->
<folder name="Menu">
    <folder name="Window">
        <file name="Assistant.shadow">
            <attr name="originalFile" stringvalue="Actions/Window/github-anandb-netbeans-ui-ToggleAssistantAction.instance"/>
        </file>
    </folder>
</folder>
<!-- Shortcut -->
<folder name="Shortcuts">
    <file name="D-L.shadow">
        <attr name="originalFile" stringvalue="Actions/Window/github-anandb-netbeans-ui-ToggleAssistantAction.instance"/>
    </file>
</folder>
```
   - Both `Assistant.shadow` (menu) and `D-L.shadow` (Ctrl+L) point to
     `ToggleAssistantAction`, NOT the generated open action.
   - `D-L` = Ctrl+L in NetBeans keymap notation (D = Ctrl, S = Shift).

3. **`ToggleAssistantAction.java`** — must use `findInstance()` + `toggleVisibility()`:
```java
@ActionID(category = "Window", id = "github.anandb.netbeans.ui.ToggleAssistantAction")
public class ToggleAssistantAction implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent assistant = AssistantTopComponent.findInstance();
        if (assistant != null) {
            assistant.toggleVisibility();
        }
    }
}
```

4. **`AssistantTopComponent.toggleVisibility()`** — must check `isOpened() && isShowing()`:
```java
public void toggleVisibility() {
    if (isOpened() && isShowing()) {
        close();
    } else {
        if (!isOpened()) {
            open();
        }
        requestActive();
    }
}
```
   - Do NOT replace with `close()` + `open()` (always reopens, can never close).
   - Do NOT remove the `isShowing()` check (needed for minimized/docked state).

5. **`ComponentLifecycleHandler.createPageKeyDispatcher()`** — must NOT intercept Ctrl+L.
   A `KeyEventDispatcher` for Page Up/Down/Ctrl+Home/End was stealing Ctrl+L when focus
   was inside the assistant panel. The interceptor was removed. Do NOT re-add Ctrl+L
   handling in any `KeyEventDispatcher`.

6. MCP tools that we add must have names <= 14 characters in length.
