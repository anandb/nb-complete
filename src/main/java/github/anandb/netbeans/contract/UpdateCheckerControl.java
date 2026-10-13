package github.anandb.netbeans.contract;

import java.util.concurrent.CompletableFuture;

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

    /** Runs one update check immediately without changing the background schedule. */
    CompletableFuture<UpdateCheckResult> checkNow();

    /** Outcome of one update check. */
    enum UpdateCheckResult {
        UPDATE_AVAILABLE,
        NO_UPDATE,
        UPDATE_SKIPPED,
        CHECK_FAILED
    }
}
