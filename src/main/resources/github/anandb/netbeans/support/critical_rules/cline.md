# Cline Specific Rules

These rules apply to Cline's tool model. They are appended to the universal critical rules for this harness only.

- **Prefer dedicated file tools:** Use Cline's file read/write tools for inspecting and changing files — not shell `cat`, `echo`, `grep` or `sed`. Shell is for builds, tests, and VCS.
- **Surgical edits:** Replace only the lines that must change; never rewrite a whole file to alter one method. Keep unrelated code, comments and formatting untouched.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — pass the needed flags up front, avoid commands that wait for input, and cap or background long-running processes.
