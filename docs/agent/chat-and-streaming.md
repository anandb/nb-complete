# Chat And Streaming

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: changing message rendering, streaming, steering, plans, scrolling or message ids.

## Notifications & Streaming
- OpenHands' `sendsSystemPromptAsThought` filters `System Prompt:` thoughts; suppress its stdout
  banner with `OPENHANDS_SUPPRESS_BANNER=1`. Route `session/update` through the full pipeline to UI.
- End a turn on `responding_finished`, `end_turn`, RPC `stopReason`, or load config options; flush
  pending text. Never treat `available_commands_update` as turn-end. Cursor permits interleaved
  prompts: ignore stale completions by send generation. Call `onUserPromptSent` only when a prompt
  is actually sent. Finalize via `stopStreaming()` → `finalizeStreaming()`.
- `stopReason=max_tokens` drives the truncation notice; do not also inspect goose per-chunk flags.
  Classify prompt failures by `RpcFailure` code/data (goose auth `-32000/turn_failed`, credits
  `-32603/credits_exhausted`), never by message text. Check `MessageType` before adding updates.

## Harness Version Capabilities (OpenCode v1 vs v2)
Capabilities that vary by version belong in `HarnessCatalog.forAgentVersion()` and are applied
after resolution. OpenCode v2 requires queueing; v1 keeps interleaved prompts. `OPENCODE_V2` shares
the base id/name/binaries and stays out of `HarnessCatalog.ALL`. Unknown versions keep the catalog
default; never guess.

## goose Mid-Turn Steering (`_goose/unstable/session/steer`)

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

## Hermes Slash Steering (`/steer <guidance>`)

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

## ACP Steering Extension (`_session/steering`)

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

## goose Custom Notifications (`_goose/unstable/session/update`)

- Enable this channel through `clientCapabilities._meta.goose.customNotifications=true`.
  Decode `message_usage` and `status_message`; ignore its duplicate `usage_update` because goose
  also sends the standard update. `UpdateData.status` is a `JsonNode` and may be text or object.
- Capture `message_usage` per execution, dedupe by message id, skip LOADING replay, and retain
  compaction rows without ids. Treat `status_message` as live status, never transcript; do not
  reset Ready during a turn.
- Attribute usage from `_meta.goose.inference.resolvedModel`, then requested/session model; never
  use the dropdown value when a provider alias or sub-agent may differ.

## Plan Slideout (do not reintroduce the chat bubble)
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

## User Message Display
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

## Assistant MessageId Generation
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

## Renderer Selection

- `MarkdownStyledRenderer` bypasses the Swing HTML engine entirely by inserting text ranges
  with `SimpleAttributeSet` into a `JTextPane`. Fast and lightweight; use for streaming
  collapsible activity/thought panes.
- `FitEditorPane` renders complete HTML/CSS via Swing's native rendering engine. Heavy and
  complex; reserved for main chat bubbles needing full list, link, and nested styling support.

## Auto-Scroll Contract
Capture `wasAtBottom` before every content mutation; scroll only when anchored. Keep the 150px
sticky zone and `followStreamBottom` through flush/finalize/stop, but clear it on turn end or user
scroll. Apply it to thought/tool bursts as well as assistant text.
