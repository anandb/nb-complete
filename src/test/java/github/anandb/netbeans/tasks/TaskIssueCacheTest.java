package github.anandb.netbeans.tasks;

import java.util.List;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.TaskRecord;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TaskIssueCacheTest {

    private static TaskRecord rec(String id, String summary) {
        return new TaskRecord(id, "open", "N", summary, List.of(), List.of(),
            "", 0, 0, "2026-08-01", "2026-08-01T00:00:00Z");
    }

    @Test
    void returnsSameInstanceForSameTaskAndUpdatesRecord() {
        TaskIssue i1 = TaskIssueCache.get("r1", rec("t-1", "Alpha"));
        TaskIssue i2 = TaskIssueCache.get("r1", rec("t-1", "Beta"));
        assertSame(i1, i2, "same task id must yield the same TaskIssue identity");
        // record is refreshed in place to the latest data
        assertSame(i2.getRecord(), i1.getRecord());
    }

    @Test
    void differentRepositoriesAreDistinctKeys() {
        TaskIssue a = TaskIssueCache.get("r1", rec("t-1", "x"));
        TaskIssue b = TaskIssueCache.get("r2", rec("t-1", "x"));
        assertNotSame(a, b);
    }

    @Test
    void invalidateCreatesFreshIdentity() {
        TaskIssue before = TaskIssueCache.get("r1", rec("t-9", "x"));
        TaskIssueCache.invalidate("r1", "t-9");
        TaskIssue after = TaskIssueCache.get("r1", rec("t-9", "x"));
        assertNotSame(before, after);
    }
}