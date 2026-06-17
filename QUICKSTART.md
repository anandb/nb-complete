# Quickstart Guide

[![Support this project](https://img.shields.io/badge/OpenCode-Referral-FF6C37?style=flat&logo=openai&logoColor=white)](https://opencode.ai/go?ref=DWTNHGN9KX)

This document covers features available in the latest release. The plugin can be downloaded from
**Maven Central** or built from source. Due to the release cadence, there may be a delay before
new versions appear on the NetBeans plugin portal.

---

## Table of Contents

- [Setup](#setup)
- [Free Models](#free-models)
- [Keyboard & Navigation](#keyboard--navigation)
- [Input Area](#input-area)
- [Slash Commands](#slash-commands)
- [Model & Command Control](#model--command-control)
- [Message Display](#message-display)
- [Message Management](#message-management)
- [Session Management](#session-management)
- [Settings](#settings)
- [Media & Troubleshooting](#media--troubleshooting)

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

## Free Models

If you don't have an OpenAI, Gemini or other subscription, the following models are available for
free at this time (28-May-2026) via the OpenCode provider, and might be worth trying.

| Model | ID | Notes |
|-------|----|-------|
| **Big Pickle** | `opencode/big-pickle` | General-purpose reasoning model |
| **Nemotron 3 Super Free** | `opencode/nemotron-3-super-free` | NVIDIA's free-tier model |
| **DeepSeek V4 Flash Free** | `opencode/deepseek-v4-flash-free` | Fast, lightweight model for quick tasks |
| **Mimo V2.5 Free** | `opencode/mimo-v2.5-free` | Vision-capable model |

These can be selected from the model dropdown in the options panel. No API key is required for
these models — they are provided as part of the free OpenCode tier.

---

## Keyboard & Navigation

- **Ctrl + L** — Open or switch focus to the assistant panel.
- **Tab** — Cycle between agents (e.g., `build`, `plan`) defined in your OpenCode configuration.
  Each agent can be bound to a different model, letting you route low-level shell execution to a
  cheaper model while reserving reasoning capacity for planning.
- **Page Up / Page Down** — Scroll the chat thread one message bubble at a time.
- **Ctrl + Home / Ctrl + End** — Jump to the top or bottom of the chat thread.
- **Escape** — If the options panel is open, closes it and returns focus to the input area.
- **Ctrl + W** — Intercepted to prevent the assistant panel from closing accidentally when it has
  keyboard focus.

---

## Input Area

- **Send** — Click the **send** button or press **Enter** to send the current message.
- **Stop** — While the assistant is responding, a **stop** button replaces the send button. Click
  it to cancel the response (the AI is notified immediately).
- **Newline** — Press **Shift + Enter** to insert a line break without sending.
- **History** — Press **Up Arrow** / **Down Arrow** to cycle through previously sent messages.
- **Autocomplete** — Type `/` to trigger a popup with available commands, agents, and
  mention suggestions.
- **Attachments** — Click the **paperclip** icon to open an attachment menu with file checkboxes
  and a **Select File...** browser. You can also drag and drop files or paste screenshots directly
  into the input area (requires a vision-capable model).

---

## Slash Commands

Typing `/` opens a popup listing available commands and skills. The following built-in commands are
handled locally:

| Command | Action |
|---------|--------|
| `/new` | Creates a new session |
| `/models` | Opens the model selector dropdown |
| `/agents` | Opens the agent selector dropdown |
| `/level` | Opens the thinking level selector dropdown |
| `/sessions` | Opens the session switcher dropdown |
| `/compact` | Sends a context compression signal to the server (summarizes conversation) |
| `/dcp` | Dynamic Context Pruning - [another compression signal](https://github.com/Opencode-DCP/opencode-dynamic-context-pruning), needs the DCP Skill |
| `/title` | Prompts the AI to suggest a session title and rename it via the `nb_rename_session` tool |

Skills (e.g., `plan`, `explore`) are delegated to the OpenCode agent.

---

## Model & Command Control

- **Model Selection** — Choose the active model and thinking budget from the dropdown in the inline
  options panel (toggle via the **settings** button). The **copy button** next to the model name
  copies the exact identifier to your clipboard for use in prompts or configuration.
- **Toggle Views** — Use the **Filter** button to hide tool output or "thinking" message types,
  reducing visual clutter.
- **Expand Thoughts** — Only the most recent thought bubble is expanded by default. Use the
  **Expand/Collapse** toolbar button to unfold all bubbles at once.

---

## Message Display

The assistant panel renders responses with structured formatting:

- **Code Blocks** — Code snippets are displayed with syntax highlighting, line numbers, and **copy**
  / **insert** buttons. The language label is shown in the header.
- **Tool Output** — Tool results (shell commands, file reads/writes, search results) appear in
  collapsible panes with a tag icon and language label.
- **Thinking Process** — The model's reasoning chain is shown in its own collapsible pane.
- **Tables** — Pipe-delimited ASCII tables in responses are rendered as formatted HTML tables with
  alternating row colors.
- **Permission Requests** — When the AI wants to execute a tool (read a file, run a command), a
  permission bubble appears with Accept/Deny buttons and full context (file path, shell command).
- **Error Messages** — Server and connection errors are displayed in red-tinted bubbles.

---

## Message Management

- **Resend Messages** — Hover over a user message and click the **Copy** icon to restore its text
  to the input area for editing or resending.
- **Rename Sessions** — Click the **Pencil** icon next to the session name to label a conversation.
  Name-to-session mappings are stored locally in the NetBeans user directory.
- **Export** — Save the full session transcript (including tool calls and thinking logs) as a
  **Markdown** file via the **Export** toolbar button.
- **Pin Messages** — Older messages are gradually evicted from the chat panel to conserve memory
  (they remain in session history). Use the **Pin** button to keep important messages visible
  indefinitely.

---

## Session Management

- **Status Bar** — The header shows the current connection status: `Ready`, `Thinking...`,
  `Responding...`, or `Error` with details.
- **Reload** — The **Reload Conversation** toolbar button fetches the latest message history from
  the server without clearing local state.
- **Session Dropdown** — Switch between active sessions from the dropdown in the header. Each
  session preserves its own message history independently.
- **Session Archiving** — The **Archive** button on the toolbar hides the current session from the
  dropdown without deleting it from the server. A **Show/Hide archived** toggle (eye icon) next to
  the dropdown controls visibility of archived sessions. The hidden flag is stored client-side only
  — it is not passed to the opencode server via ACP, so archiving is purely a local UI filter.
  The session remains fully intact on the server and can be unarchived at any time.
- **Auto-Open** — On first install or version change, the assistant opens automatically to help you
  get started.

---

## Settings

The settings panel is accessible via `Tools > Options > Assistant`. Key settings include:

- **OpenCode Binary Path** — Auto-detected from `PATH`; override here if needed.
- **Process Arguments** — Additional command-line arguments passed to the OpenCode process.
- **Preamble (System Prompt)** — A global prompt prepended to every new session.
- **Local Echo** — When enabled, your message appears instantly in the chat panel (before the
  server responds). Disabled by default.
- **Session Idle Timeout** — Minutes of inactivity before the connection is terminated and then re-established.
- **User Icon** — Set a custom avatar; the preview area shows the current icon and right-clicking clears it.

---

## Media & Troubleshooting

- **Custom Icon** — Set a custom user avatar in `Options > Assistant` to make your messages easier
  to identify while scrolling.
- **Restart Server** — If the assistant becomes unresponsive or the status indicator remains on
  "Ready" without responding, click **Restart ACP Server** in the toolbar to refresh the OpenCode
  process.
- **Version Control** — Keep your project under version control (git, mercurial, etc.) before
  allowing the LLM to modify files. This ensures you can restore content from an earlier revision if
  needed.

---

Sign up for [OpenCode Go](https://opencode.ai/go?ref=DWTNHGN9KX) 🚀