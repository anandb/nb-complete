package github.anandb.netbeans.manager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openide.util.RequestProcessor;

import github.anandb.netbeans.contract.TaskInput;
import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxtTaskRepositoryTest {

    @TempDir
    private Path tempDir;

    @Test
    void addGetUpdateDeleteReflectSynchronously() {
        TaskRepositoryControl repo = new TxtTaskRepository();
        repo.registerRepository("r1", tempDir.resolve("tasks.txt").toString(), "Test Repo");

        assertTrue(repo.repositoryIds().contains("r1"));
        assertEquals(0, repo.list("r1").size());

        TaskInput input = new TaskInput("open", "B", "Fix the bug", "urgent", "alpha",
            "2026-09-01", 3, 1);
        TaskRecord added = repo.add("r1", input);
        assertNotNull(added);
        assertEquals("Fix the bug", added.summary());

        assertEquals(1, repo.list("r1").size());
        assertEquals(added, repo.get("r1", added.id()));

        TaskRecord edited = added.withDetails("closed", "C",
            "Fixed", List.of("urgent"), List.of("alpha"), "", 3, 1);
        assertTrue(repo.update("r1", edited));
        assertEquals("Fixed", repo.get("r1", added.id()).summary());
        assertTrue(repo.get("r1", added.id()).isFinished());

        assertTrue(repo.delete("r1", added.id()));
        assertEquals(0, repo.list("r1").size());
    }

    @Test
    void reloadLoadsExistingTxtFromDisk() throws IOException {
        Path txt = tempDir.resolve("existing.txt");
        String content = "x (A) 2026-08-02 2026-08-01 Already there @urgent +proj id:t-1 estimate:4 consumed:2 upd:2026-08-02T10:00:00Z\n";
        Files.writeString(txt, content);

        TaskRepositoryControl repo = new TxtTaskRepository();
        repo.registerRepository("r1", txt.toString(), "Test");

        assertTrue(repo.reload("r1"));
        List<TaskRecord> loaded = repo.list("r1");
        assertEquals(1, loaded.size());
        assertEquals("t-1", loaded.get(0).id());
        assertEquals("Already there", loaded.get(0).summary());
        assertTrue(loaded.get(0).isFinished());
        assertEquals(List.of("urgent"), loaded.get(0).tags());
        assertEquals(List.of("proj"), loaded.get(0).projects());
        assertEquals(4, loaded.get(0).estimate());
        assertEquals(2, loaded.get(0).consumed());
    }

    @Test
    void unregisterForgetsRepository() {
        TaskRepositoryControl repo = new TxtTaskRepository();
        repo.registerRepository("r1", tempDir.resolve("a.txt").toString(), "A");
        repo.registerRepository("r2", tempDir.resolve("b.txt").toString(), "B");
        assertEquals(2, repo.repositoryIds().size());

        repo.unregisterRepository("r1");
        assertEquals(List.of("r2"), repo.repositoryIds());
        assertTrue(repo.list("r1").isEmpty());
    }

    @Test
    void backgroundLoadDoesNotClobberMutationsMadeAfterRegister() throws Exception {
        TxtTaskRepository repo = new TxtTaskRepository();

        // Park the single-threaded I/O processor so the load posted by
        // registerRepository is queued until after the synchronous mutations below.
        Field ioField = TxtTaskRepository.class.getDeclaredField("io");
        ioField.setAccessible(true);
        RequestProcessor io = (RequestProcessor) ioField.get(repo);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        io.post(() -> {
            started.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS), "I/O processor did not start");

        repo.registerRepository("r1", tempDir.resolve("tasks.txt").toString(), "Test");
        TaskInput input = new TaskInput("open", "B", "Keep me", "", "", "", 0, 0);
        TaskRecord added = repo.add("r1", input);
        assertNotNull(added);

        release.countDown();
        RequestProcessor.Task sentinel = io.post(() -> { });
        sentinel.waitFinished(5000);

        assertEquals(List.of(added), repo.list("r1"));
        assertTrue(repo.update("r1", added.withDetails("closed", "C", "Kept",
            List.of(), List.of(), "", 0, 0)));
    }
}
