package github.anandb.netbeans.ui.platform;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.netbeans.api.project.Project;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.ProcessControl;
import github.anandb.netbeans.contract.SessionControl;

/**
 * Aggregator for the NetBeans platform-access seams. Resolved via
 * {@code Lookup.getDefault().lookup(PlatformBridge.class)} so DSL-bound views
 * obtain all platform services from a single lookup. The default implementation
 * ({@link DefaultPlatformBridge}) wraps the existing NetBeans APIs.
 * <p>
 * <b>Phase 4 seam (current state):</b> the interfaces are defined and a default
 * impl is registered, but existing ui/ call sites still use
 * {@code Lookup.getDefault().lookup(SessionControl.class)} /
 * {@code NbPreferences.forModule(...)} /
 * {@code NbBundle.getMessage(...)} /
 * {@code ACPProjectManager.getInstance()} directly. Migrating those call sites
 * is a follow-up PR (tracked in MIGRATION.md); it is a large mechanical change
 * with behavioral risk and is deferred.
 */
public interface PlatformBridge {
    SessionService sessionService();
    ProcessService processService();
    PrefStore prefStore();
    Bundle bundle();
    ProjectContext projectContext();

    static SessionService sessionServiceSafe() {
        return () -> {
            PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
            if (bridge != null) return bridge.sessionService().get();
            return createDummy(SessionControl.class);
        };
    }

    static ProcessService processServiceSafe() {
        return () -> {
            PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
            if (bridge != null) return bridge.processService().get();
            return createDummy(ProcessControl.class);
        };
    }

    static ProjectContext projectContextSafe() {
        return new ProjectContext() {
            @Override
            public Project[] getAllOpenProjects() {
                PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
                if (bridge != null) return bridge.projectContext().getAllOpenProjects();
                return new Project[0];
            }
            @Override
            public void addProjectChangeListener(Runnable listener) {
                PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
                if (bridge != null) bridge.projectContext().addProjectChangeListener(listener);
            }
            @Override
            public void removeProjectChangeListener(Runnable listener) {
                PlatformBridge bridge = Lookup.getDefault().lookup(PlatformBridge.class);
                if (bridge != null) bridge.projectContext().removeProjectChangeListener(listener);
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <T> T createDummy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
            clazz.getClassLoader(),
            new Class<?>[]{clazz},
            (p, m, a) -> {
                Class<?> ret = m.getReturnType();
                if (ret == boolean.class) return false;
                if (ret == int.class) return 0;
                if (ret == long.class) return 0L;
                if (ret == double.class) return 0.0;
                if (ret == String.class) return "";
                if (ret == CompletableFuture.class) return CompletableFuture.completedFuture(null);
                if (ret == List.class) return Collections.emptyList();
                if (ret == Map.class) return Collections.emptyMap();
                return null;
            }
        );
    }
}
