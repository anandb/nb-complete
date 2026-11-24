# Architecture

BeanBot follows a hexagonal (ports & adapters) architecture integrated into the NetBeans Platform.

- **`model/`**: ACP-compliant data records (sessions, messages, updates, config options) plus the harness catalog, theme colour tokens and usage records. A clean leaf: it imports nothing else in the project.
- **`contract/`**: Service interfaces that define ports for session control, process management, UI callbacks and the local stores. Implemented by `manager/` (session/process/tool execution), `db/` (usage, session, pin and model-list stores) and `mcp/McpToolAdapter` (tool executor); consumed by `ui/`.
- **`manager/`**: Core orchestration — protocol client (JSON-RPC over stdin/stdout), session state machine, process lifecycle, and SSE strategy dispatch.
- **`mcp/`**: MCP server integration — hosts a local server that registers the IDE tool set (editor context and navigation, filesystem, git/hg, tasks, projects, stash diff) so that the AI client can inspect open tabs and control editor navigation.
- **`project/`**: NetBeans lifecycle hooks (`@OnStart`/`@OnStop`), the open-project manager, and the markdown project type.
- **`tasks/`**: todo.txt task repository integration — bugtracking providers, issue cache, task editors.
- **`support/`**: Pure utilities — logging, JSON mapping, text scanning, constants, browser helpers, shortcut utils, binary resolution. It imports only `model/` (a few static codecs) and `pattern/`.
- **`db/`**: H2 persistence layer — usage-stats store, session store, pinned-message and model-list caches, SQL loader, preference migration. Depends on `model/`, `support/`, `contract/`.
- **`pattern/`**: Compiled regex patterns as pure constants with no dependencies; consumed by all layers (ui, support, mcp, tasks, project, manager, db).
- **`stereotype/`**: Dependency-free marker annotations (`@HexagonalPort`, `@Boundary`, `@McpToolInput`, `@McpToolProvider`) carried by ports and adapters; imports nothing.
- **`ui/`**: All Swing components — chat window, message bubbles, streaming animation, theming, options panel, stash diff viewer. Depends on `contract/` interfaces, plus `model/` data records, `support/` utilities, and `project/` only through `ui/platform/` (plus the two `ui/mdproject/` wizard classes, which read `project/mdproject/MdProjectMarker` directly). Never imports `manager/`, `mcp/` or `db/`.

For the long-form architectural review — concurrency/EDT rules, protocol details and subsystem
notes — see [ARCHITECTURE-REVIEW.md](ARCHITECTURE-REVIEW.md). The generated component graph is
[DIAGRAM.dot](DIAGRAM.dot).

## Layer Dependencies

Dependencies flow downward only. `ui/` and `manager/` are **peers over `contract/`** — the ports are
what invert the dependency, so `ui/` never imports `manager/` — and everything rests on `model/`,
`support/`, the dependency-free `pattern/` and the marker-only `stereotype/`.

```
        ┌──────────┐                  ┌──────────────┐
        │   ui/    │                  │   manager/   │   ← adapters (peers)
        └────┬─────┘                  └──────┬───────┘
             │            ┌──────────────────┘
             ▼            ▼
        ┌──────────────────────┐
        │      contract/       │  ← ports (interfaces only)
        └──────────┬───────────┘
                   ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ model/   │ │ support/ │ │ pattern/ │  ← data, utilities, regex constants (lowest)
        └──────────┘ └──────────┘ └──────────┘
```

`project/`, `mcp/`, `tasks/` and `db/` sit outside this five-layer model and reach downward only:

| Satellite | May import | Role |
| --- | --- | --- |
| `project/` | `contract/`, `model/`, `support/`, `pattern/` | NetBeans lifecycle wiring and the open-project manager |
| `mcp/` | `contract/`, `model/`, `support/`, `pattern/` | The tool-serving adapter surface |
| `tasks/` | `contract/`, `model/`, `support/`, `pattern/`, `ui/platform/` | A self-contained bugtracking feature |
| `db/` | `contract/`, `model/`, `support/`, `pattern/` | The H2 persistence adapter behind the store ports |

`model/`, `contract/` and `support/` import none of `project/`, `mcp/` or `tasks/`. `manager/` reaches
`mcp/` only from `ProcessManager` (via `McpManager`/`McpToolAdapter`).

Two accepted exceptions exist, both recorded in [`docs/agent/architecture.md`](agent/architecture.md):

- `tasks/TaskRepositoryController` and `tasks/TaskIssueController` read open projects through
  `ui/platform/PlatformBridge` and `ProjectContext` instead of a `contract/` port.
- `ui/mdproject/MdProjectWizardIterator` and `ui/mdproject/UseAsMarkdownProjectAction` import
  `project/mdproject/MdProjectMarker` directly instead of going through `ui/platform/`.

## Connection Flow

```
┌──────────┐    SSE / JSON-RPC    ┌───────────────────┐
│  Sidebar │ ◄──────────────────► │ AcpProtocolClient │
│ (client) │     stdin/stdout     │    (transport)    │
└──────────┘                      └────────┬──────────┘
                                           │
                                      ┌────▼──────┐
                                      │ ProcessMgr│
                                      │ (dispatch)│
                                      └────┬──────┘
                                           │
                                      ┌────▼──────┐
                                      │SessionMgr │
                                      │ (session) │
                                      └────┬──────┘
                                           │
                              ┌─────────────┼─────────────┐
                              ▼             ▼             ▼
                        ┌──────────┐  ┌──────────┐  ┌──────────┐
                        │ Strategy │  │ Lifecycle│  │   MCP    │
                        │ Registry │  │ Handler  │  │  Server  │
                        └──────────┘  └──────────┘  └──────────┘
```

`project/`, `mcp/`, `tasks/`, `db/`, and `pattern/` sit outside this five-layer model: `project/` is NetBeans
lifecycle wiring, `mcp/` is the tool-serving adapter surface, `tasks/` is a self-contained
bugtracking feature, `db/` is the H2 persistence adapter, and `pattern/` is a dependency-free
regex-constant satellite imported by every layer. `model/`, `contract/`, and `support/` import none
of `project/`, `mcp/`, or `tasks/`; `manager/` reaches `mcp/` only from `ProcessManager`
(via `McpManager`/`McpToolAdapter`).
