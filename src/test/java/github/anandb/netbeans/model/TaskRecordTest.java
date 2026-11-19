package github.anandb.netbeans.model;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRecordTest {

    private static TaskRecord task(String status, String completedAt) {
        return new TaskRecord("id-1", status, "(A)", "Summary", List.of("@work"), List.of("+proj"),
                "2026-01-01", 3, 1, "2025-12-01", completedAt, "2025-12-02");
    }

    @Test
    void finishedStatusIsCaseInsensitiveAndNullSafe() {
        assertTrue(TaskRecord.isFinishedStatus("closed"));
        assertTrue(TaskRecord.isFinishedStatus("CLOSED"));
        assertFalse(TaskRecord.isFinishedStatus("open"));
        assertFalse(TaskRecord.isFinishedStatus(null));
        assertTrue(task("closed", "done").isFinished());
        assertFalse(task("open", "").isFinished());
    }

    @Test
    void nextCompletedAtStampsOnlyOnTransitionToFinished() {
        assertEquals("now", TaskRecord.nextCompletedAt(false, "", "closed", "now"));
        assertEquals("now", TaskRecord.nextCompletedAt(false, "", "CLOSED", "now"));
        assertEquals("old", TaskRecord.nextCompletedAt(true, "old", "closed", "now"));
        assertEquals("", TaskRecord.nextCompletedAt(true, "old", "open", "now"));
        assertEquals("", TaskRecord.nextCompletedAt(false, "old", null, "now"));
    }

    @Test
    void withDetailsReplacesMutableFieldsAndKeepsIdentity() {
        TaskRecord original = task("open", "");

        TaskRecord updated = original.withDetails("closed", "(B)", "New", List.of("@x"),
                List.of("+y"), "2026-02-02", 5, 4);

        assertEquals(original.id(), updated.id());
        assertEquals(original.createdAt(), updated.createdAt());
        assertEquals(original.completedAt(), updated.completedAt());
        assertEquals(original.updatedAt(), updated.updatedAt());
        assertEquals("closed", updated.status());
        assertEquals("(B)", updated.priority());
        assertEquals("New", updated.summary());
        assertEquals(List.of("@x"), updated.tags());
        assertEquals(List.of("+y"), updated.projects());
        assertEquals("2026-02-02", updated.dueDate());
        assertEquals(5, updated.estimate());
        assertEquals(4, updated.consumed());
    }

    @Test
    void withCompletedAtOnlyChangesTheStamp() {
        TaskRecord original = task("closed", "");

        TaskRecord stamped = original.withCompletedAt("2026-03-03");

        assertEquals("2026-03-03", stamped.completedAt());
        assertEquals(original.status(), stamped.status());
        assertEquals(original.summary(), stamped.summary());
        assertEquals(original.updatedAt(), stamped.updatedAt());
    }
}
