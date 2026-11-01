package github.anandb.netbeans.manager;

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.ToolCallRecorder;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.UsageRecords.Attribution;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.ToolCallStatus;
import java.util.function.Supplier;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * {@link ToolCallRecorder} over the usage-stats store: resolves the attribution
 * from {@link SessionQuery} when the execution ends and hands one row per call to
 * the store, which persists it asynchronously.
 *
 * <p>Attribution is captured here, at execution time, rather than when a turn
 * ends: a session switch, a stop, a server restart or a shutdown between the call
 * and the turn boundary can therefore never move a call to another session or
 * lose it. A call issued while no session is active is stored with a null
 * session id instead of being dropped.</p>
 */
@ServiceProvider(service = ToolCallRecorder.class)
public class ToolCallUsageRecorder implements ToolCallRecorder {

    private final Supplier<SessionQuery> sessions;
    private final Supplier<UsageStatsStore> stores;

    /** Service-provider constructor: both seams are resolved from Lookup per call. */
    public ToolCallUsageRecorder() {
        this(() -> Lookup.getDefault().lookup(SessionQuery.class),
                () -> Lookup.getDefault().lookup(UsageStatsStore.class));
    }

    /** Test constructor with explicit seams. */
    ToolCallUsageRecorder(SessionQuery sessions, UsageStatsStore store) {
        this(() -> sessions, () -> store);
    }

    private ToolCallUsageRecorder(Supplier<SessionQuery> sessions, Supplier<UsageStatsStore> stores) {
        this.sessions = sessions;
        this.stores = stores;
    }

    @Override
    public void record(String toolName, long capturedAt, long durationMs, ToolCallStatus status) {
        if (toolName == null || toolName.isBlank() || status == null) {
            return;
        }
        UsageStatsStore store = stores.get();
        if (store == null) {
            return;
        }
        SessionQuery query = sessions.get();
        String sessionId = query != null ? query.getCurrentSessionId() : null;
        String harnessId = query != null ? query.getHarnessId() : null;
        String modelId = null;
        String project = null;
        if (query != null && sessionId != null) {
            modelId = query.getSessionModelId(sessionId);
            project = query.getSessionDirectory(sessionId);
        }
        store.recordToolCall(new ToolCallExecution(
                new Attribution(sessionId, harnessId, modelId, project),
                capturedAt, toolName, Math.max(durationMs, 0), status));
    }
}
