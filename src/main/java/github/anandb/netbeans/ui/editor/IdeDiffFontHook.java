package github.anandb.netbeans.ui.editor;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.openide.modules.OnStart;
import org.openide.windows.TopComponent;

import github.anandb.netbeans.support.Logger;
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
 * <p><b>Off unless asked.</b> Poking another module's component tree is best-effort, so the hook
 * does nothing unless {@value #ENABLE_PROPERTY} is set, e.g. add
 * {@code -J-Dbeanbot.diff.ideFont=true} to {@code netbeans.conf}.</p>
 */
@OnStart
public final class IdeDiffFontHook implements Runnable {

    /** System property that enables the hook; absent or {@code false} leaves diff windows alone. */
    public static final String ENABLE_PROPERTY = "beanbot.diff.ideFont";

    private static final Logger LOG = Logger.from(IdeDiffFontHook.class);

    /** Everything the diff module draws lives under this package. */
    private static final String DIFF_MODULE_PREFIX = "org.netbeans.modules.diff.";

    /** Windows already wired, so repeated open/activate events do not stack listeners. */
    private static final Set<TopComponent> WIRED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Containers already carrying the child-added listener. */
    private static final Set<Container> OBSERVED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Panes already carrying the font listener. */
    private static final Set<JTextComponent> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void run() {
        if (!isEnabled()) {
            return;
        }
        TopComponent.getRegistry().addPropertyChangeListener(evt -> {
            String property = evt.getPropertyName();
            if (TopComponent.Registry.PROP_OPENED.equals(property)) {
                // The new value is the freshly opened window set; scanning only that keeps the
                // startup burst of opens linear instead of re-walking every window each time.
                if (evt.getNewValue() instanceof TopComponent[] opened) {
                    for (TopComponent tc : opened) {
                        applyTo(tc);
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

    /** Package-visible for tests: the hook is a no-op unless the property is set. */
    static boolean isEnabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private static void applyTo(TopComponent tc) {
        if (tc == null || !isDiffWindow(tc)) {
            return;
        }
        if (WIRED.add(tc)) {
            tc.addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && tc.isShowing()) {
                    SwingUtilities.invokeLater(() -> styleWindow(tc));
                }
            });
            LOG.fine("Diff font hook watching window: {0}", tc.getClass().getName());
        }
        SwingUtilities.invokeLater(() -> styleWindow(tc));
    }

    /**
     * Matched by class name, not by contents: on the first open the window is still empty, and the
     * Git module's Show Changes / Diff to Head window is not a diff-module class at all.
     */
    private static boolean isDiffWindow(TopComponent tc) {
        String name = tc.getClass().getName();
        return name.startsWith(DIFF_MODULE_PREFIX) || name.contains("Diff") || containsDiffView(tc);
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
