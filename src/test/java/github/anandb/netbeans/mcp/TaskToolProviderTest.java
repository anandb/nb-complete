package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import github.anandb.netbeans.contract.TaskInput;
import github.anandb.netbeans.contract.TaskRepositoryControl;
import github.anandb.netbeans.model.TaskRecord;
import github.anandb.netbeans.support.MapperSupplier;
import org.openide.util.Lookup;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskToolProviderTest {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    @TempDir
    Path tempDir;

    @Mock
    private McpTools mcpTools;

    @Mock
    private TaskRepositoryControl taskRepositoryControl;

    private MockedStatic<Lookup> lookupMock;
    private TaskToolProvider provider;

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        Lookup mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(TaskRepositoryControl.class)).thenReturn(taskRepositoryControl);

        provider = new TaskToolProvider();
    }

    @AfterEach
    void tearDown() {
        if (lookupMock != null) {
            lookupMock.close();
        }
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<AddTaskInput, Map<String, Object>> registerAndGetExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> executorCaptor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("add_task"), any(), any(), executorCaptor.capture());
        return executorCaptor.getValue();
    }

    private File createTempCsvFile(String repoId) throws Exception {
        File csvFile = tempDir.resolve("tasks_" + repoId + ".txt").toFile();
        csvFile.createNewFile();
        when(taskRepositoryControl.tasksPathOf(repoId)).thenReturn(csvFile.getAbsolutePath());
        return csvFile;
    }

    private TaskRecord mockTaskRecord(String id, String summary) {
        return new TaskRecord(id, "open", "B", summary, List.of(), List.of(),
            "", 0, 0, "", "", "");
    }

    @Test
    void registersAddTaskTool() {
        provider.registerTools(mcpTools);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ObjectNode> schemaCaptor = ArgumentCaptor.forClass(ObjectNode.class);

        verify(mcpTools).registerTool(nameCaptor.capture(), descCaptor.capture(), schemaCaptor.capture(), any());

        assertEquals("add_task", nameCaptor.getValue());
        assertNotNull(descCaptor.getValue());
        assertTrue(descCaptor.getValue().contains("Creates a task"));
    }

    @Test
    void schemaHasAllProperties() {
        provider.registerTools(mcpTools);

        ArgumentCaptor<ObjectNode> schemaCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(mcpTools).registerTool(any(), any(), schemaCaptor.capture(), any());

        ObjectNode schema = schemaCaptor.getValue();
        assertEquals("object", schema.get("type").asText());

        ObjectNode properties = (ObjectNode) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.has("repoId"));
        assertTrue(properties.has("summary"));
        assertTrue(properties.has("status"));
        assertTrue(properties.has("priority"));
        assertTrue(properties.has("projects"));
        assertTrue(properties.has("tags"));
        assertTrue(properties.has("dueDate"));
        assertTrue(properties.has("estimate"));
        assertTrue(properties.has("consumed"));

        assertEquals(1, schema.get("required").size());
        assertEquals("summary", schema.get("required").get(0).asText());
    }

    @SuppressWarnings("unchecked")
    @Test
    void successfulTaskCreation() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1"));
        when(taskRepositoryControl.displayNameOf("repo1")).thenReturn("My Tasks");
        createTempCsvFile("repo1");
        when(taskRepositoryControl.add(eq("repo1"), any(TaskInput.class))).thenReturn(mockTaskRecord("t-1", "Fix bug"));

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput(null, "Fix bug", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("ok", result.get("status"));
        assertEquals("t-1", result.get("id"));
        assertEquals("Task added to 'My Tasks'.", result.get("message"));

        ArgumentCaptor<TaskInput> inputCaptor = ArgumentCaptor.forClass(TaskInput.class);
        verify(taskRepositoryControl).add(eq("repo1"), inputCaptor.capture());
        assertEquals("Fix bug", inputCaptor.getValue().summary());
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsErrorWhenSummaryIsBlank() throws Exception {
        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput(null, "  ", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("error", result.get("status"));
        assertEquals("summary is required", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsErrorWhenNoRepositoriesConfigured() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of());

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput(null, "Fix bug", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("No Beanbot Tasks repository configured"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsErrorWhenMultipleReposAndNoRepoId() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1", "repo2"));

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput(null, "Fix bug", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("Multiple repositories exist"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsErrorWhenUnknownRepoId() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1"));

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput("unknown", "Fix bug", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("Unknown repository 'unknown'"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void autoSelectsSingleRepository() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("only-repo"));
        when(taskRepositoryControl.displayNameOf("only-repo")).thenReturn("Only Repo");
        createTempCsvFile("only-repo");
        when(taskRepositoryControl.add(eq("only-repo"), any(TaskInput.class))).thenReturn(mockTaskRecord("t-1", "Fix bug"));

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput(null, "Fix bug", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("ok", result.get("status"));
        verify(taskRepositoryControl).add(eq("only-repo"), any(TaskInput.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void passesAllFieldsToTaskInput() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1"));
        when(taskRepositoryControl.displayNameOf("repo1")).thenReturn("My Tasks");
        createTempCsvFile("repo1");
        TaskRecord mockRecord = new TaskRecord("t-1", "open", "A", "Critical fix", List.of("urgent"), List.of("backend"),
            "2026-09-01", 5, 2, "", "", "");
        when(taskRepositoryControl.add(eq("repo1"), any(TaskInput.class))).thenReturn(mockRecord);

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput("repo1", "Critical fix", "open", "A", "backend", "urgent",
            "2026-09-01", 5, 2);
        Map<String, Object> result = executor.execute(input);

        assertEquals("ok", result.get("status"));

        ArgumentCaptor<TaskInput> inputCaptor = ArgumentCaptor.forClass(TaskInput.class);
        verify(taskRepositoryControl).add(eq("repo1"), inputCaptor.capture());
        TaskInput captured = inputCaptor.getValue();
        assertEquals("Critical fix", captured.summary());
        assertEquals("open", captured.status());
        assertEquals("A", captured.priority());
        assertEquals("urgent", captured.tags());
        assertEquals("backend", captured.projects());
        assertEquals("2026-09-01", captured.dueDate());
        assertEquals(5, captured.estimate());
        assertEquals(2, captured.consumed());
    }

    @SuppressWarnings("unchecked")
    @Test
    void worksWithExplicitRepoId() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1", "repo2"));
        when(taskRepositoryControl.displayNameOf("repo2")).thenReturn("Second Repo");
        createTempCsvFile("repo2");
        when(taskRepositoryControl.add(eq("repo2"), any(TaskInput.class))).thenReturn(mockTaskRecord("t-1", "Task"));

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput("repo2", "Task", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("ok", result.get("status"));
        verify(taskRepositoryControl).add(eq("repo2"), any(TaskInput.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void handlesNullOptionalFields() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1"));
        when(taskRepositoryControl.displayNameOf("repo1")).thenReturn("My Tasks");
        createTempCsvFile("repo1");
        when(taskRepositoryControl.add(eq("repo1"), any(TaskInput.class))).thenReturn(mockTaskRecord("t-1", "Minimal task"));

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput(null, "Minimal task", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("ok", result.get("status"));

        ArgumentCaptor<TaskInput> inputCaptor = ArgumentCaptor.forClass(TaskInput.class);
        verify(taskRepositoryControl).add(eq("repo1"), inputCaptor.capture());
        TaskInput captured = inputCaptor.getValue();
        assertEquals("Minimal task", captured.summary());
        assertEquals("", captured.status());
        assertEquals("", captured.priority());
        assertEquals("", captured.tags());
        assertEquals("", captured.projects());
        assertEquals("", captured.dueDate());
        assertEquals(0, captured.estimate());
        assertEquals(0, captured.consumed());
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsErrorWhenRepositoryFileNotFound() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1"));
        when(taskRepositoryControl.tasksPathOf("repo1")).thenReturn("/nonexistent/path/tasks.txt");

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput("repo1", "Fix bug", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("Repository file not found"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsErrorWhenRepositoryHasNoFilePath() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1"));
        when(taskRepositoryControl.tasksPathOf("repo1")).thenReturn("");

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput("repo1", "Fix bug", null, null, null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("has no file path configured"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsErrorWhenPriorityIsInvalid() throws Exception {
        when(taskRepositoryControl.repositoryIds()).thenReturn(List.of("repo1"));
        createTempCsvFile("repo1");

        ToolExecutor<AddTaskInput, Map<String, Object>> executor = registerAndGetExecutor();
        AddTaskInput input = new AddTaskInput("repo1", "Fix bug", null, "URGENT", null, null, null, null, null);
        Map<String, Object> result = executor.execute(input);

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("Invalid priority"));
    }
}
