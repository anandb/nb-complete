package github.anandb.netbeans.project;

import java.beans.PropertyChangeListener;

import org.netbeans.api.project.Project;

/**
 * Test seam over the NetBeans {@code OpenProjects} singleton and
 * {@link github.anandb.netbeans.support.ProjectPaths#nativePath(Project)}.
 *
 * <p>{@link ACPProjectManager} uses this so tests can inject a fake and exercise
 * the project sync headlessly, without the platform {@code OpenProjects} instance.
 * Production uses {@link NBOpenProjectsSource}. Package-private: not part
 * of the public API.</p>
 */
interface OpenProjectsSource {

    /** Snapshot of currently open projects. Never {@code null}. */
    Project[] getOpenProjects();

    void addPropertyChangeListener(PropertyChangeListener listener);

    void removePropertyChangeListener(PropertyChangeListener listener);

    /** Native OS path of the project's directory. */
    String nativePath(Project project);
}
