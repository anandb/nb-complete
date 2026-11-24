# MCP And File Access

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: adding or changing MCP tools, file guards, ignore files or run_command policy.

## File Access Policy Is Shared By ACP And MCP Tools
- `FileAccessGuard.check(...)` is the shared ACP/MCP enforcement point. `ALLOW` reads/writes within
  the project; `READ_ANY_WRITE_PROJECT` reads anywhere but writes only within; `READ_ONLY` allows
  project reads and rejects writes. Missing ACP session directory denies access.
- Every ACP and MCP filesystem path must use `FileAccessGuard`; MCP filesystem/git/hg tools also use
  `ProjectPathGuard`. Containment uses canonical paths with separator boundaries; never use bare
  `startsWith`. `search_project` stays limited to open projects. `run_command` is separately guarded
  by working directory and `RunCommandPolicy`, not the file policy.
- The guard denies credentials on reads and writes and `.git`, `.hg`, `.mvn` writes. Extend
  `SecretPaths`/`ProtectedPaths` rather than bypassing checks. The harness permission prompt remains
  active; this is an additional IDE-side policy, not a sandbox.
- Agent-driven file access must not steal focus. Open/write in the background; use `FOCUS` only for
  an explicit user navigation request. `EditorCookie.openDocument()` is suitable for background
  writes; do not activate editors through `open()`, `requestActive()` or equivalent.

## fs/read_text_file Line Windows
- `AcpRequestRouter.selectLines(content, startLine, limit)` slices content from both the editor document and
  disk. `line` is 1-based (absent → 1, clamped to ≥1); `limit` absent means to EOF, and a non-positive limit
  returns an empty string. The slice keeps the trailing newline of the last selected line; a start line past
  EOF returns empty. The whole file is read before slicing — do not "optimize" that without preserving CRLF
  and trailing-newline semantics.

## .bbignore Display Policy
- `manager/BbIgnoreStrategy` implements `VcsIgnoreStrategy` over a project-root `.bbignore` (gitignore syntax).
  When the file exists it replaces `.gitignore`/`.hgignore` entirely; `FileCacheManager.detectStrategy` checks
  it first.
- An empty `.bbignore` listing is authoritative: `scanProject` must NOT fall back to `walkFileSystem` for
  `BbIgnoreStrategy`, otherwise an exclude-everything policy resurrects every file.
- Invalid patterns (bad character class) are skipped per line and must never abort the scan. Containment uses a
  separator boundary, so a sibling root sharing a string prefix is outside the project.

## MCP Tools

- Keep MCP tool names short. OpenHands must not receive the internal MCP server until its adapter
  accepts the expected headers shape.
- `Harness.acceptsMcpAuthToken()` controls MCP URL authentication; resolve the harness before
  `toolExecutor.start()`. Never hand authenticated MCP URLs to Pi adapter or Jcode sessions.
  `ServerProcessLifecycle.requiresMcpAuthToken()` reads only that catalog flag — do not add
  binary-name exceptions or sets in `BinaryResolver`; the YAML `binaryNames` already drive harness
  resolution.
- Every filesystem/git/hg tool must use `ProjectPathGuard`/`FileAccessGuard`; never bypass
  `RunCommandPolicy.rejection`. `run_command` is confined by working directory and command policy.
- Return tool errors as `{status:"error", message:...}`; set `fatal:true` only for service failures,
  failed writes/updates, or timed-out/nonzero VCS commands. Do not throw for ordinary validation.
- When an empty `grep`/`rg` result is expected in harness shell calls, neutralize its exit code or
  use the command tool's separate `exitCode` field.
- For connected IDE tools, use `find_type`/`find_file` for indexed Java types and known filenames;
  other language searches use `search_project`.
