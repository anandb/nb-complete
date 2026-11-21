package github.anandb.netbeans.project;

import java.beans.PropertyChangeListener;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;

import github.anandb.netbeans.support.ProjectPaths;

/** Production {@link OpenProjectsSource} over the NetBeans singleton. */
final class NBOpenProjectsSource implements OpenProjectsSource {

    @Override
    public Project[] getOpenProjects() {
        return OpenProjects.getDefault().getOpenProjects();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        OpenProjects.getDefault().addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        OpenProjects.getDefault().removePropertyChangeListener(listener);
    }

    @Override
    public String nativePath(Project project) {
        return ProjectPaths.nativePath(project);
    }
}
