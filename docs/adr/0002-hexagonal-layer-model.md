# ADR 0002: Hexagonal layer model and ports

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** Anand Balasubramanian

## Context

A NetBeans plugin that drives 20+ coding harnesses and exposes IDE tools over
MCP accumulates dependencies fast. Without a layer rule, `ui/` ends up calling
managers directly, managers end up importing Swing, and the model ends up
depending on utilities — making the code untestable headlessly and impossible
to reason about.

## Decision

Dependencies flow downward only, across five layers:

- `model/` — leaf records. No imports of any other project package.
- `contract/` — ports (interfaces). Imports only `model/` and `stereotype/`
  marker annotations.
- `manager/`, `mcp/` — adapters over `contract/`. Peers; `manager/` may import
  `mcp/` only to construct the `ToolExecutor` port implementation
  (`McpToolAdapter`), confined to `ProcessManager`.
- `ui/`, `db/`, `tasks/`, `project/` — adapters over `contract/`. `ui/` uses
  `contract/`, never `manager/` or `mcp/` concrete classes.
- `support/` — shared utilities. May import `model/HarnessCatalog` only
  (catalog lookup, not record data). Pure static utilities stay here even when
  they reference `model/` records for parsing.
- `stereotype/` — dependency-free leaf of marker annotations
  (`@HexagonalPort`, `@Boundary`, `@McpToolInput`, `@McpToolProvider`).

Services are reached through `Lookup.getDefault().lookup(...)`, never via
`Manager.getInstance()` singletons, so `ui/` and `tasks/` never hold concrete
manager references.

## Alternatives considered

- **Flat package with naming conventions only.** Rejected: nothing enforces it;
  the `LayerReferencesTest` guard exists precisely because conventions rot.
- **Full hexagonal with separate modules per layer.** Rejected: NetBeans
  Platform plugins are single modules; multi-module Maven would add build
  complexity disproportionate to the benefit. Package-level discipline plus a
  test guard is the boring option.
- **Let `ui/` call managers directly for "convenience."** Rejected: this was
  the prior state and produced the `ui/ → manager/` singleton violation that
  had to be cleaned up across the whole codebase.

## Consequences

- `ui/` accesses services via `Lookup.getDefault().lookup(SessionControl.class)`
  and `ProcessControl.class`. `contract/ → manager/` calls are forbidden and
  guarded by `LayerReferencesTest`.
- New `contract/` types import only `model/` and `stereotype/`. `model/` is a
  clean leaf in `src/main` (tests in the `model` package may reuse
  `support/` helpers).
- Three accepted debts are documented and must not widen: `support/ → model/`
  (three files), `manager/ → mcp/` (ProcessManager only), and
  `tasks/ → ui/platform/` (deferred, 14 files).
- Adding a feature means adding a port in `contract/` first, then an adapter.
  This adds a file but keeps the dependency graph acyclic and reviewable.
