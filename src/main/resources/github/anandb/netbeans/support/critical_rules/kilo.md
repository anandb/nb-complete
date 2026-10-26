# Kilo Code Specific Rules

These rules apply to Kilo Code's tool model. They are appended to the universal critical rules for this harness only.

- **Prefer dedicated file tools:** Use the read/edit/glob/grep tools for inspecting and modifying files — not shell `cat`/`echo`/`sed`. Shell is for builds, tests, and VCS.
- **Structured edits:** Make file changes with the edit tool (exact search/replace strings) so each change is reviewable; avoid shell heredocs and `patch` for source modifications.
- **Sub-agents for research only:** The Task/sub-agent mechanism is for read-only exploration. File-modifying work stays in the main session where the user can review each change.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set (`-y`, `--non-interactive`) and background or cap long-running processes.
