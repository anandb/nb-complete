# Quickstart Guide

[![Support this project](https://img.shields.io/badge/OpenCode-Referral-FF6C37?style=flat&logo=openai&logoColor=white)](https://opencode.ai/go?ref=DWTNHGN9KX)

> **⚠ v1.7.3 Breaking Changes** — The ACP Next protocol is now the default. Session config
> payloads have been restructured and model variants are split into a separate `thought_level`
> option. See [NOTES.md](NOTES.md) for full details.

This document covers features available in the latest release. The plugin can be downloaded from
**Maven Central** or built from source. Due to the release cadence, there may be a delay before
new versions appear on the NetBeans plugin portal.

---

## Setup

1. **Prerequisites** — Install [OpenCode](https://opencode.ai/), then connect and configure your
   providers. This plugin acts as a client to an OpenCode process running as an agent server.
   On Wayland, install the `wl-clipboard` package for image paste support.
2. **Installation** — Download the `.nbm` file from
   [Maven Central](https://central.sonatype.com/artifact/io.github.anandb/beanbot/versions). In
   NetBeans, navigate to `Tools > Plugins > Downloaded` and add the file.
3. **Access** — Open the interface via `Window > Assistant` or the keyboard shortcut `Ctrl + L`.
4. **Configuration** — The plugin auto-detects the OpenCode binary on your system `PATH`. To
   override, set the path manually under `Options > Assistant`.
5. **Preamble** — Define a global system prompt in `Options > Assistant > Preamble`. This is
   prepended to every new session alongside your OpenCode agent prompts.

---

## Features

### Keyboard & Navigation

- **Ctrl + L** — Open or switch focus to the assistant panel.
- **Tab** — Cycle between agents defined in your OpenCode configuration.
- **Alt + Up / Alt + Down** — Cycle through previously sent messages.
- **Ctrl + R** — Search message history.
- **Page Up / Page Down** — Scroll the chat thread.
- **Ctrl + Home / Ctrl + End** — Jump to top or bottom of the chat thread.
- **Escape** — Close the options panel and return focus to the input area.
- **?** toolbar button — Open this quickstart guide.
- **Keyboard** toolbar button — Open the keyboard shortcuts reference dialog.

### Input Area

- **Enter** — Send message. **Shift + Enter** — Insert newline.
- **/** — Trigger autocomplete popup with commands, agents, and mentions.
- **Paperclip** — Open attachment menu. Drag-and-drop and screenshot paste also supported (requires a vision-capable model).
- **Stop** — Cancel an in-progress response (the AI is notified immediately).

### Slash Commands

| Command | Action |
|---------|--------|
| `/new` | Creates a new session |
| `/models` | Opens the model selector dropdown |
| `/agents` | Opens the agent selector dropdown |
| `/level` | Opens the thinking level selector dropdown |
| `/sessions` | Opens the session switcher dropdown |
| `/compact` | Context compression (summarizes conversation) |
| `/title` | AI suggests a session title and renames it |

### Model & Command Control

- **Settings button** — Toggle the inline options panel to choose model and thinking budget.
- **Copy model name** — Copy the exact model identifier to clipboard.
- **Filter** — Hide tool output or "thinking" message types.
- **Expand/Collapse All** — Unfold or fold all thought/tool bubbles at once.

### Message Display

- **Code Blocks** — Syntax highlighting, line numbers, copy/insert buttons, language label.
- **Tool Output** — Collapsible panes with tag icon and language label.
- **Thinking Process** — Reasoning chain in its own collapsible pane.
- **Tables** — ASCII tables rendered as formatted HTML with alternating row colors.
- **Permission Requests** — Accept/Deny bubble with full context when the AI wants to execute a tool.
- **Error Messages** — Red-tinted bubbles for server and connection errors.

### Message Management

- **Copy** — Hover a user message and click the icon to restore its text to the input area.
- **Pencil icon** — Rename a session. Mappings are stored locally.
- **Export** — Save the full session transcript as Markdown.
- **Pin** — Keep older messages visible indefinitely (they're otherwise evicted to conserve memory).
- **Right-click toolbar** — Show/hide individual toolbar buttons.

### Session Management

- **Status Bar** — Shows `Ready`, `Thinking...`, `Responding...`, or `Error` with details.
- **Reload** — Fetch latest message history from the server without clearing local state.
- **Session Dropdown** — Switch between active sessions.
- **Right-click dropdown** — Quick access to Rename, Archive/Unarchive, Reload.
- **Archive** — Hide a session from the dropdown without deleting it from the server. Toggle archived visibility with the eye icon.
- **Auto-Open** — Assistant opens automatically on first install or version change.

### Settings (`Tools > Options > Assistant`)

- **OpenCode Binary Path** — Auto-detected from `PATH`; override here if needed.
- **Process Arguments** — Additional command-line arguments passed to the OpenCode process.
- **Preamble** — Global system prompt prepended to every new session.
- **Local Echo** — Show your message instantly in the chat panel before the server responds.
- **Session Idle Timeout** — Minutes of inactivity before the connection is re-established.
- **User Icon** — Set a custom avatar; right-click the preview to clear it.

### Media & Troubleshooting

- **Custom Icon** — Set a custom user avatar in `Options > Assistant`.
- **Restart Server** — Click **Restart ACP Server** in the toolbar if the assistant becomes unresponsive.
- **Version Control** — Keep your project under git or similar before allowing the LLM to modify files.

---

## Free Models

No subscription? Some models are available for free via the OpenCode provider — no API key required.
Select from the model dropdown. See [OpenCode Privacy](https://opencode.ai/docs/zen/#privacy) for data handling details.

---

Sign up for [OpenCode Go](https://opencode.ai/go?ref=DWTNHGN9KX) 🚀
