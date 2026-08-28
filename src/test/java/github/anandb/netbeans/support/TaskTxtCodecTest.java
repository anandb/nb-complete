package github.anandb.netbeans.support;

import java.util.List;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.TaskRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTxtCodecTest {

    @Test
    void roundTripPreservesAllFields() {
        List<TaskRecord> tasks = List.of(new TaskRecord(
            "t-1", "open", "B", "Do the thing", List.of("a", "b"),
            List.of("proj-x", "proj-y"), "2026-09-01", 5, 2,
            "2026-08-01", "", "2026-08-02T10:00:00Z"));

        String txt = TaskTxtCodec.serialize(tasks);
        List<TaskRecord> parsed = TaskTxtCodec.parse(txt);

        assertEquals(1, parsed.size());
        TaskRecord t = parsed.get(0);
        assertEquals("t-1", t.id());
        assertEquals("open", t.status());
        assertEquals("B", t.priority());
        assertEquals("Do the thing", t.summary());
        assertEquals(List.of("a", "b"), t.tags());
        assertEquals(List.of("proj-x", "proj-y"), t.projects());
        assertEquals("2026-09-01", t.dueDate());
        assertEquals(5, t.estimate());
        assertEquals(2, t.consumed());
        assertEquals("2026-08-01", t.createdAt());
        assertEquals("2026-08-02T10:00:00Z", t.updatedAt());
    }

    @Test
    void closedUsesXPrefixAndNoStatusToken() {
        List<TaskRecord> tasks = List.of(new TaskRecord(
            "t-2", "closed", "A", "Done item", List.of(), List.of(), "",
            0, 0, "2026-08-01", "2026-08-02", "2026-08-03T10:00:00Z"));
        String txt = TaskTxtCodec.serialize(tasks);
        assertTrue(txt.contains("x (A) "), "closed task must carry x prefix + priority");
        assertFalse(txt.contains("status:"), "no status: token on disk");
        List<TaskRecord> parsed = TaskTxtCodec.parse(txt);
        assertEquals(1, parsed.size());
        assertTrue(parsed.get(0).isFinished());
        assertEquals("A", parsed.get(0).priority());
    }

    @Test
    void closedTaskWritesTwoDatesAndRoundTrips() {
        List<TaskRecord> tasks = List.of(new TaskRecord(
            "t-4", "closed", "B", "Done", List.of(), List.of(), "",
            0, 0, "2026-08-01", "2026-08-05", "2026-08-05T12:00:00Z"));
        String txt = TaskTxtCodec.serialize(tasks);
        // Must write both completion and creation dates for closed tasks.
        assertTrue(txt.startsWith("x (B) 2026-08-05 2026-08-01 "),
            "closed task must write completion date before creation date, got: " + txt);
        List<TaskRecord> parsed = TaskTxtCodec.parse(txt);
        assertEquals(1, parsed.size());
        TaskRecord t = parsed.get(0);
        assertTrue(t.isFinished());
        assertEquals("2026-08-05", t.completedAt(), "completion date must round-trip");
        assertEquals("2026-08-01", t.createdAt(), "creation date must round-trip");
    }

    @Test
    void summaryTrailingSpaceDoesNotDoubleSeparator() {
        List<TaskRecord> tasks = List.of(new TaskRecord(
            "t-6", "open", "", "Fix bug ", List.of("urgent"), List.of(), "",
            0, 0, "2026-08-01", "", "2026-08-01T00:00:00Z"));
        String txt = TaskTxtCodec.serialize(tasks);
        // A single space must separate the summary from the first @tag token.
        assertTrue(txt.contains("Fix bug @urgent"),
            "summary trailing space must not produce a doubled separator, got: " + txt);
        assertFalse(txt.contains("Fix bug  @urgent"), "double space before tag: " + txt);
        // The summary is trimmed, matching parse() behavior on reload.
        assertEquals("Fix bug", TaskTxtCodec.parse(txt).get(0).summary());
    }

    @Test
    void closedTaskWithoutCompletedAtWritesCreationDateTwice() {
        // No separate completion date: both positional dates are the creation date.
        List<TaskRecord> tasks = List.of(new TaskRecord(
            "t-5", "closed", "C", "Auto-closed", List.of(), List.of(), "",
            0, 0, "2026-08-01", "", "2026-08-01T00:00:00Z"));
        String txt = TaskTxtCodec.serialize(tasks);
        assertTrue(txt.startsWith("x (C) 2026-08-01 2026-08-01 "),
            "closed task without completedAt must write creation date twice, got: " + txt);
    }

    @Test
    void tagsAndProjectsUseNativeTokens() {
        List<TaskRecord> tasks = List.of(new TaskRecord(
            "t-3", "open", "", "Sum", List.of("urgent"), List.of("alpha"),
            "", 0, 0, "2026-08-01", "", "2026-08-01T00:00:00Z"));
        String txt = TaskTxtCodec.serialize(tasks);
        assertTrue(txt.contains("@urgent"), "tag emitted as @token");
        assertTrue(txt.contains("+alpha"), "project emitted as +token");
        assertFalse(txt.contains("tags:"), "no tags: mirror token");
        assertFalse(txt.contains("projects:"), "no projects: mirror token");
        List<TaskRecord> parsed = TaskTxtCodec.parse(txt);
        assertEquals(List.of("urgent"), parsed.get(0).tags());
        assertEquals(List.of("alpha"), parsed.get(0).projects());
    }

    @Test
    void generatesIdWhenMissing() {
        String txt = "(B) 2026-08-01 Some thought @random\n"
            + "x (C) 2026-08-01 2026-07-01 Finished @done +p id:t-9\n";
        List<TaskRecord> parsed = TaskTxtCodec.parse(txt);
        assertEquals(2, parsed.size());
        // First line has no explicit id -> assigned a NetBeans-format id.
        assertTrue(parsed.get(0).id().startsWith("t-"), "auto id uses NetBeans format");
        // Second line keeps its explicit id.
        assertEquals("t-9", parsed.get(1).id());
        assertTrue(parsed.get(1).isFinished());
    }

    @Test
    void generatesUniqueIdsForIdlessLines() {
        String txt = "Buy milk @shop\n" + "Walk dog @home\n";
        List<TaskRecord> parsed = TaskTxtCodec.parse(txt);
        assertEquals(2, parsed.size());
        assertNotEquals(parsed.get(0).id(), parsed.get(1).id());
        assertTrue(parsed.get(0).id().startsWith("t-"));
        assertTrue(parsed.get(1).id().startsWith("t-"));
    }

    @Test
    void toleratesUnknownTokens() {
        String txt = "(Z) 2026-08-01 Buy milk @shop +home id:t-x randomtoken:foo\n";
        List<TaskRecord> parsed = TaskTxtCodec.parse(txt);
        assertEquals(1, parsed.size());
        assertEquals("t-x", parsed.get(0).id());
        assertEquals("Z", parsed.get(0).priority());
        assertEquals(List.of("shop"), parsed.get(0).tags());
        assertEquals(List.of("home"), parsed.get(0).projects());
    }

    @Test
    void emptyContentYieldsNoTasks() {
        assertTrue(TaskTxtCodec.parse("").isEmpty());
        assertTrue(TaskTxtCodec.parse("   \n  \n").isEmpty());
    }
}
