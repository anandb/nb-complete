package github.anandb.netbeans.tasks;

import github.anandb.netbeans.model.TaskRecord;

/**
 * The implementation-specific issue type ({@code I}) for the bugtracking SPI.
 * Wraps a {@link TaskRecord} together with the id of the repository that owns
 * it, so the shared provider implementations can dispatch per-repository.
 * Editing produces a new immutable {@link TaskRecord}; this holder keeps the
 * latest one.
 */
public final class TaskIssue {

    private final String repositoryId;
    private volatile TaskRecord record;

    public TaskIssue(String repositoryId, TaskRecord record) {
        this.repositoryId = repositoryId;
        this.record = record;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public TaskRecord getRecord() {
        return record;
    }

    public void setRecord(TaskRecord record) {
        this.record = record;
    }

    @Override
    public String toString() {
        return "TaskIssue{" + (record == null ? null : record.id()) + "}";
    }
}