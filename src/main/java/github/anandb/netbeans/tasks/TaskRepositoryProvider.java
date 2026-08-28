package github.anandb.netbeans.tasks;

import java.awt.Image;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.netbeans.modules.bugtracking.spi.RepositoryController;
import org.netbeans.modules.bugtracking.spi.RepositoryInfo;
import org.netbeans.modules.bugtracking.spi.RepositoryProvider;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.tasks.TasksModel.TaskQuery;
import github.anandb.netbeans.tasks.TasksModel.TaskRepository;
import org.openide.util.Lookup;

/**
 * {@link RepositoryProvider} for Beanbot Tasks. The tasks-file (todo.txt) path
 * lives in the {@link RepositoryInfo} under {@link TasksModel#VALUE_TASKS_PATH}
 * so the framework persists it alongside the repository definition.
 */
public final class TaskRepositoryProvider implements RepositoryProvider<TaskRepository, TaskQuery, TaskIssue> {

    /**
     * One controller instance per repository. The framework creates the
     * controller via {@link #getController} for the create/edit dialog and then
     * calls the SAME method again to {@code applyChanges()} on save — a fresh
     * controller would have unbuilt fields and lose the user's input.
     */
    private final Map<TaskRepository, TaskRepositoryController> controllers =
        new ConcurrentHashMap<>();

    private final TaskQueryProvider queryProvider;

    public TaskRepositoryProvider(TaskQueryProvider queryProvider) {
        this.queryProvider = queryProvider;
    }

    private TaskRepositoryControl store() {
        return Lookup.getDefault().lookup(TaskRepositoryControl.class);
    }

    @Override
    public RepositoryInfo getInfo(TaskRepository r) {
        if (r == null || r.getRepositoryId() == null || r.getRepositoryId().isEmpty()) {
            return null; // new, not yet saved
        }
        // The RepositoryInfo "url" is the repository's web URL shown by the
        // Dashboard and used to build Open-In-Browser links / favicon fetches.
        // Beanbot Tasks are backed by a local todo.txt file, not a remote
        // tracker, so we must NOT set url to the tasks-file path: a non-empty,
        // non-http value makes the bugtracking framework attempt a remote
        // icon/link fetch on the EDT (see the MediaTracker/Image Fetcher freeze
        // in a.txt). The tasks-file path is persisted separately under
        // VALUE_TASKS_PATH and is never treated as a URL.
        RepositoryInfo info = new RepositoryInfo(
            r.getRepositoryId(),
            TasksModel.CONNECTOR_ID,
            "",
            displayNameOf(r),
            "");
        info.putValue(TasksModel.VALUE_TASKS_PATH, r.getTasksPath() == null ? "" : r.getTasksPath());
        return info;
    }

    /** Display name for the repository, defaulting to "BeanBot" when unset. */
    private static String displayNameOf(TaskRepository r) {
        String dn = r == null ? null : r.getDisplayName();
        if (dn == null || dn.trim().isEmpty()) {
            return "BeanBot";
        }
        return dn;
    }

    @Override
    public Image getIcon(TaskRepository r) {
        // The create-repository dialog and the Tasks Dashboard render this via
        // ImageUtilities.image2Icon, which NPEs on null — never return null.
        return TasksIcon.getIcon();
    }

    @Override
    public Collection<TaskIssue> getIssues(TaskRepository r, String... ids) {
        TaskRepositoryControl s = store();
        List<TaskIssue> out = new ArrayList<>();
        if (s == null || r.getRepositoryId() == null) {
            return out;
        }
        for (String id : ids) {
            TaskRecord t = s.get(r.getRepositoryId(), id);
            if (t != null) {
                out.add(TaskIssueCache.get(r.getRepositoryId(), t));
            }
        }
        return out;
    }

    @Override
    public void removed(TaskRepository r) {
        controllers.remove(r);
        TaskRepositoryControl s = store();
        if (s != null && r.getRepositoryId() != null) {
            s.unregisterRepository(r.getRepositoryId());
        }
        // Drop containers/listeners for this repo's queries so they don't leak.
        queryProvider.cleanup(r.getRepositoryId());
    }

    @Override
    public RepositoryController getController(TaskRepository r) {
        return controllers.computeIfAbsent(r, TaskRepositoryController::new);
    }

    @Override
    public TaskQuery createQuery(TaskRepository r) {
        return new TaskQuery(TasksModel.DEFAULT_QUERY_NAME, r);
    }

    @Override
    public TaskIssue createIssue(TaskRepository r) {
        return newIssue(r, "");
    }

    @Override
    public TaskIssue createIssue(TaskRepository r, String summary, String description) {
        // description is not part of the todo.txt format; summary is used.
        return newIssue(r, summary == null ? "" : summary);
    }

    private TaskIssue newIssue(TaskRepository r, String summary) {
        String now = Instant.now().toString();
        TaskRecord t = new TaskRecord(
            store() == null ? "t-tmp" : store().createTaskId(),
            "", "", summary, List.of(), List.of(), "", 0, 0, now, "", now);
        return TaskIssueCache.get(r.getRepositoryId(), t);
    }

    @Override
    public Collection<TaskQuery> getQueries(TaskRepository r) {
        return List.of(
            new TaskQuery(TasksModel.DEFAULT_QUERY_NAME, r, TaskQuery.Kind.ALL),
            new TaskQuery(TasksModel.OPEN_QUERY_NAME, r, TaskQuery.Kind.OPEN),
            new TaskQuery(TasksModel.CLOSED_QUERY_NAME, r, TaskQuery.Kind.CLOSED));
    }

    @Override
    public Collection<TaskIssue> simpleSearch(TaskRepository r, String criteria) {
        TaskRepositoryControl s = store();
        List<TaskIssue> out = new ArrayList<>();
        if (s == null || r.getRepositoryId() == null) {
            return out;
        }
        String c = criteria == null ? "" : criteria.toLowerCase(Locale.ROOT);
        for (TaskRecord t : s.list(r.getRepositoryId())) {
            if (t.id().toLowerCase(Locale.ROOT).contains(c)
                    || t.summary() != null && t.summary().toLowerCase(Locale.ROOT).contains(c)) {
                out.add(TaskIssueCache.get(r.getRepositoryId(), t));
            }
        }
        return out;
    }

    @Override
    public boolean canAttachFiles(TaskRepository r) {
        return false;
    }

    @Override
    public void removePropertyChangeListener(TaskRepository r, PropertyChangeListener listener) {
    }

    @Override
    public void addPropertyChangeListener(TaskRepository r, PropertyChangeListener listener) {
    }
}