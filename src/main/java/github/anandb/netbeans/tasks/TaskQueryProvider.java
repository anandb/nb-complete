package github.anandb.netbeans.tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.SwingUtilities;

import org.netbeans.modules.bugtracking.spi.QueryController;
import org.netbeans.modules.bugtracking.spi.QueryProvider;
import org.openide.util.RequestProcessor;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.tasks.TasksModel.TaskQuery;
import org.openide.util.Lookup;

/**
 * {@link QueryProvider} for the single fixed "All tasks" query per repository.
 * {@link #refresh} reloads the repository's CSV from disk (off the EDT) and
 * republishes the list; store {@link TaskRepositoryControl.RepositoryListener}s
 * republish on in-session changes.
 */
public final class TaskQueryProvider implements QueryProvider<TaskQuery, TaskIssue> {

    private static final RequestProcessor RP = new RequestProcessor("BeanbotTasks-Refresh", 1, true);
    private static final Logger LOG = Logger.from(TaskQueryProvider.class);

    // Containers are keyed by a composite (repository id, query kind) — NOT the
    // TaskQuery instance and not the bare repo id — because a repository now
    // exposes three fixed queries (All / Open / Closed tasks), each rendering a
    // different status-filtered list into its own Dashboard node. The framework
    // constructs fresh TaskQuery instances per getQueries()/createQuery() call,
    // so an instance-keyed map would miss the container when the Find Tasks
    // editor re-runs a query on a different instance, and a repo-only key would
    // publish the same (unstatused) list to every query node. One IssueContainer
    // per QueryImpl; the framework may create several for the same key (one per
    // Dashboard tree node), so we keep ALL of them per key and publish to every
    // one — last-write-wins would redirect filter pushes to only the newest
    // container and miss the visible Dashboard list.
    private final Map<QueryKey, Set<IssueContainer<TaskIssue>>> containers = new ConcurrentHashMap<>();
    // Authoritative, (repo, kind)-scoped tag filter. Stored here (not on the
    // TaskQuery instance) because the framework spins up fresh TaskQuery
    // instances per getQueries()/getController() call: the store listener and
    // the Find Tasks editor would otherwise hold different instances with
    // diverging filters.
    private final Map<QueryKey, Set<String>> tagFilters = new ConcurrentHashMap<>();
    private final Set<QueryKey> registeredQueries = new HashSet<>();

    private TaskRepositoryControl store() {
        return Lookup.getDefault().lookup(TaskRepositoryControl.class);
    }

    /** Stable identity of a fixed query: its repo id and status kind. */
    private record QueryKey(String repoId, TaskQuery.Kind kind) {
    }

    private QueryKey keyOf(TaskQuery q) {
        String repoId = repoOf(q);
        return repoId == null ? null : new QueryKey(repoId, q.getKind());
    }

    private String repoOf(TaskQuery q) {
        return q.getRepository() == null ? null : q.getRepository().getRepositoryId();
    }

    @Override
    public String getDisplayName(TaskQuery q) {
        return q.getName();
    }

    @Override
    public String getTooltip(TaskQuery q) {
        return q.getName();
    }

    @Override
    public QueryController getController(TaskQuery q) {
        return new TaskQueryController(this, q);
    }

    @Override
    public boolean canRemove(TaskQuery q) {
        return false;
    }

    @Override
    public void remove(TaskQuery q) {
    }

    @Override
    public boolean canRename(TaskQuery q) {
        return false;
    }

    @Override
    public void rename(TaskQuery q, String newName) {
    }

    @Override
    public void setIssueContainer(TaskQuery q, IssueContainer<TaskIssue> container) {
        QueryKey key = keyOf(q);
        if (key == null) {
            LOG.warn("setIssueContainer: repository id is null for query {0}", q.getName());
            return;
        }
        LOG.info("setIssueContainer: registering container for repo {0} kind {1}", key.repoId(), key.kind());
        containers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(container);
        if (registeredQueries.add(key)) {
            TaskRepositoryControl s = store();
            if (s != null) {
                s.addListener((r, type, taskId) -> {
                    LOG.info("setIssueContainer: store change {0} for repo {1}", type, r);
                    if (r.equals(key.repoId())) {
                        // Re-run every query kind of this repo (All/Open/Closed)
                        // so each Dashboard node reflects the store change.
                        for (QueryKey k : Set.copyOf(containers.keySet())) {
                            if (k.repoId().equals(key.repoId())) {
                                push(k);
                            }
                        }
                    }
                });
            }
        }
        push(key);
    }

    /**
     * Called by the Find Tasks editor when the chosen tags change. Stores the
     * (repo, kind)-scoped filter and republishes the query results immediately
     * (no disk reload needed for a pure filter change).
     */
    public void setTagFilter(TaskQuery q, Set<String> tags) {
        QueryKey key = keyOf(q);
        if (key == null) {
            return;
        }
        tagFilters.put(key, tags == null ? Set.of() : Set.copyOf(tags));
        push(key);
    }

    @Override
    public void refresh(TaskQuery q) {
        QueryKey key = keyOf(q);
        if (key == null) {
            return;
        }
        LOG.info("refresh: repo {0} kind {1} tagFilter={2}", key.repoId(), key.kind(), q.getTagFilter());
        // Reload from disk (off the EDT) so external edits/deletions to the CSV
        // are picked up, then publish the results. The RELOADED event fired by
        // reload() also re-pushes via the (repo, kind)-scoped store listener;
        // both paths read the same filter map, so they cannot diverge.
        RP.post(() -> {
            TaskRepositoryControl s = store();
            if (s != null) {
                s.reload(key.repoId());
            }
            push(key);
        });
    }

    /**
     * Re-runs the query's status scope (reloading from the store) and publishes
     * the results, applying the query's transient
     * {@link TaskQuery#getTagFilter()} so the Find Issues dialog shows only
     * tasks carrying at least one selected tag.
     */
    private void push(QueryKey key) {
        Set<IssueContainer<TaskIssue>> holders = containers.get(key);
        if (holders == null || holders.isEmpty()) {
            LOG.warn("push: no container registered for repo {0} kind {1}", key.repoId(), key.kind());
            return;
        }
        Set<String> wanted = tagFilters.getOrDefault(key, Set.of());
        List<TaskIssue> issues = new ArrayList<>();
        TaskRepositoryControl s = store();
        if (s != null) {
            for (TaskRecord t : s.list(key.repoId())) {
                if (matchesStatus(t, key.kind()) && matchesTagFilter(t, wanted)) {
                    issues.add(TaskIssueCache.get(key.repoId(), t));
                }
            }
        }
        LOG.info("push: repo {0} kind {1} matched {2}/{3} tasks, tagFilter={4}",
                key.repoId(), key.kind(), issues.size(), s == null ? 0 : s.list(key.repoId()).size(), wanted);
        TaskIssue[] arr = issues.toArray(new TaskIssue[0]);
        Runnable deliver = () -> {
            for (IssueContainer<TaskIssue> c : holders) {
                c.restoreStarted();
                c.clear();
                c.add(arr);
                c.restoreFinished();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            deliver.run();
        } else {
            SwingUtilities.invokeLater(deliver);
        }
    }

    /** Applies the query's status scope; {@link Kind#ALL} shows every task. */
    private static boolean matchesStatus(TaskRecord t, TaskQuery.Kind kind) {
        switch (kind) {
            case OPEN:
                return !t.isFinished();
            case CLOSED:
                return t.isFinished();
            case ALL:
            default:
                return true;
        }
    }

    /** OR semantics: a task matches when it has no filter, or carries any wanted tag. */
    private static boolean matchesTagFilter(TaskRecord t, Set<String> wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        if (t.tags() == null || t.tags().isBlank()) {
            return false;
        }
        Set<String> have = new HashSet<>(List.of(t.tags().split(",")));
        for (String w : wanted) {
            for (String h : have) {
                if (h.trim().equalsIgnoreCase(w)) {
                    return true;
                }
            }
        }
        return false;
    }
}