package github.anandb.netbeans.ui.platform;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.swing.SwingUtilities;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.SlidingTask;

/**
 * Default {@link PlatformBridge} — wraps the existing NetBeans APIs (Lookup,
 * NbPreferences, NbBundle, ACPProjectManager) without changing behavior. This
 * is the only implementation today; a headless test harness can register an
 * alternative via {@code Lookup.getDefault().lookup(PlatformBridge.class)}.
 * <p>
 * <b>Not Swing-free</b> — lives in the platform-bridge layer, so NetBeans
 * imports are expected. DSL-bound views depend only on the interfaces, not on
 * this class.
 */
@ServiceProvider(service = PlatformBridge.class)
public final class DefaultPlatformBridge implements PlatformBridge, ProjectQuery {

    private final SessionService sessionService = new SessionServiceImpl();
    private final ProcessService processService = new ProcessServiceImpl();
    private final PrefStore prefStore = NbPrefStore.INSTANCE;
    private final Bundle bundle = new BundleImpl();
    private final ProjectContext projectContext = new ProjectContextImpl();

    @Override public SessionService sessionService() { return sessionService; }
    @Override public ProcessService processService() { return processService; }
    @Override public PrefStore prefStore() { return prefStore; }
    @Override public Bundle bundle() { return bundle; }
    @Override public ProjectContext projectContext() { return projectContext; }

    @Override
    public <T> T service(Class<T> type) {
        return Lookup.getDefault().lookup(type);
    }

    /** @see ProjectQuery#getAllOpenProjects() */
    @Override
    public Project[] getAllOpenProjects() {
        return projectContext.getAllOpenProjects();
    }

    /** @see ProjectQuery#setProjectOpenListener(Consumer) */
    @Override
    public void setProjectOpenListener(Consumer<String> listener) {
        ACPProjectManager.getInstance().setProjectOpenListener(listener);
    }

    /** @see ProjectQuery#setProjectCloseListener(Consumer) */
    @Override
    public void setProjectCloseListener(Consumer<String> listener) {
        ACPProjectManager.getInstance().setProjectCloseListener(listener);
    }

    private static final class SessionServiceImpl implements SessionService {
        private static final Logger LOG = Logger.from(SessionServiceImpl.class);
        @Override public SessionControl get() {
            SessionControl sc = Lookup.getDefault().lookup(SessionControl.class);
            if (sc == null) {
                LOG.log(Level.WARNING, "SessionControl not found in Lookup — callers must handle null");
            }
            return sc;
        }
    }

    private static final class ProcessServiceImpl implements ProcessService {
        private static final Logger LOG = Logger.from(ProcessServiceImpl.class);
        @Override public ProcessControl get() {
            ProcessControl pc = Lookup.getDefault().lookup(ProcessControl.class);
            if (pc == null) {
                LOG.log(Level.WARNING, "ProcessControl not found in Lookup — callers must handle null");
            }
            return pc;
        }
    }

    private static final class BundleImpl implements Bundle {
        @Override public String message(Class<?> clazz, String key) {
            return NbBundle.getMessage(clazz, key);
        }
        @Override public String message(Class<?> clazz, String key, Object... params) {
            return NbBundle.getMessage(clazz, key, params);
        }
    }

    private static final class ProjectContextImpl implements ProjectContext {
        /** Coalescing window: a burst of project events notifies listeners once. */
        static final int CHANGE_DELAY_MS = 250;

        private static final RequestProcessor RP = new RequestProcessor("ACP-ProjectContext", 1);

        private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
        private final SlidingTask notifyTask = new SlidingTask(RP, CHANGE_DELAY_MS, this::fireChange);

        ProjectContextImpl() {
            // Register a PropertyChangeListener on OpenProjects to detect
            // project open/close events. We still read from the ACPProjectManager
            // cache — this is only for notification, not data access.
            // The listener fires once per project on startup and on group
            // switches, so a sliding task collapses the burst into one notify.
            OpenProjects.getDefault().addPropertyChangeListener((PropertyChangeEvent evt) -> {
                if (OpenProjects.PROPERTY_OPEN_PROJECTS.equals(evt.getPropertyName())) {
                    notifyTask.schedule();
                }
            });
        }

        /** Runs the fan-out on the EDT — one event per burst, not one per project. */
        private void fireChange() {
            SwingUtilities.invokeLater(() -> {
                for (Runnable r : changeListeners) {
                    r.run();
                }
            });
        }

        @Override public Project[] getAllOpenProjects() {
            // AGENTS.md: getAllOpenProjects() returns the cached
            // currentProjects field — do NOT bypass the cache.
            return ACPProjectManager.getInstance().getAllOpenProjects();
        }

        @Override public void addProjectChangeListener(Runnable listener) {
            changeListeners.add(listener);
        }

        @Override public void removeProjectChangeListener(Runnable listener) {
            changeListeners.remove(listener);
        }
    }
}
