# ADR 0003: ACP over direct model APIs

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** Anand Balasubramanian

## Context

The plugin could either call model APIs directly (OpenAI/Anthropic SDKs) or
drive an external coding agent. Direct calls would give full control but would
require reimplementing tool use, file editing, context management, and
permission prompts for every provider — and would make the plugin responsible
for credentials and billing.

## Decision

BeanBot is an **ACP client**, not a model client. It launches the selected
harness as a subprocess and speaks JSON-RPC over stdin/stdout, consuming
streamed `session/update` notifications. In the other direction, an embedded
MCP server exposes IDE tools (projects, files, editor, Git/Hg, tasks) so the
agent can act inside the editor.

BeanBot itself makes no model calls. Credentials, configuration, and billing
stay with the harness the user already authenticates.

## Alternatives considered

- **Direct SDK calls per provider.** Rejected: duplicates tool-use, context,
  and permission logic that each harness already implements; multiplies
  credential handling; forces the plugin to track every provider's API changes.
- **A single "universal" adapter.** Rejected: harnesses differ in capabilities
  (steering, session close, delete, rename, context report) that cannot be
  papered over without losing fidelity. ACP gives a common protocol while
  letting capabilities vary per connection.
- **Remote/cloud agent instead of local subprocess.** Rejected: local subprocess
  keeps data on the user's machine, works offline, and reuses the user's
  existing harness install and auth.

## Consequences

- The plugin's value is IDE integration, not model access. Feature work focuses
  on editor/project/VCS integration and rendering, not on chasing provider APIs.
- Capabilities vary by harness and are captured per connection
  (`sessionCapabilities.close`, `delete`, `changeWorkingDirectory`,
  `_meta.steering.supported`, `_meta.goose.customNotifications`). UI features
  gate on these rather than on harness ids where possible.
- Harness-specific quirks (goose run-id steering, Hermes lost-turn attach, OMP
  model-restore retry) are isolated behind `Harness` flags and catalog entries,
  not spread through the UI.
- The plugin cannot work without at least one harness installed and
  authenticated. Onboarding and the harness chooser exist to make that explicit.
- Updates to ACP or a harness can break integration; the protocol surface is
  small enough (JSON-RPC + MCP) to track, and `AcpProtocolClient` is the single
  owner of wire format.
