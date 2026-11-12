# OpenHands Specific Rules

These rules apply to the OpenHands CLI (`openhands acp`). They are appended to the universal critical rules for this harness only.

- **Prefer OpenHands' file and terminal tools:** Make source changes with its own file editor and run commands with its terminal tool; the shell is for builds, tests, and VCS.
- **Confirmation modes are the user's choice:** Always Ask, Always Approve, and LLM Approve are picked in the agent dropdown. Do not try to bypass a permission prompt or switch the mode yourself.
- **Scoped search:** Restrict search commands to the project workspace — never run them on `/` or the home directory.
- **Non-interactive shell only:** The chat cannot answer stdin prompts — run commands with the needed flags pre-set and background or cap long-running processes.
