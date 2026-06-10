# Agent Instructions for beanbot

## Project Overview
- **Project**: Coding Assistant (NetBeans IDE plugin, Java 17, Maven)
- **Current Version**: 1.5.26
- **Key Tech**: NetBeans API (RELEASE290), Flexmark, Jackson, RSyntaxTextArea, JUnit 5.

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

## Token Efficiency Rules
- **Targeted File Reads**: NEVER read full files unless they are <100 lines. Use `view_file`
  with precise `StartLine` and `EndLine` parameters.
- **Concise Edits**: Prefer `replace_file_content` over `write_to_file`. Only replace the
  specific lines of interest to keep token counts low.
- **Avoid Redundant Builds**: Run checks and test suites only when validating completed features.
- **Component Reuse**: Reuse lightweight rendering components where possible. Avoid custom
  layout managers or deep containers.

## Critical Technical Details

### Notifications & Streaming
- `session/update` pipeline: `AcpProtocolClient.handleMessage()` → `ProcessManager` →
  `SessionManager` → `SessionLifecycleHandler.onSessionUpdate()` → `StrategyRegistry.handle()`
  → UI. Returning early anywhere drops the message.
- Streaming is finalized via `ChatThreadPanel.stopStreaming()` → `finalizeStreaming()`.
- End-of-turn signals: `responding_finished`, `end_turn`, or `available_commands_update` set
  `turnEnded=true` and start a 150ms flush timer. `session/load` configOptions also triggers a
  flush.
- `MessageType` enum contains all valid session updates (e.g. `agent_message_chunk`,
  `agent_thought_chunk`, `plan`, `tool_call`). Check this enum before adding message types.

### Connection & Lifecycle
- `AcpProtocolClient.setConnectionErrorHandler()` is a noop. Disconnection handles
  IOExceptions silently; pending futures receive a generic client closed exception.
- `@OnStart` handler (`ACPStartup.java`) triggers `componentOpened()`. Defer heavy tasks
  (server start, session load) via `SwingUtilities.invokeLater()` to avoid blocking plugin
  installation.

### UI Sizing & Document Re-use
- Bubbles use a cheap `JTextArea` during streaming (timer flush every 150ms).
- `finalizeStreaming(expanded, immediate=false)` starts a 300ms cooldown timer. New content
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
- Clamping: `McpServer` connector idle timeout is clamped to a minimum of 30s.

### Auto-Scroll Contract
`ScrollController.isAtBottom()` uses a 50px tolerance (not 400px). All content-modifying calls in `ChatThreadPanel` (`addSingleBubble`, `processMessageSections`, `stopStreaming`, stream timer) capture `wasAtBottom` BEFORE touching content. Auto-scroll only fires when `wasAtBottom` is true. The stream timer path (`Timer 300ms`) already captured `wasAtBottom` before `flushUpdate()` — this is the correct pattern; new callers must follow it.

### Renderer Selection
- `MarkdownStyledRenderer` bypasses the Swing HTML engine entirely by inserting text ranges
  with `SimpleAttributeSet` into a `JTextPane`. Fast and lightweight; use for streaming
  collapsible activity/thought panes.
- `FitEditorPane` renders complete HTML/CSS via Swing's native rendering engine. Heavy and
  complex; reserved for main chat bubbles needing full list, link, and nested styling support.