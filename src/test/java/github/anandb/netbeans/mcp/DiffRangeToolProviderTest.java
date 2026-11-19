package github.anandb.netbeans.mcp;

import github.anandb.netbeans.mcp.input.DiffRangeInput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
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
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.DiffRangeControl;
import github.anandb.netbeans.contract.ProjectQuery;

/**
 * Tests the {@code diff_range} MCP tool: repository resolution (explicit path
 * vs. first open project), revision-token validation, lastCommits bounds, and
 * delegation to {@link DiffRangeControl}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiffRangeToolProviderTest {

    @TempDir
    Path tempDir;

    @Mock
    private McpTools mcpTools;

    @Mock
    private DiffRangeControl control;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private Project project;

    @Mock
    private FileObject projectDirFO;

    private Lookup mockLookup;
    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<FileUtil> fileUtilMock;
    private DiffRangeToolProvider provider;

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(DiffRangeControl.class)).thenReturn(control);
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[0]);

        fileUtilMock = mockStatic(FileUtil.class);
        provider = new DiffRangeToolProvider();
    }

    @AfterEach
    void tearDown() {
        if (lookupMock != null) {
            lookupMock.close();
        }
        if (fileUtilMock != null) {
            fileUtilMock.close();
        }
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<DiffRangeInput, Map<String, Object>> diffRange() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("diff_range"), any(), any(), captor.capture());
        return captor.getValue();
    }

    private void openProjectAt(File root) {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] { project });
        when(project.getProjectDirectory()).thenReturn(projectDirFO);
        fileUtilMock.when(() -> FileUtil.toFile(projectDirFO)).thenReturn(root);
    }

    private File vcsRepo(String name, String marker) {
        File repo = tempDir.resolve(name).toFile();
        assertTrue(new File(repo, marker).mkdirs());
        return repo;
    }

    private File plainDir(String name) {
        File dir = tempDir.resolve(name).toFile();
        assertTrue(dir.mkdirs());
        return dir;
    }

    // ---- registration -------------------------------------------------------

    @Test
    void registersDiffRangeTool() {
        provider.registerTools(mcpTools);

        verify(mcpTools).registerTool(eq("diff_range"), any(), any(), any());
    }

    @Test
    void schemaIsRegisteredWithDescription() {
        provider.registerTools(mcpTools);

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(mcpTools).registerTool(eq("diff_range"), description.capture(), any(), any());
        assertNotNull(description.getValue());
        assertTrue(description.getValue().contains("commit"),
                "description should explain commit ranges: " + description.getValue());
    }

    // ---- control availability ----------------------------------------------

    @Test
    void reportsMissingControl() throws Exception {
        when(mockLookup.lookup(DiffRangeControl.class)).thenReturn(null);
        File repo = vcsRepo("repo", ".git");

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), "HEAD~1", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("DiffRangeControl not available", result.get("message"));
    }

    // ---- repository resolution ---------------------------------------------

    @Test
    void rejectsMissingRepoDirectory() throws Exception {
        String missing = tempDir.resolve("missing").toString();

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(missing, "HEAD~1", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("Repository directory not found: " + missing, result.get("message"));
    }

    @Test
    void rejectsDirectoryOutsideOpenProjects() throws Exception {
        noOpenProjects();

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(plainDir("outside").getAbsolutePath(), "HEAD~1", null, null));

        assertEquals("error", result.get("status"));
        assertTrue(String.valueOf(result.get("message")).startsWith("Path is outside the open projects:"),
                "unexpected message: " + result.get("message"));
    }

    @Test
    void rejectsDirectoryWithoutVcsMarker() throws Exception {
        File plain = plainDir("plain");
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(plain.getAbsolutePath(), "HEAD~1", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("Not a Git or Mercurial repository: " + plain.getAbsolutePath(),
                result.get("message"));
    }

    @Test
    void reportsNoOpenProjectWhenRepoDirBlank() throws Exception {
        noOpenProjects();

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(null, "HEAD~1", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("No open project found. Specify repoDir explicitly.", result.get("message"));
    }

    @Test
    void defaultsToFirstOpenProjectRoot() throws Exception {
        File repo = vcsRepo("repo", ".git");
        openProjectAt(repo);

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(null, "HEAD~2", null, null));

        assertEquals("ok", result.get("status"));
        verify(control).openDiffRange(repo, "HEAD~2", null, null);
    }

    // ---- arguments ----------------------------------------------------------

    @Test
    void requiresFromRevisionOrLastCommits() throws Exception {
        File repo = vcsRepo("repo", ".git");

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), " ", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("fromRevision or lastCommits is required", result.get("message"));
        verify(control, never()).openDiffRange(any(), any(), any(), any());
    }

    @Test
    void rejectsOutOfRangeLastCommits() throws Exception {
        File repo = vcsRepo("repo", ".git");
        ToolExecutor<DiffRangeInput, Map<String, Object>> executor = diffRange();

        Map<String, Object> zero = executor.execute(
                new DiffRangeInput(repo.getAbsolutePath(), null, null, 0));
        assertEquals("error", zero.get("status"));
        assertEquals("lastCommits must be between 1 and 1000", zero.get("message"));

        Map<String, Object> tooMany = executor.execute(
                new DiffRangeInput(repo.getAbsolutePath(), null, null, 1001));
        assertEquals("error", tooMany.get("status"));
        assertEquals("lastCommits must be between 1 and 1000", tooMany.get("message"));
    }

    @Test
    void rejectsUnsafeGitRevisionToken() throws Exception {
        File repo = vcsRepo("repo", ".git");
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), "--exec=evil", null, null));

        assertEquals("error", result.get("status"));
        assertTrue(String.valueOf(result.get("message")).startsWith("Invalid diff target:"),
                "unexpected message: " + result.get("message"));
    }

    @Test
    void rejectsUnsafeToRevisionToken() throws Exception {
        File repo = vcsRepo("repo", ".git");
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), "HEAD~1", "HEAD; rm -rf /", null));

        assertEquals("error", result.get("status"));
        assertTrue(String.valueOf(result.get("message")).startsWith("Invalid diff target:"),
                "unexpected message: " + result.get("message"));
    }

    // ---- delegation ---------------------------------------------------------

    @Test
    void delegatesExplicitGitRange() throws Exception {
        File repo = vcsRepo("repo", ".git");
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput("  " + repo.getAbsolutePath() + "  ", " abc123 ", " def456 ", null));

        assertEquals("ok", result.get("status"));
        assertEquals("Opened diff for abc123..def456", result.get("message"));
        verify(control).openDiffRange(repo, "abc123", "def456", null);
    }

    @Test
    void lastCommitsWinsOverFromRevision() throws Exception {
        File repo = vcsRepo("repo", ".git");
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), "abc123", null, 5));

        assertEquals("ok", result.get("status"));
        assertEquals("Opened diff for the last 5 commits including working tree changes",
                result.get("message"));
        verify(control).openDiffRange(repo, "abc123", null, 5);
    }

    @Test
    void blankToRevisionMeansWorkingTree() throws Exception {
        File repo = vcsRepo("repo", ".git");
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), "abc123", "  ", null));

        assertEquals("ok", result.get("status"));
        assertEquals("Opened diff for abc123..working tree", result.get("message"));
        verify(control).openDiffRange(repo, "abc123", null, null);
    }

    @Test
    void detectsMercurialRepository() throws Exception {
        File repo = vcsRepo("hg-repo", ".hg");
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), "tip", "tip", 2));

        assertEquals("ok", result.get("status"));
        verify(control).openDiffRange(repo, "tip", "tip", 2);
    }

    @Test
    void returnsControlError() throws Exception {
        File repo = vcsRepo("repo", ".git");
        openProjectAt(tempDir.toFile());
        when(control.openDiffRange(repo, "HEAD~9", null, null))
                .thenReturn("Unknown Git revision: HEAD~9");

        Map<String, Object> result = diffRange().execute(
                new DiffRangeInput(repo.getAbsolutePath(), "HEAD~9", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("Unknown Git revision: HEAD~9", result.get("message"));
    }

    private void noOpenProjects() {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[0]);
    }
}
