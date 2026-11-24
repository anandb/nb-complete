# Agent Instructions for coding-assistant

Loaded by every harness. The detailed rules live in `docs/agent/`, one file per area: read the
ones your task touches and skip the rest. This file keeps the overview, the build commands, the
always-apply rules and the index. The memory protocol is in `docs/agent/memory.md`; the section at
the end of this file only points to it.

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
| Recalling or storing durable facts | [memory.md](docs/agent/memory.md) |

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

## Memory (GrayMatter)

The memory protocol lives in [`docs/agent/memory.md`](docs/agent/memory.md): read it before
your first reply, not on every turn. It is moved out of this file on purpose so every harness does
not load it on every edit. When re-running `graymatter init` here, pass `--skip-instructions`;
otherwise it appends the full protocol to this file again.

# Agent Configuration

- System Framework: BMAD Method Core
- Skills Directory: .agents/skills/
- Mode: Orchestrator / Terminal CLI Execution
