package github.anandb.netbeans.tasks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.SwingUtilities;

import org.netbeans.modules.bugtracking.spi.QueryController;
import org.netbeans.modules.bugtracking.spi.QueryProvider;
import org.openide.util.RequestProcessor;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
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

    private final Map<TaskQuery, IssueContainer<TaskIssue>> containers = new ConcurrentHashMap<>();
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
        return new TaskQueryController();
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
        containers.put(q, container);
        String repoId = repoOf(q);
        if (repoId != null && registeredRepos.add(repoId)) {
            TaskRepositoryControl s = store();
            if (s != null) {
                s.addListener((r, type, taskId) -> {
                    if (r.equals(repoId)) {
                        push(q);
                    }
                });
            }
        }
        push(q);
    }

    @Override
    public void refresh(TaskQuery q) {
        String repoId = repoOf(q);
        if (repoId == null) {
            return;
        }
        // File reload runs off the EDT; republish afterwards.
        RP.post(() -> {
            TaskRepositoryControl s = store();
            if (s != null) {
                s.reload(repoId);
            }
            push(q);
        });
    }

    private void push(TaskQuery q) {
        IssueContainer<TaskIssue> container = containers.get(q);
        if (container == null) {
            return;
        }
        List<TaskIssue> issues = new ArrayList<>();
        String repoId = repoOf(q);
        if (repoId != null) {
            TaskRepositoryControl s = store();
            if (s != null) {
                for (TaskRecord t : s.list(repoId)) {
                    issues.add(TaskIssueCache.get(repoId, t));
                }
            }
        }
        Runnable deliver = () -> {
            container.restoreStarted();
            container.clear();
            container.add(issues.toArray(new TaskIssue[0]));
            container.restoreFinished();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            deliver.run();
        } else {
            SwingUtilities.invokeLater(deliver);
        }
    }
}