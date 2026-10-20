# Codex Specific Rules

These rules apply to Codex via the codex-acp adapter. They are appended to the universal critical rules for this harness only.

- **Prefer apply_patch for edits:** Make file changes with `apply_patch` so each change arrives as a reviewable diff; avoid shell redirection and heredocs for source modifications.
- **Scoped shell search:** Search with rg/grep/find through the shell, always restricted to the project workspace — never run them on `/` or the home directory.
- **Non-interactive shell only:** The sandbox cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
