package github.anandb.netbeans.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskStatusTest {

    @Test
    void mapsKnownValues() {
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue("open"));
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.fromValue("in-progress"));
        assertEquals(TaskStatus.CLOSED, TaskStatus.fromValue("closed"));
    }

    @Test
    void unknownValuesDefaultToOpen() {
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue("done"));
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue("completed"));
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue("finished"));
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue("cancelled"));
    }

    @Test
    void defaultsToOpenForBlankOrUnknown() {
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue(""));
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue(null));
        assertEquals(TaskStatus.OPEN, TaskStatus.fromValue("bogus"));
    }

    @Test
    void valueRoundTripsLowercase() {
        assertEquals("open", TaskStatus.OPEN.value());
        assertEquals("in-progress", TaskStatus.IN_PROGRESS.value());
        assertEquals("closed", TaskStatus.CLOSED.value());
    }
}