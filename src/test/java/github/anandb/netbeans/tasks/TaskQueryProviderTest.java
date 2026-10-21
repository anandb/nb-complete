package github.anandb.netbeans.tasks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.netbeans.modules.bugtracking.spi.QueryProvider.IssueContainer;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.tasks.TasksModel.TaskQuery;
import github.anandb.netbeans.tasks.TasksModel.TaskRepository;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskQueryProviderTest {

    private static final String REPO_ID = "test-repo";

    @Test
    void burstOfRefreshesCollapsesIntoOneReload() throws Exception {
        AtomicInteger reloads = new AtomicInteger();
        TaskRepositoryControl store = mock(TaskRepositoryControl.class);
        doAnswer(inv -> {
            reloads.incrementAndGet();
            return null;
        }).when(store).reload(REPO_ID);

        TaskRepository repo = new TaskRepository();
        repo.setRepositoryId(REPO_ID);
        TaskQuery query = new TaskQuery("All tasks", repo);

        Lookup mockLookup = mock(Lookup.class);
        when(mockLookup.lookup(TaskRepositoryControl.class)).thenReturn(store);

        try (MockedStatic<Lookup> lookupMock = mockStatic(Lookup.class)) {
            lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
            TaskQueryProvider provider = new TaskQueryProvider();
            provider.setIssueContainer(query, mock(IssueContainer.class));

            // The framework calls refresh() once per store/metadata change.
            provider.refresh(query);
            provider.refresh(query);
            provider.refresh(query);

            // Still inside the coalescing window: no reload may have started.
            verify(store, never()).reload(anyString());
            assertEquals(0, reloads.get());

            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(store, times(1)).reload(REPO_ID));

            // Nothing may follow: the burst must not queue extra reloads.
            await().atMost(2000L + TaskQueryProvider.REFRESH_DELAY_MS * 2L, TimeUnit.MILLISECONDS)
                    .during(TaskQueryProvider.REFRESH_DELAY_MS * 2L, TimeUnit.MILLISECONDS)
                    .until(() -> reloads.get() == 1);
            verify(store, times(1)).reload(REPO_ID);
        }
    }
}
