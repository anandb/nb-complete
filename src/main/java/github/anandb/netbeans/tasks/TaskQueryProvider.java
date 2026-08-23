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

    // Containers are keyed by repository id, not by the TaskQuery instance: the
    // framework constructs fresh TaskQuery instances per getQueries()/createQuery()
    // call, so an instance-keyed map would miss the container when the Find Tasks
    // editor re-runs a query on a different instance of the same repository.
    // One IssueContainer per QueryImpl. The framework may create several
    // QueryImpls (hence containers) for the same repository — one per Dashboard
    // tree node — so we keep ALL of them per repo and publish to every one;
    // last-write-wins would redirect filter pushes to only the newest container
    // (e.g. the Find Tasks dialog's node) and miss the visible Dashboard list.
    private final Map<String, Set<IssueContainer<TaskIssue>>> containers = new ConcurrentHashMap<>();
    // Authoritative, repo-scoped tag filter. Stored here (not on the TaskQuery
    // instance) because the framework spins up fresh TaskQuery instances per
    // getQueries()/getController() call: the store listener and the Find Tasks
    // editor would otherwise hold different instances with diverging filters.
    private final Map<String, Set<String>> tagFilters = new ConcurrentHashMap<>();
    private final Set<String> registeredRepos = new HashSet<>();

    private TaskRepositoryControl store() {
        return Lookup.getDefault().lookup(TaskRepositoryControl.class);
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
        String repoId = repoOf(q);
        if (repoId == null) {
            LOG.warn("setIssueContainer: repository id is null for query {0}", q.getName());
            return;
        }
        LOG.info("setIssueContainer: registering container for repo {0}", repoId);
        containers.computeIfAbsent(repoId, k -> ConcurrentHashMap.newKeySet()).add(container);
        if (registeredRepos.add(repoId)) {
            TaskRepositoryControl s = store();
            if (s != null) {
                s.addListener((r, type, taskId) -> {
                    LOG.info("setIssueContainer: store change {0} for repo {1}", type, r);
                    if (r.equals(repoId)) {
                        // Push with the repo's current filter (map-backed), not a
                        // captured query instance that may carry a stale filter.
                        push(repoId);
                    }
                });
            }
        }
        push(repoId);
    }

    /**
     * Called by the Find Tasks editor when the chosen tags change. Stores the
     * repo-scoped filter and republishes the query results immediately (no disk
     * reload needed for a pure filter change).
     */
    public void setTagFilter(TaskQuery q, Set<String> tags) {
        String repoId = repoOf(q);
        if (repoId == null) {
            return;
        }
        tagFilters.put(repoId, tags == null ? Set.of() : Set.copyOf(tags));
        push(repoId);
    }

    @Override
    public void refresh(TaskQuery q) {
        String repoId = repoOf(q);
        if (repoId == null) {
            return;
        }
        LOG.info("refresh: repo {0} tagFilter={1}", repoId, q.getTagFilter());
        // Reload from disk (off the EDT) so external edits/deletions to the CSV
        // are picked up, then publish the results. The RELOADED event fired by
        // reload() also re-pushes via the repo-scoped store listener; both paths
        // read the same repo-scoped filter map, so they cannot diverge.
        RP.post(() -> {
            TaskRepositoryControl s = store();
            if (s != null) {
                s.reload(repoId);
            }
            push(repoId);
        });
    }

    /**
     * Re-runs the query (reloading from the store) and publishes the results,
     * applying the query's transient {@link TaskQuery#getTagFilter()} so the
     * Find Issues dialog shows only tasks carrying at least one selected tag.
     */
    private void push(String repoId) {
        Set<IssueContainer<TaskIssue>> holders = containers.get(repoId);
        if (holders == null || holders.isEmpty()) {
            LOG.warn("push: no container registered for repo {0}", repoId);
            return;
        }
        Set<String> wanted = tagFilters.getOrDefault(repoId, Set.of());
        List<TaskIssue> issues = new ArrayList<>();
        TaskRepositoryControl s = store();
        if (s != null) {
            for (TaskRecord t : s.list(repoId)) {
                if (matchesTagFilter(t, wanted)) {
                    issues.add(TaskIssueCache.get(repoId, t));
                }
            }
        }
        LOG.info("push: repo {0} matched {1}/{2} tasks, tagFilter={3}",
                repoId, issues.size(), s == null ? 0 : s.list(repoId).size(), wanted);
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