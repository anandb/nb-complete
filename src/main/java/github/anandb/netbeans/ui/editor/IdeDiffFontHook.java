package github.anandb.netbeans.ui.editor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.openide.modules.OnStart;
import org.openide.windows.TopComponent;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.rendering.IconResourceManager;

/**
 * Applies the plugin's diff font — the same one the stash and permission diffs use — to the IDE's
 * own diff windows: DefaultDiff's window and the Git module's Show Changes / Diff to Head window.
 *
 * <p>NetBeans sets no diff font of its own: the panes are ordinary editor panes, so they take the
 * editor's default colouring font
 * ({@code FontColorSettings.getFontColors(FontColorNames.DEFAULT_COLORING)}), and
 * {@code EditableDiffView} installs that editor kit asynchronously after the window opens. That is
 * why this styles the window's panes itself and keeps watching: a one-shot apply is overwritten as
 * soon as the kit installs, and the panes of the multi-file window are rebuilt per selected file.</p>
 *
 * <p>It only styles text components whose ancestry includes an {@code org.netbeans.modules.diff}
 * class, so the Git window's file tree and labels keep their own font.</p>
 *
 * <p><b>On by default, restart-scoped.</b> Poking another module's component tree is best-effort,
 * so *Options &gt; BeanBot &gt; IDE Extensions &gt; Features &gt; Use a smaller font size on diffs*
 * turns it off; the hook reads that setting once at startup, so a change applies after NetBeans
 * restarts.</p>
 */
@OnStart
public final class IdeDiffFontHook implements Runnable {

    private static final Logger LOG = Logger.from(IdeDiffFontHook.class);

    /** Everything the diff module draws lives under this package. */
    private static final String DIFF_MODULE_PREFIX = "org.netbeans.modules.diff.";

    /** Windows already wired, so repeated open/activate events do not stack listeners. */
    private static final Set<TopComponent> WIRED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Containers already carrying the child-added listener. */
    private static final Set<Container> OBSERVED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Panes already carrying the font listener. */
    private static final Set<JTextComponent> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Windows scanned without a diff view, remembered with their child count at that scan. */
    private static final Map<TopComponent, Integer> SCANNED_WITHOUT_DIFF = new WeakHashMap<>();

    @Override
    public void run() {
        if (!isEnabled()) {
            return;
        }
        TopComponent.getRegistry().addPropertyChangeListener(evt -> {
            String property = evt.getPropertyName();
            if (TopComponent.Registry.PROP_OPENED.equals(property)) {
                // Apply only to the freshly opened windows. NetBeans delivers an array; accept a
                // collection too, so a Set-valued event does not degrade to scanning every window.
                Object opened = evt.getNewValue();
                if (opened instanceof TopComponent[] windows) {
                    for (TopComponent tc : windows) {
                        applyTo(tc);
                    }
                } else if (opened instanceof Collection<?> windows) {
                    for (Object window : windows) {
                        if (window instanceof TopComponent tc) {
                            applyTo(tc);
                        }
                    }
                } else {
                    for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
                        applyTo(tc);
                    }
                }
            } else if (TopComponent.Registry.PROP_ACTIVATED.equals(property)
                    && evt.getNewValue() instanceof TopComponent activated) {
                applyTo(activated);
            }
        });
    }

    /** Package-visible for tests: the hook is a no-op while the setting is off. */
    static boolean isEnabled() {
        return PluginSettings.isDiffIdeFontEnabled();
    }

    private static void applyTo(TopComponent tc) {
        if (tc == null || WIRED.contains(tc)) {
            // The first wiring installed listeners on the whole tree; the ContainerListener
            // styles components added later (including per-file pane swaps), so an activation
            // must not re-walk a window that is already watched.
            return;
        }
        if (!isDiffWindow(tc) || !WIRED.add(tc)) {
            return;
        }
        tc.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && tc.isShowing()) {
                SwingUtilities.invokeLater(() -> styleWindow(tc));
            }
        });
        LOG.fine("Diff font hook watching window: {0}", tc.getClass().getName());
        SwingUtilities.invokeLater(() -> styleWindow(tc));
    }

    /**
     * Matched by class name first; only a window whose class name does not identify it as a diff
     * view but which might contain one is walked. A negative walk is remembered together with the
     * window's child count, so an unchanged non-diff window is not walked again on every
     * activation; a populated tree invalidates the answer.
     */
    private static boolean isDiffWindow(TopComponent tc) {
        String name = tc.getClass().getName();
        if (name.startsWith(DIFF_MODULE_PREFIX) || name.contains("Diff")) {
            return true;
        }
        int children = tc.getComponentCount();
        Integer scannedAt = SCANNED_WITHOUT_DIFF.get(tc);
        if (scannedAt != null && scannedAt == children) {
            return false;
        }
        if (containsDiffView(tc)) {
            SCANNED_WITHOUT_DIFF.remove(tc);
            return true;
        }
        // An empty window is cheap to re-check and may be populated after this event.
        if (children > 0) {
            SCANNED_WITHOUT_DIFF.put(tc, children);
        }
        return false;
    }

    /**
     * Styles the window's diff panes and keeps watching, because the view and its panes are built
     * asynchronously and the multi-file window swaps them per selected file.
     */
    private static void styleWindow(Component root) {
        if (root instanceof Container container && OBSERVED.add(container)) {
            container.addContainerListener(new ContainerAdapter() {
                @Override
                public void componentAdded(ContainerEvent event) {
                    Component child = event.getChild();
                    SwingUtilities.invokeLater(() -> styleWindow(child));
                }
            });
        }
        if (root instanceof JTextComponent pane) {
            if (hasDiffAncestor(pane)) {
                applyFont(pane);
                if (WATCHED.add(pane)) {
                    pane.addPropertyChangeListener("font", event -> applyFont(pane));
                }
            }
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleWindow(child);
            }
        }
    }

    /** The panes are plain {@code JEditorPane}s, so ancestry — not their own class — identifies them. */
    private static boolean hasDiffAncestor(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (current.getClass().getName().startsWith(DIFF_MODULE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDiffView(Component component) {
        if (component.getClass().getName().startsWith(DIFF_MODULE_PREFIX)) {
            return true;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsDiffView(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void applyFont(JTextComponent pane) {
        Font target = IconResourceManager.getDiffMonospaceFont();
        if (target.equals(pane.getFont())) {
            return;
        }
        pane.setFont(target);
        pane.revalidate();
        pane.repaint();
    }
}
