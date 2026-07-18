package github.anandb.netbeans.ui.platform;

import org.netbeans.api.project.Project;

/**
 * Access seam for {@code ACPProjectManager.getInstance().getAllOpenProjects()}.
 * Hides the project-cache singleton from DSL-bound views.
 * <p>
 * <b>Cache contract (AGENTS.md):</b> implementations must return the cached
 * {@code currentProjects} field populated by the manager's {@code start()} and
 * updated via {@code propertyChange()} on project open/close — do NOT bypass
 * the cache by calling {@code OpenProjects.getDefault().getOpenProjects()}.
 * <p>
 * Not Swing-free — carries {@link Project} (a NetBeans API type). This is a
 * platform-bridge type, so NetBeans types are expected here.
 */
public interface ProjectContext {
    Project[] getAllOpenProjects();

    /**
     * Register a listener that fires when the set of open projects changes
     * (project opened or closed). The listener runs on the EDT.
     */
    void addProjectChangeListener(Runnable listener);

    /** Remove a previously registered project change listener. */
    void removeProjectChangeListener(Runnable listener);
}
