# Agent Instructions for opencode-netbeans-plugin

## Project Overview
- **Project name**: OpenCode NetBeans Plugin
- **Project type**: NetBeans IDE plugin (NBM packaging)
- **Language**: Java 17
- **Build tool**: Maven
- **Current Version**: 1.2.9

## Build Commands
- Build: `mvn package`
- Clean: `mvn clean`
- Skip Tests: `mvn package -DskipTests`

## Key Technologies
- NetBeans Platform API (RELEASE210)
- Flexmark for markdown processing
- Jackson for JSON processing
- RSyntaxTextArea for code block syntax highlighting
- JUnit 5 for testing

## 📖 The "codex7" (Context7) Protocol
When working on this codebase, you **must** leverage Context7 for documentation on external libraries.

## Source Structure
- `src/main/java/ai/opencode/netbeans/` - Main source code
  - `completion/` - Code completion provider
  - `manager/` - OpenCodeManager, JsonRpcClient
  - `model/` - Message, Session, Agent, SessionUpdate (ACP compliant)
  - `project/` - Project management (startup, project manager)
  - `ui/` - UI components (chat panel, message bubbles, collapsible panes, theme manager)

## Architecture & Communication
- **Agent Client Protocol (ACP)**: Plugin is compliant with ACP for session metadata and updates.
- **JSON-RPC**: Bidirectional communication via `JsonRpcClient`.
- **SSE Streams**: Handles `session/update` notifications for real-time AI response streaming.
- **UI Architecture**:
    - `OpenCodeChatTopComponent`: Primary chat window with global controls.
    - `ChatThreadPanel`: Manages the thread of message bubbles.
    - `MessageBubble`: Handles rendering of specific message turns, including "thought" and "code" segments.
    - `CollapsibleCodePane`: Custom component for syntax-highlighted code with copy/insert actions.

## Important Files
- `pom.xml` - Maven configuration and dependencies (RSyntaxTextArea, Flexmark, Jackson).
- `src/main/resources/ai/opencode/netbeans/ui/layer.xml` - NetBeans registration for the chat window.

## Coding Notes
- **Braces**: Always use braces for `if-else`, `for`, `while`, and `do-while` loops.
- **Logging**: Use index-based placeholders (e.g., `{0}`) for `java.util.logging.Logger`; do not concatenate strings.
- **Theming**: Use `ThemeManager.getCurrentTheme()` for colors to ensure Darcula and Light mode compatibility.
- **Asynchrony**: Use `SwingUtilities.invokeLater()` for all UI updates coming from background RPC/SSE threads.