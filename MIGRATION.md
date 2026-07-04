# UI → Declarative DSL (swingtree) Migration Guide

This document tracks the refactor that prepares the `ui/` layer for a future
declarative-DSL migration (target: **swingtree**). The refactor itself is
**non-behavioral**: it introduces view-models, spec builders, platform-bridge
interfaces, and quarantine tags — but does **not** add the swingtree dependency.

## Target DSL (adoption-time, not this refactor)

| Coordinate | Version | Notes |
|---|---|---|
| `io.github.globaltcad:swing-tree` | 0.23.2 | Java-8 bytecode (runs on 17), LaF-agnostic |
| `io.github.globaltcad:sprouts` | 2.6.0 | Pulled transitively by swing-tree |

### Adoption gate: NetBeans classloader precondition

swing-tree's default layout manager is **MigLayout**, and NetBeans already ships
`net.miginfocom.swing.*` via `org-netbeans-libs-miglayout`. Bundling
`miglayout-swing` in an NBM risks a **split-package violation**. Before any view
imports `swingtree.UI`, the adoption PR must:

1. Exclude `com.miglayout:miglayout-swing` from the `swing-tree` dependency.
2. Depend on NetBeans' bundled `org-netbeans-libs-miglayout` (friend/dependency
   module) so swing-tree resolves `net.miginfocom.swing.MigLayout` from the
   platform classloader.
3. Wrap `com.github.weisj:jsvg` and `io.github.globaltcad:sprouts` as
   library-wrapper NBM modules (or `provided`/friend deps) to avoid
   classloader conflicts with NetBeans' own SVG modules and SLF4J.
4. Keep `org.slf4j:slf4j-api` as `provided` — NetBeans runtime supplies SLF4J.

This refactor does **not** solve the above; it only prepares the source tree.

## Quarantine tags

Two header comments are used in source files to mark code that stays imperative
when the DSL lands:

- `// DSL-LEAF: keep imperative, wrap via UI.of(...)` — custom-paint leaves
  (`RoundedPanel`, `PlaceholderTextArea`, `ScrollController` floating button,
  `FitEditorPane`, `MessageBubble`'s `Scrollable` impl). A future DSL tree
  wraps them via `UI.of(component)`; their internal Graphics2D work is not
  ported to `withStyle`.

- `// DSL-CONTROLLER: not a view` — streaming/timer logic
  (`ChatThreadPanel.flushTimer` + `messageQueue` EDT bridge,
  `BubbleStreamer.deferredFinalizeTimer`, `StreamingCoordinator`,
  `StatusController` timers, `ScrollController.scrollTimer`,
  `ChatLayoutBuilder.newSessionDebounceTimer`). The DSL migration declares the
  *containers* they drive; `restart()`/`stop()` hot-path calls stay here.

## Refactor phases (status)

| Phase | Description | Status |
|---|---|---|
| 0 | This document + quarantine tag convention | ✅ done |
| 1 | `ui/vm/` Swing-free view-models (`ChatToolbarVM`, `InputAreaVM`, `ConfigPanelVM`, `WelcomeVM`) | ✅ done |
| 2 | `ChatLayoutSpec` + `ChatToolbarActions` seam; `ChatLayoutBuilder.refs()` accessor | ✅ seam done — full field-collapse **deferred** |
| 3 | Declarative sub-specs (`CollapsibleHeaderSpec`, `CodePaneToolbarSpec`, `OptionsFormSpec`, `ConfigComboSpec`, `WelcomeSpec`, `PermissionBubbleSpec`) | ⏳ deferred (risky mechanical extractions) |
| 4 | `ui/PlatformBridge` interfaces (`SessionService`, `ProcessService`, `PrefStore`, `Bundle`, `ProjectContext`) + `DefaultPlatformBridge` `@ServiceProvider` | ✅ seam + call-site migration done (Lookup + ACPProjectManager); `NbPreferences` / `NbBundle` migration deferred |
| 5 | Quarantine custom-paint leaves (`DSL-LEAF`) | ✅ done — 32 files tagged |
| 6 | Quarantine streaming/timer controllers (`DSL-CONTROLLER`) | ✅ done — covered by the 32-file tag pass |
| 7 | Pilot `StatusView` / `Status Spec` seam test (imperative, DSL-shaped) | ✅ done (not wired into live UI) |
| 8 | Finalize per-file migration order + quarantine inventory below | ✅ done |

### Deferred follow-up PRs (tracked here, not in this refactor)

1. **Phase 2 full field-collapse**: move `ChatLayoutBuilder` construction body
   into `ChatLayoutSpec.build(...)`, delete the duplicate `JButton` fields from
   `ChatLayoutBuilder`, make it stateless. Large mechanical change; validate
   toolbar rendering + all 11 `*Action` classes.
2. **Phase 3 sub-specs**: extract the 6 leaf-cluster specs. Mechanical; do one
   per PR.
3. **Phase 4 `NbPreferences` / `NbBundle` migration**: the Lookup +
   `ACPProjectManager` call sites are now routed through `PlatformBridge`
   (this PR). Remaining: `NbPreferences.forModule(...)` (15 sites across
   `UIUtils`, `MessageHistory`, `MessageFilterManager`, `MessageSender`,
   `ChatLayoutBuilder`, `ACPOptionsPanel`, `ToolThoughtCombiner`) and
   `NbBundle.getMessage(...)` (27 sites in `ChatLayoutBuilder`, 24 in
   `ACPOptionsPanel`, etc.) still call NetBeans APIs directly. Migrate via
   `PrefStore` + `Bundle` sub-services; preserve the AGENTS.md hot-path
   `static volatile` + `PreferenceChangeListener` cache pattern in `UIUtils`.
4. **Phase 7 wiring**: replace `AssistantTopComponent`'s ad-hoc status label
   construction with `StatusSpec.build(...)`. Verify identical rendering.
5. **Adoption gate**: solve the MigLayout split-package NBM precondition
   (exclude `miglayout-swing`, depend on `org-netbeans-libs-miglayout`, wrap
   `jsvg` + `sprouts` as friend modules). Then add the `swing-tree` dependency
   and flip `StatusSpec` to a swingtree tree as the first real DSL view.

## Per-file adoption order (when the DSL lands)

1. **DSL-LEAF leaves** (wrap, do not port):
   - `RoundedPanel` — Graphics2D gradient/clip paintComponent + paintBorder.
   - `PlaceholderTextArea` — paintComponent placeholder overlay + word-wise undo.
   - `FitEditorPane` — HTML Document reuse via `doc.remove()`+`kit.read()`.
   - `MessageBubble` — Scrollable impl + heavy delegation (shell stays a leaf;
     its sub-specs are extractable).
   - `ScrollController.createScrollDownBtn()` — anonymous JComponent with
     Graphics2D paintComponent for the floating scroll-down button.
2. **DSL-CONTROLLER** (stay imperative; the DSL declares the containers they drive):
   - `ChatThreadPanel` — flushTimer + messageQueue EDT bridge + trimMessages.
   - `BubbleStreamer` — deferredFinalizeTimer + streaming-text accumulation.
   - `StreamingCoordinator` — streamFlushTimer driver.
   - `StatusController` — thinkingTimer (500ms) + statusResetTimer (1.5s).
   - `ChatLayoutBuilder` — newSessionDebounceTimer (300ms).
3. **Dialogs** (low-risk, self-contained): `HistorySearchDialog`,
   `KeyboardShortcutsDialog`.
4. **`StatusSpec` pilot** — flip to swingtree first (proves the seam).
5. **`WelcomeScreen`** — simple session-list panel.
6. **Toolbar** — `ChatLayoutSpec.build(...)` body becomes declarative; refs
   record + `ChatToolbarActions` interface unchanged.
7. **`MessageBubble` sub-specs** (Phase 3 outputs) — header, code toolbar;
   bubble shell stays a leaf.
8. **`ChatThreadPanel` container** — only the `messagesContainer`/`scrollPane`
   shell becomes declarative; the controller stays imperative.
