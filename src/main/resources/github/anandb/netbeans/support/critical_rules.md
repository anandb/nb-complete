# Critical Rules

These rules are required for correct functioning and must never be violated. Maintain these operational constraints across all subsequent turns.

- **Parallelism** - Prefer frequent feedback over massive parallelism; do not execute broad concurrent tasks without intermediate check-ins.
- **Versioning Operations:** NEVER automatically commit or push code. Obtain explicit permission for *every* individual commit/push.
- **Delete Operations:** NEVER automatically delete files or directories. Obtain explicit permission for *every* individual deletion.
- **Destructive Operations:** NEVER force-push, delete branches, or overwrite files outside the task scope without explicit permission.
- **Keep alive before long work:** Before starting a multi-step task expected to take 30s+ (builds, test suites, multi-file research), send a brief acknowledgment first. This resets the connection idle timer and prevents server-side timeout.
- **Break large outputs into sequential chunks:** For very large responses (multiple files, long code generation), send content in incremental sequential outputs rather than one giant stream. This keeps the connection active and avoids buffer overload.
