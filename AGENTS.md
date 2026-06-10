# Agent Instructions for beanbot

## Project Overview
- **Project name**: Coding Assistant
- **Project type**: NetBeans IDE plugin (NBM packaging)
- **Language**: Java 17
- **Build tool**: Maven
- **Current Version**: 1.5.26

## Build Commands
- Build: `mvn package` (runs checkstyle + javadoc + tests)
- Clean: `mvn clean`
- Skip Tests: `mvn package -DskipTests`

## Key Technologies
- NetBeans Platform API (RELEASE290)
- Flexmark for markdown processing
- Jackson for JSON processing
- RSyntaxTextArea for code block syntax highlighting
- JUnit 5 for testing

## Source Structure
- `src/main/java/github/anandb/netbeans/` - Main source code
  - `manager/` - ACPManager, AcpProtocolClient
  - `model/` - Message, Session, Agent, SessionUpdate (ACP compliant)
  - `project/` - Project management (startup, project manager)
  - `ui/` - UI components (chat panel, message bubbles, collapsible panes, theme manager)

## Architecture & Communication
- **Agent Client Protocol (ACP)**: Plugin is compliant with ACP for session metadata and updates.
- **JSON-RPC**: Bidirectional communication via `AcpProtocolClient`.
- **SSE Streams**: Handles `session/update` notifications for real-time AI response streaming.
- **Stop Mechanism**: `session/cancel` MUST be sent as a **notification**, not a request, as per ACP protocol v1.
- **UI Architecture**:
    - `AssistantTopComponent`: Primary chat window with global controls.
    - `ChatThreadPanel`: Manages the thread of message bubbles.
    - `MessageBubble`: Handles rendering of specific message turns, including "thought", "tool", and "code" segments.
    - `CollapsibleCodePane`: Custom component for syntax-highlighted code with copy/insert actions.

## Important Files
- `pom.xml` - Maven configuration and dependencies (RSyntaxTextArea, Flexmark, Jackson).
- `src/main/resources/github/anandb/netbeans/ui/layer.xml` - NetBeans registration for the chat window.

## Coding Notes
- **Logging**: Use index-based placeholders (e.g., `{0}`) for `github.anandb.netbeans.support.Logger`; do not concatenate strings. The custom Logger API has `severe/warn/info/fine(String, Object...)` and `log(Level, String, Object...)`. There is NO `warning()` method. To pass both placeholder args AND a Throwable in one call, use `LOG.log(Level.SEVERE, "msg {0}", arg1, ex)` with the Throwable as the LAST arg — Java 17's `java.util.logging.Logger.log()` extracts it. Level needs `import java.util.logging.Level;`.
- **Theming**: Use `ThemeManager.getCurrentTheme()` for colors. Icons should have `-dark.svg` variants for high-contrast dark mode support, resolved automatically via `ThemeManager`.
- **Asynchrony**: Use `SwingUtilities.invokeLater()` for all UI updates coming from background RPC/SSE threads.

## Critical Learnings

### Notification Pipeline
`session/update` notifications flow through: `AcpProtocolClient.handleMessage()` → `ProcessManager` notification handler → `notifyListeners()` → `SessionManager` SSE listener → `SessionLifecycleHandler.onSessionUpdate()` → `StrategyRegistry.handle()` → UI. Any early `return` in this chain silently drops the notification from all downstream handlers.

### End-of-Turn Signals
Streaming is finalized via `ChatThreadPanel.stopStreaming()` which calls `finalizeStreaming()`. End-of-turn triggers in `SessionLifecycleHandler`:
- `responding_finished` / `end_turn` / `available_commands_update` notification types → `turnEnded=true` + 150ms flush timer
- `session/load` JSON-RPC response with `result.configOptions` → `onSessionLoaded()` → flush timer

### JSON-RPC Response vs Notification
- Responses have `"id"` and `"result"`/`"error"` (no `"method"`) → handled by `AcpProtocolClient.handleMessage()` completing pending futures
- Notifications have `"method"` (no `"id"`) → routed to notification listeners
- `config_options_update` exists in `MessageType` enum but is NOT used as a notification type; configOptions arrives as a JSON-RPC response to `session/load`

### MessageType Enum
`MessageType.java` values: agent_message_chunk, agent_thought_chunk, available_commands_update, plan, tool_call, tool_call_update, usage_update, user_message_chunk, config_options_update, session_info_update, message, error_response, responding_finished, end_turn. Check this enum before adding new notification handling.

### Connection Error Handler
`AcpProtocolClient.setConnectionErrorHandler()` is NEVER called by `ProcessManager`. The `notifyConnectionError()` method in `readLoop` and `sendRequest` is a silent noop. The original reader thread exception is only logged via `LOG.severe(...)`. Pending futures get a generic `IOException("Client closed")` when `close()` drains them.

### Plugin Installation
The `@OnStart` handler (`ACPStartup.java`) opens the window on version change, which triggers `componentOpened()`. During plugin installation, this happens within module activation. Always defer heavy init (server start, session refresh) via `SwingUtilities.invokeLater()` in `componentOpened()` so the installer wizard can complete first.

### Key Files
| File | Role |
|------|------|
| `AcpProtocolClient.java` | JSON-RPC client, reader thread, watchdog idle timer, pending request map |
| `ProcessManager.java` | Server lifecycle (start/stop/restart), notification routing, disconnection handling |
| `SessionManager.java` | Session CRUD, state machine, session loading (`session/load` RPC) |
| `SessionLifecycleHandler.java` | UI callbacks: onSessionUpdate, onSessionLoaded, onSessionError, end_turn |
| `StrategyRegistry.java` | Type-switch dispatch of session updates to UI handlers; must handle every valid type or it logs a warning |
 | `ComponentLifecycleHandler.java` | Component open/close lifecycle; called during plugin install via @OnStart

### Streaming Performance & Finalization
- During streaming: bubbles use a cheap `JTextArea` (fast `append()` via `doc.insertString()` with 150ms timer).
- `finalizeStreaming(boolean expanded, boolean immediate)`: `immediate=false` starts a 300ms cooldown timer. New `appendText()` calls reset the timer. Only one full `updateContent()` runs when cooldown expires. `stopStreaming()` always uses `immediate=true`.
- `finalizeStreaming(boolean)` (legacy 1-param) calls `finalizeStreaming(expanded, false)` — deferred by default.
- `flushUpdate()` treats `isFinalizingDeferred` the same as `isStreaming` — both use the fast JTextArea delta path.

### FitEditorPane HTML Document Reuse
`FitEditorPane.setText(String)` overrides `super.setText()` to reuse the existing `Document`. Instead of re-obtaining the EditorKit and dispatching separate remove+insert events, it does `doc.remove()` + `kit.read()` on the same document. Avoids view tree teardown/rebuild.

### HtmlContentPreparer Markdown Cache
LRU cache (256 entries, synchronized) for markdown→HTML output. Texts >32 KB bypass the cache. `clearCache()` exposed for theme switches. Cache keyed by full markdown string.

### Filter Changes — Apply vs Reload
`applyTypeFilters()` first checks `cachedMessages`. If non-empty, calls `setMessages(cachedMessages)` — full clear + re-add, which triggers fresh `combineToolThoughtBubbles()` + visibility logic. This avoids subtle revalidation bugs with hidden/shown combined bubbles.

`setMessages()` caches the message list; `clearMessages()` clears the cache.

### Bubble Corner Radius
Assistant bubbles: `RoundedPanel(16)` — matches user bubbles (16). Table segments and other content elements use `RoundedPanel(12)`.

### Code Review — Resource Lifecycle
- `removeNotify()` in UI components MUST stop all `javax.swing.Timer` instances AND finalize any streaming state before calling `super.removeNotify()`. Timers hold strong references via action listener lambdas, preventing GC.
- Inline `Timer(150, e -> ...)` should guard action with `component.isDisplayable()` check to prevent operation on disposed panels.
- `McpServer` connector idle timeout clamped to minimum 30s (`Math.max(30, idleSec)`). 0 or negative disables idle detection, leaking connections.

### SessionListener EDT Contract
All `SessionListener` implementations in `SessionLifecycleHandler` wrap callbacks in `SwingUtilities.invokeLater()`. Background threads (CompletableFuture pool, reader thread, watchdog thread) freely call listener methods — each implementation MUST marshall UI work to EDT. The design is documented in `SessionManager.createNewSession()` comment.