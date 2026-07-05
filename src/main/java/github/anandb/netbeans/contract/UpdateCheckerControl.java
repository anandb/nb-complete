package github.anandb.netbeans.contract;

/**
 * Control interface for the background update checker.
 * Lower layers should depend on this interface rather than the concrete
 * manager/UpdateCheckerService.
 */
public interface UpdateCheckerControl {

    /** Starts the background update-check loop. Safe to call multiple times. */
    void start();

    /** Called on install or upgrade to schedule the first check. */
    void onInstallOrUpgrade();
}
