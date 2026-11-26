package github.anandb.netbeans.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.SlidingTask;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = ACPProjectManager.class)
public class ACPProjectManager implements PropertyChangeListener {
    private static final Logger LOG = Logger.from(ACPProjectManager.class);

    /** Coalescing window: a burst of project events syncs the cache once. */
    static final int SYNC_DELAY_MS = 250;

    private static final RequestProcessor RP = new RequestProcessor("ACP-ProjectSync", 1);

    /** Sliding task: every event reschedules it, so a burst recomputes once. */
    private final SlidingTask syncTask = new SlidingTask(RP, SYNC_DELAY_MS, this::syncActiveProject);

    private final AtomicReference<Project[]> currentProjects = new AtomicReference<>(new Project[0]);
    private final OpenProjectsSource projectSource;
    private volatile Consumer<String> projectCloseListener;
    private volatile Consumer<String> projectOpenListener;

    public ACPProjectManager() {
        this(new NBOpenProjectsSource());
    }

    /** Test seam: inject a fake project source instead of the NetBeans singleton. */
    ACPProjectManager(OpenProjectsSource projectSource) {
        this.projectSource = projectSource;
    }

    public static ACPProjectManager getInstance() {
        ACPProjectManager pm = Lookup.getDefault().lookup(ACPProjectManager.class);
        if (pm == null) {
            pm = new ACPProjectManager();
        }
        return pm;
    }

    public void setProjectCloseListener(Consumer<String> listener) {
        this.projectCloseListener = listener;
    }

    public void setProjectOpenListener(Consumer<String> listener) {
        this.projectOpenListener = listener;
        // Replay open events for any projects already loaded before the listener was registered.
        // Without this, if OpenProjects fires between start() and SessionManager registration,
        // the open event is silently dropped and the session dropdown stays empty.
        if (listener != null && currentProjects.get() != null) {
            for (Project p : currentProjects.get()) {
                if (p != null) {
                    listener.accept(projectSource.nativePath(p));
                }
            }
        }
    }

    public void start() {
        projectSource.addPropertyChangeListener(this);
        LOG.fine("ACPProjectManager started, tracking projects.");
        // Immediate first sync: startup readers must see the current list without
        // waiting for the coalescing window.
        syncActiveProject();
    }

    /** Reverses {@link #start()}: unregisters from the project source so the
     *  module can shut down without leaving a listener on the IDE singleton. */
    public void stop() {
        projectSource.removePropertyChangeListener(this);
        LOG.fine("ACPProjectManager stopped.");
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (isProjectEvent(evt.getPropertyName())) {
            // OpenProjects fires once per project during startup / group switches;
            // the last trigger recomputes the diff, so earlier ones are redundant.
            syncTask.schedule();
        }
    }

    /** True for the two {@code OpenProjects} properties that change the project set. */
    static boolean isProjectEvent(String prop) {
        return OpenProjects.PROPERTY_MAIN_PROJECT.equals(prop)
            || OpenProjects.PROPERTY_OPEN_PROJECTS.equals(prop);
    }

    void syncActiveProject() {
        Project[] openProjects = projectSource.getOpenProjects();

        Set<String> oldDirs = dirsOf(currentProjects.get());
        Set<String> newDirs = dirsOf(openProjects);

        // Update currentProjects BEFORE calling listeners so their use of
        // getAllOpenProjects() (e.g. refreshSessions()) sees the correct list.
        currentProjects.set(openProjects);

        Set<String> closedDirs = new HashSet<>(oldDirs);
        closedDirs.removeAll(newDirs);

        if (!closedDirs.isEmpty()) {
            for (String closedDir : closedDirs) {
                LOG.fine("Project closed: {0}", closedDir);
                if (projectCloseListener != null) {
                    projectCloseListener.accept(closedDir);
                }
            }
        }

        Set<String> openedDirs = new HashSet<>(newDirs);
        openedDirs.removeAll(oldDirs);

        if (!openedDirs.isEmpty()) {
            for (String openedDir : openedDirs) {
                LOG.fine("Project opened: {0}", openedDir);
                if (projectOpenListener != null) {
                    projectOpenListener.accept(openedDir);
                }
            }
        }
    }

    private Set<String> dirsOf(Project[] projects) {
        Set<String> dirs = new HashSet<>();
        if (projects != null) {
            for (Project p : projects) {
                if (p != null) {
                    dirs.add(projectSource.nativePath(p));
                }
            }
        }
        return dirs;
    }

    public Project[] getAllOpenProjects() {
        Project[] projects = currentProjects.get();
        if (projects == null) {
            return new Project[0];
        }
        return Arrays.copyOf(projects, projects.length);
    }
}
