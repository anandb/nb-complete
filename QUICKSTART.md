# QuickStart Guide

**BeanBot/Coding Assistant** is a NetBeans plugin that provides an AI assistant panel powered by [OpenCode](https://opencode.ai/).
  It communicates with OpenCode over ACP (Agent Client Protocol), running it as a background server so you can chat
  with AI models directly inside the IDE.

## Table of Contents

- [Setup](#setup)
- [Features](#features)
  - [Starting a session](#starting-a-session)
  - [Picking an Agent, Model and Thinking Level](#picking-an-agent-model-and-thinking-level)
  - [Showing all messages](#showing-all-messages)
  - [Exporting a conversation](#exporting-a-conversation)
  - [Commands](#commands)
  - [Selecting toolbar buttons](#selecting-toolbar-buttons)
  - [Archiving Sessions](#archiving-sessions)
  - [Restarting the server](#restarting-the-server)
  - [Renaming the session](#renaming-the-session)
  - [Check token usage](#check-token-usage)
  - [Filtering Chat Messages](#filtering-chat-messages)
  - [Attachments](#attachments)
  - [Permission Requests](#permission-requests)
  - [Keyboard Shortcuts](#keyboard-shortcuts)
  - [Message History](#message-history)
  - [Chat Features](#chat-features)
    - [Time to first token](#time-to-first-token)
    - [Thinking and Tool Responses](#thinking-and-tool-responses)
  - [Assistant Options](#assistant-options)
  - [IDE Enhancements](#ide-enhancements)
- [Known Issues](#known-issues)
- [Troubleshooting](#troubleshooting)

---

## Setup

### 1. Install OpenCode

The plugin relies on the OpenCode CLI (`opencode`) running as a background server. You must install OpenCode on your system first. For complete and up-to-date installation instructions, please refer to the [OpenCode Documentation](https://opencode.ai/docs/).

**Installation Methods:**
- **macOS / Linux (Recommended):** Run the install script in your terminal:
  ```bash
  curl -fsSL https://opencode.ai/install | bash
  ```
- **NPM (Global):**
  ```bash
  npm install -g opencode-ai
  ```
- **Homebrew (macOS):**
  ```bash
  brew install anomalyco/tap/opencode
  ```
- **Windows:** It is highly recommended to run OpenCode inside **WSL (Windows Subsystem for Linux)** for the best performance and compatibility. You can also use Chocolatey (`choco install opencode`) or Scoop (`scoop install opencode`).

**Configure AI Providers:**
Once installed, you need to connect an AI provider (unless you only plan to use the free models).
1. Open a terminal and run `opencode auth login` or launch the TUI and type `/connect` to add your API keys.
2. If you want to use local models (like Ollama), ensure they are running and accessible on your local machine.

**Verify your installation:**

1. Open a terminal and navigate to your project directory.
2. Run `opencode .` to launch the OpenCode TUI (terminal-based interface).
3. Confirm you can interact with your configured providers (type `/models` to select a model).
4. If the `opencode` command is not found, ensure it was added to your system `PATH` and restart your terminal/IDE.

On **Wayland** (Linux), also install the `wl-clipboard` package for image paste support to work inside the plugin.

### 2. Install the Plugin

You can install the plugin directly from the NetBeans plugin catalogue by navigating to `Tools > Plugins > Available Plugins` and searching for it.

However, for the absolute latest version and newer releases, you can download the `.nbm` file from [Maven Central](https://central.sonatype.com/artifact/io.github.anandb/beanbot/versions). Once downloaded, navigate to `Tools > Plugins > Downloaded` in NetBeans and add the `.nbm` file manually.

### 3. Open the Assistant

Open the interface via `Window > Assistant` or the keyboard shortcut `Ctrl + L`.

### 4. Configure the Binary Path

The plugin auto-detects the OpenCode binary on your system `PATH`. To
override, set the path manually under `Options > Assistant`.

### 5. Set a Preamble (Optional)

Define a global prompt in `Options > Assistant > Preamble`. This is
added to every new session alongside your OpenCode agent prompts. An example
comes out of the box, you can change it to suit your needs.

---

## Features

### Starting a session

Click the '+' button on the toolbar to start a new session, this will initialize the chat interface and post the preamble to the OpenCode server (Tools > Options > Assistant > Preamble). The input text-area is at the bottom of the sidebar panel, you'll see placeholder text that says *Type Message Here*, type your messages and press `Enter` to send to the server, the response will be rendered as a chat bubble, if you need to send multi-line text, press `Shift + Enter` to add a newline to the text-area. You can also type `/new` in the input area to quickly start a new session.

![Toolbar](./screenshots/toolbar.png)

### Picking an Agent, Model and Thinking Level

Hitting `Tab` when in the input text-area or pressing the small gear icon, will slide open the chat options panel, by default you'll see two agents build and plan, OpenCode also allows custom agents to be defined, pick one of the models supported by the providers you have configured, and a suitable Thinking Level, the actual levels displayed depend on the exact model selected. The gear would have turned into a down arrow button, click the button to close the options panel. You can also use the `/agents`, `/models`, and `/level` slash commands to open these selectors.

![Options Panel](./screenshots/options_panel.png)

### Showing all messages

By default, older messages are gradually hidden from the chat panel to conserve memory, to always show all messages, click the 'Show All Messages' button on the toolbar, and then click Reload, the session will be reloaded but all the messages will be retained. If you wish to retain just one or two messages for quick reference such as a plan or to-do list, hovering over the response bubble will show a copy and a pin button, the pin will keep the message in the chat panel, it won't be cleared with time.

### Exporting a conversation

The share button on the toolbar will export the session to a markdown file, you can select individual user messages by hovering over the user icon, the icon will change into a copy button, clicking the copy button will place a copy in the clipboard and also add it to the input text-area, you can use this if you want to resend a message. Assistant responses can be copied by hovering over the bubble, you'll see a copy and a pin button, copy will copy to the clipboard.

### Commands

Slash commands let you perform quick actions directly from the chat input. Type `/` in the input area to trigger the autocomplete popup. You can use `/new` to create a new session, `/models` to choose a different AI model, `/agents` to select an agent, and `/level` to configure the thinking budget. Other commands include `/sessions` to switch between active chats, `/compact` to summarize and condense the message history when approaching context limits, and `/title` to have the AI suggest and apply a new name for the current session.

### Selecting toolbar buttons

You can customize your workspace by choosing which toolbar buttons are visible. Right-click anywhere on the toolbar to bring up a context menu where you can show or hide individual buttons. The toolbar provides quick access to actions like starting a session, viewing this guide, opening keyboard shortcuts, checking token usage, restarting the server, and exporting conversations. You can adjust the size of these icons in `Options > Assistant > Appearance`.

### Archiving Sessions

Keep your active session list clean by archiving old conversations instead of deleting them. You can archive a session by opening the session dropdown and right-clicking the session name, then selecting Archive. This hides it from the default dropdown list but keeps it saved on the server. You can toggle the visibility of archived sessions by clicking the eye icon in the session dropdown menu.

![Archive Toggle](./screenshots/archive_toggle.png)

### Restarting the server

If the assistant becomes unresponsive or you experience connection issues, you can restart the underlying OpenCode background server. Click the **Restart ACP Server** button in the toolbar. This will safely terminate the current background process and spin up a new one, reconnecting your IDE without losing your saved sessions.

### Renaming the session

To keep your conversations organized, you can rename any session. Click the **Pencil icon** in the chat UI or right-click a session in the session dropdown to give it a custom name. Alternatively, type the `/title` slash command to let the AI automatically generate and apply a suitable title based on the conversation context. These custom names are stored locally by the plugin, not in the OpenCode DB.

### Check token usage

You can monitor how many tokens your current conversation has consumed by clicking the Currency/Token Stats icon on the toolbar to keep track of your context window limits. Checking the token usage helps you decide when it might be necessary to use the `/compact` command to summarize the conversation and free up memory.

### Filtering Chat Messages

To reduce clutter in your chat history, you can selectively show or hide different message types using the **Filter** button on the toolbar. Clicking this button opens a dropdown menu allowing you to toggle the visibility of:
*   **Activity**: Hides or shows tool outputs and internal model reasoning chains together.
*   **Assistant**: Hides or shows the assistant's responses.
*   **User**: Hides or shows your own messages.

If the option to combine tools and thoughts is disabled in your settings, the menu will dynamically update to show individual checkboxes for **Tool** outputs and **Thought** reasoning. Your filter choices are instantly applied to the active chat thread and are automatically saved so they persist across NetBeans IDE restarts.

### Attachments

You can attach files and images to provide more context to the AI. Click the **Paperclip icon** in the input area to open the attachment menu. The plugin also supports dragging and dropping files directly into the chat, as well as pasting screenshots straight from your clipboard (provided you are using a vision-capable model).

### Permission Requests

When the AI attempts to execute a tool, modify files, or run shell commands, the plugin ensures you remain in control. An Accept/Deny bubble will appear in the chat with full context of the requested action. You must explicitly grant permission before the operation proceeds. You can configure automatic allowances or denials in your OpenCode configuration.

### Keyboard Shortcuts

Navigate and control the assistant efficiently using keyboard shortcuts. Press `Ctrl + L` to open or switch focus to the assistant panel. Use `Alt + Up / Down` to cycle through previously sent messages, `Page Up / Down` to scroll the chat, and `Ctrl + R` to search message history. You can also open the Jump to File dialog with `Ctrl + Alt + J` (or `Cmd + Option + J` on Mac) and trigger the Stash Diff viewer with `Ctrl + Shift + L`. Press `Escape` to close the options panel.

### Message History

Your complete message history is always safely stored on the server. While the local chat view might hide older messages ("Forget") to conserve IDE memory, you can always retrieve the full history by clicking **Show All Messages** followed by **Reload**. You can search through your past user messages using `Ctrl + R`.

### Chat Features

#### Time to first token
The status bar at the bottom of the assistant panel keeps you informed of the AI's progress. It will display states like `Ready`, `Thinking...`, and `Responding...`. The plugin optimizes for speed, reducing the time to first token so you can see responses being streamed immediately.

#### Thinking and Tool Responses
Advanced models use a "thinking" budget to reason before responding. This internal reasoning is displayed in its own collapsible pane. Similarly, when the AI uses tools, the tool output is shown in collapsible panes marked with a tag icon. You can use the **Filter** button to hide these thought or tool bubbles from the chat view, or use the **Expand/Collapse All** button to manage them all at once. Error messages will appear as red-tinted bubbles, and tables are rendered cleanly with alternating row colors.

### Assistant Options

Customize the plugin behavior by navigating to `Tools > Options > Assistant`. Here, you can configure the path to the OpenCode binary, add process arguments, set a global system Preamble for all sessions, and toggle "Local Echo" for instant message rendering. You can also adjust UI preferences like toolbar icon size, chat font size, enable or disable specific features (like Stash Diff or Jump to file), and set a custom user avatar.

Additionally, the plugin includes a background **Update Checker Service** that automatically queries for new releases (with randomized intervals between 16 and 24 hours). You can toggle the update checks under `Tools > Options > Assistant > Check for updates`. When an update is found, you will be prompted with a dialog to download the update immediately, get reminded later, or skip the version entirely.

### IDE Enhancements

The plugin integrates deeply with NetBeans beyond just the chat panel to boost your coding productivity:

*   **Markdown Project Type:** 
    *   Allows you to create a minimal project for managing folders of text notes, design files, or markdown documentation without requiring a compiler or build system (e.g., Maven or Gradle).
    *   To create one, go to `Tools > New Project > Other > Markdown Project`. Alternatively, you can run `touch .mdproject` inside any folder to instantly make that directory loadable as a project in NetBeans.
    *   It is identified by a `.mdproject` marker file, and renders a clean directory tree in the NetBeans Projects tab, automatically filtering out OS junk files and editor swap files.
    *   You can close or delete the project directly from the right-click context menu on the project node.
*   **Stash Diff (Experimental):**
    *   A side-by-side diff viewer for git stashes. Select any stash in the NetBeans Git Repository Browser and press `Ctrl + Shift + L` (or click the **Diff Stash** button in the Git toolbar) to open it.
    *   **To Base (default):** Displays stash parent commit vs stash content (equivalent to `git stash show -p`).
    *   **To HEAD:** Simulates a 3-way merge applying the stash on your current HEAD, showing conflict markers inline (with a red "Conflict" label in the right panel if conflicts occur).
    *   **To Working Tree:** Simulates applying the stash onto the working tree, showing unchanged/modified tree status on the left.
    *   **File List & Partial Apply:** Click any file to view its syntax-highlighted diff, or right-click a file to "Apply this change" individually from the stash.
*   **Editor Context Menu Utilities:**
    *   **Search Web:** Right-click selected text (or the word under the cursor) in the editor and click **Search Web** to quickly search Google using your NetBeans-configured web browser.
    *   **Sort Lines Ascending / Descending:** Select multiple lines, right-click, and choose this option to alphabetically sort them.
    *   **Minify JSON:** Right-click any JSON content to quickly strip out all formatting whitespace.
*   **Jump to File Dialog:**
    *   A modeless file search dialog triggered via `Ctrl + Alt + J` (or `Cmd + Option + J` on Mac) that lets you search all open projects by filename prefix, path substring, or glob patterns.
    *   Supports quick keyboard navigation with arrow keys and `Enter` to open.
    *   Powered by an **In-Memory File Indexing Cache** that rebuilds in the background when projects are opened or closed. It automatically respects `.gitignore` rules and ignores common directories like `node_modules/`, `target/`, and `build/`.
    *   Includes incremental live indexing via file system listeners, meaning new, deleted, or renamed files are immediately searchable without full rebuilds.
    *   Can be enabled or disabled under `Tools > Options > Assistant`.

        ![Jump to File](./screenshots/jump_to_file.png)


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
| `Ctrl+L` stops working | Close and reopen the assistant panel from the Window menu. If that doesn't work, restart the IDE. |
| Sidebar doesn't open after install/upgrade | The plugin auto-opens the sidebar on version change. If it doesn't appear, open it from `Window > Assistant`. |
| Image paste doesn't work on Linux | Install the `wl-clipboard` package (Wayland) or check your clipboard manager. |
| Image paste broken after OpenCode upgrade | Upgrade to OpenCode >= 1.17.17 to resolve the breakage introduced in v1.17.13. |
| Model not appearing after upgrade to OpenCode v1.17.9, upgrade plugin to >= 1.7.3 | Re-select your model via `/models`. The upgrade resets model selection due to the new `thought_level` split. |
| Session config payloads restructured after upgrade | Upgrade the plugin to >= 1.7.3 and OpenCode to >= 1.17.17. Re-select your model and review any custom preamble or session settings. |
| Messages disappear from view | This is display-only — the session still has all messages. Click **Show All Messages** to keep them visible, and use **Reload** to re-fetch from the server. |
| LLM modified files unexpectedly | Always keep your project under version control (git) before allowing file modifications. Use **Accept/Deny** permission prompts to review changes. |
| High CPU usage or UI freezes | Upgrade the plugin to >= 1.9.2, which resolves an infinite layout validation loop in the chat panel. |

---

[Sign up for OpenCode Go](https://opencode.ai/go?ref=DWTNHGN9KX) 🚀
