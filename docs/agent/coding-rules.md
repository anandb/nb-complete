# Coding Rules

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: writing or editing any Java, Swing or bundle code.

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
