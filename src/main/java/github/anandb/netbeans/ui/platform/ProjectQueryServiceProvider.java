package github.anandb.netbeans.ui.platform;

import org.netbeans.api.project.Project;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.function.Consumer;

import github.anandb.netbeans.contract.ProjectQuery;

/**
 * Adapter exposing {@link ProjectQuery} (a {@code contract/} port) for
 * {@code manager/} and {@code mcp/} consumers that must not depend on the
 * presentation {@code ui/} layer. Registered via {@code @ServiceProvider} so
 * {@code Lookup.getDefault().lookup(ProjectQuery.class)} resolves it, delegating
 * to the {@link PlatformBridge}'s project context (which reads the cached
 * project list without bypassing {@code ACPProjectManager}).
 */
@ServiceProvider(service = ProjectQuery.class)
public final class ProjectQueryServiceProvider implements ProjectQuery {

    private static ProjectQuery bridgeOrNull() {
        PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
        return bridge instanceof ProjectQuery pq ? pq : null;
    }

    @Override
    public Project[] getAllOpenProjects() {
        ProjectQuery pq = bridgeOrNull();
        return pq == null ? new Project[0] : pq.getAllOpenProjects();
    }

    @Override
    public void setProjectOpenListener(Consumer<String> listener) {
        ProjectQuery pq = bridgeOrNull();
        if (pq != null) {
            pq.setProjectOpenListener(listener);
        }
    }

    @Override
    public void setProjectCloseListener(Consumer<String> listener) {
        ProjectQuery pq = bridgeOrNull();
        if (pq != null) {
            pq.setProjectCloseListener(listener);
        }
    }
}