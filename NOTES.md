# Release Notes

## 1.4.1 (2026-04-27)
- **UI Modernization & Premium Aesthetics**:
    - **Markdown Table Rendering**: Completely redesigned the table rendering engine. Tables are now wrapped in a custom `RoundedPanel` container to achieve 12px rounded corners, overcoming the limitations of standard Swing HTML rendering.
    - **Styling System**: Introduced a curated cream background (`#fdf6e3` in light mode) for tables with 8px internal cell padding and themed borders (`#e8e0c8`).
    - **Responsive Layouts**: Optimized `FitEditorPane` to perform width-aware height calculations, ensuring that text wrapping in chat bubbles is accurate and fluid.
- **Core Architecture & Protocol Enhancements**:
    - **ACP Logic Refactor**: Standardized the `ACPManager` and `AcpProtocolClient` for improved session-aware messaging. Implemented background routing for asynchronous server updates.
    - **Tool Parameter Extraction**: Added specialized logic to extract and sanitize tool parameters from complex model outputs.
    - **Language Resolution**: Enhanced the `LanguageResolver` to support a wider range of programming languages for syntax highlighting in `RSyntaxTextArea`.
- **Maintenance & Branding**:
    - Completed the transition to the "Coding Assistant" brand across all documentation, UI components, and project metadata.
    - Aligned project requirements with Java 17 and NetBeans RELEASE210 specifications.

## v1.3.38 (2026-04-24)
- **Model Compatibility & Prompt Optimization**:
    - **Structured Context**: Migrated context metadata from JSON-style comments to structured XML tags to improve parsing reliability for models like Qwen and Nemotron.
    - **Recency Bias Handling**: Implemented prompt block reordering logic that places the final user instruction last in the context window, optimizing for model attention.
    - **Message Annotations**: Introduced support for 'tags' in the `Message` model, allowing for features like 'hidden' messages that are processed by the AI but not displayed in the UI.
- **System Stability**:
    - Refactored `AssistantTopComponent` for improved serialization efficiency and cleaner dependency management.
    - Version alignment across all module manifests and documentation files.

## v1.3.26 (2026-04-24)
- **Visual Refinement**:
    - Resolved persistent icon scaling issues in the chat thread, ensuring consistency across different monitor DPI settings.
    - Tweaked dark mode theme mappings to improve the visibility of borders and separators in the `ChatThreadPanel`.

## v1.3.25 (2026-04-24)
- **Execution Control & Reliability**:
    - **Stop Mechanism**: Stabilized the implementation of the `session/cancel` notification, ensuring reliable halting of active AI streaming sessions.
    - **SVG Migration**: Completed the migration of core UI assets (new chat, rename, export, settings) to scalable SVG format, including specialized `-dark.svg` variants for high-contrast dark mode.
    - **Server Management**: Added an explicit "Restart Server" capability in the Options dialog to allow users to recover from backend process hangs without restarting the IDE.
- **Infrastructure**: Established a comprehensive unit testing suite using JUnit 5, focusing on protocol serialization and UI component state management.

## v1.2.20 (2026-04-13)
- **Deployment Fixes**:
    - Corrected the `OpenIDE-Module-Layer` path in `pom.xml`, resolving issues where the Assistant window was not properly registered in the "Window" menu on clean installations.
- **UI Enhancements**:
    - Integrated global "Expand All" and "Collapse All" controls for message blocks, allowing for quick navigation through long technical discussions.

## v1.2.19 (2026-04-13)
- **Theme & Persistence Engine**:
    - **Native Theme Detection**: Implemented a sophisticated theme engine that queries NetBeans `UIManager` to automatically adapt colors and icons to the active Look and Feel (e.g., Solarized Light, Darcula).
    - **Persistent Sessions**: Introduced local storage for chat sessions in the NetBeans user root. Sessions now persist across IDE restarts.
    - **Dynamic Renaming**: Added the ability for users to rename sessions, with metadata automatically synchronized to the local database.
- **Preference Management**: Unified all plugin settings into a centralized Options panel under **Tools > Options > Advanced > Assistant**.

## 1.2.9 (2026-04-13)
- **Security & Content Management**:
    - **Interactive Permissions**: Implemented a security-aware workflow for tool calls. Sensitive operations like file modifications now require explicit user approval via a dedicated UI dialog.
    - **Markdown Export**: Added the "Export to Markdown" feature, enabling users to save entire conversations as formatted documentation for project logs or sharing.
- **UI Quality**:
    - Standardized typography across the plugin using curated font stacks (Inter, Roboto, Segoe UI) with proper fallbacks for macOS and Linux.
    - Improved vertical centering and alignment in collapsible pane headers.

## 1.2.3 (2026-04-12)
- **Rendering & Layout**:
    - Implemented a "first-available" font fallback system for code blocks, prioritizing high-quality monospaced fonts like 'JetBrains Mono' or 'Source Code Pro'.
    - Refined the chat UI with improved vertical spacing and header padding to reduce visual density.

## 1.2.2 (2026-04-12)
- **Core Feature Implementation**:
    - **Markdown Tables**: Introduced the first iteration of markdown table rendering within message bubbles.
    - **Session Loading**: Refined the `loadSession` logic to prioritize the active project directory when searching for previous chat context.
    - **Dependencies**: Integrated `commons-lang3` to assist with robust string parsing and HTML escaping.

## 1.1.49 (2026-04-11)
- **Foundation & Public Release**:
    - **NetBeans Integration**: Established the `AssistantTopComponent` as the primary entry point for AI-assisted coding within the IDE.
    - **Protocol Implementation**: Developed the initial version of the JSON-RPC client for communication with the AI backend service.
    - **Content Rendering**: Integrated Flexmark for markdown-to-HTML conversion and `RSyntaxTextArea` for professional-grade code syntax highlighting.
- **Hardening & Infrastructure**:
    - Addressed critical concurrency and memory management issues in the early RPC communication layer.
    - Standardized on index-based placeholder logging to improve performance and diagnosability.
    - Initial relocation of all settings to the native NetBeans Miscellaneous options category.
