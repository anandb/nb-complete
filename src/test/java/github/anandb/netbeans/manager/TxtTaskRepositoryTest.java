package github.anandb.netbeans.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
