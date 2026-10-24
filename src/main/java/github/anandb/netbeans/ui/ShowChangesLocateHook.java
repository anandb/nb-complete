package github.anandb.netbeans.ui;

import org.openide.explorer.ExplorerManager;
import org.openide.modules.OnStart;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * Adds a "Locate in System" item to the right-click (context) menu of the IDE's
 * git and Mercurial "Show Changes" windows ({@code GitVersioningTopComponent},
 * {@code HgVersioningTopComponent}), covering both the table view and the
 * git package tree view. Activating the item opens the selected file(s)' parent
 * directory (or the directory itself when the selection is a folder) in the OS
 * file manager.
 *
 * <p>The versioning UIs' popups are constructed dynamically with no
 * layer/SPI extension point, and the IDE modules cannot be modified. This
 * therefore follows the {@link ShowHistoryRefresh}/{@code InspectorContextMenu}
 * runtime-hook pattern, but instead of patching a component tree it listens for
 * popup clicks and injects the menu item into the popup right after the IDE builds
 * and shows it. Selection is recovered with public API where possible
 * ({@link ExplorerManager.Provider} ancestors in tree mode, activated nodes in
 * Mercurial, {@code node.getLookup()} lookups) and reflection only for
 * module-internal/package-private types (the git status table model and
 * Mercurial's {@code SyncFileNode.getFile()}). Gated at startup on the plugin's
 * "Enable View File History" option.</p>
 */
@OnStart
public final class ShowChangesLocateHook implements Runnable {

    private static final Logger LOG = Logger.from(ShowChangesLocateHook.class);

    /**
     * Fully-qualified names of the Show Changes windows; used to scope the hook.
     * Matched by name instead of a compile-time dependency because the plugin's
     * pom does not depend on the git/Mercurial UI modules (same approach as
     * ShowHistoryRefresh).
     */
    private static final String[] SHOW_CHANGES_TC_CLASSES = { // NOI18N
        "org.netbeans.modules.git.ui.status.GitVersioningTopComponent", // NOI18N
        "org.netbeans.modules.mercurial.ui.status.HgVersioningTopComponent", // NOI18N
    };
    /** Class of the module-internal table model used by the Show Changes table view. */
    private static final String STATUS_MODEL_CLASS // NOI18N
            = "org.netbeans.modules.versioning.util.status.VCSStatusTableModel"; // NOI18N
    /** Class of the module-internal node backing the status table rows. */
    private static final String STATUS_NODE_CLASS // NOI18N
            = "org.netbeans.modules.versioning.util.status.VCSStatusNode"; // NOI18N

    /** Popup client property marking a popup as already injected (idempotency). */
    private static final String CLIENT_PROP_INJECTED = "beanbot.locateInSystemInjected"; // NOI18N

    /** The toolkit keeps AWT listeners via weak references; hold a strong one. */
    private static ShowChangesLocateHook instance;

    /** Popup-trigger hook; the field keeps it strongly reachable. */
    private PopupProbe probe;

    @Override
    public void run() {
        if (instance != null) {
            return; // @OnStart can fire again on module reload; register exactly once
        }
        // Gated on the plugin's "Enable View File History" option; takes effect at startup.
        if (!PluginSettings.isViewFileHistoryEnabled()) {
            return; // no AWT listener, no polling while disabled
        }
        instance = this;
        // @OnStart runs off the EDT; the AWT listener and the timer must be created on the EDT
        SwingUtilities.invokeLater(this::register);
    }

    private void register() {
        // Repeats while the IDE is still assembling/showing the popup (its own path
        // runs through a request-processor + invokeLater); stops on first injection
        // or after the probe's tick cap, so a click costs at most about one second of scans.
        probe = new PopupProbe("Show Changes", ShowChangesLocateHook::matchesShowChangesWindow,
                ShowChangesLocateHook::accepts, ShowChangesLocateHook::inject);
        probe.start();
    }

    /** Adds Locate in System to the Show Changes popup (runs with the popup on screen). */
    private static void inject(JPopupMenu popup) {
        List<File> files = resolveSelection((JTable) popup.getInvoker());
        if (files.isEmpty()) {
            LOG.fine("probe: selection could not be resolved; no item added"); // NOI18N
            return;
        }
        popup.putClientProperty(CLIENT_PROP_INJECTED, Boolean.TRUE);
        popup.addSeparator();
        JMenuItem item = new JMenuItem(NbBundle.getMessage(ShowChangesLocateHook.class,
                "CTL_LocateInSystem")); // NOI18N
        item.addActionListener(e -> revealInSystem(files));
        popup.add(item);
        // the menu is already on screen; it must be repacked or the added item is cut off
        popup.pack();
        popup.repaint();
    }

    /** A popup qualifies when its invoker is a row-table of a git/Mercurial Show Changes window. */
    private static boolean accepts(JPopupMenu popup) {
        if (popup.getClientProperty(CLIENT_PROP_INJECTED) != null) {
            return false;
        }
        if (!(popup.getInvoker() instanceof JTable table)) {
            return false; // table and tree views both present rows in JTable-like widgets
        }
        return matchesShowChangesWindow(table);
    }

    /** @return whether any ancestor of the given component is a git/Mercurial Show Changes window. */
    private static boolean matchesShowChangesWindow(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (!(c instanceof TopComponent)) {
                continue;
            }
            for (String windowClass : SHOW_CHANGES_TC_CLASSES) {
                if (classNameMatches(c, windowClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Selection recovery. Tree mode: an {@link ExplorerManager.Provider} ancestor holds
     * the selection as {@link Node}s and each node's {@code Lookup} exposes the {@link File}.
     * Table mode: the ETable's model is the module-internal {@code VCSStatusTableModel}
     * whose {@code getNode(int)}/{@link #fileOf} pair yields the selected files.
     * Mercurial populates neither: its table selection is published as the window's
     * activated nodes, which is the last fallback.
     */
    private static List<File> resolveSelection(JTable table) {
        for (Component c = table; c != null; c = c.getParent()) {
            if (c instanceof ExplorerManager.Provider provider) {
                List<File> files = filesOf(provider.getExplorerManager().getSelectedNodes());
                if (!files.isEmpty()) {
                    return files;
                }
                // Mercurial's VersioningPanel exposes a manager it never populates;
                // fall through to the model / activated-node sources.
                break;
            }
        }
        List<File> files = filesFromTableModel(table, table.getModel());
        return files.isEmpty() ? filesOf(activatedNodes(table)) : files;
    }

    /** Maps nodes to files via their Lookup, then a public {@code getFile()} accessor. */
    static List<File> filesOf(Node[] nodes) {
        if (nodes == null) {
            return Collections.emptyList();
        }
        List<File> files = new ArrayList<>(nodes.length);
        for (Node node : nodes) {
            File file = fileOf(node);
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * Mercurial's {@code SyncFileNode} exposes its file only through a public
     * {@code getFile()} method; the class and method are public, so no
     * {@code setAccessible} is needed across the module boundary.
     */
    static File fileOf(Node node) {
        File file = node.getLookup().lookup(File.class);
        if (file != null) {
            return file;
        }
        try {
            Object value = node.getClass().getMethod("getFile").invoke(node); // NOI18N
            return value instanceof File f ? f : null;
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOG.fine("Could not read the file of a Show Changes row: {0}", ex); // NOI18N
            return null;
        }
    }

    /** @return the activated nodes of the enclosing window (empty when there is none). */
    private static Node[] activatedNodes(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof TopComponent tc) {
                Node[] nodes = tc.getActivatedNodes();
                return nodes != null ? nodes : new Node[0];
            }
        }
        return new Node[0];
    }

    private static List<File> filesFromTableModel(JTable table, Object model) {
        ModelAccess access = modelAccess(model);
        if (access == null) {
            return Collections.emptyList();
        }
        int[] rows = table.getSelectedRows();
        List<File> files = new ArrayList<>(rows.length);
        try {
            for (int row : rows) {
                File file = access.fileAt(table.convertRowIndexToModel(row));
                if (file != null) {
                    files.add(file);
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOG.warn("Could not recover Show Changes selection: {0}", ex); // NOI18N
            return Collections.emptyList();
        }
        return files;
    }

    private static boolean classNameMatches(Component component, String fqn) {
        for (Class<?> k = component.getClass(); k != null; k = k.getSuperclass()) {
            if (fqn.equals(k.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reflects {@code getModel().getNode(int).getFile()} off the module-internal model.
     * The methods are public on public classes, {@link java.lang.reflect.Method} handles
     * the cross-module call without {@code setAccessible}.
     */
    private static ModelAccess modelAccess(Object model) {
        for (Class<?> k = model.getClass(); k != null; k = k.getSuperclass()) {
            if (!STATUS_MODEL_CLASS.equals(k.getName())) {
                continue;
            }
            try {
                Method getNode = k.getMethod("getNode", int.class); // NOI18N
                Class<?> nk = getNode.getReturnType();
                Method found = null;
                for (; nk != null; nk = nk.getSuperclass()) {
                    if (STATUS_NODE_CLASS.equals(nk.getName())) {
                        found = nk.getMethod("getFile"); // NOI18N
                        break;
                    }
                }
                Method getFile = found;
                if (found == null) {
                    return null;
                }
                return index -> (File) getFile.invoke(getNode.invoke(model, index));
            } catch (NoSuchMethodException | NoClassDefFoundError ex) {
                return null;
            }
        }
        return null;
    }

    /** Invocation wrapper for the reflected {@code getNode(int)} / {@code getFile()} pair. */
    private interface ModelAccess {
        File fileAt(int modelIndex) throws ReflectiveOperationException, LinkageError;
    }

    /** Opens the parent directory of each file (the directory itself for folders). */
    private static void revealInSystem(List<File> files) {
        List<File> dirs = new ArrayList<>();
        for (File file : files) {
            File dir = file.isDirectory() ? file : file.getParentFile();
            if (dir != null && !dirs.contains(dir)) {
                dirs.add(dir);
            }
            if (dirs.size() == 10) {
                break;
            }
        }
        if (dirs.isEmpty()) {
            return;
        }
        // Desktop.open performs local I/O; never run it on the EDT
        exIo(() -> {
            if (!Desktop.isDesktopSupported()) {
                LOG.warn("Desktop API unsupported; cannot locate in system."); // NOI18N
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                LOG.warn("Desktop OPEN action unsupported; cannot locate in system."); // NOI18N
                return;
            }
            for (File dir : dirs) {
                try {
                    desktop.open(dir);
                } catch (IOException ex) {
                    LOG.warn("Failed to open {0}: {1}", dir.getAbsolutePath(), ex); // NOI18N
                }
            }
        });
    }

    private static void exIo(Runnable task) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception ex) {
                LOG.warn("Failed to locate in system: {0}", ex); // NOI18N
            }
        });
    }
}
