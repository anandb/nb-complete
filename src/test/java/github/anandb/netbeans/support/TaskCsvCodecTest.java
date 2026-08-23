package github.anandb.netbeans.support;

import java.util.List;

import org.junit.jupiter.api.Test;

import github.anandb.netbeans.model.TaskRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskCsvCodecTest {

    @Test
    void roundTripPreservesAllFields() {
        List<TaskRecord> tasks = List.of(new TaskRecord(
            "t-1", "open", "high", "Do the thing", "A long description, with commas",
            "/home/user/file.java", "a, b", "2026-09-01", List.of("t-2", "t-3"),
            "2026-08-01T10:00:00Z", "2026-08-02T10:00:00Z"));

        String csv = TaskCsvCodec.serialize(tasks);
        List<TaskRecord> parsed = TaskCsvCodec.parse(csv);

        assertEquals(1, parsed.size());
        TaskRecord t = parsed.get(0);
        assertEquals("t-1", t.id());
        assertEquals("open", t.status());
        assertEquals("high", t.priority());
        assertEquals("Do the thing", t.summary());
        assertEquals("A long description, with commas", t.description());
        assertEquals("/home/user/file.java", t.filePath());
        assertEquals("a, b", t.tags());
        assertEquals("2026-09-01", t.dueDate());
        assertEquals(List.of("t-2", "t-3"), t.subtasks());
        assertEquals("2026-08-01T10:00:00Z", t.createdAt());
        assertEquals("2026-08-02T10:00:00Z", t.updatedAt());
    }

    @Test
    void skipsRowsWithoutIdAndBlankLines() {
        String csv = "id,status,summary\n"
            + "\n"
            + "t-1,open,First\n"
            + ",,no id\n";
        List<TaskRecord> parsed = TaskCsvCodec.parse(csv);
        assertEquals(1, parsed.size());
        assertEquals("t-1", parsed.get(0).id());
    }

    @Test
    void toleratesUnknownColumns() {
        String csv = "id,summary,newColumn\n"
            + "t-9,Same,\"ignored\"\n";
        List<TaskRecord> parsed = TaskCsvCodec.parse(csv);
        assertEquals(1, parsed.size());
        assertEquals("t-9", parsed.get(0).id());
        assertEquals("Same", parsed.get(0).summary());
    }

    @Test
    void completionStatusMapping() {
        assertTrue(TaskRecord.isFinishedStatus("closed"));
        assertTrue(TaskRecord.isFinishedStatus("CLOSED"));
        assertFalse(TaskRecord.isFinishedStatus("done"));
        assertFalse(TaskRecord.isFinishedStatus("completed"));
        assertFalse(TaskRecord.isFinishedStatus("finished"));
        assertFalse(TaskRecord.isFinishedStatus("cancelled"));
        assertFalse(TaskRecord.isFinishedStatus("open"));
        assertFalse(TaskRecord.isFinishedStatus("in-progress"));
        assertFalse(TaskRecord.isFinishedStatus(""));
        assertFalse(TaskRecord.isFinishedStatus(null));
    }

    @Test
    void emptyContentYieldsNoTasks() {
        assertTrue(TaskCsvCodec.parse("").isEmpty());
        assertTrue(TaskCsvCodec.parse("id,status,summary\n").isEmpty());
    }
}