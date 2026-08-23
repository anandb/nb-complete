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

class CsvTaskRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void addGetUpdateDeleteReflectSynchronously() {
        TaskRepositoryControl repo = new CsvTaskRepository();
        repo.registerRepository("r1", tempDir.resolve("tasks.csv").toString(), "Test Repo");

        assertTrue(repo.repositoryIds().contains("r1"));
        assertEquals(0, repo.list("r1").size());

        TaskInput input = new TaskInput("open", "high", "Fix the bug", "details",
            "/tmp/a.java", "urgent", "2026-09-01");
        TaskRecord added = repo.add("r1", input);
        assertNotNull(added);
        assertEquals("Fix the bug", added.summary());

        assertEquals(1, repo.list("r1").size());
        assertEquals(added, repo.get("r1", added.id()));

        TaskRecord edited = added.withDetails("closed", "low", "Fixed", "", "", "", "");
        assertTrue(repo.update("r1", edited));
        assertEquals("Fixed", repo.get("r1", added.id()).summary());
        assertTrue(repo.get("r1", added.id()).isFinished());

        assertTrue(repo.delete("r1", added.id()));
        assertEquals(0, repo.list("r1").size());
    }

    @Test
    void reloadLoadsExistingCsvFromDisk() throws IOException {
        Path csv = tempDir.resolve("existing.csv");
        String content = "id,status,priority,summary,description,filePath,tags,dueDate,subtasks,createdAt,updatedAt\n"
            + "t-1,closed,normal,Already there,,,,,,,\n";
        Files.writeString(csv, content);

        TaskRepositoryControl repo = new CsvTaskRepository();
        repo.registerRepository("r1", csv.toString(), "Test");

        assertTrue(repo.reload("r1"));
        List<TaskRecord> loaded = repo.list("r1");
        assertEquals(1, loaded.size());
        assertEquals("t-1", loaded.get(0).id());
        assertEquals("Already there", loaded.get(0).summary());
        assertTrue(loaded.get(0).isFinished());
    }

    @Test
    void unregisterForgetsRepository() {
        TaskRepositoryControl repo = new CsvTaskRepository();
        repo.registerRepository("r1", tempDir.resolve("a.csv").toString(), "A");
        repo.registerRepository("r2", tempDir.resolve("b.csv").toString(), "B");
        assertEquals(2, repo.repositoryIds().size());

        repo.unregisterRepository("r1");
        assertEquals(List.of("r2"), repo.repositoryIds());
        assertTrue(repo.list("r1").isEmpty());
    }
}