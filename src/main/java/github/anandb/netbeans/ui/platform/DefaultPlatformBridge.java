package github.anandb.netbeans.ui.platform;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import java.util.logging.Level;

import javax.swing.SwingUtilities;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;
import github.anandb.netbeans.project.ACPProjectManager;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PreferenceKeys;

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
public final class DefaultPlatformBridge implements PlatformBridge {

    private final SessionService sessionService = new SessionServiceImpl();
    private final ProcessService processService = new ProcessServiceImpl();
    private final PrefStore prefStore = new PrefStoreImpl();
    private final Bundle bundle = new BundleImpl();
    private final ProjectContext projectContext = new ProjectContextImpl();

    @Override public SessionService sessionService() { return sessionService; }
    @Override public ProcessService processService() { return processService; }
    @Override public PrefStore prefStore() { return prefStore; }
    @Override public Bundle bundle() { return bundle; }
    @Override public ProjectContext projectContext() { return projectContext; }

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

    private static final class PrefStoreImpl implements PrefStore {
        private Preferences prefs() {
            return NbPreferences.forModule(PreferenceKeys.MODULE_ANCHOR);
        }
        @Override public boolean getBoolean(String key, boolean def) { return prefs().getBoolean(key, def); }
        @Override public void putBoolean(String key, boolean v) { prefs().putBoolean(key, v); }
        @Override public int getInt(String key, int def) { return prefs().getInt(key, def); }
        @Override public void putInt(String key, int v) { prefs().putInt(key, v); }
        @Override public String get(String key, String def) { return prefs().get(key, def); }
        @Override public void put(String key, String v) { prefs().put(key, v); }
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
        private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
        private volatile boolean listening;

        ProjectContextImpl() {
            // Register a PropertyChangeListener on OpenProjects to detect
            // project open/close events. We still read from the ACPProjectManager
            // cache — this is only for notification, not data access.
            OpenProjects.getDefault().addPropertyChangeListener((PropertyChangeEvent evt) -> {
                if (!OpenProjects.PROPERTY_OPEN_PROJECTS.equals(evt.getPropertyName())) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    for (Runnable r : changeListeners) {
                        r.run();
                    }
                });
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
