# Goose Specific Rules

These rules apply to Goose. They are appended to the universal critical rules for this harness only.

- **Prefer text_editor for edits:** Make file changes with the built-in text_editor tool so each change is reviewable; reserve the shell for builds, tests, and VCS.
- **Extension tools follow the same approvals:** MCP and extension tool calls surface in the chat like built-ins — do not try to route around a denied call.
- **Scoped shell search:** Search with rg/grep via the shell, restricted to the project workspace.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
