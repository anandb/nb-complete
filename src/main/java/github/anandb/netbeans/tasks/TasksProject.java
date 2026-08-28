package github.anandb.netbeans.tasks;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;

/** Shared project-name resolution for the Beanbot Tasks feature. */
final class TasksProject {

    private TasksProject() {
    }

    /** Resolves a project's display name, falling back to the directory name. */
    static String displayName(Project project) {
        ProjectInformation info = project.getLookup().lookup(ProjectInformation.class);
        if (info != null && info.getDisplayName() != null && !info.getDisplayName().isEmpty()) {
            return info.getDisplayName();
        }
        return project.getProjectDirectory().getName();
    }
}
