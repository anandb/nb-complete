# Release Notes

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
