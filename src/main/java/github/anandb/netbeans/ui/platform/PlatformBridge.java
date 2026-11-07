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
 * {@link #get()} so {@code ui/} code obtains platform services from a single
 * seam. The default implementation ({@link DefaultPlatformBridge}) wraps the
 * existing NetBeans APIs.
 * <p>
 * <b>Migration state:</b> {@code ui/} no longer calls
 * {@code Lookup.getDefault().lookup(...)} or {@code NbPreferences.forModule(...)}
 * directly. It uses {@link #sessionControl()}, {@link #processControl()},
 * {@link #serviceSafe(Class)}, and {@link #prefStoreSafe()} instead. The few
 * remaining direct uses are the bridge implementation itself and a contract
 * method that accepts a {@code Lookup} context
 * ({@code SlashCommandInterceptor.intercept}).
 */
public interface PlatformBridge {
    SessionService sessionService();
    ProcessService processService();
    PrefStore prefStore();
    Bundle bundle();
    ProjectContext projectContext();

    /**
     * Resolves a contract service through the bridge. Call sites must not touch
     * {@code Lookup} directly; this is the single seam that hides it.
     */
    <T> T service(Class<T> type);

    /** The registered bridge, or {@code null} when the platform is not available. */
    static PlatformBridge get() {
        return Lookup.getDefault().lookup(PlatformBridge.class);
    }

    /**
     * Resolves a contract service, falling back to NetBeans Lookup when no
     * bridge is registered (headless/partial environments).
     */
    static <T> T serviceSafe(Class<T> type) {
        PlatformBridge bridge = get();
        if (bridge != null) {
            T service = bridge.service(type);
            if (service != null) return service;
        }
        // Also the fallback when a mock bridge answers null: keep Lookup behavior.
        return Lookup.getDefault().lookup(type);
    }

    /** The preference store, falling back to the NetBeans preferences node. */
    static PrefStore prefStoreSafe() {
        PlatformBridge bridge = get();
        return bridge != null ? bridge.prefStore() : NbPrefStore.INSTANCE;
    }

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

    /** The session-control service, or a no-op stand-in when the platform is absent. */
    static SessionControl sessionControl() {
        return sessionServiceSafe().get();
    }

    /** The process-control service, or a no-op stand-in when the platform is absent. */
    static ProcessControl processControl() {
        return processServiceSafe().get();
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
