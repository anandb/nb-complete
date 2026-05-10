package github.anandb.netbeans.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import github.anandb.netbeans.support.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;

public class ACPProjectManager implements PropertyChangeListener {
    private static final Logger LOG = new Logger(ACPProjectManager.class);
    private static ACPProjectManager instance;

    private Project[] currentProjects = new Project[0];
    private Consumer<String> projectCloseListener;
    private Consumer<String> projectOpenListener;

    private ACPProjectManager() {
    }

    public static synchronized ACPProjectManager getInstance() {
        if (instance == null) {
            instance = new ACPProjectManager();
        }
        return instance;
    }

    public void setProjectCloseListener(Consumer<String> listener) {
        this.projectCloseListener = listener;
    }

    public void setProjectOpenListener(Consumer<String> listener) {
        this.projectOpenListener = listener;
    }

    public void start() {
        OpenProjects.getDefault().addPropertyChangeListener(this);
        LOG.fine("ACPProjectManager started, tracking projects.");
        syncActiveProject();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        if (OpenProjects.PROPERTY_MAIN_PROJECT.equals(prop) || 
            OpenProjects.PROPERTY_OPEN_PROJECTS.equals(prop)) {
            syncActiveProject();
        }
    }

    private void syncActiveProject() {
        Project[] openProjects = OpenProjects.getDefault().getOpenProjects();

        Set<String> oldDirs = dirsOf(currentProjects);
        Set<String> newDirs = dirsOf(openProjects);

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

        currentProjects = openProjects;
    }

    private Set<String> dirsOf(Project[] projects) {
        Set<String> dirs = new HashSet<>();
        if (projects != null) {
            for (Project p : projects) {
                if (p != null) {
                    dirs.add(p.getProjectDirectory().getPath());
                }
            }
        }
        return dirs;
    }

    public Project[] getAllOpenProjects() {
        return OpenProjects.getDefault().getOpenProjects();
    }
}
