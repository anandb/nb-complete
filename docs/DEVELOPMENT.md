# Development

## Prerequisites

- JDK 17+
- Apache NetBeans (built and tested against RELEASE220)
- `python3` and Harlowe 3 `tweego` on `PATH` to build the bundled user guide
- One supported coding harness installed and authenticated for manual testing

## Build

Use the pinned Maven wrapper, never a system Maven:

```bash
./mvnw package -DskipTests     # quick package
./mvnw package                 # full build with tests and the user guide
./mvnw clean                   # clean
```

The generated NBM lands in `target/nbm/`. Installing it through
*Tools → Plugins → Downloaded* is the normal end-to-end check.

## Tests

- Surefire runs `*Test`; Failsafe runs `*IT` during `verify`.
- `./mvnw test` does **not** run the integration tests — use `./mvnw verify`.
- Local test suites must use the randomized order profile: `./mvnw test -P random-test-order`.
  CI intentionally uses the default order.
- Test health is reported as tests run, not coverage percentages: 2006 unit tests plus one failsafe IT, all run headless by `./mvnw verify`.
- The build pins `-Dnetbeans.user=target/test-userdir` for the test JVM so no test can reach the
  production stats store.

The user guide is stitched by `python3 docs/stitch.py` into
`target/generated-guide/UserGuide.html.gz`; stitching failures fail the build, and
`-DskipUserGuide=true` skips it. Release verification requires the output, so keep the pinned
`tweego` step in CI.

## Dependency Security Scan

The `check-cvss` profile runs [OWASP Dependency-Check](https://jeremylong.github.io/DependencyCheck/)
against the resolved dependencies:

```bash
export NVD_API_KEY=<your key>    # free from https://nvd.nist.gov/developers/request-an-api-key
./mvnw verify -Pcheck-cvss
```

The key is read from the `NVD_API_KEY` environment variable (or `-Dnvd.api.key=...`). Without a key
the NVD feed is rate-limited heavily, so the first run is slow either way — it downloads the NVD
dataset into the local Maven repository and caches it. Reports land in
`target/dependency-check-report.html` and `.json`; accepted false positives belong in
`scripts/dependency-check-suppressions.xml`.

## Source Organization

All source lives under `src/main/java/github/anandb/netbeans/`:

| Package | Files | Role |
| --- | --- | --- |
| `contract/` | 27 | Service interfaces — the ports (UI callbacks, session & process control, permission & request handlers, pinned message control, usage/session stores, tool execution) |
| `db/` | 8 | H2 persistence layer (usage-stats store, session store, pinned-message and model-list caches, SQL loader, preference migration) |
| `manager/` | 34 | Core orchestration, protocol clients, session management, process lifecycle (includes `strategy/`, file cache, VCS ignore) |
| `mcp/` | 49 | MCP server integration (editor, filesystem, VCS, task and project tool providers, tool input records, message servlet) |
| `model/` | 34 | ACP-compliant data models (session, messages, updates, config options, colour tokens, usage records); `harness-catalog.yaml` holds the harness definitions |
| `pattern/` | 43 | Compiled regex patterns (pure constants, no dependencies; consumed by all layers) |
| `project/` | 13 | NetBeans lifecycle hooks, project manager (includes `mdproject/`, the markdown project type) |
| `stereotype/` | 4 | Dependency-free marker annotations (`@HexagonalPort`, `@Boundary`, `@McpToolInput`, `@McpToolProvider`) |
| `support/` | 49 | Utilities (logging, JSON mapping, text scanning, constants, browser helpers, shortcut utils, binary resolution, steering requests) |
| `tasks/` | 17 | todo.txt task repository integration (bugtracking providers, issue cache, task editors) |
| `ui/` | 148 | Swing components, platform integration, markdown project UI (chat, bubbles, theming, options, stash diff, file search, export, send-to-assistant actions) |

Counts are recursive and current; adjust the row when you add or remove a class.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the package layout, layer rules and connection flow.

## Code Reading Path

For a guided walkthrough mapped to the plugin's execution flow, read files in this order:

### Phase 1: Entry & Lifecycle
1. [`project/ACPStartup.java`](../src/main/java/github/anandb/netbeans/project/ACPStartup.java) — NetBeans `@OnStart` hook
2. [`project/ACPShutdown.java`](../src/main/java/github/anandb/netbeans/project/ACPShutdown.java) — `@OnStop` cleanup
3. [`src/main/resources/github/anandb/netbeans/ui/layer.xml`](../src/main/resources/github/anandb/netbeans/ui/layer.xml) — NetBeans registration (window menu/shortcuts, editor popup, Git toolbar); the Options panel is registered by annotation in `ui/options/ACPOptionsPanelController.java`

### Phase 2: Server Process
4. [`manager/ProcessManager.java`](../src/main/java/github/anandb/netbeans/manager/ProcessManager.java) — Owns the ACP server subprocess and acts as the central request-dispatch hub; spawn itself is in `manager/ServerProcessLifecycle.startServer()`, launching the harness binary the user selected (19 supported)
5. [`support/BinaryResolver.java`](../src/main/java/github/anandb/netbeans/support/BinaryResolver.java) — Locates the binary on PATH
6. [`manager/AcpProtocolClient.java`](../src/main/java/github/anandb/netbeans/manager/AcpProtocolClient.java) — JSON-RPC over stdin/stdout, SSE read loop, pending request tracking

### Phase 3: Session Management
7. [`manager/SessionManager.java`](../src/main/java/github/anandb/netbeans/manager/SessionManager.java) — Session CRUD, state machine, SSE routing
8. [`manager/SessionStateMachine.java`](../src/main/java/github/anandb/netbeans/manager/SessionStateMachine.java) — Finite-state machine for session lifecycle
9. [`model/Session.java`](../src/main/java/github/anandb/netbeans/model/Session.java) — Session data record
10. [`model/SessionUpdate.java`](../src/main/java/github/anandb/netbeans/model/SessionUpdate.java) — SSE notification payload model
11. [`model/Message.java`](../src/main/java/github/anandb/netbeans/model/Message.java) — Message model (prompts, tool calls, results)
12. [`manager/PreambleSender.java`](../src/main/java/github/anandb/netbeans/manager/PreambleSender.java) — Posts the startup preamble for a new session, one chunk at a time

### Phase 4: Strategy Dispatch (SSE handler chain)
13. [`contract/UIHandler.java`](../src/main/java/github/anandb/netbeans/contract/UIHandler.java) — Callback interface for rendering
14. [`manager/strategy/StrategyRegistry.java`](../src/main/java/github/anandb/netbeans/manager/strategy/StrategyRegistry.java) — Sole dispatch class: type switch routes `SessionUpdate` → extraction logic, eliminating the strategy interface hierarchy

### Phase 5: UI Rendering
15. [`ui/assistant/AssistantTopComponent.java`](../src/main/java/github/anandb/netbeans/ui/assistant/AssistantTopComponent.java) — Main chat window (NetBeans TopComponent)
16. [`ui/assistant/ComponentLifecycleHandler.java`](../src/main/java/github/anandb/netbeans/ui/assistant/ComponentLifecycleHandler.java) — Wires lifecycle events → managers
17. [`ui/session/SessionLifecycleHandler.java`](../src/main/java/github/anandb/netbeans/ui/session/SessionLifecycleHandler.java) — Glue: receives SSE updates, calls `StrategyRegistry.handle()`, invokes UI
18. [`ui/chat/ChatThreadPanel.java`](../src/main/java/github/anandb/netbeans/ui/chat/ChatThreadPanel.java) — Thread of message bubbles with streaming animation
19. [`ui/chat/MessageBubble.java`](../src/main/java/github/anandb/netbeans/ui/chat/MessageBubble.java) — Individual message turn (thought, tool, code segments)
20. [`ui/session/MessageSender.java`](../src/main/java/github/anandb/netbeans/ui/session/MessageSender.java) — Send/cancel logic (mid-turn steering, queueing, local echo)
21. [`ui/session/StatusController.java`](../src/main/java/github/anandb/netbeans/ui/session/StatusController.java) — Status label, Go/Stop/Thinking animation, composer enablement and the new-session config-gate lock

### Phase 6: Supporting
22. [`model/ProcessedMessage.java`](../src/main/java/github/anandb/netbeans/model/ProcessedMessage.java) — The rendered output model consumed by UI
23. [`mcp/McpManager.java`](../src/main/java/github/anandb/netbeans/mcp/McpManager.java) — MCP server integration layer
24. [`contract/RequestHandler.java`](../src/main/java/github/anandb/netbeans/manager/RequestHandler.java) — Interface for incoming RPC requests from the server

## System Properties

The plugin reads the following system properties and environment variables:

| Property | System | Description |
|---|---|---|
| `user.dir` | System | **Not used.** `SessionManager` deliberately fails fast instead of falling back to the IDE launcher directory (`createSession` requires an explicit project `cwd`) |
| `user.home` | System | Default folder for Markdown Project creation (`MdProjectPanelVisual`) |
| `java.io.tmpdir` | System | Temp directory for pasted images (`ImagePasteTransferHandler`) |
| `os.name` | System | Detect Windows for binary resolution and platform-specific launching (`BinaryResolver`, `ProcessTerminator`, `ACPOptionsPanel`) |
| `beanbot.roundedPanels` | System (`true`) | Toggle rounded panel corners (`RoundedPanel`) |
| `beanbot.color.*` | System (varies) | Override any UI color. Read by `model/ColorRegistry` (resolution order: system property → Options setting → `UIManager` key → built-in light/dark fallback), not by `ColorTheme`, which only loads `colors.json`. The four bubble backgrounds have Options settings under *BeanBot → Appearance* |
| `beanbot.font.family` | System | Chat font family for all chat text (`ui/components/UIUtils`, `ui/rendering/IconResourceManager`); the *BeanBot → Appearance → Chat font family* setting is used when the property is absent |
| `beanbot.update.url` | System | Override the update metadata URL fetched by the update checker (`support/AppLinks`); defaults to `https://repo1.maven.org/maven2/io/github/anandb/beanbot/maven-metadata.xml` — lets a fork or self-hosted deployment serve its own `maven-metadata.xml` |
| `beanbot.nbm.base` | System | Base for the "Download Now" link, default `https://repo1.maven.org/maven2/io/github/anandb/beanbot`; the checker appends `/<version>/beanbot-<version>.nbm`, so it does not guess release-asset URLs |
| `beanbot.changelog.url` | System | Target of the "View Release Notes" link, default `https://github.com/anandb/nb-complete/blob/main/CHANGELOG.md` — points at the changelog so the link works before a release is tagged |
| `beanbot.support.url` | System | Funding link behind the sprouts icon on the composer toolbar (`support/AppLinks`); defaults to `https://buymeacoffee.com/anandb`, so a fork can point at its own page |
| `beanbot.issues.url` | System | Issue tracker behind Help ▸ Report an Issue (`support/AppLinks`); defaults to `https://github.com/anandb/nb-complete/issues/` |
| `beanbot.user.guide.url` | System | Override the user-guide URL opened from the help menu when the bundled guide cannot be served (`support/AppLinks`); unset means no remote fallback |
| `nb.dark.theme` | UIManager (Boolean) | Boolean dark-theme flag used for icon resolution (`IconResourceManager`); when absent, theme is inferred from `Panel.background` luminance |
| `ACP_WIRE_LOG` | Env | Path template for the ACP wire-protocol log (`WireLogger`). The file written is `<template-without-extension>_<YYYYMMDD>-<harness>.log` in the template's directory, so successive days and harnesses do not share one file. Every entry is one line: `<local time, no time zone> <json message>` |
| `PATH` | Env | Search path for the harness binary; every catalogued harness binary name is probed (`BinaryResolver`) |

The color properties are declared in [`colors.json`](../src/main/resources/github/anandb/netbeans/ui/rendering/colors.json) and cover: background, foreground, selection, accent, sunken background, bubble (user/assistant), code, table, header, thinking, tool, permission, and error colors. Most entries define both light and dark variants; the ones resolved straight from a `UIManager` key or a single fallback (`foreground`, `sunkenBackground`, `codeBackground`, `codeForeground`, `codeSelection`) do not.

## Contributing

Development follows standard NetBeans Platform patterns. Keep changes consistent with existing
styling and logging conventions, and validate new components against both light and dark IDE themes.
See [AGENTS.md](../AGENTS.md) for the project's coding rules and architectural constraints.
