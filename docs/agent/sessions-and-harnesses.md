# Sessions And Harnesses

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: working on session lifecycle, session/load, harness identity, working directories or harness quirks.

## Hermes Lost-Turn Attach (`unknown_execution`)

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

## Harness Identity For Custom Binaries And Containers
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

## session/close Before session/load (Do Not Break)

Capture `sessionCapabilities.close` per connection and reset it on process start. Before loading,
cancel/close only when the target is already loaded by this server generation; gate on the
capability and swallow close failures so load continues. A plain session switch must not cancel
the target load. Do not make cancel/close unconditional.

## Failed session/load Is Reported, Not Silently Completed

`SessionManager.loadSessionFromServer` still completes with `null` when `session/load` (or
`session/resume`) fails after the MCP and startup retries — the agent may keep the session open —
but it MUST emit `SessionListener.onSessionLoadFailed(sessionId, reason)`. Without that event the
UI rendered an empty thread as a successful load. `reason` is `rootMessage(ex)`, which appends the
harness's `error.data.details` (Hermes `unknown_execution`, an unrestorable model, goose's
textual `error.data`). `SessionLifecycleHandler.onSessionLoadFailed` only announces it — an
`info` notice bubble (`createInfo`, grey italic on the sunken background, NOT `error_response`'s
red fill), plus `STATUS_Error` in the status label and loading off. It must never tear down the
thread, clear `currentSessionId` or set `turnEnded` the way `onSessionError` does.
`AcpProtocolClient` logs `error.data` with the error response for the same reason.

OMP's model catalogue is populated a few seconds after `initialize`, so a `session/load` issued
before it settles answers `Could not restore model <id>`. `SessionManager.withModelRestoreRetry`
retries that answer for OMP only, across ~10 s ({2,3,5} s delays) inside a 60 s chain budget,
before the failure reaches `onSessionLoadFailed`. Do not re-add the blanket "unrestorable models
are permanent" exclusion: it made a load that succeeds seconds later fail immediately. Do not
extend the retry to other harnesses without re-measuring — so far only OMP has shown this
transient; the lost-turn (`unknown_execution`) and `-32601` exclusions are unchanged.

## ACP Server Working Directory (Experimental)

- `projectSwitchRestart.enabled` is read at startup; changing it requires IDE restart. When on,
  `session/new` and `session/load` rebind by restarting the server, regardless of harness support.
  Do not make this preference live.
- When off (default), new sessions do not bind/restart. On `session/load`, only harnesses supporting
  `changeWorkingDirectory` (currently goose) move cwd live before loading; log a failed move and
  continue. Preserve this compatibility behavior.
- Rebind terminates served sessions. Bind handshakes to their client/generation, compare canonical
  paths, and do not auto-reload during `explicitSessionOps`. Manual and preference-driven restarts
  are independent of this option.

## Connection & Lifecycle
- `AcpProtocolClient.setConnectionErrorHandler()` is a noop. Disconnection handles
  IOExceptions silently; pending futures receive a generic client closed exception.
- `@OnStart` handler (`ACPStartup.java`) triggers `componentOpened()`. Defer heavy tasks
  (server start, session load) via `SwingUtilities.invokeLater()` to avoid blocking plugin
  installation.

## Startup Session/List Optimization
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

## Session Rename And Delete
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

## SSE → EDT Session Race Guard
Capture the update's session id before `invokeLater`; on the EDT, drop it if it no longer matches
`SessionControl.getCurrentSessionId()`.
