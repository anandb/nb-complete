package github.anandb.netbeans.contract;

import java.util.List;

import github.anandb.netbeans.model.TaskRecord;

/**
 * Port used by the Beanbot Tasks connector (Dashboard), the MCP {@code add_task}
 * tool and the chat plugin to read and mutate the CSV-backed task repositories.
 *
 * <p>All operations are keyed by repository id; a single repository maps to one
 * CSV file. Implementations keep the in-memory cache up to date synchronously,
 * perform all file I/O off the EDT, and fire {@link RepositoryListener}s so the
 * Dashboard can republish changed tasks.</p>
 *
 * <p>Only this port (and the {@code BugtrackingConnector}) is looked up globally;
 * the underlying provider interfaces are never registered as services.</p>
 */
public interface TaskRepositoryControl {

    /** The ids of all known repositories. */
    List<String> repositoryIds();

    /** The CSV file path backing the given repository, or empty string. */
    String csvPathOf(String repoId);

    /** The user-visible name of the given repository, or empty string. */
    String displayNameOf(String repoId);

    /** Registers (or re-registers) a repository and loads its CSV from disk. */
    void registerRepository(String repoId, String csvPath, String displayName);

    /** Removes a repository and its cached state (the CSV file is not deleted). */
    void unregisterRepository(String repoId);

    /** The tasks of the given repository (snapshot of the in-memory cache). */
    List<TaskRecord> list(String repoId);

    /** A single task by id, or null if absent. */
    TaskRecord get(String repoId, String id);

    /** Creates a fresh unique task id. */
    String createTaskId();

    /** Inserts a task (new id + timestamps generated) and persists; returns it. */
    TaskRecord add(String repoId, TaskInput input);

    /** Inserts the given task verbatim (preserving id/timestamps) and persists. */
    TaskRecord addRecord(String repoId, TaskRecord task);

    /** Replaces a task (by its id) and persists; true if it existed. */
    boolean update(String repoId, TaskRecord task);

    /** Removes a task and persists; true if it existed. */
    boolean delete(String repoId, String id);

    /**
     * Reloads the given repository's CSV from disk and refreshes the cached
     * stat snapshot; true on success (missing file yields an empty repository).
     */
    boolean reload(String repoId);

    void addListener(RepositoryListener listener);

    void removeListener(RepositoryListener listener);

    /** Notifies that a repository's task set changed. */
    interface RepositoryListener {
        void repositoryChanged(String repoId, ChangeType type, String taskId);
    }

    enum ChangeType { ADDED, UPDATED, REMOVED, RELOADED }
}