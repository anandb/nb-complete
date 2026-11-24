# Agent Memory

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: starting a session, recalling earlier decisions, or storing a durable fact.

This protocol is kept out of the always-loaded index: it matters at session boundaries, not
on every edit. Run `graymatter init` with `--skip-instructions` in this repository — without
that flag it rewrites the pointer block in `AGENTS.md` back into the full protocol, and you
should copy the new text here afterwards.

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
