package github.anandb.netbeans.tasks;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.netbeans.modules.bugtracking.api.Repository;
import org.netbeans.modules.bugtracking.api.RepositoryManager;
import org.netbeans.modules.bugtracking.spi.BugtrackingConnector;
import org.netbeans.modules.bugtracking.spi.BugtrackingSupport;
import org.netbeans.modules.bugtracking.spi.RepositoryInfo;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.support.TasksMetadata;
import github.anandb.netbeans.tasks.TasksModel.TaskQuery;
import github.anandb.netbeans.tasks.TasksModel.TaskRepository;
import org.openide.util.Lookup;

/**
 * Beanbot Tasks {@link BugtrackingConnector}. Registers the connector type via
 * {@code @BugtrackingConnector.Registration} (the Processor in the bugtracking
 * module turns it into a {@code Services/Bugtracking} layer entry).
 *
 * <p>Each repository owns one external todo.txt file whose path is stored in
 * its {@link RepositoryInfo}; the connector restores repositories on startup
 * via {@link #createRepository(RepositoryInfo)} and registers them with the
 * tasks store.</p>
 */
@BugtrackingConnector.Registration(
    id = TasksModel.CONNECTOR_ID,
    displayName = "BeanBot",
    tooltip = "BeanBot Tasks repositories backed by todo.txt files.",
    iconPath = "github/anandb/netbeans/tasks/icons/tasks.png",
    providesRepositoryManagement = true
)
public final class TasksConnector implements BugtrackingConnector {

    private static final Logger LOG = Logger.from(TasksConnector.class);
    private final TaskQueryProvider queryProvider = new TaskQueryProvider();
    private final TaskRepositoryProvider repositoryProvider = new TaskRepositoryProvider(queryProvider);
    private final TaskIssueProvider issueProvider = new TaskIssueProvider();
    private final TaskScheduleProvider scheduleProvider = new TaskScheduleProvider();
    private final TaskPriorityProvider priorityProvider = new TaskPriorityProvider();

    private final BugtrackingSupport<TaskRepository, TaskQuery, TaskIssue> support =
        new BugtrackingSupport<>(repositoryProvider, queryProvider, issueProvider);

    @Override
    public Repository createRepository() {
        return support.createRepository(new TaskRepository(), null, scheduleProvider,
                priorityProvider, null);
    }

    @Override
    public Repository createRepository(RepositoryInfo info) {
        if (!PluginSettings.isTaskRepositoryEnabled()) {
            return null;
        }
        TaskRepository r = new TaskRepository();
        r.setRepositoryId(info.getID());
        r.setTasksPath(info.getValue(TasksModel.VALUE_TASKS_PATH));
        r.setDisplayName(info.getDisplayName());
        TaskRepositoryControl s = Lookup.getDefault().lookup(TaskRepositoryControl.class);
        if (s != null && info.getID() != null) {
            s.registerRepository(info.getID(),
                info.getValue(TasksModel.VALUE_TASKS_PATH), info.getDisplayName());
        }
        return support.createRepository(r, null, scheduleProvider, priorityProvider, null);
    }

    /**
     * Re-registers every persisted repository (from {@link TasksMetadata}) into
     * the bugtracking registry. The framework only restores repositories during
     * its one-shot {@code loadRepositories()}, which runs once and may happen
     * before this connector is registered in the Lookup; re-calling
     * {@link #createRepository(RepositoryInfo)} after that point is idempotent
     * (matched by connector id + repository id) and auto-adds any that were
     * skipped, so repositories reliably reappear on startup.
     */
    public void restoreFromMetadata() {
        RepositoryManager mgr = RepositoryManager.getInstance();
        // Track paths already claimed so stale metadata (multiple repository ids
        // pointing at the same tasks file) doesn't re-create duplicate repositories.
        Set<String> claimed = new HashSet<>();
        for (String id : TasksMetadata.allIds()) {
            String csvPath = TasksMetadata.tasksPathOf(id);
            if (csvPath.isEmpty()) {
                continue;
            }
            String canonical = canonical(csvPath);
            // Skip repositories the framework already restored this session.
            if (mgr.getRepository(TasksModel.CONNECTOR_ID, id) != null) {
                claimed.add(canonical);
                continue;
            }
            if (!claimed.add(canonical)) {
                // Another repository already owns this tasks file — do not duplicate it.
                continue;
            }
            TaskRepository r = new TaskRepository();
            r.setRepositoryId(id);
            r.setTasksPath(csvPath);
            r.setDisplayName(TasksMetadata.displayNameOf(id));
            RepositoryInfo info = repositoryProvider.getInfo(r);
            if (info == null) {
                continue;
            }
            try {
                createRepository(info);
                LOG.fine("Restored Beanbot Tasks repository {0} -> {1}", id, csvPath);
            } catch (RuntimeException ex) {
                LOG.warn("Failed to restore repository {0}: {1}", id, ex.getMessage());
            }
        }
    }

    private static String canonical(String p) {
        try {
            return new File(p).getCanonicalPath();
        } catch (IOException ex) {
            // Best effort: resolve against CWD and collapse . / .. so two raw
            // paths to the same file (e.g. "./tasks.txt" and "tasks.txt") still
            // map to one key even when canonicalization fails.
            return new File(p).getAbsoluteFile().toPath().normalize().toString();
        }
    }
}