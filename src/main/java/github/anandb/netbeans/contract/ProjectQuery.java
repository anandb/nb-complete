package github.anandb.netbeans.contract;

import org.netbeans.api.project.Project;

import java.util.function.Consumer;

/**
 * Query interface for accessing the cached list of open projects and observing
 * project open/close events. Lives in the {@code contract} layer so {@code manager/}
 * and {@code mcp/} can depend on it without importing {@code ui/} or {@code project/}.
 * <p>
 * <b>Cache contract (AGENTS.md):</b> implementations must return the cached
 * {@code currentProjects} field populated by the manager's {@code start()} and
 * updated via {@code propertyChange()} on project open/close — do NOT bypass
 * the cache by calling {@code OpenProjects.getDefault().getOpenProjects()}.
 * <p>
 * Not Swing-free — carries {@link Project} (a NetBeans API type). This is a
 * platform boundary type, so NetBeans types are expected here.
 */
public interface ProjectQuery {

    /**
     * Returns all currently open projects from the cache.
     * Never returns {@code null}; returns empty array if no projects open.
     */
    Project[] getAllOpenProjects();

    /** Registers a listener invoked with the project directory path when a project opens. */
    void setProjectOpenListener(Consumer<String> listener);

    /** Registers a listener invoked with the project directory path when a project closes. */
    void setProjectCloseListener(Consumer<String> listener);
}