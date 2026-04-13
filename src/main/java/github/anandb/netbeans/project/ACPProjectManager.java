package github.anandb.netbeans.project;

import github.anandb.netbeans.manager.ACPManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileObject;

public class ACPProjectManager implements PropertyChangeListener {
    private static final Logger LOG = Logger.getLogger(ACPProjectManager.class.getName());
    private static ACPProjectManager instance;

    private ACPProjectManager() {
    }

    public static synchronized ACPProjectManager getInstance() {
        if (instance == null) {
            instance = new ACPProjectManager();
        }
        return instance;
    }

    public void start() {
        OpenProjects.getDefault().addPropertyChangeListener(this);
        LOG.info("ACPProjectManager started, tracking projects.");
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
        Project main = OpenProjects.getDefault().getMainProject();
        if (main != null) {
            FileObject dir = main.getProjectDirectory();
            String path = dir.getPath();
            LOG.log(Level.INFO, "Active project synchronized: {0}", path);
            ACPManager.getInstance().setActiveProject(path);
        } else {
            // Handle case where no project is active - maybe default to user dir?
            ACPManager.getInstance().setActiveProject(null);
        }
    }
}
