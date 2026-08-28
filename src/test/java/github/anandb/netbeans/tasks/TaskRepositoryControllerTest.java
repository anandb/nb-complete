package github.anandb.netbeans.tasks;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import javax.swing.JTextField;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openide.util.Lookup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.support.TasksMetadata;
import github.anandb.netbeans.tasks.TasksModel.TaskRepository;

/** Headless validation tests for {@link TaskRepositoryController}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskRepositoryControllerTest {

    @TempDir
    Path tempDir;

    @org.mockito.Mock
    private TaskRepositoryControl store;

    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<TasksMetadata> metadataMock;

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        Lookup mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(TaskRepositoryControl.class)).thenReturn(store);

        metadataMock = mockStatic(TasksMetadata.class);
        metadataMock.when(TasksMetadata::allIds).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        if (lookupMock != null) {
            lookupMock.close();
        }
        if (metadataMock != null) {
            metadataMock.close();
        }
    }

    private TaskRepositoryController newController() {
        TaskRepository repo = new TaskRepository();
        repo.setRepositoryId("r-mine");
        TaskRepositoryController c = new TaskRepositoryController(repo);
        c.getComponent(); // builds the private JTextField fields headlessly
        return c;
    }

    private void setText(TaskRepositoryController c, String field, String value)
            throws ReflectiveOperationException {
        Field f = TaskRepositoryController.class.getDeclaredField(field);
        f.setAccessible(true);
        ((JTextField) f.get(c)).setText(value == null ? "" : value);
    }

    @Test
    void rejectsMissingFields() throws Exception {
        when(store.repositoryIds()).thenReturn(List.of());
        TaskRepositoryController c = newController();
        assertFalse(c.isValid(), "empty name/path must be invalid");
    }

    @Test
    void acceptsValidUniquePath() throws Exception {
        when(store.repositoryIds()).thenReturn(List.of("r-other"));
        when(store.tasksPathOf("r-other")).thenReturn(tempDir.resolve("other.txt").toString());

        TaskRepositoryController c = newController();
        setText(c, "nameField", "My Tasks");
        setText(c, "pathField", tempDir.resolve("mine.txt").toString());
        assertTrue(c.isValid(), "unique path must be valid");
    }

    @Test
    void rejectsPathAlreadyClaimedByAnotherRepository() throws Exception {
        Path shared = tempDir.resolve("tasks.txt");
        when(store.repositoryIds()).thenReturn(List.of("r-mine", "r-other"));
        when(store.tasksPathOf("r-other")).thenReturn(shared.toString());

        TaskRepositoryController c = newController();
        setText(c, "nameField", "My Tasks");
        setText(c, "pathField", shared.toString());

        assertFalse(c.isValid(), "path claimed by another repository must be invalid");
        assertTrue(c.getErrorMessage() != null
                && c.getErrorMessage().contains("already uses this tasks file"),
            "error message must explain the duplicate path");
    }

    @Test
    void ownRepoPathIsNotTreatedAsDuplicate() throws Exception {
        Path mine = tempDir.resolve("mine.txt");
        when(store.repositoryIds()).thenReturn(List.of("r-mine"));
        when(store.tasksPathOf("r-mine")).thenReturn(mine.toString());

        TaskRepositoryController c = newController();
        setText(c, "nameField", "My Tasks");
        setText(c, "pathField", mine.toString());
        assertTrue(c.isValid(), "a repository may keep its own current path");
    }
}