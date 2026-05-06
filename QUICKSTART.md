# Quickstart Guide

This document covers features available in the latest release. You can download the plugin from **Maven Central** or build it from source. While it will eventually be available on the Plugin Portal, currently only key stable versions are published there.

---

## 🛠 Setup

1.  **Prerequisites**: Install [OpenCode](https://opencode.ai/), then connect and configure your providers.
2.  **Installation**:
    *   Download the `.nbm` file from [Maven Central](https://central.sonatype.com/artifact/io.github.anandb/beanbot/versions).
    *   In NetBeans, navigate to `Tools > Plugins > Downloaded` to add the file.
3.  **Access**: Open the interface via `Window > Assistant` or use the shortcut `CTRL + L`.
4.  **Configuration**: The plugin usually auto-locates the OpenCode binary via your system path. If needed, set it manually under `Options > Assistant`.
5.  **Environment**: Define a global system prompt in `Options > Assistant > Preamble`. This runs alongside your OpenCode agent prompts to enable specific skills for every new session.

---

## 🚀 Features to Explore

### Interface & Navigation
*   **Switch Agents**: Press `TAB` to toggle between 'build', 'plan', or any custom agents defined in OpenCode.
*   **Toggle Views**: Use the **Filter** button to hide tool outputs or "thinking" messages.
*   **Expand Thoughts**: Only the current "thought bubble" is expanded by default; use the **Expand/Collapse** toolbar button to unfold all blocks.
*   **Auto-Scroll**: Click the **blue arrow** that appears during scrolling to jump back to the bottom of the thread.
* **Code** Select a snippet of code and ask the assistant to explain it, or ask the assistant to generate tests.

### Model & Command Control
*   **Model Selection**: Change models and thinking levels via the dropdown.
    *   *Pro Tip*: Set a default by exporting the `OPENCODE_MODEL` environment variable in your shell before launching NetBeans. Use the copy button next to the model list to get the exact name.
*   **Slash Commands**: Type `/` to access a popup list of available commands and skills.

### Message Management
*   **Resend Messages**: Hover over your user icon and click the **Copy** icon to move an older message back into the input area.
*   **Rename Topics**: Click the **Pencil** icon to label a conversation (stored locally).
*   **Export**: Save your entire session, including tool and thinking logs, to a **Markdown** file.

### Media & Troubleshooting
*   **Attachments**: Drag and drop images/documents or paste a screenshot directly into the text area (requires a vision-capable model).
*   **Customization**: Change your user icon in settings to make your messages easier to spot while scrolling.
*   **Reset Server**: If the assistant is unresponsive or the status stays on "Ready" without responding, try **Restart ACP Server** in the toolbar to refresh the OpenCode process.

