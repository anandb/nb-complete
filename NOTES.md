# Release Notes

## v1.7.3 (Changes since v1.7.2)

**BREAKING CHANGE** OpenCode Upstream Changes 1.17.9+ (ACP Next Promotion)
- Promoted ACP Next Implementation: The experimental OPENCODE_ACP_NEXT feature flag has been removed, and the "next" implementation
  of the Agent Client Protocol (ACP) is now the default behavior for all users.
- Decoupled Model Variants: The session/load configuration payload has been restructured. Previously, model variants
  (such as thinking effort levels) were combined with base models inside the single model config option.
- New thought_level Config Option: Effort levels and variants are now isolated into their own dedicated thought_level
  config option (identified as effort). The available options for this field update dynamically based on the currently
  selected base model.
- Continue to use 1.7.2 or older if you have an earlier version of OpenCode.

### Features
- **Resizable input split pane**: Bottom panel replaced with `JSplitPane` so users can resize the input area vertically.
- **Determinate progress bar**: Session load progress bar is now blue and determinate (0–100) — starts at 10%, advances to 30% on ACP request, 60% on response, 100% on session loaded. Works for both new session creation and session reload.
- **Preamble context menu**: Right-click the preamble text area for "Clear" and "Reset to default" options.
- **`/title <name>` direct rename**: Passing a name argument to `/title` renames the session immediately without an AI tool call. `/title` alone (no args) still uses AI.
- **Permission dialog code preview**: Permission requests now display code diffs/patches inline. Supports `text`, `diff` (with `text`/`patch`/`oldText+newText`), rendered in `CollapsibleCodePane` with syntax highlighting. Content is scrollable with a 300px max height cap.
- **Markdown Project type**: Minimal project type for folders containing markdown notes and other text files — no build system, no source roots. Detected by a `.mdproject` marker. Use `File > New Project > Other > Markdown Project` or open any existing folder containing a `.mdproject` file. The Projects tab shows the full directory tree with OS junk and editor swap files filtered out.

### Fixes
- **Progress bar through preamble**: Progress bar now stays visible during preamble response (fixed `onPreambleDone` contract).
- **Slash commands in history**: Slash commands now appear in `Alt+Up` navigation history (`messageHistory.add` called before slash intercept).
- **`get_opened_files` scoped to project**: Editor context capture now filters to project files only, excluding phantom `/tmp/xxx` paths.
- **Mac last-line clipping**: Increased `FitEditorPane` height fudge factor (+5→+10) and streaming `JTextArea` bottom margin (4→8) to prevent clipped last line on macOS.
- **Dark/light system properties**: Color resolution now supports separate `propertyDark`/`propertyLight` keys alongside the existing `property` fallback, enabling distinct system property overrides per theme mode.
- **SVG user icon removed**: Dropped `batik-transcoder` dependency — SVG is not supported for user icons. `loadSvgIcon()` removed; SVG paths in settings show "(no preview)".
- **Streaming failsafe**: Added `sweepStreamingBubbles()` dual-source-of-truth sweep to clean up orphaned `JTextArea` instances.
- **Log noise reduction**: MCP and Strategy messages downgraded from `INFO` to `FINE`.
- **Word-granular undo**: Input area groups consecutive character inserts into word-level `CompoundEdit`s. Whitespace ends a group so each undo removes one word.
- **Mac undo (Cmd+Z/Y)**: Undo/redo registered via `InputMap`/`ActionMap` (wins over Swing default bindings) instead of `KeyListener`.
- **TTFT only on real sends**: Time-to-first-token measured from `MessageSender.sendMessage()` callback, not from reloaded messages.
- **PNG-only icon filter**: User icon file chooser filtered to `.png` only; label updated from "SVG or PNG".
- **Session load EDT freeze**: Replaced per-message `invokeLater` with recursive drain queue — each message gets its own EDT tick, paint events fire between messages, preventing 1+ second EDT lockup during 50+ message SSE burst.
- **`/title` and `rename_session` Lookup context**: Slash command handler and MCP tool now use passed Lookup context instead of global Lookup; blank sessionId guard added.
- **EditorContextCapture selection bounds**: Validates selection offsets against document length to prevent `IllegalArgumentException`.
- **`/title` invisible prompt annotation**: Title suggestion now sends prompt with `audience: assistant` annotation so it doesn't render as a user message bubble.
- **Session dropdown rename**: `onSessionRenamed` listener updates the dropdown item in-place without requiring a full session list reload.
- **Debounce timer leak**: `ChatLayoutBuilder.newSessionDebounceTimer` stopped in `removeNotify()` via `layoutBuilder.cleanup()` to prevent timer firing after component disposal.
- **McpManager.start() race**: `readyFuture.complete(null)` moved inside `synchronized(McpManager.this)` on the success path to prevent a concurrent `start()` from prematurely completing a replaced future.
- **ProcessManager cleanup**: Removed redundant `permissionHandler` field (delegated to `requestRouter`); added `@Override` to `shutdown()`.
- **Update notification branding**: Fixed notification title from "BeanBot" to "Coding Assistant".
- **UpdateCheckerService fallback**: `getInstance()` caches Lookup result and logs `SEVERE` on missing registration instead of silently creating an orphan instance.
- **MarkdownTokenizer unescape double-append**: `StringCharacterIterator`-based unescape skipped `next()` after appending the unescaped char, causing escaped chars like `\|` to emit the raw backslash plus the char instead of just the char.
- **Project template position**: Added `position="110"` attribute to new project template registration so it appears in the correct order in the New Project wizard.
- **ACPProjectManager cache**: `getAllOpenProjects()` now returns the cached `currentProjects` field (updated on project open/close events) instead of calling the slow `OpenProjects.getDefault().getOpenProjects()` API every time — eliminates EDT lag when many projects are open.
- **New session debounce**: Reduced toolbar debounce timer from 2000ms to 300ms — button now responds within a few hundred milliseconds instead of 2 seconds.
- **EditorToolProvider EDT fix**: Moved project-check loop out of `SwingUtilities.invokeLater()` so the open-file MCP tool runs the project membership check on the background thread, resolving the result before dispatching line navigation on the EDT.

### UI
- **Wait cursor**: Shows WAIT cursor while a new session is being created.
- **`rename_session` logging**: Tool now logs `sessionId` and `title` at INFO level.

### Performance
- **Caffeine caches**: Replaced manual LRU and `ConcurrentHashMap` caches with Caffeine across `ColorTheme.HEX_CACHE`, `HtmlContentPreparer.MARKDOWN_HTML_CACHE`, `HtmlContentPreparer.HTML_WRAPPER_CACHE`, and `StyleResolver.DERIVED_CACHE`. Caffeine handles concurrency, size eviction, and access-order tracking internally — eliminates external `synchronized` blocks and manual eviction logic.

### Dependencies
- **commons-text 1.13.0**: Added for `unescapeHtml4()` in `SessionManager` custom title decoding.

### Refactoring
- **`SlashCommandInterceptor`**: Switched to custom `Logger` with index-based placeholders.
- **`PluginSettings`**: Exposed `getDefaultPreamble()` for context menu reset.
- **`MockSvgLoader`**: Added for test compatibility after Batik removal.
- **Theming system extraction**: Replaced 45-field `ColorTheme` record with type-safe `ColorKey` enum + `ColorRegistry` map-based container. CSS generation extracted to `CssGenerator`. Resolution logic (property → key → fallback) moved to `ColorRegistry`. Removed 3 dead color entries from `colors.json`. Zero caller API changes — all 48+ call sites unaffected.
- **UpdateCheckerService**: Replaced raw `Thread` with `RequestProcessor` daemon. Extracted `HttpClient` to static field (reused across checks). Added `User-Agent` header to HTTP requests. Cached `NbPreferences.forModule()` in local variable. Added `cancel()` method for clean shutdown.


## v1.7.2 (Changes since v1.7.0)

### Fixes
- **MCP client abort on macOS**: Removed artificial 5,000ms minimum latency from `handleToolsCallAsync`. Tool responses now stream back immediately instead of being delayed, preventing HTTP timeout aborts (`AbortError: The operation was aborted`) from MCP clients on macOS.
- **Phantom editor context**: `EditorContextCapture` now returns `null` when no real file is open (e.g. empty editor, temp document), preventing `/tmp/xxx` paths from being sent to the AI.
- **Session race condition**: Added guarded session ID check in `displayMessage` EDT callback. If the user switches sessions between the SSE filter check and the EDT execution, late messages are silently dropped instead of appearing in the wrong session's chat panel.
- **Slash commands excluded from history**: Removed the `isForwardedSlash` guard in `MessageSender` so slash commands are recorded in navigation history alongside regular messages.
- **`session/delete` removed**: The unsupported `session/delete` method has been removed from the codebase.

### Documentation
- **Known Issues section**: Added to README documenting known limitations with nested agents and session switching during active requests.
- **Version bumps**: `pom.xml`, `README.md`, and `AGENTS.md` updated to v1.7.2.

## v1.7.0 (Changes since v1.6.1)

### Features
- **Session archiving**: Archive/unarchive sessions with toggle in toolbar; show/hide archived sessions next to session dropdown (client-side only). Right-click session dropdown for quick archive/rename/reload.
- **`/title` slash command**: Generate session titles via AI.
- **Session rename**: Rename sessions via MCP editor tool, input dialog, or right-click session dropdown.
- **History search dialog**: Searchable message history with 1024-entry store.
- **Keyboard shortcuts dialog**: Two-column layout with dynamic shortcut resolution; toggle open/close on click.
- **Toolbar customization**: 10 NetBeans action classes for toolbar buttons (assignable shortcuts, no defaults). Right-click toolbar to toggle button visibility via checkbox menu.
- **MCP resources**: Expose caveman skill as MCP resource; implement `resources/list` and `resources/read` in `MessageServlet`.
- **Resume after reconnect**: Sends invisible 'Proceed' prompt with `audience: assistant` after ACP reconnection so the agent resumes from where it left off.

### UI & Accessibility
- **Accessible components**: Added `Accessible` context to `MessageBubble`, `BaseCollapsiblePane`, `ScrollController`, and session dropdown for screen reader navigation.
- **PlaceholderTextArea implements Scrollable**: Input area starts at 2 lines, grows with content — no more hardcoded 100×100 size.
- **Group toggle icons**: `groupToggleBtn` uses `expand.svg`/`collapse.svg` instead of `+`/`-` text.
- **Mnemonics**: Added mnemonics to Go/Stop buttons.
- **History navigation**: Changed from Up/Down to Alt+Up/Alt+Down to avoid conflicts.
- **Reconnect crash details**: ACP server disconnection notification now includes exception details.
- **Tab label format**: Sidebar tab displays `model/level | Agent` with bold blue agent name.

### Performance
- **Swing rendering optimizations**: Batched `stopStreaming()` revalidates (single pass); single `invokeLater` for all messages in `setMessages()`; removed redundant `revalidate()` calls in stream flush.
- **RoundedPanel paint cache**: Cached resolved theme border color and shape across paint passes; avoid `ThemeManager` lookup and shape allocation every repaint.
- **BaseCollapsiblePane short-circuit**: `updateAppearance` skips when colors/expanded state unchanged.
- **MarkdownStyledRenderer**: Index arithmetic for `#` counting; hoisted `ThemeManager.getFont()`; shared `tableAttr` for non-header rows.
- **CollapsibleActivityPane**: `StringBuilder` for streamed text; dropped redundant `revalidate()`/`repaint()` after `JTextArea.append()`.
- **UIUtils**: Single compiled pattern for Markdown table separator rows; cached `fontStackWithActual` result.
- **HtmlContentPreparer**: Synchronised cache overflow check+clear+put; LRU cache (256 entries) for markdown→HTML.
- **TextScanner**: Limit `containsAsciiArt` scan to first 512 chars.

### Concurrency & Stability
- **Race condition fixes**: Cache `NbPreferences` reads in static volatile fields with `PreferenceChangeListener` for hot-path reads (`MessageFilterManager`, `PluginSettings`, `ToolThoughtCombiner`).
- **HtmlContentPreparer**: Synchronised cache overflow.
- **McpManager/McpServer/SessionCacheManager**: Thread safety fixes.
- **ScrollController cleanup**: Proper removal of mouse wheel listeners.
- **MCP server cleanup on failed startup**.

### Fixes
- **Memory leaks**: Stop `copyRevertTimer` in `MessageBubble.removeNotify()`; remove `MouseListener` from `HelpButtonFlash` after flash completes.
- **Activity pane toggle bug**: Fixed duplicate toggle events caused by re-adding listeners in `removeNotify()`; listeners only capture `this` and die with the component.
- **Hex architecture violation**: Replaced `ProcessManager.getInstance()` with `Lookup`-based `ProcessControl` injection in `SlashCommandInterceptor`.
- **Double bubble radius**: Increased `RoundedPanel` radius from 16 to 32 for user/assistant bubbles.
- **Newlines preserved** in user messages.
- **BubbleContentRenderer race**: Store `pendingSegmentedContent` for create-on-addNotify instead of silent no-op.
- **Page Up/Down conflicts**: Scroll blocker check moved before `isDescendingFrom()` so autocomplete popup navigation works correctly.
- **Unknown slash commands**: Fixed being sent as literal user prompts.
- **ACPOptionsPanel**: Validate executable path with inline error display.
- **Reconnect after crash**: Fixed `ProcessManager.onReady` callback being empty lambda; session now reloads and respawns correctly.
- **Crash resume guard**: Only sends Resume prompt on reconnect-after-crash, not manual restart.
- **Activity pane rendering**: Fixed deferred rendering race in `BubbleContentRenderer`.
- **Column gap**: Fixed keyboard shortcuts dialog layout.
- **Reconnect notification**: Now includes disconnection exception details.
- **TextScanner**: Lowered length threshold from 10 to 5 so short valid patterns like `"-----"` aren't rejected.
- **MessageHistory**: Added package-private constructor to skip `NbPreferences` loading in tests.
- **Toolbar popup sync**: Toolbar customization popup now rebuilds on each show so checkbox states always reflect current preferences; shared single popup instance across all buttons.

### Refactoring
- **Class extraction**: Extracted dedicated classes from monolithic components to enforce hexagonal architecture.
- **Hexagonal architecture violations fixed**: Eliminated upward dependency violations across multiple files.
- **License**: Changed from Unlicense to Apache License 2.0 across `pom.xml`, `README.md`.
- **Font family init log**: `ThemeManager` now logs font family and monospace font on first theme resolution.

## v1.6.1 (Changes since v1.6.0)

### UI Fixes
- **FitEditorPane layout loop fix**: Removed `revalidate()` scheduling in `setBounds()` that caused BoxLayout to repeatedly recompute heights, leading to high CPU usage. Also fixed `getPreferredSize()` to prefer the pane's own assigned width over parent width fallback, preventing width/height feedback loops.

## v1.6.0 (Changes since v1.5.26)

### Wayland Support
- **Image paste on Wayland**: Added `wl-paste` fallback in `ImagePasteTransferHandler` for pasting screenshots/images when AWT's `DataFlavor.imageFlavor` fails on Wayland. Probes `wl-paste --list-types` for correct MIME type detection.
- **Wayland detection**: `isWayland()` now checks `os.name` for Linux before checking `XDG_SESSION_TYPE`.
- **Missing wl-clipboard error**: Shows clear status message if `wl-paste` is not installed.

### UI Improvements
- **CWD context menu**: Right-click on the CWD label shows "Locate in System" to open the directory in the system file browser.
- **Tab label format**: Sidebar tab now displays `model (agent/level)` instead of `agent (model/level)`.
- **Activity pane streaming fix**: Deferred bubble finalization, cached markdown HTML to reduce redundant re-renders during streaming.
- **Bubble finalization timer**: Increased flush interval and reduced scroll margin to prevent premature scroll-to-bottom during streaming.
- **Text wrapping fix**: Replaced `JEditorPane` HTML with `JTextPane` StyledDocument for reliable text reflow on resize; added options toggle for combined tool/thought bubbles.
- **FitEditorPane revalidation**: Fixed empty space after width change by triggering revalidation.

### Architecture & Refactoring
- **Hexagonal architecture enforcement**: Eliminated upward dependency violations (`ui/` → `manager/` singletons). UI now accesses services via `Lookup.getDefault().lookup(SessionControl.class)` and `ProcessControl.class`.
- **Contract interfaces**: Added `SessionControl`, `SessionQuery`, `ProcessControl` interfaces in `contract/` package for cleaner dependency injection.
- **Base component extraction**: Eliminated duplicate code by extracting `BaseCollapsiblePane` and test utilities.
- **Markdown renderer extraction**: Separated `MarkdownStyledRenderer` from `ChatThreadPanel` for independent reuse.
- **Timing constants**: Centralized timing values (`STREAM_FLUSH_MS`, `AUTO_SCROLL_TOLERANCE`) in `TimingConstants` class.
- **Tool-thought combiner**: Extracted logic for merging consecutive tool/thought updates into a single accordion.

### Internationalization
- **All hardcoded strings externalized**: User-facing strings moved to `NbBundle.Messages`/`Bundle.properties` across `AssistantTopComponent`, `MessageBubble`, `CollapsibleToolPane`, `WelcomeScreen`.

### Reliability
- **Timer cleanup**: Stopped leaked `javax.swing.Timer` instances in `removeNotify()` to prevent memory leaks.
- **Idle clamp**: `McpServer` connector idle timeout clamped to minimum 30s.
- **isDisplayable guards**: Added checks before UI operations to prevent exceptions on disposed components.
- **Process leak fix**: Cleaned up leaked process on startup failure, early return on write failure.
- **DCL fix**: Corrected double-checked locking in `ProcessManager.getInstance()`.

### Documentation
- **README accuracy**: Fixed manager/ file count (11→10), removed fabricated `beanbot.sessionPromptTimeout` property, corrected color properties count (31→33).
- **QUICKSTART update**: Added wl-clipboard prerequisite for Wayland image paste support.
- **AGENTS.md learnings**: Added critical architectural decisions and known violations to agent instructions.

## v1.5.26 (Changes since v1.5.25)

### Reliability & Message Handling
- **Null-safety for `session/update` processing**: Fixed NPE when server sends a `session/update` notification with a missing or unmapped `sessionUpdate` field. `SessionUpdate.type()`, `SessionLifecycleHandler.onSessionUpdate`, and `StrategyRegistry.reClassify` now guard against null `UpdateData.type` and null `update.update()`, preventing the entire notification from being silently dropped.
- **Watchdog alive on outbound writes**: `AcpProtocolClient.sendRequest` and `sendNotification` call `touch()` after every write so the idle watchdog resets its timer on outbound traffic. Prevents the watchdog from killing the connection while the server is still processing a request.
- **Configurable `session/prompt` timeout**: `sendMessage` now uses `beanbot.sessionPromptTimeout` system property (default 300s) instead of hardcoded infinite wait.

### Panel Open/Close Lifecycle
- **Resources kept alive across open/close cycles**: `componentClosed()` is now a no-op — listeners, handlers, and message state survive panel close/reopen. First-time initialization happens only once in `componentOpened()`.
- **Toggle shortcut**: Changed from `Ctrl+W` to `Ctrl+L` to avoid conflicting with NetBeans close-tab action.
- **Panel minimize**: `minimizeToDock()` replaces the old `canClose()`-hack for safe panel toggling.

### State Machine Atomicity
- **Lock-based synchronization**: `SessionStateMachine` migrated from `synchronized` method to a dedicated lock object (`_lock`). Notification of state listeners happens outside the monitor to prevent deadlock.
- **Conditional transitions**: New `transitionToIf(expectedCurrent, newState)` method enables safe STOPPING→STREAMING transitions in `onTurnEnded` and `scheduleStopRecovery`, eliminating TOCTOU races between state check and transition.
- **EDT simplification**: Removed unnecessary `SwingUtilities.invokeLater` in `SessionManager.createNewSession` — the state machine is thread-safe and listeners marshal their own UI work.

### Concurrency
- **`ProcessManager` singleton**: Added `INSTANCE` field with double-checked locking as fallback when `Lookup` returns null.
- **`availableCommands` safe publication**: Changed from `CopyOnWriteArrayList` to volatile `List.of()` swap for atomic reader visibility.
- **Volatile fields**: `permissionHandler`, `statusListener`, `crashHandler`, `readyHandler` marked `volatile`.

## v1.5.25 (Changes since v1.5.24)

### UI Fixes
- **CollapsibleToolPane header**: Fixed height jitter when copy button visibility toggles — wrapped in fixed-size placeholder panel.
- **ScrollController**: Increased auto-scroll threshold from 16px to 400px to reduce premature scroll-to-bottom activation.

## v1.5.24 (Changes since v1.5.23)

### MCP & Editor Context
- **MCP enabled by default** (`McpManager.mcpDisabled: true → false`). The embedded MCP server provides editor tools (`get_opened_files`, `open_file_at_line`) to the AI on demand.
- **Editor context removed from MCP, restored as auto-inject**: The `get_current_file_context` MCP tool was removed. Editor context (file path, cursor, selection) is now sent with every user message as XML metadata, reverting to the pre-MCP approach.

### Static Analysis & NB API Migration
- **NB Platform API migration**: Replaced raw `Thread`/`Executor` with `RequestProcessor`, manual singletons with `@ServiceProvider` + `Lookup`, `Properties` with `NbPreferences`, `JOptionPane` with `DialogDisplayer`/`NotifyDescriptor`, and blocking I/O moved off EDT.
- **SpotBugs fixes**: Addressed `DM_DEFAULT_ENCODING`, `OBL_UNSATISFIED_OBLIGATION`, `IS2_INCONSISTENT_SYNC`, `CT_CONSTRUCTOR_THROW`.
- **PMD ruleset**: Added with exclusions for `UselessOperationOnImmutableRule`.
- **Checkstyle compliance**: `structuredContent` array wrapping fixed.

### i18n & Bundle
- **All hardcoded strings externalized** into `@NbBundle.Messages`/`Bundle.properties` across `AssistantTopComponent`, `MessageBubble`, `CollapsibleToolPane`.
- **Duplicate-key restriction** worked around by using unique key names per file within the same module.

### UI
- **Help icon**: Added `help.svg`/`help_dark.svg` (question mark in a circle) with a help button in the CWD row that opens the QUICKSTART guide URL → later changed to outline-only (no fill color).
- **Copy buttons**: Added to `CollapsibleToolPane` and `MessageBubble` for one-click content copying.
- **Icon cache**: Migrated to Caffeine for automatic icon resource caching.
- **Copy content tooltip**: Standardized across code/collapsible panes.

### Fixes
- **Permission dialog context**: `extractToolContext` now handles ACP permission format — falls back through `rawInput` → `locations` → `patterns` when `args`/`arguments` are absent. Supports lowercase `filepath` key (write/edit tools).
- **SSE turn-end handling**: Added `responding_finished`/`end_turn` to `MessageType` enum; synthetic `SessionUpdate` construction for textual SSE `session/update` signals.
- **Panel close cleanup**: Active message cancelled in `componentClosed()` to prevent stale SSE content. `turnEnded` flag reset on `componentOpened()`.
- **SessionUpdate NPE**: Fixed null pointer in session update processing.
- **Duplicate tool call dedup**: Addressed redundant tool call display.
- **EDT redundancy**: Reduced unnecessary `SwingUtilities.invokeLater` wrapping.
- **Copy timer leak**: Fixed leaked `Timer` instances.
- **ACP listener cleanup**: Proper listener removal on shutdown.
- **MCP timeout**: `MessageServlet` async timeout now correctly multiplies by 1000 (seconds → ms).
- **Request timeout**: Adjusted session/prompt request timeout.
- **AcpProtocolClient**: Fixed `close()` deadlock caused by `BufferedReader` lock contention.
- **Log arguments**: Timed-out branch in `AcpProtocolClient` now passes log arguments correctly.

### Other
- `PlaceholderTextArea` undo/redo logging and context menu additions.
- `.gitignore` updated with `.opencode/`, `.vscode/`.
- Jackson BOM for version management.
- 160 unit tests passing (22 new tests for permission context).

## v1.5.22 & v1.5.23 (Changes since v1.5.21)

### Concurrency Hardening
- **TOCTOU race fix** (`ProcessManager.rpcClient`): Replaced `volatile AcpProtocolClient` with `final AtomicReference<AcpProtocolClient>`. All reads use `.get()`. `handleDisconnection()` and `stopServer()` use `getAndSet(null)` for atomic extract-and-clear, eliminating the null-check-then-dereference window.
- **Non-atomic lazy init fixes**: `ColorTheme.getNativeTheme()`, `ColorTheme.loadCssTemplate()`, `CollapsibleCodePane.loadCodeTheme()` — added `synchronized` to prevent double-computation under contention.
- **Static CSS cache race** (`ColorTheme.toCss()`): Wrapped cache read/write in `synchronized (ColorTheme.class)`.
- **Read loop guard race** (`AcpProtocolClient.readLoop()`): Removed `if (running)` from `finally` block — `notifyDisconnection()` is now always called on loop exit. Safe because `handleDisconnection()` guards with `isClosing`.
- **NPE guard** (`ProcessManager.stopServer()`): Added null check on `reconnectExecutor` before `shutdownNow()`.

### UI
- **Non-closable sidebar tab**: Added `putClientProperty("TabDisplayer.Closable", false)` to prevent accidental tab closure via the X button.

## v1.5.21 (Changes since v1.5.20)

### Architecture
- MCP extracted to dedicated package (mcp/)
- ToolExecutor separated from manager/ package
- Component extraction from AssistantTopComponent

### Fixes
- Concurrency/threading: 3 UI handler threading issues fixed
- Bundle params: missing documentation comments for bundle parameters
- Tool pane titles: fixed tool pane header display
- Page up/down: restored key dispatch (scroll refactor regression)
- Button race condition: stop/send button race fixed with timeout logging

### New models/features
- ToolCallData (+67) — model class for structured tool call dedup
- Caffeine cache for tool call deduplication
- WeatherInput/WeatherOutput — MCP example tool models
- roundedPanels system property to control panel rendering

### Other
- Preamble default content updated
- Caches, imports, and code style cleanup
- Tests updated for AgentUtils, SessionManager, ToolDataExtractor, SessionUpdate
- POM updates, README/AGENTS.md/NOTES.md documentation

## v1.5.20 (2026-05-12)

### Architecture
- Extracted non-UI responsibilities from `AssistantTopComponent` into dedicated classes:
  - `AttachmentManager` — manages file/image attachment lifecycle
  - `EditorContextCapture` — captures editor context for inspector prompts
  - `MessageHistory` — manages message navigation and history
- New contract interfaces in `contract/` package:
  - `DataExtractionStrategy`, `PermissionHandler`, `RequestHandler`
  - `SessionListener`, `SlashCommandCallback`, `SlashCommandHandler`, `UIHandler`
- `PluginSettings` — added session timeout configuration parameter

### UI & i18n
- All display labels externalized into resource bundle (`Bundle.properties`)
- `ACPOptionsPanel` refactored for i18n support
- Collapsible pane tooltips, button labels use bundle keys
- `ConfigPanelController` cleanup

### Testing
- Unit tests for `AttachmentManager`, `MessageHistory`

## v1.5.19 (2026-05-12)

### Stability & Connection Management
- Idle watchdog in `AcpProtocolClient`: 30s timeout closes hung connections with pending requests
- Crash handler callbacks (`setCrashHandler`, `setReadyHandler`) in `ProcessManager` for server recovery
- `SessionManager` resets state machine to `IDLE` on crash, auto-reloads last session on reconnect
- `ProcessManager.stopServer()` notifies crash handler before reconnect loop
- Null-safe RPC send methods — `sendRequest`/`sendNotification` handle missing client gracefully
- `SessionStateMachine.transitionTo` made `synchronized` to prevent race conditions
- Config update failure rolls back model combo selection in UI
- `SessionManager.stopMessage()` simplified to fire-and-forget (no wait on stop future)

### MCP Server Hardening
- Thread pool with bounded queue (`MAX_THREADS=10`, `AbortPolicy`) replacing unbounded cached pool
- Heartbeat executor purges dead SSE clients every 5s
- Removed stub weather tool implementation (clean tools/list only)
- Graceful shutdown with `stop(1)` drain timeout
- `SseClient` uses `CountDownLatch` for clean disconnect detection
- SSE endpoint renamed from "weather" to "netbeans"

### Editor Tools & Actions
- Removed `fs/writeTextFile` handler (server handles writes directly)
- `fs/readTextFile` reads via `SwingUtilities.invokeAndWait` on EDT for editor sync
- Removed resource link blocks from prompt context
- New `CompactJsonAction` editor action: minifies JSON selection or full file
- New `SortLinesDescAction`: sort lines descending (reverse order)
- `SortLinesAction` enhanced: works on entire file when no selection, ascending rename

### UI
- Connection touch on key release keeps idle watchdog alive during typing
- `AssistantTopComponent`: track closed project dirs, refresh sessions on `componentOpened`, clear all handler references on `componentClosed` (memory leak fix)
- Log noise reduction in `ToolDataExtractor`

## v1.5.17 (2026-05-09)

### UI & UX
- Redesigned user message bubbles with unified global text alignment across the chat thread
- New custom user icon and refactored UI utility classes
- "Restart" button renamed to "Reconnect" with new lightning bolt icon
- Quick scroll-to-bottom button for navigating long conversations
- Redesigned preferences layout in Options panel
- Fixed garbled text rendering in scroll panes
- Dark mode filter and paperclip icon fixes
- Sidebar toggle, undo/redo, and level selector for theme configuration
- Removed duplicate menu entry

### Attachments & Commands
- Preliminary file attachment support with indicator UI
- Document upload from editor or file system
- Image paste support from clipboard
- Internal slash command handling (`/session new`, `/session switch`)
- Clipboard fixes for code block copy actions

### Architecture & Protocol
- Consolidated message handling into a unified `addMessage()` path with streaming-aware bubble creation
- Stream-aware expand/collapse toggle for long-running tool calls
- Refactored message rendering and tool metadata extraction
- Extracted contract interfaces (`PermissionHandler`), fixed critical bugs, added plan strategy support
- Message type parsed immediately on receipt for faster UI updates
- Model cache cleared on server restart; pending futures drained on disconnect
- Treat context cleanup as a tool operation

### Session & Stability
- MCP server support with SSE transport and capability negotiation
- Null checks and general stability fixes across RPC communication layer
- Error display when opencode binary not found
- Namespace update to `io.github.anandb.beanbot`

### Infrastructure
- Maven Central deployment setup with GitHub Actions workflow
- Workflow dispatch and dry-run support for release pipeline
- Dependency bumps (Jackson Core)
- NPE fix in `SessionManagerTest`

## v1.5.16 (2026-05-09)

### Fixes & Cleanup
- Resource leak fixes: try-with-resources in MCP server HTTP handler and `CollapsibleCodePane` theme loading
- `SessionManager` null-safety fix for preamble send when process not ready
- `AutocompleteManager` NPE guard for cell bounds calculation
- Logger migration in `McpServer` to custom `Logger` with index-based placeholders
- Logging format fixes across `AcpProtocolClient`, `McpServer`, `SlashCommandInterceptor`

## v1.5.15

### Editor Sync & Local History
- `handleWriteTextFile` now writes through the editor's `Document` and calls `saveDocument()` when file is open, avoiding read-only conflicts
- Falls back to direct `java.nio.file.Files.write()` when no editor available
- `writeThroughVFS` simplified to `FileObject.refresh()` — no longer acquires file lock
- `recordLocalHistory` added: filters by active project directory, skips files modified >2 minutes ago, handles deletes via parent `FileObject.refresh()`
- Status label flashes warning when local history skipped (file outside project)

### Slash Commands & Session Management
- New `SlashCommandInterceptor` with extensible callback system
- `/session new` — create new session, `/session switch <id>` — switch sessions
- Slash command autocomplete in chat input

### UI
- Chat bottom padding increased from 40px to 90px to prevent bubble collision with status bar
- CSS file renamed `chat-style.css` → `chat-style.css.template` to silence NetBeans CSS parser warnings on `$variable` syntax
- `$fontStack` and `$monoStack` hardcoded directly in CSS template
- New `FontStacks.java` for programmatic font resolution
- New `colors.json` for theme color definitions
- `ColorTheme` refactored with configurable slider-based theme selection

### Rendering Performance
- `MessageBubble` rendering optimized (early return on unchanged content, reduced repaints)

### Code Quality
- 25+ fully-qualified class names replaced with imports across `ChatThreadPanel`, `AssistantTopComponent`, and `MessageBubble`
- Preamble default text extracted to `preamble.md` resource file
- Checkstyle configuration added with suppressions

### Misc
- `.gitignore` updated with `*.bak`, build artifacts
- Maven wrapper properties updated
- Test imports cleaned up across all test files


## 1.4.1 (2026-04-27)
- **UI Modernization & Premium Aesthetics**:
    - **Markdown Table Rendering**: Completely redesigned the table rendering engine. Tables are now wrapped in a custom `RoundedPanel` container to achieve 12px rounded corners, overcoming the limitations of standard Swing HTML rendering.
    - **Styling System**: Introduced a curated cream background (`#fdf6e3` in light mode) for tables with 8px internal cell padding and themed borders (`#e8e0c8`).
    - **Responsive Layouts**: Optimized `FitEditorPane` to perform width-aware height calculations, ensuring that text wrapping in chat bubbles is accurate and fluid.
- **Core Architecture & Protocol Enhancements**:
    - **ACP Logic Refactor**: Standardized the `ACPManager` and `AcpProtocolClient` for improved session-aware messaging. Implemented background routing for asynchronous server updates.
    - **Tool Parameter Extraction**: Added specialized logic to extract and sanitize tool parameters from complex model outputs.
    - **Language Resolution**: Enhanced the `LanguageResolver` to support a wider range of programming languages for syntax highlighting in `RSyntaxTextArea`.
- **Maintenance & Branding**:
    - Completed the transition to the "Coding Assistant" brand across all documentation, UI components, and project metadata.
    - Aligned project requirements with Java 17 and NetBeans RELEASE290 specifications.

## v1.3.38 (2026-04-24)
- **Model Compatibility & Prompt Optimization**:
    - **Structured Context**: Migrated context metadata from JSON-style comments to structured XML tags to improve parsing reliability for models like Qwen and Nemotron.
    - **Recency Bias Handling**: Implemented prompt block reordering logic that places the final user instruction last in the context window, optimizing for model attention.
    - **Message Annotations**: Introduced support for 'tags' in the `Message` model, allowing for features like 'hidden' messages that are processed by the AI but not displayed in the UI.
- **System Stability**:
    - Refactored `AssistantTopComponent` for improved serialization efficiency and cleaner dependency management.
    - Version alignment across all module manifests and documentation files.

## v1.3.26 (2026-04-24)
- **Visual Refinement**:
    - Resolved persistent icon scaling issues in the chat thread, ensuring consistency across different monitor DPI settings.
    - Tweaked dark mode theme mappings to improve the visibility of borders and separators in the `ChatThreadPanel`.

## v1.3.25 (2026-04-24)
- **Execution Control & Reliability**:
    - **Stop Mechanism**: Stabilized the implementation of the `session/cancel` notification, ensuring reliable halting of active AI streaming sessions.
    - **SVG Migration**: Completed the migration of core UI assets (new chat, rename, export, settings) to scalable SVG format, including specialized `-dark.svg` variants for high-contrast dark mode.
    - **Server Management**: Added an explicit "Restart Server" capability in the Options dialog to allow users to recover from backend process hangs without restarting the IDE.
- **Infrastructure**: Established a comprehensive unit testing suite using JUnit 5, focusing on protocol serialization and UI component state management.

## v1.2.20 (2026-04-13)
- **Deployment Fixes**:
    - Corrected the `OpenIDE-Module-Layer` path in `pom.xml`, resolving issues where the Assistant window was not properly registered in the "Window" menu on clean installations.
- **UI Enhancements**:
    - Integrated global "Expand All" and "Collapse All" controls for message blocks, allowing for quick navigation through long technical discussions.

## v1.2.19 (2026-04-13)
- **Theme & Persistence Engine**:
    - **Native Theme Detection**: Implemented a sophisticated theme engine that queries NetBeans `UIManager` to automatically adapt colors and icons to the active Look and Feel (e.g., Solarized Light, Darcula).
    - **Persistent Sessions**: Introduced local storage for chat sessions in the NetBeans user root. Sessions now persist across IDE restarts.
    - **Dynamic Renaming**: Added the ability for users to rename sessions, with metadata automatically synchronized to the local database.
- **Preference Management**: Unified all plugin settings into a centralized Options panel under **Tools > Options > Advanced > Assistant**.

## 1.2.9 (2026-04-13)
- **Security & Content Management**:
    - **Interactive Permissions**: Implemented a security-aware workflow for tool calls. Sensitive operations like file modifications now require explicit user approval via a dedicated UI dialog.
    - **Markdown Export**: Added the "Export to Markdown" feature, enabling users to save entire conversations as formatted documentation for project logs or sharing.
- **UI Quality**:
    - Standardized typography across the plugin using curated font stacks (Inter, Roboto, Segoe UI) with proper fallbacks for macOS and Linux.
    - Improved vertical centering and alignment in collapsible pane headers.

## 1.2.3 (2026-04-12)
- **Rendering & Layout**:
    - Implemented a "first-available" font fallback system for code blocks, prioritizing high-quality monospaced fonts like 'JetBrains Mono' or 'Source Code Pro'.
    - Refined the chat UI with improved vertical spacing and header padding to reduce visual density.

## 1.2.2 (2026-04-12)
- **Core Feature Implementation**:
    - **Markdown Tables**: Introduced the first iteration of markdown table rendering within message bubbles.
    - **Session Loading**: Refined the `loadSession` logic to prioritize the active project directory when searching for previous chat context.
    - **Dependencies**: Integrated `commons-lang3` to assist with robust string parsing and HTML escaping.

## 1.1.49 (2026-04-11)
- **Foundation & Public Release**:
    - **NetBeans Integration**: Established the `AssistantTopComponent` as the primary entry point for AI-assisted coding within the IDE.
    - **Protocol Implementation**: Developed the initial version of the JSON-RPC client for communication with the AI backend service.
    - **Content Rendering**: Integrated Flexmark for markdown-to-HTML conversion and `RSyntaxTextArea` for professional-grade code syntax highlighting.
- **Hardening & Infrastructure**:
    - Addressed critical concurrency and memory management issues in the early RPC communication layer.
    - Standardized on index-based placeholder logging to improve performance and diagnosability.
    - Initial relocation of all settings to the native NetBeans Miscellaneous options category.
