# Agent Instructions for coding-assistant

## Project Overview
- **Project**: Coding Assistant (NetBeans IDE plugin, Java 17, Maven)
- **Current Stable Version**: 1.30.0
- **Test Builds**: a `0.x` version in `pom.xml` is a test build, never a release. It is
  numbered below the published `1.x` line on purpose, so an artifact built from it cannot be
  mistaken for, or auto-updated over, a release; NetBeans treats it as a downgrade. Releases
  always come from a `1.x` version and a `v*` tag.
- **Key Tech**: NetBeans API (RELEASE220), Flexmark, Jackson, RSyntaxTextArea, JUnit 5.

## Build Commands
- Use `./mvnw` (pinned Maven 3.9.x), never system `mvn`. Build: `./mvnw package`; clean:
  `./mvnw clean`; quick package: `./mvnw package -DskipTests`.
- Coverage target is 96%; use JaCoCo when reporting it.
- If a workflow helper fails and the user explicitly requests direct Bash, continue with these
  project commands.
- **Local test suites MUST use `-P random-test-order`**; CI intentionally uses default order.
  The profile prints the random seed for reproducing failures.
- Surefire runs `*Test`; Failsafe runs `*IT` during `verify`. Use `./mvnw verify` for integration
  tests; `test` alone does not run them. Tests use the pinned `netbeans.user` directory.
- **User guide**: `docs/stitch.py` needs `python3` and Harlowe 3 `tweego`; output is gzipped at
  `target/generated-guide/UserGuide.html.gz`. Stitch failures fail the build;
  `-DskipUserGuide=true` skips stitching and copying. Release verification requires the output,
  so keep CI's pinned tweego install step.

## BMAD Working Artifacts (`_bmad-output/`)
- `_bmad-output/` is gitignored local state: never commit or cite it as a repo artifact. Maintain
  its `index.md` when artifacts, pom version, README badge or passage count change.
- Specs require `title/type/created/status` frontmatter; `done`/`superseded` specs belong in
  `archive/`, and status must match the tree. Research epic status must match its spec.
- Generated project docs are dated snapshots: regenerate with `bmad-document-project`, never
  hand-edit. Run `python3 _bmad/check_artifacts.py` after spec/quick-dev/research work and before
  release verification when `_bmad/` tooling is available.

## Architecture & UI
- ACP v1 uses `AcpProtocolClient` (JSON-RPC + SSE). `session/cancel` MUST be a notification
  without an id; Stop must restore all toolbar controls and the input.
- UI entry points: `AssistantTopComponent` (panel), `ChatThreadPanel` (thread), `MessageBubble`
  (turns), `CollapsibleCodePane` (code). Put new classes in the appropriate `ui/<feature>/`
  package; keep the root for `UIBundle` and the `@OnStart` installer.

## Important Coding Rules
- Tests must run headless; mock UI instead of constructing windows/dialogs.
- Parse JSON-RPC streams with Jackson `JsonParser`, never `BufferedReader.readLine()`.
- Echo incoming JSON-RPC id nodes verbatim; ACP ids may be strings.
- Keep lines ≤120 characters.
- Keep comments to 1–2 lines; use Javadoc for longer explanations.
- **Structural edits, not scripts**: for structural edits — replace usages, delete a helper
  block, remove constants, rename across files — use parser-aware tooling: the IDE's own
  refactoring, or a tree-sitter rewrite such as `ast-grep --pattern … --rewrite …`. Never use
  `python`/`sed`/`awk`/shell one-offs for these: scripted text edits bypass the parser, miss call
  sites and are hard to review. Reserve the file tools for single-file surgical edits.
- **Logging**: Use index-based placeholders (`{0}`). NO `warning()` method (use
  `LOG.log(Level.WARNING, ...)` or `severe/warn/info/fine`). Pass exceptions as the LAST argument.
- **Theming**: Use `ThemeManager.getCurrentTheme()`. Dark mode icons require `_dark.svg` suffix.
  The chat thread surface is `theme.sunkenBackground()`: the Appearance chat-background pickers
  override that entry (system properties `beanbot.color.sunkenBackground` / `…Dark`). Do NOT add a
  separate `chatBackground` key or point the thread at another colour.
  Tool/thought headers: collapsed is `sunkenBackground()`, expanded is `base2()`; every segmented
  block header (only visible while expanded) is also `base2()`, and the pane body is
  `sunkenBackground()`. Do not swap the two, and keep the segmented and regular headers on the
  same colour.
- **Asynchrony / EDT**: Wrap all background thread UI updates in `SwingUtilities.invokeLater()`.
- **No I/O on EDT**: NEVER perform I/O operations (file reads/writes, network calls, pipe writes) on the Event Dispatch Thread (EDT). This includes `Files.readString()`, `writer.flush()`, `sendRequest()`, `sendNotification()`, and similar blocking operations. Doing so can cause deadlocks when the pipe buffer is full or the server is slow to respond, freezing the entire UI. Always wrap I/O in `CompletableFuture.runAsync()` or use background threads. Example: `AcpProtocolClient.sendRequest()` and `sendNotification()` now use `CompletableFuture.runAsync()` to avoid EDT blocking.
- **Regex in hot paths**: NEVER use `String.matches()` or `String.replaceAll()` in loops or
  per-tick callbacks. Pre-compile with `static final Pattern` and reuse via `matcher()`.
- **Swing Layout Loops**: NEVER call `revalidate()` synchronously inside sizing methods like `getPreferredSize()` or `setBounds()`. Doing so re-adds the component to Swing's invalidation queue during a layout pass, causing an infinite 100% CPU layout loop. Use a boolean guard (e.g. `suppressRevalidate`) to ignore or defer revalidation during internal size calculations.
- Keep discovery and full-cache scans off the EDT. Async combo fills must preserve existing text
  (empty `DefaultComboBoxModel` auto-selects its first item). Match open windows through the
  registry, never `WindowManager.findTopComponent()` on the EDT. Keep per-keystroke file matching
  allocation-light and size dialogs without measuring every cached entry.
- Edit tracked bundles only under `src/main/resources/`; never edit generated copies under
  `src/main/java/`. UI lookups use `UIBundle.class`; annotation keys stay beside annotated classes.
- When moving classes, move class-relative resources and update `layer.xml` class strings plus
  `Class.forName`/`getResource` FQNs. `LayerReferencesTest` covers layer names and bundle keys.
- Cache hot preferences in `static volatile` fields with a listener; use `PluginSettings` accessors
  instead of direct reads. Setters persist before updating cache; asynchronous listeners re-read the
  stored value rather than trusting stale event values.
- **Scan ranges**: When scanning large strings for patterns (e.g. ASCII art detection), limit
  the scan to a bounded prefix (e.g. first 512 chars) instead of the full string.
- `ACPProjectManager.getAllOpenProjects()` returns its event-maintained cache; do not bypass it.
- WSL launches use `wsl.exe -e bash -lc 'exec "$0" "$@"'`; pass executable and each argument as
  separate argv tokens. Parse preferences with `tokenizeArgs()`; never interpolate user args into
  the shell script.
- Convert all harness `cwd` values and session-directory comparisons through
  `ProjectPaths.nativePath(...)`; `FileObject.getPath()` is virtual and breaks Windows UNC paths.
  Preserve `SessionManager`'s native-form guards.
- Harness detection scans all candidates, ranking harness-owned directories, same-user files,
  then other files; distinctive binary names break ties. Accept system-directory installs only
  when user-owned or symlinked into `node_modules`; do not drop all system candidates or return the
  first PATH hit. WSL probes exclude system directories because host ownership cannot be checked.
- **Context-menu hooks (EDT safety)**: `InspectorContextMenu`/`TestResultsContextMenu` must
  NEVER call `WindowManager.findTopComponent(...)` on the EDT — it synchronously instantiates
  the target TopComponent and blocks on `MediaTracker` image loading. Match already-open
  windows via `TopComponent.getRegistry().getOpened()` + `@TopComponent.Description.preferredID()`.
- **New session debounce**: The toolbar New Session button has a 300ms debounce timer to prevent
  accidental double-clicks. There is no keyboard shortcut for a new session; `Ctrl+L` toggles the
  assistant panel (see the Ctrl+L Toggle section below).
- **contract/ → manager/ singletons**: `contract/` classes must use `Lookup.getDefault().lookup()`
  to access services, never direct `Manager.getInstance()` calls. The `SlashCommandInterceptor`
  violation was fixed by injecting `ProcessControl` via Lookup.
- **Request idle timeouts, not absolute**: Use per-request idle timeouts tied to
  `AcpProtocolClient`'s connection-level `lastDataTime`. Only fail requests when the connection
  is idle (no inbound data) beyond their timeout. Do NOT use `future.orTimeout()` — that is an
  absolute deadline that kills requests even when data is flowing. Set timeouts by passing
  `(method, params, timeout, unit)` to `ProcessManager.sendRequest()` or
  `SessionRpcClient` methods. (Exception: `initialize` request during startup may use `orTimeout` since it must complete quickly before streaming begins).
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
`ui/`, `manager/`, `mcp/` and `db/` are adapters over `contract/` and `model/`. `project/` and
`tasks/` are satellites; `support/` provides shared utilities. Dependencies flow downward to
`contract/`/`model/`; `model/` is a leaf. `manager/` and `mcp/` are peers.

### Forbidden Patterns (do NOT introduce these)
- No upward dependencies: `manager/` must not import `ui/`; `ui/` uses `contract/`, not
  `manager/`/`mcp/`; `model/`, `contract/`, `support/` must not import adapter packages.
- `project/` uses TopComponents by string ID. Only `ui/` consumes `ui/platform/`, except the two
  accepted `tasks/` violations listed under debt.

### Adding New Code
- `manager/`, `mcp/`, and `ui/` depend on `contract/` ports; UI must not call concrete managers.
  New `contract/` types import only `model/`; keep `model/` dependency-free.
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

Active debt — three entries, all verified against the current tree:

- **support/ → model/ (ACCEPTED)**: `support/` types itself against `model/` records in
  three files: `ToolDataExtractor` (`MessageClassification`, `MessageType`,
  `SessionUpdate`), `BinaryResolver` (`HarnessCatalog`), and `TaskTxtCodec`
  (`TaskRecord`). All three are pure static utilities (string/pattern parsing,
  binary discovery, todo.txt codec) — they are not data classes, so they do not belong
  in `model/`.
  **Exception — `HarnessCatalog`**: any `support/` file may import
  `model/HarnessCatalog` (currently `BinaryResolver` and `PluginSettings`, which
  validates the stored harness id against `HarnessCatalog.UNKNOWN`). It is a catalog
  lookup, not record data, so it does not leak model shape into `support/`. Do NOT add
  further `support/` → `model/` imports of any other type.
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

## Critical Technical Details

### Notifications & Streaming
- OpenHands' `sendsSystemPromptAsThought` filters `System Prompt:` thoughts; suppress its stdout
  banner with `OPENHANDS_SUPPRESS_BANNER=1`. Route `session/update` through the full pipeline to UI.
- End a turn on `responding_finished`, `end_turn`, RPC `stopReason`, or load config options; flush
  pending text. Never treat `available_commands_update` as turn-end. Cursor permits interleaved
  prompts: ignore stale completions by send generation. Call `onUserPromptSent` only when a prompt
  is actually sent. Finalize via `stopStreaming()` → `finalizeStreaming()`.
- `stopReason=max_tokens` drives the truncation notice; do not also inspect goose per-chunk flags.
  Classify prompt failures by `RpcFailure` code/data (goose auth `-32000/turn_failed`, credits
  `-32603/credits_exhausted`), never by message text. Check `MessageType` before adding updates.

### Harness Version Capabilities (OpenCode v1 vs v2)
Capabilities that vary by version belong in `HarnessCatalog.forAgentVersion()` and are applied
after resolution. OpenCode v2 requires queueing; v1 keeps interleaved prompts. `OPENCODE_V2` shares
the base id/name/binaries and stays out of `HarnessCatalog.ALL`. Unknown versions keep the catalog
default; never guess.

### goose Mid-Turn Steering (`_goose/unstable/session/steer`)

- Goose rejects a second `session/prompt`; keep `requiresMessageQueue=true` as fallback and
  `supportsSessionSteer()` enabled. Read `_meta.goose.activeRunId` from raw notifications into
  `ActiveRunTracker`, clear it on cancel/server stop, and send it as `expectedRunId`. If goose
  returns `error.data.actualRunId`, retry once with that id; propagate other failures.
- `MessageSender.trySteer()` takes a steer route only when the turn is in flight and no
  permission request is pending; the route depends on the harness: goose needs a known run id,
  Hermes takes `/steer`, and the `_session/steering` extension needs the handshake capability
  (no run id). Otherwise the queue branch handles it. A failed steer restores the text *and*
  the attachments and drops its echo bubble (`ChatThreadPanel.removeMessageBubble`),
  so a resend does not draw a second identical bubble — user bubbles never merge.
  While the original turn is still running, the failure handler also re-asserts Stop, the
  input and the run watchdog, because a harness can answer a failed steer with its own
  end-of-turn and otherwise leave the panel on Go with no way to stop the run that is still
  going (`keepTurnAliveAfterFailedSteer`).
  The same restore runs on the prompt path: `PromptPayload` carries the attachments it
  cleared, and `restoreAttachments()` puts them back through `AttachmentManager.restore()`,
  which honours the caps and reports whatever no longer fits (`STATUS_AttachmentsDropped`).
  A failed queued flush (`sendQueuedMessage`) drops its combined echo the same way. All
  three paths restore through `restoreComposerText()`, which keeps text typed while the
  send was in flight — the failed message is already in the Alt+Up history.
  A failed mid-turn send keeps the turn alive only while the turn is still running
  (`isMidTurn()` is re-read, the watchdog re-armed); if the turn ended in between it
  tears the turn down like a turn-starting failure. Local slash commands are intercepted
  BEFORE the mid-turn routing —
  they are UI actions and must never be steered or queued. Only the interception is early:
  the permission guard sits below it so local commands still work while a permission prompt
  is open, but every decision re-reads `isPermissionPending()` live (the request can arrive
  while a command popup is up), and a *forwarded* command (`/compact`, `/dcp`) draws its
  echo only once the send is accepted — never before the guard can drop it. A null session
  short-circuits `sendMessage()` above all of it: with no session the history, the interceptor,
  the attachments and the queue are skipped, and the status shows `STATUS_NoSession`.
- A **failed mid-turn send must not end the turn UI**, for any harness. When the send
  happens while a turn is in flight (OpenCode v1's second `session/prompt`, or goose's
  steer call), the harness's first turn keeps running, so the `exceptionally` branch in
  `MessageSender.sendMessage()` keeps Stop enabled and the stall watchdog armed, leaves
  the streaming bubble alone, skips `onMessageDoneCallback`/`onTurnEndedCallback`, and
  hands the turn-end claim back to the original prompt
  (`compareAndSet(gen, prevGen)` on `sendGeneration`). Only a failed turn-starting send
  tears the turn down. Symptom of the old behaviour: `RPC exceptionally fired:
  AcpRpcException: Invalid params` on a mid-turn send flipped Stop to Go while the
  server was still working, leaving no way to cancel.
- Echo: goose repeats the steer as a `user_message_chunk` carrying the `messageId` the
  steer returned (docs: "with `_meta.goose.steer`"). User bubbles never merge
  (`canMerge` excludes `user`), so that echo would render the text twice.
  `support/SteerEchoes` registers the id from the steer response (only when the local
  echo was drawn) and `StrategyRegistry` consumes it after `captureMessage`, before
  `displayMessage`. Do NOT use `seenMessageIdsBySession` for this: it is the
  stale-pin-cleanup set, it is removed by `retainActivePins()` after every flush, and it
  never receives live user inserts. Do NOT skip the echo when `localEchoEnabled` is
  false — then the harness chunk is the only renderer.
- An accepted steer is not instantly part of the turn: goose announces it on
  `session_info_update` as `_meta.goose.queuedSteer {messageId, runId}`.
  `StrategyRegistry.notifyQueuedSteer` forwards that to `UIHandler.onSteerQueued(...)` →
  `ChatThreadPanel.setMessageQueued(id, true)` (amber accent + hourglass via
  `BubbleActionBar.setQueued`), and the matching `user_message_chunk` echo clears it before
  `SteerEchoes.consume` returns. Do NOT treat a steer as landed on acceptance: the run may
  finish before it is consumed.
- ACP v2 `session/inject` is the eventual standard replacement; keep the goose call
  behind `supportsSessionSteer()` so it can be swapped without touching the UI.

### Hermes Slash Steering (`/steer <guidance>`)

- Hermes has no steering extension: `Harness.supportsSlashSteer()` (id `hermes` only) routes a
  mid-turn send through `MessageSender.trySlashSteer`, which posts `/steer <guidance>` as a plain
  `session/prompt`. That send MUST NOT bump `sendGeneration` or the permission epoch and MUST NOT
  wire completion handlers onto its future.
- Wire-verified (ACP logs): Hermes answers the steer prompt within ~20 ms with
  `{"id":N,"result":{"stopReason":"end_turn"}}` — an RPC **result**, not a `session/update`
  notification — and it emits no turn-end `session/update` while a run is active. The
  unhandled result is exactly why the original turn's UI survives; do NOT "harden" the route
  by wiring turn-end suppression onto `SessionLifecycleHandler`'s `end_turn` branch for it.
- Attachments and non-`/steer` slash commands fall back to the normal queue.

### ACP Steering Extension (`_session/steering`)

- Agents advertise it per connection at the **top level** of the `initialize` response —
  `_meta.steering.supported`, a sibling of `agentCapabilities` — so `ServerProcessLifecycle`
  captures it beside `sessionCapabilities.close` and `ProcessManager.supportsSessionSteering()`
  reports it. Do NOT add a `Harness` id flag: the same adapter ships with and without the
  extension. It is implemented by `claude-agent-acp` (our Claude harness), by `omp`'s `acp`
  mode (our Oh My Pi harness) and by the `@automatalabs/pi-acp`, `@openma/pi-acp`,
  `@gugu-acp/pi-acp` and `jetbrains-pi-acp` adapters; `@geohar/pi-acp` (the one onboarding
  installs), the original `pi-acp`, `@regadas/pi-acp` and `codex-acp` do not have it.
- omp answers `outcome: "failed"` when the injection itself breaks; that is surfaced as
  `STATUS_Error`, while only `promptRequired`/`startedNewTurn` get the benign
  `STATUS_SteerTurnEnded` "turn already ended" restore. Do NOT collapse every non-`injected`
  outcome into one branch.
- `MessageSender.trySteer` order is goose extension → Hermes `/steer` → this extension → queue →
  second prompt. It must stay **before** the queue: an agent that advertises steering must not
  have its mid-turn input parked until turn end just because its catalog entry says
  `requiresMessageQueue` (pi still does, as the fallback for adapters without the extension).
  It must also stay before the second-prompt path: omp's `session/prompt` **implicitly cancels**
  the running turn when one arrives mid-turn, and its catalog entry has no `requiresMessageQueue`.
- `support/SteeringRequest` owns the wire shape (`{sessionId, prompt, _meta}`, no `expectedRunId`)
  and the reply. The request MUST carry `_meta.steering.idleBehavior = promptRequired`:
  claude-agent-acp otherwise starts a detached turn when the steer loses the race to turn end,
  which the plugin would not own while it restores the text. Only `outcome: "injected"` means the
  message joined the turn; `promptRequired` and `startedNewTurn` mean the turn was already gone,
  so the text, attachments and echo bubble are restored/removed (`STATUS_SteerTurnEnded`).
- The reply carries no message id, but claude-agent-acp streams the injected message back as a
  `user_message_chunk`, so the local echo is registered by text (`SteerEchoes.expectText`) and
  `StrategyRegistry` drops the matching echo. Do NOT key this on `seenMessageIdsBySession`, and
  do NOT skip the local echo — adapters without the echo (the pi forks) would then render nothing.
- The extension answers with an outcome, not a stop reason: this route must never bump the send
  generation or wire turn-end handlers, or the original prompt's completion would be lost.
- `/steering` (the pi-adapter slash command) is unrelated: it only sets pi's queue-drain mode.

### Hermes Lost-Turn Attach (`unknown_execution`)

- Hermes 0.23's gateway marks a turn lost when the gateway restarted as a pending admission
  with `status=unknown` in `state.db` (`session_admissions`), and refuses every session
  **mutation** for that session until it is discarded. `session/load` with `mcpServers`
  resumes through an editor-MCP policy mutation, so it answers
  `-32603 Internal error` with `error.data.details = "unknown_execution"` while a plain
  attach (`mcpServers: []`) loads the transcript normally. **MCP on load is supported** —
  the refusal is the lost-turn gate, not a missing capability.
- `SessionManager.loadSessionFromServer` treats `unknown_execution` as a definitive answer:
  `isStartupRetryable` must NOT retry it (waiting never settles it), and the load retries
  **once read-only** (`SessionRpcClient.loadSessionFromServer(..., editorMcp=false)`, empty
  list) so the history loads. The read-only retry skips `closeBeforeLoad` — the first attempt
  already handled cancel/close. Do NOT drop the retry: without it the session cannot open at
  all and every later `session/prompt` answers `not_found`.
- Recovery is the harness's own notice, streamed into the attach: it names
  ``/discard <admission_id>`` (or `hermes sessions discard <session_id>` in a terminal).
  Send the command through `session/prompt` after the read-only attach — the adapter allows
  a text-only `/discard` past the pending turn. The gateway keeps the session's previously
  bound MCP servers, so the read-only attach does not strip the `nb` tools.

### goose Custom Notifications (`_goose/unstable/session/update`)

- Enable this channel through `clientCapabilities._meta.goose.customNotifications=true`.
  Decode `message_usage` and `status_message`; ignore its duplicate `usage_update` because goose
  also sends the standard update. `UpdateData.status` is a `JsonNode` and may be text or object.
- Capture `message_usage` per execution, dedupe by message id, skip LOADING replay, and retain
  compaction rows without ids. Treat `status_message` as live status, never transcript; do not
  reset Ready during a turn.
- Attribute usage from `_meta.goose.inference.resolvedModel`, then requested/session model; never
  use the dropdown value when a provider alias or sub-agent may differ.

### Harness Identity For Custom Binaries And Containers
- `acpHarnessId` is written only by onboarding (`AssistantTopComponent.onHarnessSelected`)
  and `ACPOptionsPanel.store()`. The Options **Harness** combo
  (`support/HarnessSelection`) lets the user pin a catalog harness when the binary name
  cannot identify it — the normal case for a container launcher (`docker`, `podman`,
  `nerdctl`, `finch`) or a wrapper script.
- Precedence: an explicit choice wins; `Auto-detect` derives the id from the binary
  basename via `HarnessCatalog.byBinaryName`. A stored id the path cannot explain is an
  explicit choice and must survive `load()` and later path edits; a conflict is surfaced
  with a one-click switch, never auto-corrected.
- `store()` must persist `HarnessSelection.harnessId/displayName`, not re-derive the id
  from the basename when a harness is selected, and the combo must not copy a harness's
  `launchArgs()` into Process Arguments — with a container launcher those arguments belong
  to `docker`/`podman` and rewriting them corrupts the command.
- Runtime resolution is unchanged: `ServerProcessLifecycle.initializeProtocol()` reads
  `acpHarnessId` → `byId`, falls back to `byBinaryName`, then `UNKNOWN`.

### session/close Before session/load (Do Not Break)

Capture `sessionCapabilities.close` per connection and reset it on process start. Before loading,
cancel/close only when the target is already loaded by this server generation; gate on the
capability and swallow close failures so load continues. A plain session switch must not cancel
the target load. Do not make cancel/close unconditional.

### File Access Policy Is Shared By ACP And MCP Tools
- `FileAccessGuard.check(...)` is the shared ACP/MCP enforcement point. `ALLOW` reads/writes within
  the project; `READ_ANY_WRITE_PROJECT` reads anywhere but writes only within; `READ_ONLY` allows
  project reads and rejects writes. Missing ACP session directory denies access.
- Every ACP and MCP filesystem path must use `FileAccessGuard`; MCP filesystem/git/hg tools also use
  `ProjectPathGuard`. Containment uses canonical paths with separator boundaries; never use bare
  `startsWith`. `search_project` stays limited to open projects. `run_command` is separately guarded
  by working directory and `RunCommandPolicy`, not the file policy.
- The guard denies credentials on reads and writes and `.git`, `.hg`, `.mvn` writes. Extend
  `SecretPaths`/`ProtectedPaths` rather than bypassing checks. The harness permission prompt remains
  active; this is an additional IDE-side policy, not a sandbox.
- Agent-driven file access must not steal focus. Open/write in the background; use `FOCUS` only for
  an explicit user navigation request. `EditorCookie.openDocument()` is suitable for background
  writes; do not activate editors through `open()`, `requestActive()` or equivalent.

### ACP Server Working Directory (Experimental)

- `projectSwitchRestart.enabled` is read at startup; changing it requires IDE restart. When on,
  `session/new` and `session/load` rebind by restarting the server, regardless of harness support.
  Do not make this preference live.
- When off (default), new sessions do not bind/restart. On `session/load`, only harnesses supporting
  `changeWorkingDirectory` (currently goose) move cwd live before loading; log a failed move and
  continue. Preserve this compatibility behavior.
- Rebind terminates served sessions. Bind handshakes to their client/generation, compare canonical
  paths, and do not auto-reload during `explicitSessionOps`. Manual and preference-driven restarts
  are independent of this option.

### fs/read_text_file Line Windows
- `AcpRequestRouter.selectLines(content, startLine, limit)` slices content from both the editor document and
  disk. `line` is 1-based (absent → 1, clamped to ≥1); `limit` absent means to EOF, and a non-positive limit
  returns an empty string. The slice keeps the trailing newline of the last selected line; a start line past
  EOF returns empty. The whole file is read before slicing — do not "optimize" that without preserving CRLF
  and trailing-newline semantics.

### .bbignore Display Policy
- `manager/BbIgnoreStrategy` implements `VcsIgnoreStrategy` over a project-root `.bbignore` (gitignore syntax).
  When the file exists it replaces `.gitignore`/`.hgignore` entirely; `FileCacheManager.detectStrategy` checks
  it first.
- An empty `.bbignore` listing is authoritative: `scanProject` must NOT fall back to `walkFileSystem` for
  `BbIgnoreStrategy`, otherwise an exclude-everything policy resurrects every file.
- Invalid patterns (bad character class) are skipped per line and must never abort the scan. Containment uses a
  separator boundary, so a sibling root sharing a string prefix is outside the project.

### Project Events Are Coalesced

`OpenProjects` fires one property-change event per project on startup and on project-group
switches, so any listener that fans out or rescans must debounce. All three sites go through
`support/SlidingTask` (a named `RequestProcessor.Task` whose `schedule()` restarts the window):

- `manager/FileCacheManager` — `REBUILD_TASK` (`REBUILD_DELAY_MS` = 1000).
- `project/ACPProjectManager` — `syncTask` (`SYNC_DELAY_MS` = 250). `start()` still calls
  `syncActiveProject()` directly so startup readers see the project list immediately.
- `ui/platform/DefaultPlatformBridge.ProjectContextImpl` — `notifyTask` (`CHANGE_DELAY_MS` = 250);
  the fan-out still marshals to the EDT via one `invokeLater` per burst.

Do NOT reintroduce a bare `RP.post(...)` or `SwingUtilities.invokeLater(...)` per event in these
listeners — that queues one full run per event and is what issue #5 reported.

The same helper carries the other pre-existing sliding tasks: `ModelListStore` and
`PinnedMessageStore` prefs flushes, `FileCacheManager` rebuilds, and `TaskQueryProvider`'s
per-query reload/push slots. `UpdateCheckerService` is deliberately NOT one — it schedules a
variable-delay cycle and queries `isFinished()`, which `SlidingTask` does not model.

### Plan Slideout (do not reintroduce the chat bubble)
Plan updates (`case "plan"` in `StrategyRegistry`) do NOT render as a markdown
chat bubble. They call `UIHandler.updatePlan(entries)` (priority-sorted
`PlanEntry` list); `SessionLifecycleHandler` forwards the entries — with the
same session-race guard as `displayMessage` — to `ui/PlanPanel`, which docks at
the SOUTH of the thread window (inside `AssistantTopComponent`'s `threadWindow`
wrap, squeezing `ChatThreadPanel`'s viewport, never the input textbox). The
panel is collapsed-by-default, shows the first unfinished entry + done/total in
the header, toggles on header click, clears on session start and on
`onSessionLoading(true)` (the harness replay can re-populate it while LOADING —
`updatePlan` is deliberately NOT gated by `shouldCapture`). Do NOT reintroduce
a plan `displayMessage` call, and do NOT move the panel between the split
divider and the input area (it must borrow space from the thread only).

### Connection & Lifecycle
- `AcpProtocolClient.setConnectionErrorHandler()` is a noop. Disconnection handles
  IOExceptions silently; pending futures receive a generic client closed exception.
- `@OnStart` handler (`ACPStartup.java`) triggers `componentOpened()`. Defer heavy tasks
  (server start, session load) via `SwingUtilities.invokeLater()` to avoid blocking plugin
  installation.

### User Message Display
- User bubbles are a wrapping `JTextArea` (stream and finalize). Do not use
  `FitEditorPane` or HTML for user text.
- `MessageBubble.finalizeStreaming` strips harness noise via
  `ToolDataExtractor.stripMessageNoise` then refreshes the `JTextArea`. The one-arg
  overload must delegate to the two-arg overload.
- `<metadata>`/HTML-comment blocks are always stripped. `<system-reminder>` blocks are
  stripped only for a harness whose catalog entry sets `stripsSystemReminders` (Kimi
  Code), because that harness folds them into echoed user text. `MessageBubble` takes
  the flag from `BubbleFactory`, which reads `ProcessControl.getCapabilities()`. A user
  bubble left empty after stripping is dropped at add time in
  `ChatThreadPanel.addSingleBubble` (user text renders immediately and user chunks never
  merge, so this needs no finalize); `finalizeStreaming` repeats the strip and removes a
  bubble left empty there, and copy uses the same stripping.

### Assistant MessageId Generation
- Assistant messages from the server may lack a `messageId`. For pinning support, we generate
  deterministic IDs using `MessageIdGenerator.generate(sessionId, body)`. The body is
  `strip()`ped so live stream text and reloaded history produce the same hash.
- **During streaming**: messageId remains null (or from server) so `canMergeMessages(null, null)`
  returns true and chunks merge into one bubble.
- **After finalization**: `assignMissingMessageIds()` runs in `stopStreaming()` after
  `sweepStreamingBubbles()`, assigning SHA-256 IDs to assistant bubbles that lack one.
- **Non-streaming messages**: messageId is generated in `addSingleBubble()` when
  `messageId == null && !streaming`.
- **One message id, one bubble, across interleaved blocks**: `processMessageSections` resolves an
  assistant chunk whose immediate predecessor is not mergeable to the bubble that already owns
  its message id (`findAssistantBubble`, bounded to the current turn), so text that continues
  after a tool/thought block appends to its message's bubble instead of opening a second one.
- **Only harness ids merge**: a bubble whose id came from `MessageIdGenerator` is flagged
  (`MessageBubble.setGeneratedMessageId`) and is never an owner — that hash is the same for any
  two messages with equal text.
- **Dot-only assistant text is a placeholder**: a chunk whose whole text is dots and that has no
  owning message bubble is dropped, and `removeBlankBubbles` sweeps a dot-only assistant bubble
  like a blank one. omp emits one between tool calls; a dot that ends real text still merges into
  that text (same id), so a sentence period is never lost.

### Startup Session/List Optimization
- Only `deferStartupSessionLoad()` calls `session/list` at startup (after
  `SESSION_LOAD_STARTUP_GRACE_MS`). Do NOT re-add `refreshSessions()` to `initChat()` or the
  immediate `componentOpened()` path — that caused three duplicate RPC calls at startup.
- Session titles can change without any event this panel can apply: a harness may
  rename a session itself (OpenCode sends no title in `session_info_update`), and
  the only source of titles is `session/list`. `DefaultSlashCommandInterceptor`
  therefore calls `refreshSessions()` when the `/title`-without-arguments turn
  completes — that turn is what produces an LLM-chosen name. Do NOT remove it:
  without it the dropdown keeps the old title until the IDE restarts, and do NOT
  move it to a timer or to `session_info_update` (that is the refresh storm this
  optimization removed).
- `session/list` loads the **first page only** and caps the dropdown at
  `SESSION_LIST_LIMIT` (20) rows: `SessionManager.getSessionsOnce` calls
  `rpcClient.getSessions(directory, null)` once, `distinctById` drops a duplicate id
  and `limitSessions` truncates. `nextCursor` is deliberately not followed — loading
  every page floods the dropdown with the user's whole history. Do NOT reintroduce
  cursor pagination.
- `onSessionRenamed` must capture `wasSelected` BEFORE replacing the dropdown
  item: the replacement removes the object that was selected, which clears the
  combo selection, and the closed dropdown then renders blank or the old title.

### Session Rename And Delete
- ACP has no rename. goose implements `_goose/unstable/session/rename`, exposed as
  `Harness.supportsSessionRename()` (id `goose` only). `SessionManager.renameSession` applies
  the local title first, then tells such a harness; a failure is logged and does not revert,
  because the dropdown already shows the new name and the next `session/list` would not.
- `session/delete` is gated on `agentCapabilities.sessionCapabilities.delete`, captured per
  connection by `ServerProcessLifecycle` exactly like `close`
  (`ProcessManager.isSessionDeleteSupported()`). `SessionControl.canDeleteSession()` enables
  the dropdown's "Delete Session…" item; `SessionManager.dropSessionLocally` closes the active
  session, invalidates the strategy cache and rebuilds the dropdown. A failed or unsupported
  call keeps the row — do NOT delete optimistically, or the user loses a live session's entry.
- Deleting is confirmed and permanent, unlike the local archive (`setHidden`) flag.

### UI Sizing & Document Re-use
- `BaseCollapsiblePane` caches preferred/maximum size and clears on `invalidate()`. After expand /
  collapse, repaint the enclosing viewport after layout. Keep streaming lightweight (`JTextArea`)
  and finalize to HTML after the cooldown; reuse the HTML document and retain the content LRU cache.
- Fit/elide headers in pixels during layout and preserve path tails. Do not truncate by fixed
  character count. Draw welcome accents as icons; do not put emoji glyphs in UI bundle strings.
- Assistant bubbles span the thread width; constrain the right-aligned user bubble in
  `MessageBubble.AlignedRow`. Keep flat themed cards, left pin/queue accents, left user avatar and
  current thread grouping/gap behavior. Do not restore shadows, full-height stripes or alternating
  avatars.
- UI components stop timers in `removeNotify()`. `BaseCollapsiblePane` intentionally retains its
  self-only mouse listeners; removing/readding them duplicates callbacks. Keep the MCP idle-timeout
  minimum at 30s.
- Status labels are length-capped; thinking frames keep constant width and update through the status
  helper. Merge incoming model options so an open session's dropdown does not shrink.

### Task Repository Toggle
- The `taskRepository.enabled` preference (default `true`) gates the entire todo.txt feature.
- **Two entry points are guarded** because the bugtracking framework calls
  `createRepository()` during its own `loadRepositories()` startup, which races with
  `TasksStartup`: `TasksStartup.run()` (skips `restoreFromMetadata()`) and
  `TasksConnector.createRepository(RepositoryInfo)` (returns `null` when disabled).
- The checkbox lives in Preferences > Advanced > IDE Extensions section.
- The preference key is `PreferenceKeys.TASK_REPOSITORY_ENABLED`; cached via
  `PluginSettings.isTaskRepositoryEnabled()`.

### Task Scheduler & Closed Tasks
- `TaskScheduleProvider.getSchedule()` and `getDueDate()` return `null` when
  `TaskRecord.isFinished()` is true (status == "closed"). This prevents the bugtracking
  framework from showing closed tasks in the Scheduled category or sending notifications.
- Do NOT remove the `isFinished()` guard — it is the only thing preventing stale
  notifications for completed tasks.

### Mini-Assistant Toolbar Launch
- The toolbar "Launch Mini Assistant" button opens the `MiniAssistantDialog` **without**
  closing the main sidebar (`AssistantTopComponent`). The old `minimizeToDock()` call was
  removed so both panels can coexist.
- The `ToggleMiniAssistantAction` (Alt+L shortcut) also does NOT close the sidebar — it
  only toggles the dialog's visibility/focus.

### SSE → EDT Session Race Guard
Capture the update's session id before `invokeLater`; on the EDT, drop it if it no longer matches
`SessionControl.getCurrentSessionId()`.

### Editor Context Capture
Return `null` when no real editor `FileObject` exists; callers then omit context (do not send
phantom paths).

### Auto-Scroll Contract
Capture `wasAtBottom` before every content mutation; scroll only when anchored. Keep the 150px
sticky zone and `followStreamBottom` through flush/finalize/stop, but clear it on turn end or user
scroll. Apply it to thought/tool bursts as well as assistant text.


### Live Context Report (`_goose/unstable/context/report`)
- The Token panel's CONTEXT WINDOW block is the only place that reads the harness live:
  `TokenUsageDialog.readContextReport()` runs on the stats worker (never the EDT) and asks
  `contract/ContextReportQuery` → `SessionManager.reportContext` →
  `SessionRpcClient.reportContext`. Makes no model call; goose measures with its local tokenizer.
- `Harness.supportsContextReport()` (goose only) gates it, and `SessionManager` is registered
  under both `SessionControl` and `ContextReportQuery` via `@ServiceProviders` — NetBeans'
  `@ServiceProvider` is not repeatable in RELEASE220, so a second service needs the container.
- A failed or unsupported report yields `null`, and the block is simply absent: the panel's
  stored-history tables must never fail because the live RPC did. `providerManagesContext`
  (Claude Code, Gemini CLI) shows "Provider-managed" instead of an empty segment list, and
  `autoCompactionPending` is called out rather than looking like an oversized context.

### Usage Stats Store
`H2UsageStatsStore` is single-executor: all DB work stays off the caller/EDT. Keep pin/model caches
memory-backed for reads and persist deltas; preserve startup writes. Model lists expire omitted entries
one hour after the harness's latest report (prune only on a new report). Bump `SCHEMA_VERSION` and add
migrations for semantic changes; canonicalize model ids at attribution and never use a global model
fallback. Capture usage attribution when messages arrive.

Record MCP tool usage per execution, attributed through `SessionQuery`; do not add turn-end counters or
drop calls with no session. On open failure, mark the store unavailable and allow only the explicit
user retry (at most two opens); do not auto-retry. Only an absent schema-version row means legacy;
invalid values fail safely. Catch executor rejection after shutdown, and close the store from
`ACPShutdown` (flush does not release the H2 lock).

SQL lives in `db/schema.sql` and named blocks in `db/queries.sql`; use `db/Sql` slots for controlled
identifiers and do not inline SQL or rebind slot-owned parameters.

### Preference Migration To The Store
`runPrefsMigration()` migrates history, session metadata, pins and model lists with separate markers.
Preserve ordering: write rows, write marker, then delete only successfully stored keys. Parse skips are
final; write failures leave markers unset for retry. Keep unreadable keys and report them without
blocking startup. Do not retry partial input-history inserts, which would duplicate rows.

### Test NetBeans User Directory
The build pins `-Dnetbeans.user=target/test-userdir` for the test JVM, because `Places.getUserDirectory()` is
null outside a real IDE and `new File(null, "beanbot")` silently yields a *relative* path — which once put a
stats database in the repository root. Keep that pin, and never let a test reach the production store: inject
a double through `SessionManager.setStoreForTest` or the `SessionStore` seam rather than resolving the real
one from Lookup.

### Renderer Selection

- `MarkdownStyledRenderer` bypasses the Swing HTML engine entirely by inserting text ranges
  with `SimpleAttributeSet` into a `JTextPane`. Fast and lightweight; use for streaming
  collapsible activity/thought panes.
- `FitEditorPane` renders complete HTML/CSS via Swing's native rendering engine. Heavy and
  complex; reserved for main chat bubbles needing full list, link, and nested styling support.

### Config Combo Enabled State
`ConfigPanelController` stores model/agent/thinking capability flags separately from `processing`;
`applyEnabledState()` computes `field && !processing`. Never call `combo.setEnabled()` directly.
Update capability flags from options, toggle processing on the EDT, and reapply when the options
panel becomes visible without overwriting those flags.

### Ctrl+L Toggle / Shortcut Registration (DO NOT BREAK)
The assistant's registration annotations must stay together: `@TopComponent.Registration`,
`@ActionID(id="github.anandb.netbeans.ui.AssistantTopComponent")`, and
`@TopComponent.OpenActionRegistration(preferredID="AssistantTopComponent")`. RELEASE220 does not
support shortcut registration on that annotation.

Register `ToggleAssistantAction` in `layer.xml`; both `Menu/Window/Assistant.shadow` and
`Shortcuts/D-L.shadow` (Ctrl+L) must target it, not the generated open action. The action uses
`AssistantTopComponent.findInstance().toggleVisibility()`. Toggle closes only when
`isOpened() && isShowing()`; otherwise open if needed and activate. Do not intercept Ctrl+L in a
`KeyEventDispatcher`.

## MCP Tools

- Keep MCP tool names short. OpenHands must not receive the internal MCP server until its adapter
  accepts the expected headers shape.
- `Harness.acceptsMcpAuthToken()` controls MCP URL authentication; resolve the harness before
  `toolExecutor.start()`. Never hand authenticated MCP URLs to PI-family or Jcode sessions.
- Every filesystem/git/hg tool must use `ProjectPathGuard`/`FileAccessGuard`; never bypass
  `RunCommandPolicy.rejection`. `run_command` is confined by working directory and command policy.
- Return tool errors as `{status:"error", message:...}`; set `fatal:true` only for service failures,
  failed writes/updates, or timed-out/nonzero VCS commands. Do not throw for ordinary validation.
- When an empty `grep`/`rg` result is expected in harness shell calls, neutralize its exit code or
  use the command tool's separate `exitCode` field.
- For connected IDE tools, use `find_type`/`find_file` for indexed Java types and known filenames;
  other language searches use `search_project`.

## User Guide (docs/)

`docs/passages/NNN_<name>.txt` and `docs/images/` are the guide sources; never edit generated
`target/User Guide.twee` directly. Passages start with `:: Name {attrs}` and the standard title/index
header. Add new pages at the next number, link them from `051_topic_index.txt` (alphabetically), and
cross-link related pages. Images use `![alt](../images/<name>-N.webp){style="max-width:100%;"}`.

After edits, run `python3 docs/stitch.py` and
`python3 docs/check_reachable.py "target/User Guide.twee"`; all passages must be reachable from
Home through page links and there must be no dangling links. Build output is gzipped at
`target/generated-guide/UserGuide.html.gz`. `UserGuideOpener` opens the extracted temp file off the
EDT; do not move that I/O onto the EDT.

<!-- graymatter:instructions:begin — managed by `graymatter init`; edits inside this block are overwritten -->
## Memory (GrayMatter)

You have persistent memory through the `graymatter` MCP tools. Hooks and MCP are
complementary; neither replaces the other. MCP wiring alone only makes tools
available; this briefing and optional Claude Code hooks define when they run.

This block can be installed globally, so it may reach a project that has no
GrayMatter wired. If `memory_search` is not in your toolbelt for this session,
skip the rest of this section. That is a check on what tools exist, not a
judgement call about whether memory seems useful: if the tools are there,
everything below applies.

### Your identity

Your `agent_id` is the name of this repository's root directory, used verbatim
every session. Add a `-<role>` suffix only when several agents share the repo
(`myapp-backend`, `myapp-frontend`). Inventing a new id per session scatters
your facts across namespaces and looks exactly like memory being broken.

Facts every agent in the project should see go to the reserved id `__shared__`.

### Every session, without exception

1. **Resuming long-running work**: call `checkpoint_resume` first.
2. **Before your first substantive reply**, inspect only the newest hook block
   available for the session's initial turn; ignore quoted examples and blocks
   from older turns. A real recall block begins with a bracketed
   `GrayMatter hook recall ran` marker
   naming its `agent_id`. If that id differs from the `agent_id` you would
   search, run both project and `__shared__` searches: cross-namespace dedup may
   have placed a shared duplicate under `## Memory`. When the ids match, reuse
   each non-empty section and run the search for every missing section. Fold
   all results into your working context before answering.
3. A fresh marker suppresses only those matching routine searches. Keep using
   `memory_search` or `memory_search_batch` for focused, ad-hoc lookups; hooks
   do not replace MCP writes, reflections, aliases, or checkpoint tools.
4. **Before you stop**, store what you learned (see the table) and call
   `checkpoint_save` if the task is unfinished.

### What triggers a call

| When this happens | Call |
|---|---|
| The newest hook block has a different id | `memory_search` for both the project and `__shared__` |
| A same-id hook block lacks a non-empty section | `memory_search` for that missing scope |
| You need additional or focused context | `memory_search` or `memory_search_batch` |
| The user states a preference | `memory_add` |
| You discover a project convention | `memory_add` with `agent_id: "__shared__"` |
| You make a non-obvious decision | `memory_add`, include the reasoning |
| You fix a non-trivial bug or find a workaround | `memory_add` |
| The user corrects you | `memory_reflect` with `action="update"` |
| A stored fact becomes wrong | `memory_reflect` with `action="update"` or `action="forget"` (never `"unpin"`) |
| The user or authoritative project policy declares a stored fact permanent | `memory_reflect` with `action="pin"` |
| A pinned fact is still true but no longer needs permanence | `memory_reflect` with `action="unpin"` |
| A search comes back with a **weak-match note** | Reformulate **once** with the note's suggested terms; if your wording and the store's differ, declare it with `memory_alias` before trying more synonyms |

Prefer durable, actionable, atomic conclusions; skip low-signal, duplicate,
or transient information. Stored facts add retrieval/decay cost. Pin only
facts explicitly designated permanent by the user or authoritative project
guidance.

### The tools

| Tool | Required | Optional |
|---|---|---|
| `memory_search` | `agent_id`, `query` | `top_k` (default 8), `explain` |
| `memory_search_batch` | `agent_id`, `queries` | `top_k` (default 8) |
| `memory_add` | `agent_id`, `text` | |
| `memory_reflect` | `action`, `agent_id` (or deprecated `agent` alias) | `text`, `target` (required by action) |
| `memory_alias` | `agent_id`, `term`, `equivalents` | teach the store a vocabulary bridge |
| `checkpoint_save` | `agent_id` | `state` |
| `checkpoint_resume` | `agent_id` | |

`agent_id` is canonical for every tool. `memory_reflect` also accepts the
deprecated `agent` alias; `agent_id` wins when both are set.

### The store learns its own vocabulary

When a search misses because your wording and the store's differ, the store
learns that bridge from use: declare it once with `memory_alias`, or let two
sessions repeat the same unknown word and the store promotes the alias by
itself. `graymatter alias list` shows which aliases you taught (`authored`)
and which the store concluded from use (`usage`). A wrong one is revised like
any fact.

### Store conclusions, not transcripts

One idea per call. Skip anything already in the code or the README, anything
transient (that is what checkpoints are for), and never store secrets.
<!-- graymatter:instructions:end -->

# Agent Configuration

- System Framework: BMAD Method Core
- Skills Directory: .agents/skills/
- Mode: Orchestrator / Terminal CLI Execution
