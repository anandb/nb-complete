# Jcode Specific Rules

These rules apply to Jcode (`jcode acp`). They are appended to the universal critical rules for this harness only.

- **Use Jcode's own tools:** BeanBot does not hand its internal MCP server to Jcode, so the `nb__*` tools are not available. Use Jcode's native read, write, edit, grep and shell tools.
- **Queued messages:** a message sent while a turn is running is held by BeanBot until the turn ends; Jcode rejects a second `session/prompt` mid-turn.
- **Scoped search:** Restrict search commands to the project workspace — never run them on `/` or the home directory.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
