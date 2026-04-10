package ai.opencode.netbeans.project;

import ai.opencode.netbeans.manager.OpenCodeManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileObject;

public class OpenCodeProjectManager implements PropertyChangeListener {
    private static final Logger LOG = Logger.getLogger(OpenCodeProjectManager.class.getName());
    private static OpenCodeProjectManager instance;

    private OpenCodeProjectManager() {
    }

    public static synchronized OpenCodeProjectManager getInstance() {
        if (instance == null) {
            instance = new OpenCodeProjectManager();
        }
        return instance;
    }

    public void start() {
        OpenProjects.getDefault().addPropertyChangeListener(this);
        LOG.info("OpenCodeProjectManager started, tracking projects.");
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
            LOG.info("Active project synchronized: " + path);
            OpenCodeManager.getInstance().setActiveProject(path);
        } else {
            // Handle case where no project is active - maybe default to user dir?
            OpenCodeManager.getInstance().setActiveProject(null);
        }
    }
}
