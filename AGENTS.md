# Agent Instructions for coding-assistant

Loaded by every harness. The detailed rules live in `docs/agent/`, one file per area: read the
ones your task touches and skip the rest. This file keeps the overview, the build commands, the
always-apply rules and the index. The Memory block at the end is managed by `graymatter init` and
must stay in this file.

## Start Here

| Working on | Read |
| --- | --- |
| Package dependencies, layer model, accepted debt | [architecture.md](docs/agent/architecture.md) |
| Writing or editing Java, Swing or bundle code | [coding-rules.md](docs/agent/coding-rules.md) |
| Messages, streaming, steering, plans, scrolling | [chat-and-streaming.md](docs/agent/chat-and-streaming.md) |
| Session lifecycle, harness identity, harness quirks | [sessions-and-harnesses.md](docs/agent/sessions-and-harnesses.md) |
| MCP tools, file guards, ignore files, `run_command` | [mcp-and-file-access.md](docs/agent/mcp-and-file-access.md) |
| Swing components, shortcuts, annotations, mini assistant | [ui-and-swing.md](docs/agent/ui-and-swing.md) |
| Usage stats, token panel, preferences, tasks, project events | [usage-and-data.md](docs/agent/usage-and-data.md) |
| The user guide under `docs/passages/` | [user-guide.md](docs/agent/user-guide.md) |

`docs/agent/architecture.md` holds the debt registry that
`src/test/.../architecture/LayerDependencyTest` enforces; update it in the same change when an
accepted edge or file changes.

## Always Apply

- **Never do I/O on the EDT**: file reads/writes, pipe writes and
  `sendRequest`/`sendNotification` go through `CompletableFuture.runAsync()` or a background
  thread — a full pipe deadlocks the UI.
- **Swing state changes from background threads** go through `SwingUtilities.invokeLater()`.
- **Parse JSON-RPC with Jackson `JsonParser`**, never `BufferedReader.readLine()`, and echo
  incoming `id` nodes verbatim — ACP ids may be strings.
- **Preferences** go through `PluginSettings` accessors (`static volatile` cache + listener), never
  a direct read in a hot path; UI code registers listeners through `PrefStore`.
- **`ui/` reaches services through `contract/` ports via `Lookup`**, never
  `SessionManager.getInstance()` / `ProcessManager.getInstance()`, never `ui/` → `manager/`/`mcp/`.
- **Structural edits use parser-aware tooling** (IDE refactor, `ast-grep`), not `sed`/`python`
  one-offs; keep lines ≤120 characters and comments to 1–2 lines.
- **Log with index placeholders** (`{0}`), exception as the last argument, and no `warning()`.
- **Tests run headless**: mock the UI instead of constructing windows or dialogs.
- **Edit bundle strings only under `src/main/resources/`**; the `src/main/java/` copy is generated.

## Project Overview
- **Project**: Coding Assistant (NetBeans IDE plugin, Java 17, Maven)
- **Current Stable Version**: 1.32.1
- **Test Builds**: a `0.x` version in `pom.xml` is a test build, never a release. It is
  numbered below the published `1.x` line on purpose, so an artifact built from it cannot be
  mistaken for, or auto-updated over, a release; NetBeans treats it as a downgrade. Releases
  always come from a `1.x` version and a `v*` tag.
- **Key Tech**: NetBeans API (RELEASE220), Flexmark, Jackson, RSyntaxTextArea, JUnit 5.

## Build Commands
- Use `./mvnw` (pinned Maven 3.9.x), never system `mvn`. Build: `./mvnw package`; clean:
  `./mvnw clean`; quick package: `./mvnw package -DskipTests`.
- Eventual Coverage Target is 96% on core modules (`contract/`, `support/`, `manager/`); `ui/` is
  excluded (Swing code is hard to unit-test headlessly). Current: core ~82%, excluding `ui/` ~81%,
  overall ~58%. Use JaCoCo when reporting it.
- If a workflow helper fails and the user explicitly requests direct Bash, continue with these
  project commands.
- **Local test suites MUST use `-P random-test-order`**; CI intentionally uses default order.
  The profile prints the random seed for reproducing failures.
- Surefire runs `*Test`; Failsafe runs `*IT` during `verify`. Use `./mvnw verify` for integration
  tests; `test` alone does not run them. Tests use the pinned `netbeans.user` directory.
- **User guide**: `docs/stitch.py` needs `python3` and `tweego`; it compiles against the Harlowe
  story format vendored at `docs/storyformats/harlowe-3` (3.3.9) via `TWEEGO_PATH`, not the older
  format bundled with tweego. Output is gzipped at `target/generated-guide/UserGuide.html.gz`.
  Stitch failures fail the build; `-DskipUserGuide=true` skips stitching and copying. Release
  verification requires the output, so keep CI's pinned tweego install step. Bump the vendored
  files, `FORMAT_VERSION` in `stitch.py` and `StoryData.format-version` together.

### Test NetBeans User Directory
The build pins `-Dnetbeans.user=target/test-userdir` for the test JVM, because `Places.getUserDirectory()` is
null outside a real IDE and `new File(null, "beanbot")` silently yields a *relative* path — which once put a
stats database in the repository root. Keep that pin, and never let a test reach the production store: inject
a double through `SessionManager.setStoreForTest` or the `SessionStore` seam rather than resolving the real
one from Lookup.

## BMAD Working Artifacts (`_bmad-output/`)
- `_bmad-output/` is gitignored local state: never commit or cite it as a repo artifact. Maintain
  its `index.md` when artifacts, pom version, README badge or passage count change.
- Specs require `title/type/created/status` frontmatter; `done`/`superseded` specs belong in
  `archive/`, and status must match the tree. Research epic status must match its spec.
- Generated project docs are dated snapshots: regenerate with `bmad-document-project`, never
  hand-edit. Run `python3 _bmad/check_artifacts.py` after spec/quick-dev/research work and before
  release verification when `_bmad/` tooling is available.

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
