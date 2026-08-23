package github.anandb.netbeans.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A single task persisted in a Beanbot Tasks repository CSV file.
 *
 * <p>Field ordering is the canonical CSV column order; see {@code TaskCsvCodec}.
 * {@code status} is an enum value (see {@link github.anandb.netbeans.model.TaskStatus});
 * a task is finished only when its status is {@code closed}, which drives the Tasks
 * Dashboard "Open / Finished" grouping.
 * {@code tags} is a comma-separated list; {@code subtasks} a typed list of parent
 * task ids serialized by the codec as a delimited string.</p>
 */
public record TaskRecord(
    String id,
    String status,
    String priority,
    String summary,
    String description,
    String filePath,
    String tags,
    String dueDate,
    List<String> subtasks,
    String createdAt,
    String updatedAt
) {

    /** Status value that signals the task is finished/closed. */
    private static final Set<String> DONE = Set.of("closed");

    /** True if the given free-text status belongs to the completion set. */
    public static boolean isFinishedStatus(String status) {
        return status != null && DONE.contains(status.toLowerCase(Locale.ROOT));
    }

    /** True if this task is in the completion set. */
    public boolean isFinished() {
        return isFinishedStatus(status);
    }

    /** Creates a copy with all mutable display fields replaced. */
    public TaskRecord withDetails(String status, String priority, String summary,
            String description, String filePath, String tags, String dueDate) {
        return new TaskRecord(id, status, priority, summary, description, filePath,
                tags, dueDate, subtasks, createdAt, updatedAt);
    }
}