package github.anandb.netbeans.ui.platform;

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
            PlatformBridge bridge = org.openide.util.Lookup.getDefault().lookup(PlatformBridge.class);
            if (bridge != null) return bridge.sessionService().get();
            return createDummy(github.anandb.netbeans.contract.SessionControl.class);
        };
    }

    static ProcessService processServiceSafe() {
        return () -> {
            PlatformBridge bridge = org.openide.util.Lookup.getDefault().lookup(PlatformBridge.class);
            if (bridge != null) return bridge.processService().get();
            return createDummy(github.anandb.netbeans.contract.ProcessControl.class);
        };
    }

    static ProjectContext projectContextSafe() {
        return new ProjectContext() {
            @Override
            public org.netbeans.api.project.Project[] getAllOpenProjects() {
                PlatformBridge bridge = org.openide.util.Lookup.getDefault().lookup(PlatformBridge.class);
                if (bridge != null) return bridge.projectContext().getAllOpenProjects();
                return new org.netbeans.api.project.Project[0];
            }
            @Override
            public void addProjectChangeListener(Runnable listener) {
                PlatformBridge bridge = org.openide.util.Lookup.getDefault().lookup(PlatformBridge.class);
                if (bridge != null) bridge.projectContext().addProjectChangeListener(listener);
            }
            @Override
            public void removeProjectChangeListener(Runnable listener) {
                PlatformBridge bridge = org.openide.util.Lookup.getDefault().lookup(PlatformBridge.class);
                if (bridge != null) bridge.projectContext().removeProjectChangeListener(listener);
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <T> T createDummy(Class<T> clazz) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
            clazz.getClassLoader(),
            new Class<?>[]{clazz},
            (p, m, a) -> {
                Class<?> ret = m.getReturnType();
                if (ret == boolean.class) return false;
                if (ret == int.class) return 0;
                if (ret == long.class) return 0L;
                if (ret == double.class) return 0.0;
                if (ret == String.class) return "";
                if (ret == java.util.concurrent.CompletableFuture.class) return java.util.concurrent.CompletableFuture.completedFuture(null);
                if (ret == java.util.List.class) return java.util.Collections.emptyList();
                if (ret == java.util.Map.class) return java.util.Collections.emptyMap();
                return null;
            }
        );
    }
}
