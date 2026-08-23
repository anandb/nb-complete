package github.anandb.netbeans.tasks;

import java.util.concurrent.ConcurrentHashMap;

import github.anandb.netbeans.model.TaskRecord;

/**
 * Caches one {@link TaskIssue} per (repository, task id).
 *
 * <p>The bugtracking framework keys its internal {@code IssueImpl} wrappers
 * ({@code RepositoryImpl.issueMap}, a {@code HashMap}) by the issue data object
 * <em>identity</em>, and the scheduling manager keys scheduled tasks by the
 * {@code IssueImpl} identity. If a fresh {@code TaskIssue} were produced every
 * time a task was read or an issue was created, each read would spawn a new
 * {@code IssueImpl} and the Schedule categories would count the same task
 * several times. Caching the {@code TaskIssue} (and updating its record in
 * place) keeps exactly one identity per task.</p>
 */
final class TaskIssueCache {

    private static final ConcurrentHashMap<String, TaskIssue> CACHE = new ConcurrentHashMap<>();

    private TaskIssueCache() {
    }

    /** Returns the single {@link TaskIssue} for the task, creating/updating it. */
    static TaskIssue get(String repoId, TaskRecord record) {
        String key = key(repoId, record.id());
        TaskIssue issue = CACHE.computeIfAbsent(key, k -> new TaskIssue(repoId, record));
        if (issue.getRecord() != record) {
            issue.setRecord(record);
        }
        return issue;
    }

    /** Forgets a task (e.g. after deletion) so its identity is not reused. */
    static void invalidate(String repoId, String id) {
        CACHE.remove(key(repoId, id));
    }

    private static String key(String repoId, String id) {
        return (repoId == null ? "" : repoId) + '\u0000' + id;
    }
}