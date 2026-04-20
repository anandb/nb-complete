package github.anandb.netbeans.project;

import github.anandb.netbeans.manager.ACPManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileObject;

public class ACPProjectManager implements PropertyChangeListener {
    private static final Logger LOG = Logger.getLogger(ACPProjectManager.class.getName());
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
        Project main = OpenProjects.getDefault().getMainProject();

        Set<String> oldDirs = dirsOf(currentProjects);
        Set<String> newDirs = dirsOf(openProjects);

        Set<String> closedDirs = new HashSet<>(oldDirs);
        closedDirs.removeAll(newDirs);

        if (!closedDirs.isEmpty()) {
            for (String closedDir : closedDirs) {
                LOG.log(Level.FINE, "Project closed: {0}", closedDir);
                if (projectCloseListener != null) {
                    projectCloseListener.accept(closedDir);
                }
            }
        }

        Set<String> openedDirs = new HashSet<>(newDirs);
        openedDirs.removeAll(oldDirs);

        if (!openedDirs.isEmpty()) {
            for (String openedDir : openedDirs) {
                LOG.log(Level.FINE, "Project opened: {0}", openedDir);
                if (projectOpenListener != null) {
                    projectOpenListener.accept(openedDir);
                }
            }
        }

        Project active = main != null ? main : (openProjects.length > 0 ? openProjects[0] : null);
        if (active != null) {
            FileObject dir = active.getProjectDirectory();
            String path = dir.getPath();
            LOG.log(Level.FINE, "Active project synchronized: {0}", path);
            ACPManager.getInstance().setActiveProject(path);
        } else {
            ACPManager.getInstance().setActiveProject(null);
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
