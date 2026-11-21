# ADR 0004: Harness catalog and version capabilities

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** Anand Balasubramanian

## Context

The plugin supports 20+ harnesses whose capabilities differ: steering support,
session close/delete/rename, working-directory change, context report, MCP
auth token acceptance, system-reminder stripping, queueing vs interleaved
prompts. Hard-coding these per harness id scatters conditionals through the
code and makes a new harness a multi-file change. Worse, some capabilities
vary by *version* of the same harness (OpenCode v1 vs v2).

## Decision

Capabilities live in `harness-catalog.yaml` (`src/main/resources/.../model/`),
loaded into `HarnessCatalog`. A `Harness` entry carries flags such as
`requiresMessageQueue`, `supportsSessionSteer`, `supportsSessionRename`,
`supportsContextReport`, `stripsSystemReminders`, `acceptsMcpAuthToken`.

Version-dependent capabilities are resolved by `HarnessCatalog.forAgentVersion()`
and applied *after* resolution, so a version-specific entry overrides the base
without duplicating the whole record. OpenCode v2 shares the base id/name/binaries
and stays out of `HarnessCatalog.ALL`; unknown versions keep the catalog default.

Runtime resolution: `acpHarnessId` (explicit user choice) → `byId`, then
`byBinaryName`, then `UNKNOWN`. Capabilities that vary by version are applied
after that.

## Alternatives considered

- **Hard-coded `if (harnessId.equals("goose"))` checks.** Rejected: scatters
  harness knowledge across the codebase; adding a harness touches many files;
  capabilities cannot vary by version.
- **Capability discovery only (no catalog).** Rejected: some capabilities are
  not discoverable at runtime (e.g. `stripsSystemReminders`, `acceptsMcpAuthToken`,
  binary names for container launchers). A static catalog is the source of
  truth for those.
- **One catalog entry per version.** Rejected: duplicates shared fields
  (binaries, name, base id). Version overrides apply on top of the base instead.

## Consequences

- Adding a harness is a YAML edit plus, at most, a `Harness` flag — not a
  code change across the plugin.
- Binary-name resolution handles container launchers (`docker`, `podman`) via
  the user-pinnable `acpHarnessId`, because the binary name alone cannot
  identify the harness in that case. Precedence: explicit choice wins; Auto-detect
  derives from basename; a stored id the path cannot explain survives `load()`.
- `OPENCODE_V2` is not in `HarnessCatalog.ALL`; it is resolved by version after
  the base entry. Unknown versions keep the catalog default — never guess.
- `ServerProcessLifecycle` captures per-connection capabilities
  (`sessionCapabilities.close`, `delete`, `changeWorkingDirectory`,
  `_meta.steering.supported`) separately from the catalog, because they depend
  on the running server, not the harness binary.
- MCP auth token gating reads only `Harness.acceptsMcpAuthToken()` via
  `ServerProcessLifecycle.requiresMcpAuthToken()` — no binary-name exceptions or
  sets in `BinaryResolver`.
