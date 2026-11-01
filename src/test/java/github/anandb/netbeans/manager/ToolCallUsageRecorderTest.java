package github.anandb.netbeans.manager;

import github.anandb.netbeans.contract.SessionQuery;
import github.anandb.netbeans.contract.UsageStatsStore;
import github.anandb.netbeans.model.UsageRecords.ToolCallExecution;
import github.anandb.netbeans.model.UsageRecords.ToolCallStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the capture seam: attribution is read from {@link SessionQuery} at
 * execution time, a call without a session is still stored, and nothing is
 * written for input that cannot name a tool execution.
 */
class ToolCallUsageRecorderTest {

    private final SessionQuery sessions = mock(SessionQuery.class);
    private final UsageStatsStore store = mock(UsageStatsStore.class);
    private final ToolCallUsageRecorder recorder = new ToolCallUsageRecorder(sessions, store);

    private ToolCallExecution capture() {
        ArgumentCaptor<ToolCallExecution> captor = ArgumentCaptor.forClass(ToolCallExecution.class);
        verify(store).recordToolCall(captor.capture());
        return captor.getValue();
    }

    @Test
    void attributesTheExecutionToTheSessionThatIssuedIt() {
        when(sessions.getCurrentSessionId()).thenReturn("s1");
        when(sessions.getHarnessId()).thenReturn("omp");
        when(sessions.getSessionModelId("s1")).thenReturn("opencode-go/m1");
        when(sessions.getSessionDirectory("s1")).thenReturn("/proj/a");

        recorder.record("read_file", 1_000L, 42L, ToolCallStatus.OK);

        ToolCallExecution row = capture();
        assertEquals("s1", row.attribution().sessionId());
        assertEquals("omp", row.attribution().harnessId());
        assertEquals("opencode-go/m1", row.attribution().modelId());
        assertEquals("/proj/a", row.attribution().project());
        assertEquals("read_file", row.toolName());
        assertEquals(1_000L, row.capturedAt());
        assertEquals(42L, row.durationMs());
        assertEquals(ToolCallStatus.OK, row.status());
    }

    @Test
    void aCallWithoutASessionIsStoredWithNullAttribution() {
        when(sessions.getCurrentSessionId()).thenReturn(null);
        when(sessions.getHarnessId()).thenReturn("omp");

        recorder.record("list_projects", 1_000L, 1L, ToolCallStatus.OK);

        ToolCallExecution row = capture();
        assertNull(row.attribution().sessionId(), "no session is recorded as null, not dropped");
        assertNull(row.attribution().modelId());
        assertNull(row.attribution().project());
        assertEquals("omp", row.attribution().harnessId());
    }

    @Test
    void negativeDurationIsStoredAsZero() {
        recorder.record("read_file", 1_000L, -9L, ToolCallStatus.OK);

        assertEquals(0L, capture().durationMs());
    }

    @Test
    void blankToolNameWritesNothing() {
        recorder.record("  ", 1_000L, 1L, ToolCallStatus.OK);
        recorder.record(null, 1_000L, 1L, ToolCallStatus.OK);

        verifyNoInteractions(store);
    }

    @Test
    void nullStatusWritesNothing() {
        recorder.record("read_file", 1_000L, 1L, null);

        verifyNoInteractions(store);
    }

    @Test
    void aMissingStoreNeverThrows() {
        ToolCallUsageRecorder withoutStore = new ToolCallUsageRecorder(sessions, null);

        assertDoesNotThrow(() -> withoutStore.record("read_file", 1_000L, 1L, ToolCallStatus.OK));
    }
}
