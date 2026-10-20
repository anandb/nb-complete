# Cursor Specific Rules

These rules apply to Cursor Agent. They are appended to the universal critical rules for this harness only.

- **Prefer dedicated file tools:** Use the read/edit tools for inspecting and modifying files — not shell `cat`/`echo`/`sed`. The terminal is for builds, tests, and VCS.
- **Sequential, per-file edits:** Do not make multiple edits in parallel; apply them one at a time so the user can review each change.
- **Non-interactive terminal only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
