package github.anandb.netbeans.ui;

import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.PreferenceKeys;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import org.openide.modules.OnStart;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;

/** Adds a live, optional Copy All button to NetBeans' Action Items toolbar. */
@OnStart
public class ActionItemsCopyButton implements Runnable, PropertyChangeListener, PreferenceChangeListener {

    private static final Logger LOG = Logger.getLogger(ActionItemsCopyButton.class.getName());
    private static final String WINDOW_CLASS =
            "org.netbeans.modules.tasklist.ui.TaskListTopComponent"; // NOI18N
    private static final String BUTTON_NAME = "beanbot.actionItemsCopyAll"; // NOI18N
    private static final String OBSERVER_PROPERTY = "beanbot.actionItemsCopyAllObserver"; // NOI18N

    private volatile boolean enabled;
    private boolean registered;

    @Override
    public void run() {
        if (registered) {
            return;
        }
        registered = true;
        enabled = PluginSettings.isCopyAllItemsEnabled();
        TopComponent.getRegistry().addPropertyChangeListener(this);
        Preferences prefs = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        prefs.addPreferenceChangeListener(this);
        SwingUtilities.invokeLater(this::syncOpenWindows);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (TopComponent.Registry.PROP_OPENED.equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(this::syncOpenWindows);
        }
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
        if (PreferenceKeys.ACTIONS_COPY_ALL_ITEMS.equals(evt.getKey())) {
            enabled = NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR)
                    .getBoolean(PreferenceKeys.ACTIONS_COPY_ALL_ITEMS, true);
            SwingUtilities.invokeLater(this::syncOpenWindows);
        }
    }

    private void syncOpenWindows() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::syncOpenWindows);
            return;
        }
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (matchesWindow(tc.getClass())) {
                if (enabled) {
                    patch(tc);
                } else {
                    removeInjectedButtons(tc);
                }
            }
        }
    }

    private static boolean matchesWindow(Class<?> windowClass) {
        for (Class<?> type = windowClass; type != null; type = type.getSuperclass()) {
            if (WINDOW_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private void patch(TopComponent tc) {
        JToolBar toolbar = findChild(tc, JToolBar.class);
        if (toolbar == null) {
            return;
        }
        observeComponentTree(tc, tc);
        injectToolbar(tc, toolbar);
    }

    private void observeComponentTree(TopComponent tc, Container container) {
        if (container instanceof JComponent component && component.getClientProperty(OBSERVER_PROPERTY) == null) {
            ContainerAdapter observer = new ContainerAdapter() {
                @Override
                public void componentAdded(ContainerEvent event) {
                    if (event.getChild() instanceof Container child) {
                        observeComponentTree(tc, child);
                    }
                    scheduleRefresh(tc);
                }

                @Override
                public void componentRemoved(ContainerEvent event) {
                    scheduleRefresh(tc);
                }
            };
            component.putClientProperty(OBSERVER_PROPERTY, observer);
            container.addContainerListener(observer);
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                observeComponentTree(tc, nested);
            }
        }
    }

    private void scheduleRefresh(TopComponent tc) {
        SwingUtilities.invokeLater(() -> {
            if (enabled && tc.isOpened()) {
                patch(tc);
            } else {
                removeInjectedButtons(tc);
            }
        });
    }

    private void injectToolbar(TopComponent tc, JToolBar toolbar) {
        if (!enabled || findInjectedButton(toolbar) != null) {
            return;
        }
        JTable table = findChild(tc, JTable.class);
        if (table == null || !(toolbar.getComponentCount() > 0
                && toolbar.getComponent(toolbar.getComponentCount() - 1) instanceof JToggleButton)) {
            return;
        }

        try {
            createTaskAccess(table.getModel());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            removeInjectedButtons(toolbar);
            LOG.log(Level.FINE, "Action Items structure is not supported; Copy All was not added", ex);
            return;
        }
        addCopyButton(toolbar, () -> copyAll(tc));
    }

    private static void copyAll(TopComponent tc) {
        try {
            JTable table = findChild(tc, JTable.class);
            if (table == null) {
                return;
            }
            TaskAccess access = createTaskAccess(table.getModel());
            List<TaskDetails> items = access.readItems();
            String text = formatItems(items);
            if (!text.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOG.log(Level.FINE, "Could not copy Action Items", ex);
        }
    }

    static String formatItems(List<TaskDetails> items) {
        StringBuilder text = new StringBuilder();
        for (TaskDetails item : items) {
            String description = clean(item.description());
            if (description == null) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("- ").append(description);
            String file = clean(item.file());
            String location = clean(item.location());
            if (file != null) {
                text.append("\n  File: ").append(file);
            }
            if (location != null) {
                text.append("\n  Location: ").append(location);
            }
        }
        return text.toString();
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    static List<?> extractTaskObjects(Object tableModel) throws ReflectiveOperationException {
        return findTaskSource(tableModel).tasks();
    }

    private static TaskAccess createTaskAccess(Object tableModel) throws ReflectiveOperationException {
        TaskSource source = findTaskSource(tableModel);
        ClassLoader loader = source.taskList().getClass().getClassLoader();
        Class<?> taskType = Class.forName("org.netbeans.spi.tasklist.Task", false, loader);
        Class<?> accessor = Class.forName("org.netbeans.modules.tasklist.impl.Accessor", false, loader);
        Method getDescription = accessor.getMethod("getDescription", taskType);
        Method getFile = accessor.getMethod("getFileNameExt", taskType);
        Method getLocation = accessor.getMethod("getLocation", taskType);
        return new TaskAccess(source.tasks(), getDescription, getFile, getLocation);
    }

    private static TaskSource findTaskSource(Object tableModel) throws ReflectiveOperationException {
        if (tableModel == null) {
            throw new IllegalArgumentException("Missing Action Items table model");
        }
        Field taskListField = findField(tableModel.getClass(), "taskList");
        taskListField.setAccessible(true);
        Object taskList = taskListField.get(tableModel);
        if (taskList == null) {
            throw new IllegalStateException("Action Items task list is not initialized");
        }
        Method getTasks = taskList.getClass().getMethod("getTasks");
        Object tasks = getTasks.invoke(taskList);
        if (!(tasks instanceof List<?> taskObjects)) {
            throw new IllegalStateException("Action Items task list did not return a list");
        }
        return new TaskSource(taskList, taskObjects);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through the model hierarchy.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void removeInjectedButtons(TopComponent tc) {
        for (JToolBar toolbar : findChildren(tc, JToolBar.class)) {
            removeInjectedButtons(toolbar);
        }
    }

    static void removeInjectedButtons(Container toolbar) {
        for (Component child : toolbar.getComponents()) {
            if (child instanceof JButton button && BUTTON_NAME.equals(button.getName())) {
                toolbar.remove(child);
            }
        }
        toolbar.revalidate();
        toolbar.repaint();
    }

    private static JButton findInjectedButton(Container toolbar) {
        for (Component child : toolbar.getComponents()) {
            if (child instanceof JButton button && BUTTON_NAME.equals(button.getName())) {
                return button;
            }
        }
        return null;
    }

    static JButton addCopyButton(JToolBar toolbar, Runnable onCopy) {
        JButton existing = findInjectedButton(toolbar);
        if (existing != null) {
            return existing;
        }
        int last = toolbar.getComponentCount() - 1;
        if (last < 0 || !(toolbar.getComponent(last) instanceof JToggleButton)) {
            return null;
        }
        JButton button = UIUtils.createToolbarButton("copy-action-items.svg", 16,
                NbBundle.getMessage(ActionItemsCopyButton.class, "TT_ActionItemsCopyAll"),
                event -> onCopy.run());
        button.setName(BUTTON_NAME);
        button.setFocusable(false);
        toolbar.add(button);
        toolbar.revalidate();
        toolbar.repaint();
        return button;
    }

    private static <T extends Component> T findChild(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = findChild(container, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends Component> List<T> findChildren(Container root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                matches.add(type.cast(child));
            }
            if (child instanceof Container container) {
                matches.addAll(findChildren(container, type));
            }
        }
        return matches;
    }

    record TaskDetails(String description, String file, String location) {}

    private record TaskSource(Object taskList, List<?> tasks) {}

    private record TaskAccess(
            List<?> tasks,
            Method getDescription,
            Method getFile,
            Method getLocation) {

        private List<TaskDetails> readItems() {
            List<TaskDetails> items = new ArrayList<>(tasks.size());
            for (Object task : tasks) {
                String description = read(getDescription, task);
                if (clean(description) != null) {
                    items.add(new TaskDetails(
                            description, read(getFile, task), read(getLocation, task)));
                }
            }
            return items;
        }

        private static String read(Method method, Object task) {
            try {
                return (String) method.invoke(null, task);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return null;
            }
        }
    }
}
