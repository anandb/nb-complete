package github.anandb.netbeans.tasks;

import org.openide.modules.OnStart;
import org.openide.util.RequestProcessor;

/**
 * Safer than relying on the bugtracking framework's one-shot repository restore
 * (which may run before this connector is registered): at module startup,
 * re-register any persisted Beanbot Tasks repositories on a background thread
 * so they reappear in the Tasks Dashboard even on a fresh IDE session.
 *
 * <p>The work is deferred/debounced so the framework's own restore and this
 * connector's Lookup registration have settled first; re-registration is
 * idempotent (matched by connector id + repository id).</p>
 */
@OnStart
public final class TasksStartup implements Runnable {

    private static final RequestProcessor RP = new RequestProcessor("BeanbotTasks-Startup", 1, true);

    @Override
    public void run() {
        RP.post(() -> new TasksConnector().restoreFromMetadata(), 1500);
    }
}