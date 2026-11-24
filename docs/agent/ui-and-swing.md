# UI And Swing

Part of the agent instructions. Index and always-apply rules: [`AGENTS.md`](../../AGENTS.md).

Read this when: building or resizing Swing components, shortcuts, annotations or the mini assistant.

## UI Sizing & Document Re-use
- `BaseCollapsiblePane` caches preferred/maximum size and clears on `invalidate()`. After expand /
  collapse, repaint the enclosing viewport after layout. Keep streaming lightweight (`JTextArea`)
  and finalize to HTML after the cooldown; reuse the HTML document and retain the content LRU cache.
- Fit/elide headers in pixels during layout and preserve path tails. Do not truncate by fixed
  character count. Draw welcome accents as icons; do not put emoji glyphs in UI bundle strings.
- Assistant bubbles span the thread width; constrain the right-aligned user bubble in
  `MessageBubble.AlignedRow`. Keep flat themed cards, left pin/queue accents, left user avatar and
  current thread grouping/gap behavior. Do not restore shadows, full-height stripes or alternating
  avatars.
- UI components stop timers in `removeNotify()`. `BaseCollapsiblePane` intentionally retains its
  self-only mouse listeners; removing/readding them duplicates callbacks. Keep the MCP idle-timeout
  minimum at 30s.
- Status labels are length-capped; thinking frames keep constant width and update through the status
  helper. Merge incoming model options so an open session's dropdown does not shrink.

## Mini-Assistant Toolbar Launch
- The toolbar "Launch Mini Assistant" button opens the `MiniAssistantDialog` **without**
  closing the main sidebar (`AssistantTopComponent`). The old `minimizeToDock()` call was
  removed so both panels can coexist.
- The `ToggleMiniAssistantAction` (Alt+L shortcut) also does NOT close the sidebar — it
  only toggles the dialog's visibility/focus.

## Git Annotations First Name Display

`GitAnnotationsFirstName` shows the commit author's first name in git blame annotations when the
**Show first name in git annotations** option (IDE Extensions › Features) is on. It is triggered
from `ShowAnnotationsAction` (Toggle Annotations) after the git action runs: blame is asynchronous,
so a bounded EDT poll waits for `AnnotationBar.elementAnnotations` and rewrites each line's private
`authorShort`, then stops. Lines are processed in document order and the name alternates between
title case and lower case per author block as a visual change hint. Toggle Annotations has no default
shortcut; it stays discoverable in Tools › Options › Keymap so users can bind it. A user shortcut
belongs on that action, not on the IDE's own Show Annotations action, which has no listener seam. A
bar's applied map is recorded so a repeat toggle does not redo the work. `ShowAnnotationsAction` also
sets `NAME` in its constructor: the Keymap dialog drops actions whose `NAME` is empty, so without it
the action is missing from Tools › Options › Keymap and cannot be bound.

Known limits, do not "fix" them by adding machinery:
- `AnnotationBar` is final and renders exactly `COMMIT_ID`, `DATE`, `AUTHOR`; there is no extra
  display field or formatter SPI. The Author column must be displayed; when it is hidden the name
  cannot render. Do not force `DisplayField.AUTHOR.visible` or write the display-field mask.
- Do not add a popup/menu item to the annotation bar, and do not install a global
  `RepaintManager`; both were rejected as too intrusive for this feature.
- Annotations shown through the IDE's own versioning menus, or re-fetched for a revision, keep
  NetBeans formatting until Toggle Annotations is used again. Turning the option off leaves names
  already on screen until the next toggle. Both are accepted.

## Editor Context Capture
Return `null` when no real editor `FileObject` exists; callers then omit context (do not send
phantom paths).

## Config Combo Enabled State
`ConfigPanelController` stores model/agent/thinking capability flags separately from `processing`;
`applyEnabledState()` computes `field && !processing`. Never call `combo.setEnabled()` directly.
Update capability flags from options, toggle processing on the EDT, and reapply when the options
panel becomes visible without overwriting those flags.

## Ctrl+L Toggle / Shortcut Registration (DO NOT BREAK)
The assistant's registration annotations must stay together: `@TopComponent.Registration`,
`@ActionID(id="github.anandb.netbeans.ui.AssistantTopComponent")`, and
`@TopComponent.OpenActionRegistration(preferredID="AssistantTopComponent")`. RELEASE220 does not
support shortcut registration on that annotation.

Register `ToggleAssistantAction` in `layer.xml`; both `Menu/Window/Assistant.shadow` and
`Shortcuts/D-L.shadow` (Ctrl+L) must target it, not the generated open action. The action uses
`AssistantTopComponent.findInstance().toggleVisibility()`. Toggle closes only when
`isOpened() && isShowing()`; otherwise open if needed and activate. Do not intercept Ctrl+L in a
`KeyEventDispatcher`.
