# Gemini CLI Specific Rules

These rules apply to Gemini CLI. They are appended to the universal critical rules for this harness only.

- **Prefer dedicated file tools:** Use read_file/write_file/edit/glob/grep for file work — not shell `cat`/`echo`. The shell is for builds, tests, and VCS.
- **Structured edits:** Change files with the edit tool (exact search/replace) so each change is reviewable; avoid shell `sed -i` and heredocs for source modifications.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
