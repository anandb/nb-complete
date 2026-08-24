package github.anandb.netbeans.tasks;

/**
 * Implementation-specific container types used by the generic bugtracking SPI
 * ({@code RepositoryProvider<R,Q,I>}): the repository ({@code R}) and the query
 * ({@code Q}). The issue type ({@code I}) is {@code TaskRecord}.
 *
 * <p>{@link TaskRepository} carries the metadata that backs a Beanbot Tasks
 * repository: display name and the external CSV path (both user-editable in the
 * repository dialog). Its id is {@code null} until the repository is first
 * saved, at which point {@code TaskRepositoryController.applyChanges()}
 * assigns one.</p>
 */
public final class TasksModel {

    /** Globally-unique connector id (keys the framework's persisted repositories). */
    public static final String CONNECTOR_ID = "beanbot.tasks";

    /** The fixed queries every repository exposes. */
    public static final String DEFAULT_QUERY_NAME = "All tasks";
    public static final String OPEN_QUERY_NAME = "Open tasks";
    public static final String CLOSED_QUERY_NAME = "Closed tasks";

    /** RepositoryInfo value key holding the external tasks-file (todo.txt) path. */
    public static final String VALUE_TASKS_PATH = "tasksPath";

    private TasksModel() {
    }

    /** The implementation-specific repository ({@code R}). */
    public static final class TaskRepository {

        private volatile String repositoryId;
        private volatile String displayName;
        private volatile String csvPath;

        public String getRepositoryId() {
            return repositoryId;
        }

        public void setRepositoryId(String repositoryId) {
            this.repositoryId = repositoryId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getCsvPath() {
            return csvPath;
        }

        public void setCsvPath(String csvPath) {
            this.csvPath = csvPath;
        }

        @Override
        public String toString() {
            return "TaskRepository{" + repositoryId + ", " + displayName + "}";
        }
    }

    /** The implementation-specific query ({@code Q}); currently a fixed view. */
    public static final class TaskQuery {

        /** The status scope this query shows; {@link #ALL} shows every task. */
        public enum Kind {
            ALL,
            OPEN,
            CLOSED
        }

        private final String name;
        private final TaskRepository repository;
        private final Kind kind;
        // Transient, session-only search criteria (not persisted): when non-empty
        // the Find Issues dialog filters the repository's tasks to those carrying
        // at least one of the selected tags.
        private volatile java.util.Set<String> tagFilter = java.util.Set.of();

        public TaskQuery(String name, TaskRepository repository) {
            this(name, repository, Kind.ALL);
        }

        public TaskQuery(String name, TaskRepository repository, Kind kind) {
            this.name = name;
            this.repository = repository;
            this.kind = kind == null ? Kind.ALL : kind;
        }

        public String getName() {
            return name;
        }

        public TaskRepository getRepository() {
            return repository;
        }

        public Kind getKind() {
            return kind;
        }

        public java.util.Set<String> getTagFilter() {
            return tagFilter;
        }

        public void setTagFilter(java.util.Set<String> tagFilter) {
            this.tagFilter = tagFilter == null ? java.util.Set.of() : tagFilter;
        }
    }
}