# Grok Specific Rules

These rules apply to Grok Build (`grok agent stdio`). They are appended to the universal critical rules for this harness only.

- **Prefer file tools over shell for edits:** Modify source files with Grok's file tools so each change is reviewable; the shell is for builds, tests, and VCS.
- **Queued messages:** a message sent while a turn is running is held by BeanBot until the turn ends; Grok rejects a second `session/prompt` mid-turn.
- **Scoped search:** Restrict search commands to the project workspace — never run them on `/` or the home directory.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
