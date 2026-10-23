package github.anandb.netbeans.ui;

import org.openide.explorer.ExplorerManager;
import org.openide.modules.OnStart;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.MenuSelectionManager;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Adds a "Locate in System" item to the right-click (context) menu of the IDE's
 * git "Show Changes" window ({@code GitVersioningTopComponent}), covering both
 * the table view and the package tree view. Activating the item opens the selected
 * file(s)' parent directory (or the directory itself when the selection is a folder)
 * in the OS file manager.
 *
 * <p>The git UI's popup is constructed dynamically by
 * {@code org.netbeans.modules.git.ui.status.VersioningPanelController#getPopupFor}
 * with no layer/SPI extension point, and the IDE module cannot be modified. This
 * therefore follows the {@link ShowHistoryRefresh}/{@code InspectorContextMenu}
 * runtime-hook pattern, but instead of patching a component tree it listens for
 * popup clicks and injects the menu item into the popup right after the IDE builds
 * and shows it. Selection is recovered with public API where possible
 * ({@link ExplorerManager.Provider} ancestors in tree mode, {@code node.getLookup()}
 * lookups) and reflection only for module-internal classes (the status table model).
 * Gated at startup on the plugin's "Enable View File History" option.</p>
 */
@OnStart
public final class ShowChangesLocateHook implements Runnable, AWTEventListener {

    private static final Logger LOG = Logger.from(ShowChangesLocateHook.class);

    /**
     * Fully-qualified name of the Show Changes window; used to scope the hook.
     * Matched by name instead of a compile-time dependency because the plugin's
     * pom does not depend on the git UI module (same approach as ShowHistoryRefresh).
     */
    private static final String GIT_VERSIONING_TC_CLASS // NOI18N
            = "org.netbeans.modules.git.ui.status.GitVersioningTopComponent"; // NOI18N
    /** Class of the module-internal table model used by the Show Changes table view. */
    private static final String STATUS_MODEL_CLASS // NOI18N
            = "org.netbeans.modules.versioning.util.status.VCSStatusTableModel"; // NOI18N
    /** Class of the module-internal node backing the status table rows. */
    private static final String STATUS_NODE_CLASS // NOI18N
            = "org.netbeans.modules.versioning.util.status.VCSStatusNode"; // NOI18N

    /** Popup client property marking a popup as already injected (idempotency). */
    private static final String CLIENT_PROP_INJECTED = "beanbot.locateInSystemInjected"; // NOI18N

    /** Poll interval; the popup is typically up after the very first tick. */
    private static final int PROBE_DELAY_MS = 40;
    /** Ticks after a click before polling gives up (no popup opened, e.g. plain right-click). */
    private static final int PROBE_MAX_TICKS = 25;

    /** The toolkit keeps AWT listeners via weak references; hold a strong one. */
    private static ShowChangesLocateHook instance;

    /** Poll timer: right-click restarts it; it injects as soon as the popup shows. */
    private Timer probe;

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
        // or after PROBE_MAX_TICKS, so a click costs at most about one second of scans.
        probe = new Timer(PROBE_DELAY_MS, e -> injectIntoShownPopup());
        Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        // Right-button press or release (popup trigger varies by platform/LAF).
        // Coalesce: the timer is simply restarted, so an entire click produces one probe.
        // Scope the click to the Show Changes window first: every right-click in the
        // IDE reaches this listener, and an unscoped one would start a poll that walks
        // every open window hierarchy for up to PROBE_MAX_TICKS before giving up.
        if (event instanceof MouseEvent mouseEvent && (mouseEvent.isPopupTrigger()
                || SwingUtilities.isRightMouseButton(mouseEvent))
                && insideGitShowChanges(mouseEvent.getComponent())) {
            probeTicks = 0;
            probe.restart();
        }
    }

    /** Ticks since the last popup-trigger click; caps the poll loop. */
    private int probeTicks;

    /** Runs on the EDT via the poll timer. */
    private void injectIntoShownPopup() {
        if (++probeTicks > PROBE_MAX_TICKS) {
            probe.stop(); // no qualifying popup opened; stop polling
            return;
        }
        JPopupMenu popup = findShownPopup();
        if (popup == null) {
            LOG.fine("probe: no git Show Changes popup on screen yet"); // NOI18N
            return;
        }
        probe.stop();
        LOG.fine("probe: injecting Locate in System into git Show Changes popup"); // NOI18N
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

    /**
     * Finds the on-screen popup of the git Show Changes window at probe time.
     * Primary source is the {@link MenuSelectionManager} selection path — which
     * only lists the popup after one of its items has been armed, so if the
     * path is empty (no hover/selection yet) or the popup is not there, every
     * open top-level window hierarchy is walked for a showing JPopupMenu
     * instead (lightweight popups live inside one of the open windows,
     * heavyweight ones inside a Popup window).
     */
    private static JPopupMenu findShownPopup() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        for (MenuElement el : path) {
            if (el instanceof JPopupMenu p && accepts(p)) {
                return p;
            }
        }
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            JPopupMenu popup = findShowingPopup(w);
            if (popup != null && accepts(popup)) {
                return popup;
            }
        }
        return null;
    }

    /** @return the topmost showing JPopupMenu in the given component hierarchy, if any. */
    private static JPopupMenu findShowingPopup(Component c) {
        if (c instanceof JPopupMenu p && p.isShowing()) {
            return p;
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                JPopupMenu found = findShowingPopup(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A popup qualifies when its invoker is a row-table of the git Show Changes window. */
    private static boolean accepts(JPopupMenu popup) {
        if (popup.getClientProperty(CLIENT_PROP_INJECTED) != null) {
            return false;
        }
        if (!(popup.getInvoker() instanceof JTable table)) {
            return false; // table and tree views both present rows in JTable-like widgets
        }
        return insideGitShowChanges(table);
    }

    /** @return whether any ancestor of the given component is the git Show Changes window. */
    private static boolean insideGitShowChanges(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof TopComponent && classNameMatches(c, GIT_VERSIONING_TC_CLASS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Selection recovery. Tree mode: an {@link ExplorerManager.Provider} ancestor holds
     * the selection as {@link Node}s and each node's {@code Lookup} exposes the {@link File}.
     * Table mode: the ETable's model is the module-internal {@code VCSStatusTableModel}
     * whose {@code getNode(int)}/{@link #fileOf} pair yields the selected files.
     */
    private static List<File> resolveSelection(JTable table) {
        for (Component c = table; c != null; c = c.getParent()) {
            if (c instanceof ExplorerManager.Provider provider) {
                Node[] nodes = provider.getExplorerManager().getSelectedNodes();
                List<File> files = new ArrayList<>(nodes.length);
                for (Node node : nodes) {
                    File file = node.getLookup().lookup(File.class);
                    if (file != null) {
                        files.add(file);
                    }
                }
                return files;
            }
        }
        return filesFromTableModel(table, table.getModel());
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
