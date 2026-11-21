# ADR 0005: Swing layout loop prevention

- **Status:** Accepted
- **Date:** 2026-11-21
- **Deciders:** Anand Balasubramanian
- **Related:** `ui/rendering/FitEditorPane`, `ui/ConfigPanelController`,
  `BaseCollapsiblePane`

## Context

Swing's layout system calls `getPreferredSize()` and `setBounds()` during a
layout pass. If either of those methods triggers a revalidation — directly or
indirectly — the component is re-added to Swing's invalidation queue, and the
layout pass repeats. With a component that measures its own content (like an
HTML-rendering pane), this becomes an infinite loop that pins a CPU core at
100%.

This repo hit the bug in at least three places, across multiple commits:

1. **FitEditorPane** (commit `91ee330f`, closes #176): `setBounds()`
   scheduled `revalidate()` on width change, causing BoxLayout to repeatedly
   recompute heights. `getPreferredSize()` used parent width even when own
   width was valid, triggering width mismatch and extra layout passes.
   An earlier fix (`d87715a7`) removed `revalidatePending` and
   `invokeLater` revalidate but left the parent-width lookup bug.
2. **Status bar** (commit `e36be068`): `centerStatusPanel.doLayout()`
   overrode layout to mutate `setPreferredSize()` during the layout pass —
   risking a re-layout/invalidation loop.
3. **Multiple perf commits** (`920ba457`, `9d0acc64`, `b2463909`,
   `a26045f4`): batched revalidate in `setMessages`, boundary-aware
   scanning, and other swing rendering fixes all addressing layout thrashing.

## Decision

**Never call `revalidate()` or mutate preferred size during a layout pass.**
Use these patterns instead:

1. **`suppressRevalidate` guard in `getPreferredSize()` and `setBounds()`.**
   Override `revalidate()` to return early when the guard is set. Wrap the
   body of `getPreferredSize()` in `try/finally` that sets and clears the
   guard. In `setBounds()`, set the guard only around the view-reformatting
   code, not the `super.setBounds()` call.
2. **Pure `getPreferredSize()` override instead of `doLayout()` mutation.**
   When a panel needs to cap a child's width, create a wrapper panel whose
   `getPreferredSize()` returns the capped dimension — never mutate
   `setPreferredSize()` inside `doLayout()`.
3. **Cache computed sizes.** Store `lastComputedWidth`, `lastComputedHeight`,
   and `cachedSize` so repeated layout passes with the same width return
   immediately without recomputing.

`BaseCollapsiblePane` follows the same rule: it caches preferred/maximum size
and clears the cache on `invalidate()`.

## Alternatives considered

- **`revalidate()` inside `getPreferredSize()`.** Rejected: directly causes
  the infinite loop. This was the original bug.
- **`SwingUtilities.invokeLater(revalidate)`.** Rejected: still queues a
  revalidation during the layout pass; the revalidation fires on the next EDT
  cycle and re-triggers layout. Removed in `d87715a7`.
- **`doLayout()` mutating `setPreferredSize()`.** Rejected: mutating preferred
  size during layout invalidates the component and schedules another layout
  pass. Fixed in `e36be068` by moving the cap into a pure
  `getPreferredSize()` override.
- **Removing revalidate entirely.** Rejected: breaks dynamic sizing when
  content changes (e.g. after `setText`). The guard approach preserves
  revalidation outside layout passes.
- **No size caching.** Rejected: without caching, every layout pass
  recomputes the HTML view tree height, causing O(n) work per frame even
  without a loop.

## Consequences

- Any new component that measures its own content must follow the
  `suppressRevalidate` pattern. The guard must be `volatile` because
  `getPreferredSize()` can be called from multiple threads during layout.
- `setBounds()` must call `super.setBounds()` *before* setting the guard, so
  the actual bounds change is never suppressed.
- `BaseCollapsiblePane` and any future collapsible pane must cache sizes and
  clear on `invalidate()`; after expand/collapse, repaint the enclosing
  viewport after layout.
- The `GLOBAL_REVALIDATE_QUEUED` atomic boolean in `FitEditorPane` prevents
  cascading revalidates across multiple instances during a single layout pass.
- UI components must stop timers in `removeNotify()` to avoid stale layout
  callbacks after the component is removed from the hierarchy.
EOF
echo done