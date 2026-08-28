package github.anandb.netbeans.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A single task persisted in a Beanbot Tasks repository as one todo.txt line.
 *
 * <p>On-disk layout (see {@code TaskTxtCodec}): {@code x (P) <completionDate>
 * <creationDate> <summary free text> @tags +projects due: id: estimate: consumed:
 * upd:}. {@code status} is open/closed; a task is finished only when its status
 * is {@code closed}, which drives the Tasks Dashboard "Open / Finished"
 * grouping. {@code tags} are {@code @}-prefixed tokens; {@code projects} are
 * {@code +}-prefixed tokens. {@code estimate}/{@code consumed} are arbitrary
 * user-inferred integer units.</p>
 */
public record TaskRecord(
    String id,
    String status,
    String priority,
    String summary,
    List<String> tags,
    List<String> projects,
    String dueDate,
    int estimate,
    int consumed,
    String createdAt,
    String completedAt,
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
            List<String> tags, List<String> projects, String dueDate,
            int estimate, int consumed) {
        return new TaskRecord(id, status, priority, summary, tags, projects,
                dueDate, estimate, consumed, createdAt, completedAt, updatedAt);
    }

    /** Creates a copy with {@code completedAt} set (used when closing a task). */
    public TaskRecord withCompletedAt(String completedAt) {
        return new TaskRecord(id, status, priority, summary, tags, projects,
                dueDate, estimate, consumed, createdAt, completedAt, updatedAt);
    }
}
