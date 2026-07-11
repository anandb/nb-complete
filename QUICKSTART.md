# QuickStart Guide

**BeanBot/Coding Assistant** is a NetBeans plugin that provides an AI assistant panel powered by [OpenCode](https://opencode.ai/).
  It communicates with OpenCode over ACP (Agent Client Protocol), running it as a background server so you can chat
  with AI models directly inside the IDE.

## Table of Contents

- [Setup](#setup)
- [Features](#features)
  - [Keyboard & Navigation](#keyboard--navigation)
  - [Input Area](#input-area)
  - [Slash Commands](#slash-commands)
  - [Model & Command Control](#model--command-control)
  - [Message Management](#message-management)
  - [Markdown Project Type](#markdown-project-type)
  - [Stash Diff (Experimental)](#stash-diff-experimental)
  - [Session Management](#session-management)
  - [Settings](#settings)
  - [Media](#media)
- [Free Models](#free-models)
- [Known Issues](#known-issues)
- [Troubleshooting](#troubleshooting)

---

## Setup

### 1. Install OpenCode

Install [OpenCode Terminal](https://opencode.ai/download), then connect and configure your providers.
You can skip provider configuration if you only plan to use the free models. The plugin runs `opencode acp` under
the hood as a background server.

**Verify your installation:**

1. Open a terminal and navigate to your project directory.
2. Run `opencode .` to launch the OpenCode TUI (terminal-based interface).
3. Confirm you can connect and interact with your configured providers.
4. If the command is not found, add OpenCode to your `PATH` environment variable and restart the terminal.

On **Wayland**, also install the `wl-clipboard` package for image paste support.

### 2. Install the Plugin

Download the `.nbm` file from
[Maven Central](https://central.sonatype.com/artifact/io.github.anandb/beanbot/versions). In
NetBeans, navigate to `Tools > Plugins > Downloaded` and add the file.

### 3. Open the Assistant

Open the interface via `Window > Assistant` or the keyboard shortcut `Ctrl + L`.

### 4. Configure the Binary Path

The plugin auto-detects the OpenCode binary on your system `PATH`. To
override, set the path manually under `Options > Assistant`.

### 5. Set a Preamble (Optional)

Define a global system prompt in `Options > Assistant > Preamble`. This is
prepended to every new session alongside your OpenCode agent prompts.

---

## Features

### Keyboard & Navigation

| Shortcut | Action |
|----------|--------|
| `Ctrl + L` | Open or switch focus to the assistant panel |
| `Tab` | Cycle between agents defined in your OpenCode configuration |
| `Alt + Up / Alt + Down` | Cycle through previously sent messages |
| `Ctrl + R` | Search message history |
| `Page Up / Page Down` | Scroll the chat thread |
| `Ctrl + Home / Ctrl + End` | Jump to top or bottom of the chat thread |
| `Escape` | Close the options panel and return focus to the input area |

**Toolbar buttons:**

- **?** — Open this quickstart guide.
- **Keyboard** — Open the keyboard shortcuts reference dialog.

### Input Area

- **Enter** — Send message. **Shift + Enter** — Insert newline.
- **/** — Trigger autocomplete popup with commands, agents, and mentions.
- **Paperclip** — Open attachment menu. Drag-and-drop and screenshot paste are also supported.
- **Stop** — Cancel an in-progress response (the AI is notified immediately).

### Slash Commands

Type `/` in the input area to trigger the autocomplete popup.

| Command | Action |
|---------|--------|
| `/new` | Creates a new session |
| `/models` | Opens the model selector dropdown |
| `/agents` | Opens the agent selector dropdown |
| `/level` | Opens the thinking level selector dropdown (controls how much reasoning the model performs) |
| `/sessions` | Opens the session switcher dropdown |
| `/compact` | Summarizes the conversation so far and replaces the message history with a condensed version. Useful when you hit context limits. |
| `/title` | AI suggests a session title and renames it |

### Model & Command Control

- **Settings button** — Toggle the inline options panel to choose model and thinking budget (the thinking budget controls how many tokens the model can spend on internal reasoning before responding).
- **Copy model name** — Copy the exact model identifier to clipboard.
- **Filter** — Hide tool output or "thinking" message types from the chat view.
- **Expand/Collapse All** — Unfold or fold all thought/tool bubbles at once.

### Message Management

- **Copy** — Hover a user message and click the icon to restore its text to the input area.
- **Pencil icon** — Rename a session. Mappings are stored locally.
- **Export** — Save the full session transcript as Markdown.
- **Forget/Show All Messages** — Display-only: the session always holds all messages. **Show All Messages** keeps older messages visible in the chat panel. **Forget** progressively hides older messages from the local view to conserve memory (use **Reload** to re-fetch from the server).
- **Right-click toolbar** — Show/hide individual toolbar buttons.

### Markdown Project Type

- **Tools > New Project > Other > Markdown Project** — Creates a minimal project for folders containing markdown notes and text files. No build system or source roots. Detected by a `.mdproject` marker file. Shows full directory tree in the Projects tab with OS junk and editor swap files filtered out.
- **Right-click project node** — Close or delete the project directly from the Projects tab context menu.

### Stash Diff (Experimental)

Select a stash in the NetBeans Git Repository Browser and press `Ctrl+Shift+L`
or click the **Diff Stash** button in the Git toolbar to open a side-by-side
diff viewer.

| Shortcut | Action |
|----------|--------|
| `Ctrl+Shift+L` | Open stash diff viewer (assignable in Keymap) |
| `Ctrl + ,` | Previous difference |
| `Ctrl + .` | Next difference |

- **To Base** (default): Shows the stash parent commit vs stash content — the
  same as `git stash show -p`. Left panel header displays `Base (short-hash)`.
- **To HEAD**: Simulates applying the stash onto the current HEAD via 3-way
  merge. Shows the merge result with conflict markers inline. If a conflict
  exists, the right panel label shows "Conflict" in red.
- **To Working Tree**: Simulates applying the stash onto the working tree.
  Left panel shows actual uncommitted status ("Unchanged in tree" or
  "Modified in tree") instead of the stash change type.
- **File list**: Click a file to view its diff. Full file contents are shown
  with syntax highlighting. Right-click a file to "Apply this change"
  individually from the stash.

The stash diff tab does not persist across IDE restarts.

### Session Management

- **Status Bar** — Shows `Ready`, `Thinking...`, `Responding...`, or `Error` with details.
- **Reload** — Fetch latest message history from the server without clearing local state.
- **Session Dropdown** — Switch between active sessions.
- **Right-click dropdown** — Quick access to Rename, Archive/Unarchive, Reload.
- **Archive** — Hide a session from the dropdown without deleting it from the server. Toggle archived visibility with the eye icon.
- **Auto-Open** — The assistant panel opens automatically on first install or when the plugin version changes.

### Settings (`Tools > Options > Assistant`)

- **OpenCode Binary Path** — Auto-detected from `PATH`; override here if needed.
- **Process Arguments** — Additional command-line arguments passed to the OpenCode process.
- **Preamble** — Global system prompt prepended to every new session.
- **Local Echo** — Show your message instantly in the chat panel before the server responds.
- **Session Idle Timeout** — Seconds of inactivity before the plugin disconnects and reconnects to the OpenCode server.
- **Toolbar Icon Size** — Adjust toolbar and action button icon size (16/24/28/32/36/40/48) in Options > Assistant > Appearance. Requires IDE restart.
- **Chat Font Size** — Adjust chat bubble font size (Inherited / 10–16) in Options > Assistant > Appearance. Default is "Inherited" (theme font size − 2).
- **User Icon** — Set a custom avatar displayed next to your messages; right-click the preview to clear it.

### Media

- **Attachments** — Click the paperclip icon to attach files. Drag-and-drop and screenshot paste are also supported (requires a vision-capable model).
- **Code Blocks** — Rendered with syntax highlighting, line numbers, copy/insert buttons, and a language label.
- **Tool Output** — Displayed in collapsible panes with a tag icon and language label.
- **Thinking Process** — The model's reasoning chain is shown in its own collapsible pane.
- **Tables** — ASCII tables are rendered as formatted HTML with alternating row colors.
- **Permission Requests** — An Accept/Deny bubble appears with full context when the AI wants to execute a tool.
- **Error Messages** — Red-tinted bubbles for server and connection errors.

---

## Free Models

No subscription? Some models are available for free via the OpenCode provider — no API key required, the free model list
also changes from time to time, try 'Big Pickle' or 'DeepSeek V4 Flash Free', you can select them from the model
dropdown. See [OpenCode Privacy](https://opencode.ai/docs/zen/#privacy) for data handling details.

---

## Known Issues

- The plugin sometimes doesn't respond when using nested agents.
- Switching sessions or reloading the conversation while awaiting a response can cancel the current request.
- Permission requests aren't always relayed by the ACP server, so while the server is waiting for permission, the
messages can timeout, this can be mitigated to some extent by allowing external read operations. This is a sample
snippet from an `opencode.json` configuration.
```json
{
  "permission": {
    "read": {
      "~/.ssh/**": "deny",
      "~/.gnupg/**": "deny",
      "~/.aws/**": "deny",
      "~/.azure/**": "deny",
      "~/.kube/**": "deny",
      "~/.docker/**": "deny",
      "~/.config/gcloud/**": "deny",
      ".env*": "deny",
      "*.pem": "deny",
      "*.key": "deny",
      "*.p12": "deny",
      "*.jks": "deny",
      "*credentials*": "deny",
      "*": "allow"
    },
    "edit": {
      "~/.aws/**": "deny",
      "~/.azure/**": "deny",
      "~/.ssh/**": "deny",
      "~/.gnupg/**": "deny",
      "~/.kube/**": "deny",
      "~/.docker/**": "deny",
      "~/.config/gcloud/**": "deny"
    },
    "bash": {
      "git push*": "ask",
      "rm *credentials*": "deny",
      "rm *.env*": "deny"
    },
    "webfetch": "allow",
    "external_directory": "allow"
  }
}
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Plugin can't find OpenCode | Ensure `opencode` is on your `PATH`. Set the binary path manually under `Options > Assistant`. |
| Assistant becomes unresponsive | Click **Restart ACP Server** in the toolbar. |
| Sidebar goes blank after dock position change | Close and reopen the assistant panel (Choose Assistant from the Window Menu) to restore it. |
| `Ctrl+L` stops working | Close and reopen the assistant panel from the Window menu. If that doesn't work, restart the IDE. |
| Sidebar doesn't open after install/upgrade | The plugin auto-opens the sidebar on version change. If it doesn't appear, open it from `Window > Assistant`. |
| Image paste doesn't work on Linux | Install the `wl-clipboard` package (Wayland) or check your clipboard manager. |
| Image paste broken after OpenCode upgrade | Upgrade to OpenCode >= 1.17.17 to resolve the breakage introduced in v1.17.13. |
| Model not appearing after upgrade to OpenCode v1.17.9, upgrade plugin to >= 1.7.3 | Re-select your model via `/models`. The upgrade resets model selection due to the new `thought_level` split. |
| Session config payloads restructured after upgrade | Upgrade the plugin to >= 1.7.3 and OpenCode to >= 1.17.17. Re-select your model and review any custom preamble or session settings. |
| Messages disappear from view | This is display-only — the session still has all messages. Click **Show All Messages** to keep them visible, and use **Reload** to re-fetch from the server. |
| LLM modified files unexpectedly | Always keep your project under version control (git) before allowing file modifications. Use **Accept/Deny** permission prompts to review changes. |

---

Support the project: [Sign up for OpenCode Go](https://opencode.ai/go?ref=DWTNHGN9KX) 🚀
