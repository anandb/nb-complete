# Architecture And Layers

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: changing a package dependency, adding a class, or touching the accepted-debt list.

## Architecture & UI
- ACP v1 uses `AcpProtocolClient` (JSON-RPC + SSE). `session/cancel` MUST be a notification
  without an id; Stop must restore all toolbar controls and the input.
- UI entry points: `AssistantTopComponent` (panel), `ChatThreadPanel` (thread), `MessageBubble`
  (turns), `CollapsibleCodePane` (code). Put new classes in the appropriate `ui/<feature>/`
  package; keep the root for `UIBundle` and the `@OnStart` installer.

## Hexagonal Architecture Rules

### Layer Model (dependencies flow downward only)
`ui/`, `manager/`, `mcp/` and `db/` are adapters over `contract/` and `model/`. `project/` and
`tasks/` are satellites; `support/` provides shared utilities. Dependencies flow downward to
`contract/`/`model/`; `model/` is a leaf. `manager/` and `mcp/` are peers. `stereotype/` is a
dependency-free leaf of marker annotations (`@HexagonalPort`, `@Boundary`, `@McpToolInput`,
`@McpToolProvider`) carried by `contract/`, `manager/` and `mcp/` classes; it imports nothing.

### Forbidden Patterns (do NOT introduce these)
- No upward dependencies: `manager/` must not import `ui/`; `ui/` uses `contract/`, not
  `manager/`/`mcp/`; `model/`, `contract/`, `support/` must not import adapter packages.
- `project/` uses TopComponents by string ID. Only `ui/` consumes `ui/platform/`, except the two
  accepted `tasks/` violations listed under debt.

### Adding New Code
- `manager/`, `mcp/`, and `ui/` depend on `contract/` ports; UI must not call concrete managers.
  New `contract/` types import only `model/` and the `stereotype/` marker annotations; keep
  `model/` dependency-free.
- Register MCP providers from `McpServer.start()`; filesystem/git/hg tools must use
  `ProjectPathGuard`. Keep `support/` utilities dependency-light.

### Fixing Existing Violations
Move shared logic to a lower neutral layer or define a `contract/` port; do not widen upward
dependencies.

### What Belongs Where
`ui/<feature>/` owns Swing; `ui/platform/` holds UI seams. `manager/` owns session/process
orchestration; `mcp/` IDE tools and path guards; `db/` persistence; `tasks/` task repository;
`project/` project lifecycle. `contract/` defines ports, `model/` records, `support/` utilities.

### Architectural Debt (Known Violations)

Active debt — four entries, verified against the current tree by
`src/test/.../architecture/LayerDependencyTest`, which fails on any edge not listed here:

- **support/ → model/ (ACCEPTED)**: `support/` types itself against `model/` records in
  seven files. Five are pure static utilities (string/pattern parsing, binary discovery,
  todo.txt codec, usage rows, token counting) rather than data classes, so they do not
  belong in `model/`: `ToolDataExtractor` (`MessageClassification`, `MessageType`,
  `SessionUpdate`), `BinaryResolver` (`HarnessCatalog`), `TaskTxtCodec` (`TaskRecord`),
  `PromptUsageRows` (`UsageRecords.Attribution`, `UsageRecords.PromptUsageRow`) and
  `TurnTokenCounter` (`MessageType`, `ProcessedMessage`). The other two resolve a harness
  rather than carrying record data: `HarnessSelection` maps the configured executable to a
  catalog `Harness`, and `PluginSettings` validates a stored id against
  `HarnessCatalog.UNKNOWN` and resolves it to a `Harness`.
  **Exception — `HarnessCatalog`/`Harness`**: any `support/` file may import those two
  (currently `BinaryResolver`, `HarnessSelection`, `PluginSettings`). A catalog lookup is
  not record data, so it does not leak model shape into `support/`. Do NOT add further
  `support/` → `model/` imports of any other type.
- **manager/ → mcp/ (ACCEPTED)**: `manager/ProcessManager` imports
  `mcp/McpManager` and `mcp/McpToolAdapter` to construct the `ToolExecutor` port
  implementation (`new McpToolAdapter(new McpManager())`). `manager/` and `mcp/` are
  peers, so this is a horizontal, not upward, dependency. It is confined to
  `ProcessManager` — no other `manager/` file imports `mcp/`. Do NOT widen it; new
  `manager/` classes must reach tool execution through `contract/ToolExecutor`.
- **tasks/ → ui/ (ACCEPTED — deferred)**: `tasks/TaskRepositoryController` and
  `tasks/TaskIssueController` read open projects via
  `Lookup.getDefault().lookup(PlatformBridge.class).projectContext()`, so they import
  `ui/platform/PlatformBridge` and `ui/platform/ProjectContext` — a dependency from a
  feature package into the presentation layer. The seams live in `ui/platform/`; moving
  them down to `contract/` would touch 14 `ui/` files plus the `DefaultPlatformBridge`
  adapter, so it is deferred. No separate MIGRATION.md exists.
- **ui/ → project/ (ACCEPTED)**: `ui/mdproject/MdProjectWizardIterator` and
  `ui/mdproject/UseAsMarkdownProjectAction` call `project/mdproject/MdProjectMarker` to
  write and inspect the markdown-project marker file, which owns its `project.xml` I/O.
  The third edge is `ui/platform/DefaultPlatformBridge` importing
  `project/ACPProjectManager` to feed `ProjectContext` — the adapter's legitimate seam.
  Moving the marker down would mean relocating project-metadata I/O into `model/`, so it
  stays. Do not widen it into a general `ui/` → `project/` dependency.

Resolved — kept as regression guards:

- **ui/ → manager/ singletons (RESOLVED)**: All `ui/` → `manager/SessionManager` and
  `ProcessManager` imports have been eliminated, including every
  `SessionManager.getInstance()` / `ProcessManager.getInstance()` call. UI now accesses
  services via `Lookup.getDefault().lookup(SessionControl.class)` and
  `Lookup.getDefault().lookup(ProcessControl.class)`. Do NOT reintroduce either pattern.
- **project/ACPStartup → manager/ (RESOLVED)**: `ACPStartup` was importing
  `manager/UpdateCheckerService` directly. Fixed via `contract/UpdateCheckerControl`
  interface with `Lookup.getDefault().lookup()`.
- **project/ACPShutdown → ui/ (RESOLVED)**: `ACPShutdown` was importing
  `ui/ImagePasteTransferHandler` to shut down the image paste I/O thread pool.
  Fixed by extracting the `RequestProcessor` to `support/ImagePasteIoProcessor`.
- **ui/GoToFileDialog → manager/FileCacheManager (RESOLVED)**: `GoToFileDialog` was importing
  `manager/FileCacheManager` directly instead of using `contract/FileCacheQuery` via Lookup.
  Fixed by accessing `Lookup.getDefault().lookup(FileCacheQuery.class)`.
- **model/ → support/ (NOT PRESENT)**: an earlier `model/MessageTransformer →
  support/Logger` exemption is obsolete. `model/MessageTransformer` has no imports at
  all and `model/` does not reference `support/Logger` anywhere. `model/` is a clean
  leaf package — treat any `model/` → other-package import as a new violation.
  **Exception — test tree**: `src/test/.../model/ToolCallDataTest` may import
  `support/MapperSupplier` to obtain its `ObjectMapper`. The leaf rule governs
  `src/main` only; tests in the `model` package may reuse `support/` helpers. Do NOT
  add `support/` (or any project) imports to main-tree `model/` classes.
- **New extraction pattern**: When extracting utilities from `ui/` god components, place
  pure logic in `support/` (e.g. `ToolContextExtractor`, `ShortcutUtils`). Keep Swing-coupled code in `ui/`.
