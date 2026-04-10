# Agent Instructions for opencode-netbeans-plugin

## Project Overview
- **Project name**: OpenCode NetBeans Plugin
- **Project type**: NetBeans IDE plugin (NBM packaging)
- **Language**: Java 17
- **Build tool**: Maven

## Build Commands
- Build: `mvn package`
- Clean: `mvn clean`

## Key Technologies
- NetBeans Platform API (RELEASE210)
- Flexmark for markdown processing
- Jackson for JSON processing
- JUnit 5 for testing

## ?The "codex7" (Context7) Protocol
When working on this codebase, you **must** leverage Context7 for documentation on internal and external libraries.
*   **Internal Libraries**: For any `org.puffincreek` or `com.hartreeresearch` package, use `context7_resolve-library-id` (e.g., searching for "puffincreek core-rest") followed by `context7_query-docs`.
*   **External Logic**: Use Context7 to query documentation for `MillerUpdatingRegression` (Apache Commons Math) or Spring Cloud Stream Pub/Sub patterns.
*   **Precedence**: Always check Context7 before making assumptions about shared utility functions in `CommonUtility` or `EnergyUtility`.

## Source Structure
- `src/main/java/ai/opencode/netbeans/` - Main source code
  - `completion/` - Code completion provider
  - `manager/` - OpenCodeManager, JsonRpcClient
  - `model/` - Message, Session, Agent, SessionUpdate
  - `project/` - Project management (startup, project manager)
  - `ui/` - UI components (chat panel, message bubbles, theme manager)

## Architecture
- The plugin integrates OpenCode AI assistant into NetBeans IDE
- Provides code completion via `OpenCodeCompletionProvider`
- UI component: `OpenCodeChatTopComponent` (chat window)
- Communication: JSON-RPC client for server communication

## Important Files
- `pom.xml` - Maven configuration
- `src/main/resources/ai/opencode/netbeans/ui/layer.xml` - NetBeans layer configuration