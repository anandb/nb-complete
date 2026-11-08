package github.anandb.netbeans.tasks;

import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;

/** Shared project-name resolution for the Beanbot Tasks feature. */
final class TasksProject {

    private TasksProject() {
    }

    /** Resolves a project's display name, falling back to the directory name. */
    static String displayName(Project project) {
        ProjectInformation info = ProjectUtils.getInformation(project);
        if (info != null && info.getDisplayName() != null && !info.getDisplayName().isEmpty()) {
            return info.getDisplayName();
        }
        return project.getProjectDirectory().getName();
    }
}
