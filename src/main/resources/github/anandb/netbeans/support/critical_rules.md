# Critical Rules

These rules are required for correct functioning and must never be violated. Maintain these operational constraints across all subsequent turns.

- **Parallelism** - Prefer frequent feedback over massive parallelism; do not execute broad concurrent tasks without intermediate check-ins.
- **No write/refactoring sub-agents:** Never spawn sub-agents for write or refactoring operations — permission requests do not bubble up from sub-agents, so the user cannot review or approve changes.
- **External directory exploration:** Ask the user for permission **before** spawning sub-agents that access or explore directories outside the project workspace.
- **Versioning Operations:** NEVER automatically commit or push code. Obtain explicit permission for *every* individual commit/push.
- **Delete Operations:** NEVER automatically delete files or directories. Obtain explicit permission for *every* individual deletion.
- **Destructive Operations:** NEVER force-push, delete branches, or overwrite files outside the task scope without explicit permission.
- **Per-hunk permission requests:** Send each individual edit as a separate tool call. Do not batch multiple changes to the same file into a single call. This lets the user approve/reject each hunk independently via the permission panel.
- **Only single edits at a time:** Do not make multiple edits in parallel, do them sequentially so the user has the chance to review each one of them.
- **File search:** Prefer the `find` tool over broad `glob` patterns (e.g. `**/*.java`) in large repositories — unbounded recursive globs can return thousands of results and waste context tokens. Use targeted patterns or restrict to specific subdirectories. Never run `find` on `/` or `~` (home) — always scope to the project workspace and use a depth limit.
- **Keep alive before long work:** Before starting a multi-step task expected to take 30s+ (builds, test suites, multi-file research), send a brief acknowledgment first. This resets the connection idle timer and prevents server-side timeout.
- **Break large outputs into sequential chunks:** For very large responses (multiple files, long code generation), send content in incremental sequential outputs rather than one giant stream. This keeps the connection active and avoids buffer overload.
- **Use direct tools for simple operations:** Reserve sub-agents for genuinely complex multi-file research. For single-file reads, simple searches, or straightforward edits, use the available tools directly — sub-agent spawning adds unnecessary round-trip latency.
- **Permission-requiring operations in primary agent only:** Any operation that requires user permission (commits, pushes, deletes, destructive changes, file writes outside task scope) MUST run in the primary agent context. Never delegate permission-gated operations to sub-agents — the permission panel cannot intercept sub-agent tool calls, so the user loses the ability to review and approve/reject changes before they execute.
