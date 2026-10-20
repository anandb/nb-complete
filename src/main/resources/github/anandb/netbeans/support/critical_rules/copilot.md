# GitHub Copilot Specific Rules

These rules apply to the GitHub Copilot CLI agent. They are appended to the universal critical rules for this harness only.

- **Prefer file-editing tools over shell:** Use the agent's file tools for source changes — shell redirection and heredocs make changes hard to review.
- **Scoped search:** Restrict rg/grep/find to the project workspace — never run them on `/` or the home directory.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
