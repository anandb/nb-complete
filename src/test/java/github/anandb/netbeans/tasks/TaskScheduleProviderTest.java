package github.anandb.netbeans.tasks;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openide.util.Lookup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;

/** Headless tests for {@link TaskScheduleProvider} finished-task handling. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskScheduleProviderTest {

    @org.mockito.Mock
    private TaskRepositoryControl store;

    private MockedStatic<Lookup> lookupMock;

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        Lookup mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(TaskRepositoryControl.class)).thenReturn(store);
    }

    @AfterEach
    void tearDown() {
        if (lookupMock != null) {
            lookupMock.close();
        }
    }

    private static TaskRecord rec(String status) {
        return new TaskRecord("t-1", status, "N", "Task", List.of(), List.of(),
            "", 0, 0, "2026-08-01", "", "2026-08-01T00:00:00Z");
    }

    @Test
    void setScheduleIsNoopForFinishedTask() {
        TaskIssue finished = new TaskIssue("r1", rec("closed"));
        TaskScheduleProvider p = new TaskScheduleProvider();
        p.setSchedule(finished, new org.netbeans.modules.bugtracking.spi.IssueScheduleInfo(
            new java.util.Date()));
        verify(store, never()).update(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(TaskRecord.class));
    }
}