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
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/**
 * Adds a refresh button to the IDE's git "Show History" window
 * ({@code git.SearchHistoryTopComponent}), right next to the "Show All Files"
 * checkbox in its toolbar. Clicking it re-runs the history search — a fresh
 * {@code git log} against the same criteria — so commits made since the
 * window was opened appear without closing and reopening it.
 *
 * <p>The IDE module cannot be modified, so this follows the
 * {@link TestResultsContextMenu}/{@link InspectorContextMenu} runtime-hook
 * pattern: a {@code TopComponent.Registry} listener watches for the window
 * and patches its already-built component tree.</p>
 */
@OnStart
public class ShowHistoryRefresh implements Runnable, PropertyChangeListener {

    /** preferredID of the git "Show History" window, from its @TopComponent.Description. */
    private static final String HISTORY_TC_ID = "Git.SearchHistoryTopComponent"; // NOI18N
    private static final String SEARCH_PANEL_CLASS_NAME =
            "org.netbeans.modules.git.ui.history.SearchHistoryPanel"; // NOI18N
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
            if (desc != null && HISTORY_TC_ID.equals(desc.preferredID())) {
                patch(tc);
            }
        }
    }

    static void patch(TopComponent tc) {
        Component searchPanel = findSearchHistoryPanel(tc);
        if (searchPanel == null) {
            return;
        }
        injectToolbar(searchPanel, findChild((Container) searchPanel, JToolBar.class));
    }

    /**
     * Inserts the refresh button into the panel's toolbar, right after the
     * Show All Files checkbox. Idempotent: already-patched toolbars are
     * skipped (weakly tracked, so a closed and reopened window is patched
     * again into its fresh toolbar).
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

    /** Depth-first search for the git module's SearchHistoryPanel component. */
    static Component findSearchHistoryPanel(Container container) {
        return findByName(container, SEARCH_PANEL_CLASS_NAME);
    }

    static Component findByName(Container container, String className) {
        if (componentIs(container, className)) {
            return container;
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                Component found = findByName((Container) child, className);
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

    private static boolean componentIs(Component c, String className) {
        for (Class<?> k = c.getClass(); k != null; k = k.getSuperclass()) {
            if (className.equals(k.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-runs the current history search. The panel's package-private
     * {@code executeSearch()} is the same code path the IDE's own Search
     * button uses (fresh git log, same criteria); reflection reaches it
     * without a plugin dependency on the git module. If reflection is
     * blocked (JPMS), fall back to clicking the Search button itself.
     */
    static void runSearch(Component searchPanel) {
        if (searchPanel == null) {
            return;
        }
        for (Class<?> k = searchPanel.getClass(); k != null; k = k.getSuperclass()) {
            if (!SEARCH_PANEL_CLASS_NAME.equals(k.getName())) {
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
     * The Search button is the only plain {@link JButton} inside the panel
     * that is neither in the toolbar (the next/prev jump buttons) nor a
     * LinkButton (the versioning module's expand-criteria arrow extends
     * JButton). Matched structurally, no bundle strings needed.
     */
    static JButton findSearchButton(Container searchPanel) {
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
