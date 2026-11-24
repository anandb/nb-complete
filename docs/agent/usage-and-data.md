# Usage, Tasks And Persistence

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: touching usage stats, the token panel, preferences migration, tasks or project events.

## Live Context Report (`_goose/unstable/context/report`)
- The Token panel's CONTEXT WINDOW block is the only place that reads the harness live:
  `TokenUsageDialog.readContextReport()` runs on the stats worker (never the EDT) and asks
  `contract/ContextReportQuery` → `SessionManager.reportContext` →
  `SessionRpcClient.reportContext`. Makes no model call; goose measures with its local tokenizer.
- `Harness.supportsContextReport()` (goose only) gates it, and `SessionManager` is registered
  under both `SessionControl` and `ContextReportQuery` via `@ServiceProviders` — NetBeans'
  `@ServiceProvider` is not repeatable in RELEASE220, so a second service needs the container.
- A failed or unsupported report yields `null`, and the block is simply absent: the panel's
  stored-history tables must never fail because the live RPC did. `providerManagesContext`
  (Claude Code, Gemini CLI) shows "Provider-managed" instead of an empty segment list, and
  `autoCompactionPending` is called out rather than looking like an oversized context.

## Usage Stats Store
`H2UsageStatsStore` is single-executor: all DB work stays off the caller/EDT. Keep pin/model caches
memory-backed for reads and persist deltas; preserve startup writes. Model lists expire omitted entries
one hour after the harness's latest report (prune only on a new report). Bump `SCHEMA_VERSION` and add
migrations for semantic changes; canonicalize model ids at attribution and never use a global model
fallback. Capture usage attribution when messages arrive.

Record tool usage per execution through `ToolCallRecorder`, attributed through `SessionQuery`; do not
add turn-end counters or drop calls with no session. Capture the ACP `fs/read_text_file` /
`fs/write_text_file` requests and external MCP executions from goose, codex-acp, opencode and omp
(recorded as `server/tool`) too, but never the internal `nb` server there — `McpTools` already
records it. On open failure, mark the store
unavailable and allow only the explicit user retry (at most two opens); do not auto-retry. Only an
absent schema-version row means legacy; invalid values fail safely. Catch executor rejection after
shutdown, and close the store from `ACPShutdown` (flush does not release the H2 lock).

SQL lives in `db/schema.sql` and named blocks in `db/queries.sql`; use `db/Sql` slots for controlled
identifiers and do not inline SQL or rebind slot-owned parameters.

## Preference Migration To The Store
`runPrefsMigration()` migrates history, session metadata, pins and model lists with separate markers.
Preserve ordering: write rows, write marker, then delete only successfully stored keys. Parse skips are
final; write failures leave markers unset for retry. Keep unreadable keys and report them without
blocking startup. Do not retry partial input-history inserts, which would duplicate rows.

## Project Events Are Coalesced

`OpenProjects` fires one property-change event per project on startup and on project-group
switches, so any listener that fans out or rescans must debounce. All three sites go through
`support/SlidingTask` (a named `RequestProcessor.Task` whose `schedule()` restarts the window):

- `manager/FileCacheManager` — `REBUILD_TASK` (`REBUILD_DELAY_MS` = 1000).
- `project/ACPProjectManager` — `syncTask` (`SYNC_DELAY_MS` = 250). `start()` still calls
  `syncActiveProject()` directly so startup readers see the project list immediately.
- `ui/platform/DefaultPlatformBridge.ProjectContextImpl` — `notifyTask` (`CHANGE_DELAY_MS` = 250);
  the fan-out still marshals to the EDT via one `invokeLater` per burst.

Do NOT reintroduce a bare `RP.post(...)` or `SwingUtilities.invokeLater(...)` per event in these
listeners — that queues one full run per event and is what issue #5 reported.

The same helper carries the other pre-existing sliding tasks: `ModelListStore` and
`PinnedMessageStore` prefs flushes, `FileCacheManager` rebuilds, and `TaskQueryProvider`'s
per-query reload/push slots. `UpdateCheckerService` is deliberately NOT one — it schedules a
variable-delay cycle and queries `isFinished()`, which `SlidingTask` does not model.

## Task Repository Toggle
- The `taskRepository.enabled` preference (default `true`) gates the entire todo.txt feature.
- **Two entry points are guarded** because the bugtracking framework calls
  `createRepository()` during its own `loadRepositories()` startup, which races with
  `TasksStartup`: `TasksStartup.run()` (skips `restoreFromMetadata()`) and
  `TasksConnector.createRepository(RepositoryInfo)` (returns `null` when disabled).
- The checkbox lives in Preferences > Advanced > IDE Extensions section.
- The preference key is `PreferenceKeys.TASK_REPOSITORY_ENABLED`; cached via
  `PluginSettings.isTaskRepositoryEnabled()`.

## Task Scheduler & Closed Tasks
- `TaskScheduleProvider.getSchedule()` and `getDueDate()` return `null` when
  `TaskRecord.isFinished()` is true (status == "closed"). This prevents the bugtracking
  framework from showing closed tasks in the Scheduled category or sending notifications.
- Do NOT remove the `isFinished()` guard — it is the only thing preventing stale
  notifications for completed tasks.
