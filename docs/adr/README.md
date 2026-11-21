# Architecture Decision Records

Short records of significant technical decisions: the context, the choice, the
alternatives considered, and the consequences. New ADRs are numbered
sequentially and never rewritten — a superseded decision gets a new ADR that
references the old one.

Format: [Michael Nygard's template](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

## Index

| # | Title | Status |
|---|-------|--------|
| [0001](0001-mid-turn-steering-routing-order.md) | Mid-turn steering routing order | Accepted |
| [0002](0002-hexagonal-layer-model.md) | Hexagonal layer model and ports | Accepted |
| [0003](0003-acp-over-direct-apis.md) | ACP over direct model APIs | Accepted |
| [0004](0004-harness-catalog.md) | Harness catalog and version capabilities | Accepted |
| [0005](0005-swing-layout-loop-prevention.md) | Swing layout loop prevention | Accepted |
| _0006_ | Project-events coalescing (SlidingTask) | _planned_ |
| _0007_ | File-access guard as shared ACP/MCP enforcement point | _planned_ |

## How to add one

1. Copy the template from the most recent ADR.
2. Number it next in sequence.
3. Set status to `Proposed`, then `Accepted` once merged.
4. Add a row to the index above.
5. If it supersedes an earlier ADR, mark the old one `Superseded by 000N` and
   link both ways.
