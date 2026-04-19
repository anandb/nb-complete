# Assistant NetBeans Plugin

[![Version](https://img.shields.io/badge/version-1.2.160-blue.svg)](pom.xml)
[![Build Status](https://img.shields.io/badge/build-success-brightgreen.svg)](https://github.com/anandb/nb-complete)
[![NetBeans](https://img.shields.io/badge/NetBeans-RELEASE210-orange.svg)](https://netbeans.apache.org/download/index.html)

The **Assistant NetBeans Plugin** is an AI coding assistant integrated directly into the NetBeans IDE. It implements the **Agent Client Protocol (ACP)** to provide a seamless, real-time interactive chat experience for developers—supporting code generation, explanation, and project-aware assistance.

---

## 🌟 Key Features

### 🎨 Modern & Responsive UI
- **Dual-Theme Support**: Toggle between **Solarized Light** and **Darcula Dark** modes instantly with a single click.
- **Collapsible Blocks**: Automatically handles **Thinking Process** (🧠), **Tool Calls** (🛠️), and **Code** segments. Use global toggles to expand or collapse all blocks.
- **Comprehensive Syntax Highlighting**: Rich rendering for over 40+ programming languages (Java, Python, Rust, Go, TS/JS, C++, etc.) using `RSyntaxTextArea`.
- **Micro-Animations**: Smooth transitions and status indicators (Thinking..., Responding...) for a premium feel.

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
3. The plugin will be generated at `./target/nbm/acp-netbeans-plugin-1.2.160.nbm`.
4. In NetBeans, go to **Tools -> Plugins -> Downloaded -> Add Plugins...** and select the `.nbm` file.

---

## 🛠️ Configuration

Navigate to **Tools -> Options -> Advanced -> Assistant** to configure the following:

- **Executable Path**: The location of your ACP-compliant AI server binary.
- **Default Model**: Specify which LLM model you wish to use by default.
- **Ping at Startup**: Enable this to send a test message automatically when the panel is first opened to ensure connectivity.

---

## 🏗️ Technical Architecture

The plugin follows a robust, event-driven architecture designed for high responsiveness:

- **ACPManager**: Orchestrates the lifecycle of the AI server process and JSON-RPC communication.
- **JsonRpcClient**: Handles bidirectional message passing and response futures.
- **SSE Streamer**: Parses incoming streaming updates and routes them to the UI thread for real-time rendering.
- **AssistantTopComponent**: The primary controller for the NetBeans window, managing state transitions between sessions.
- **ThemeManager**: A centralized system for dynamic CSS-like restyling of Swing components across light and dark modes.

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

