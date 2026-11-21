# ADR 0001: Mid-turn steering routing order

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** Anand Balasubramanian
- **Related:** `MessageSender.trySteer()`, `MessageSender.trySlashSteer()`,
  `support/SteeringRequest`, `support/SteerEchoes`, `ActiveRunTracker`

## Context

When a user sends a message while a turn is already in flight, the plugin must
decide how to deliver it. The harnesses disagree on what is even possible:

- **goose** rejects a second `session/prompt` outright. It exposes a steering
  extension (`_goose/unstable/session/steer`) that needs a known run id from
  `_meta.goose.activeRunId`, and announces acceptance later via
  `session_info_update`.
- **Hermes** has no steering extension. The only way to steer is to post
  `/steer <guidance>` as an ordinary `session/prompt`, which the agent answers
  with a bare RPC result (no turn-end notification).
- **claude-agent-acp / omp / pi adapters** advertise an ACP steering extension
  (`_session/steering`) at the top level of `initialize` (`_meta.steering.supported`).
  omp's `session/prompt` *implicitly cancels* a running turn if a second prompt
  arrives, so a naive second-prompt path is destructive.
- **Other harnesses** (opencode v1, pi without the extension, codex-acp) only
  support queueing or interleaved prompts.

On top of that, a failed mid-turn send must not tear down the original turn's
UI (Stop stays enabled, stall watchdog stays armed), and a local slash command
must never be steered or queued — it is a UI action.

## Decision

`MessageSender.trySteer()` evaluates routes in this fixed order, taking the
first that applies, and only when the turn is in flight and no permission
request is pending:

1. **goose extension** (`_goose/unstable/session/steer`) — when the harness
   supports it and a run id is known.
2. **Hermes slash steering** (`/steer <guidance>` as `session/prompt`) — when
   `Harness.supportsSlashSteer()` (id `hermes` only).
3. **ACP steering extension** (`_session/steering`) — when the connection
   advertised `_meta.steering.supported`.
4. **Queue** — park the message until turn end.
5. **Second prompt** (`session/prompt`) — only for harnesses whose catalog entry
   permits interleaved prompts (e.g. opencode v1).

Each route is gated on live re-reads of `isPermissionPending()` and
`isMidTurn()`; permission state can change while a command popup is open.

## Alternatives considered

- **Queue everything mid-turn.** Rejected: delays input until turn end for
  harnesses that can steer, which defeats the purpose of steering and makes the
  plugin feel broken on goose/claude.
- **Always send a second `session/prompt`.** Rejected: omp implicitly cancels
  the running turn on a mid-turn prompt, destroying in-flight work. Also goose
  rejects it outright.
- **Steering extension first for every harness.** Rejected: only some adapters
  advertise it; the rest must fall through to queue or second-prompt. The
  capability is per-connection, not per-harness-id, so it cannot be a catalog
  flag.
- **Steering extension after the queue.** Rejected: an agent that advertises
  steering must not have its input parked just because its catalog entry says
  `requiresMessageQueue` (pi still does, as the fallback for adapters without
  the extension).
- **Steering extension after the second-prompt path.** Rejected: see the omp
  cancellation problem above.

## Consequences

- The order is load-bearing and must not be reordered without re-checking each
  harness's cancellation/queue semantics. It is encoded in `trySteer()` with a
  comment listing the order; keep that comment in sync.
- A failed steer restores the composer text *and* attachments and removes its
  echo bubble (`ChatThreadPanel.removeMessageBubble`), so a resend does not
  draw a second identical bubble. All three failure paths (steer, slash steer,
  extension) route through `restoreComposerText()`.
- A failed mid-turn send keeps the turn alive only while `isMidTurn()` still
  holds; if the turn ended in between, it tears down like a turn-starting
  failure.
- Local slash commands are intercepted *before* this routing, so they work even
  while a permission prompt is open; forwarded commands (`/compact`, `/dcp`)
  draw their echo only once the send is accepted.
- The extension route never bumps `sendGeneration` or wires turn-end handlers,
  because the original prompt's completion must remain owned by the first send.
- Goose's accepted steer is not instantly part of the turn: it is announced via
  `session_info_update` as `_meta.goose.queuedSteer`, driving the amber
  "queued" accent on the user bubble.
- Future: ACP v2 `session/inject` is the standard replacement. Keep the goose
  call behind `supportsSessionSteer()` so it can be swapped without touching UI.

## Rejected-elsewhere note

Do not use `seenMessageIdsBySession` to dedupe steer echoes — it is the
stale-pin-cleanup set, removed by `retainActivePins()` after every flush, and
never receives live user inserts. Steer echoes are registered by message id
(goose) or by text (extension) via `support/SteerEchoes`.
EOF
echo done