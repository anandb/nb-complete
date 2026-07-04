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
}
