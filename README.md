# Assistant NetBeans Plugin

[![Version](https://img.shields.io/badge/version-1.3.25-blue.svg)](pom.xml)
[![Build Status](https://img.shields.io/badge/build-success-brightgreen.svg)](https://github.com/anandb/nb-complete)
[![NetBeans](https://img.shields.io/badge/NetBeans-RELEASE210-orange.svg)](https://netbeans.apache.org/download/index.html)

The **Assistant NetBeans Plugin** is a tool for integrating AI assistance into the NetBeans IDE. It uses the **Agent Client Protocol (ACP)** to provide a chat interface for code generation, explanation, and project-related tasks.

---

## Quickstart

Install the .nbm from the Tools -> Plugins -> Downloaded menu,  After installing the plugin, select Assistant from the
Window menu, if `opencode` is on the system path, then the location will be picked up directly, otherwise the path can
be configured under Options -> Assistant. The Preamble will be inserted automatically into every new chat conversation.

## Features

### UI and Theming
- **Theme Support**: Compatible with both **Solarized Light** and **Darcula Dark** modes.
- **Icon Support**: Automatically switches to high-contrast icons (e.g., settings, brain, tools) in dark mode.
- **Collapsible Blocks**: Handles **Thinking Process**, **Tool Calls**, and **Code** segments.
- **Syntax Highlighting**: Support for multiple languages via `RSyntaxTextArea`.
- **Interrupt Support**: Functional "Stop" button to halt active AI processing.

### 📁 Persistent Session Management
- **User-Defined Titles**: Rename your chat sessions to find them easily later.
- **Local Persistence**: Custom titles and session metadata are stored in your NetBeans user root, ensuring they survive IDE restarts.
- **Smart Welcome View**: A dedicated welcome screen lists your recent chats for quick resumption.
- **Export to Markdown**: Export your entire AI interaction as a clean, formatted Markdown file for documentation or sharing.

### ⚙️ Deep IDE Integration
- **Project Awareness**: Automatically tracks your active project and context.
- **Standardized Options**: Configure the plugin via the official NetBeans Options dialog (**Tools -> Options -> Advanced -> Assistant**).
- **Startup Connection Test**: Optional "Ping at Startup" feature verifies your AI connection as soon as you open the IDE.
- **Context Menu Actions**: Quick access via window toggles and keyboard shortcuts (`Ctrl+L`).

### 🔌 Advanced Communication
- **SSE Streaming**: Uses Server-Sent Events for real-time, chunk-by-chunk AI response streaming.
- **ACP Compliant**: Full implementation of the Agent Client Protocol for session management and tool usage.
- **Interactive Permissions**: Handles security-sensitive AI tool requests (like file edits) through an interactive UI approval process.

---

## 🚀 Getting Started

### Prerequisites
- **NetBeans IDE**: RELEASE210 or later.
- **Java**: JDK 17+ (Project uses Java 21 features).
- **ACP Server**: An compatible ACP-compliant backend (e.g., OpenCode binary).

### Installation (Build from Source)
1. Clone the repository:
   ```bash
   git clone https://github.com/anandb/nb-complete.git
   cd nb-complete
   ```
2. Build the NBM package:
   ```bash
   mvn package -DskipTests
   ```
3. The plugin will be generated at `./target/nbm/beanagent-1.3.25.nbm`.
4. In NetBeans, go to **Tools -> Plugins -> Downloaded -> Add Plugins...** and select the `.nbm` file.

---

## 🛠️ Configuration

Navigate to **Tools -> Options -> Advanced -> Assistant** to configure the following:

- **Executable Path**: The location of your ACP-compliant AI server binary.
- **Default Model**: Specify which LLM model you wish to use by default.
- **Ping at Startup**: Enable this to send a test message automatically when the panel is first opened to ensure connectivity.

---

## Architecture

The plugin uses an event-driven approach:

- **ACPManager**: Manages the AI server process lifecycle and communication.
- **AcpProtocolClient**: Handles JSON-RPC messaging.
- **SSE Streamer**: Processes streaming updates for real-time rendering.
- **AssistantTopComponent**: Main NetBeans window controller.
- **ThemeManager**: Manages styling across different IDE themes.

---

## 📜 Source Structure

- `src/main/java/github/anandb/netbeans/`
  - `completion/`: Intelligent code completion providers.
  - `manager/`: Core business logic and RPC client.
  - `model/`: ACP Protocol data models (Jackson-powered).
  - `project/`: Integration with NetBeans Project API.
  - `ui/`: Custom Swing components and theme management.

---

## 🤝 Contributing

Contributions are welcome! Please ensure that your pull requests adhere to the project's coding standards:
- Always use braces for control flow.
- Use index-based placeholders for logging (`{0}`).
- Ensure all new components support both Light and Dark themes via `ThemeManager`.

---

## 📄 License

This project is licensed under the UNLICENSE - see the [LICENSE](LICENSE) file for details.

