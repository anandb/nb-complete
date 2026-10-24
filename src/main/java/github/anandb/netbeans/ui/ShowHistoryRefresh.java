package github.anandb.netbeans.ui;

import org.openide.modules.OnStart;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import github.anandb.netbeans.support.PluginSettings;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/**
 * Adds a refresh button to the IDE's git and Mercurial "Show History" windows,
 * right next to the file-info checkbox in their toolbars. Clicking it re-runs
 * the history search — a fresh {@code git log}/{@code hg log} against the same
 * criteria — so commits made since the window was opened appear without
 * closing and reopening it.
 *
 * <p>The IDE modules cannot be modified, so this follows the
 * {@link TestResultsContextMenu}/{@link InspectorContextMenu} runtime-hook
 * pattern: a {@code TopComponent.Registry} listener watches for the window
 * and patches its already-built component tree. Gated at startup on the
 * plugin's "Enable View File History" option.</p>
 *
 * <p>The Mercurial history window is a legacy {@code TopComponent} without a
 * {@code @TopComponent.Description}, so its window is matched by class name
 * in addition to the git window's preferred ID. Both search panels are forks
 * with the same shape: package-private {@code executeSearch()} and a toolbar
 * checkbox followed by the rest of the controls.</p>
 */
@OnStart
public class ShowHistoryRefresh implements Runnable, PropertyChangeListener {

    /** preferredIDs of git history windows, from their @TopComponent.Description. */
    private static final Set<String> HISTORY_TC_IDS =
            Set.of("Git.SearchHistoryTopComponent"); // NOI18N
    /** Window classes matched by name; Mercurial's legacy TC has no Description annotation. */
    private static final Set<String> HISTORY_TC_CLASS_NAMES = Set.of(
            "org.netbeans.modules.git.ui.history.SearchHistoryTopComponent", // NOI18N
            "org.netbeans.modules.mercurial.ui.log.SearchHistoryTopComponent"); // NOI18N
    /** The git and Mercurial search panels (Mercurial's is a fork of git's). */
    private static final Set<String> SEARCH_PANEL_CLASS_NAMES = Set.of(
            "org.netbeans.modules.git.ui.history.SearchHistoryPanel", // NOI18N
            "org.netbeans.modules.mercurial.ui.log.SearchHistoryPanel"); // NOI18N
    private static final String LINK_BUTTON_CLASS_NAME =
            "org.netbeans.modules.versioning.history.LinkButton"; // NOI18N

    /** Marker name so tests and the idempotency check can locate the injected button. */
    static final String REFRESH_BUTTON_NAME = "beanbot.showHistoryRefresh"; // NOI18N

    /** Gap between the Show All Files checkbox and the refresh button. */
    private static final int REFRESH_GAP_PX = 20;

    /** Toolbars already patched, weakly held so closed windows stay collectable. */
    private static final Set<JToolBar> INJECTED = Collections.newSetFromMap(new WeakHashMap<>());

    private boolean registered;

    @Override
    public void run() {
        if (registered) {
            return;
        }
        // Gated on the plugin's "Enable View File History" option; takes effect at startup.
        if (!PluginSettings.isViewFileHistoryEnabled()) {
            return; // no registry listener, no toolbar patching while disabled
        }
        registered = true;
        TopComponent.getRegistry().addPropertyChangeListener(this);
        // @OnStart runs on a non-EDT startup thread; defer the component tree
        // access to the EDT, exactly like the other context-menu hooks.
        SwingUtilities.invokeLater(this::patchIfOpen);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (TopComponent.Registry.PROP_OPENED.equals(evt.getPropertyName())) {
            patchIfOpen();
        }
    }

    private void patchIfOpen() {
        // Only scan already-open windows; matching via the registry (never
        // WindowManager.findTopComponent) means nothing is force-created.
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            TopComponent.Description desc = tc.getClass().getAnnotation(TopComponent.Description.class);
            if (matchesHistoryWindow(tc.getClass().getName(),
                    desc != null ? desc.preferredID() : null)) {
                patch(tc);
            }
        }
    }

    /**
     * @param windowClassName concrete {@code TopComponent} class name
     * @param preferredId     the window's {@code @TopComponent.Description} id, or {@code null}
     * @return whether the window is the git or Mercurial Show History window
     */
    static boolean matchesHistoryWindow(String windowClassName, String preferredId) {
        if (preferredId != null && HISTORY_TC_IDS.contains(preferredId)) {
            return true;
        }
        return HISTORY_TC_CLASS_NAMES.contains(windowClassName);
    }

    static void patch(TopComponent tc) {
        Component searchPanel = findSearchHistoryPanel(tc);
        if (searchPanel == null) {
            return;
        }
        injectToolbar(searchPanel, findChild((Container) searchPanel, JToolBar.class));
    }

    /**
     * Inserts the refresh button into the panel's toolbar, right after its
     * checkbox ("Show All Files" in git, file-info in Mercurial). Idempotent:
     * already-patched toolbars are skipped (weakly tracked, so a closed and
     * reopened window is patched again into its fresh toolbar).
     */
    static void injectToolbar(Component searchPanel, JToolBar toolbar) {
        if (toolbar == null || !INJECTED.add(toolbar)) {
            return;
        }
        JCheckBox showAllFiles = findChild(toolbar, JCheckBox.class);
        int index = showAllFiles != null ? toolbar.getComponentIndex(showAllFiles) + 1
                : toolbar.getComponentCount();
        ActionListener onRefresh = e -> runSearch(searchPanel);
        JButton refresh = UIUtils.createToolbarButton("refresh.svg",
                NbBundle.getMessage(ShowHistoryRefresh.class, "HINT_ShowHistoryRefresh"), onRefresh);
        refresh.setName(REFRESH_BUTTON_NAME);
        refresh.setFocusable(false);

        int at = Math.max(index, 0);
        toolbar.add(Box.createHorizontalStrut(REFRESH_GAP_PX), at);
        toolbar.add(refresh, at + 1);
        toolbar.revalidate();
        toolbar.repaint();
    }

    /** @return the injected refresh button, or {@code null} when the toolbar was not patched. */
    static JButton findRefreshButton(Container toolbar) {
        for (Component child : toolbar.getComponents()) {
            if (child instanceof JButton button && REFRESH_BUTTON_NAME.equals(button.getName())) {
                return button;
            }
        }
        return null;
    }

    /** Depth-first search for the git or Mercurial SearchHistoryPanel component. */
    static Component findSearchHistoryPanel(Container container) {
        return findByName(container, SEARCH_PANEL_CLASS_NAMES);
    }

    static Component findByName(Container container, String className) {
        return findByName(container, Set.of(className));
    }

    static Component findByName(Container container, Set<String> classNames) {
        if (componentIs(container, classNames)) {
            return container;
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                Component found = findByName((Container) child, classNames);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static <T extends Component> T findChild(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container) {
                T found = findChild((Container) child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean componentIs(Component c, Set<String> classNames) {
        for (Class<?> k = c.getClass(); k != null; k = k.getSuperclass()) {
            if (classNames.contains(k.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean componentIs(Component c, String className) {
        return componentIs(c, Set.of(className));
    }

    /**
     * Re-runs the current history search. The panel's package-private
     * {@code executeSearch()} is the same code path the IDE's own Search
     * button uses (fresh git/hg log, same criteria); reflection reaches it
     * without a plugin dependency on the versioning modules. If reflection
     * is blocked (JPMS), fall back to clicking the Search button itself.
     */
    static void runSearch(Component searchPanel) {
        if (searchPanel == null) {
            return;
        }
        for (Class<?> k = searchPanel.getClass(); k != null; k = k.getSuperclass()) {
            if (!SEARCH_PANEL_CLASS_NAMES.contains(k.getName())) {
                continue;
            }
            try {
                Method m = k.getDeclaredMethod("executeSearch"); // NOI18N
                m.setAccessible(true);
                m.invoke(searchPanel);
                return;
            } catch (LinkageError | ReflectiveOperationException ex) {
                break; // fall through to the button-based fallback
            }
        }
        JButton search = findSearchButton((Container) searchPanel);
        if (search != null && search.isEnabled()) {
            search.doClick();
        }
    }

    /**
     * The Search button ({@code bSearch}) is a direct child of the panel in
     * both git and Mercurial: their {@code GroupLayout} adds every constrained
     * component to the host. Checking direct children first keeps nested
     * buttons — the criteria panel's branch selector in Mercurial — from ever
     * being mistaken for it. The deep scan stays as a fallback.
     */
    static JButton findSearchButton(Container searchPanel) {
        for (Component child : searchPanel.getComponents()) {
            if (child instanceof JButton && !componentIs(child, LINK_BUTTON_CLASS_NAME)) {
                return (JButton) child;
            }
        }
        return findButtonOutsideToolBar(searchPanel, null);
    }

    private static JButton findButtonOutsideToolBar(Container container, Container skip) {
        for (Component child : container.getComponents()) {
            if (child == skip) {
                continue;
            }
            if (child instanceof JToolBar) {
                continue; // bNext/bPrev jump buttons live in the toolbar
            }
            if (child instanceof JButton && !componentIs(child, LINK_BUTTON_CLASS_NAME)) {
                return (JButton) child;
            }
            if (child instanceof Container) {
                JButton found = findButtonOutsideToolBar((Container) child, skip);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
