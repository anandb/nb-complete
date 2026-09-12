package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

import github.anandb.netbeans.contract.ProjectQuery;

/**
 * Tests the project-root cwd resolution of the git MCP tools: the default
 * repoDir must come from the open project's root (never {@code user.dir}),
 * and the containment guard must still reject repositories outside the
 * open projects.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitToolProviderTest {

    @TempDir
    Path tempDir;

    @Mock
    private McpTools mcpTools;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private Project project;

    @Mock
    private FileObject projectDir;

    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<FileUtil> fileUtilMock;
    private GitToolProvider provider;

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        Lookup mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);

        fileUtilMock = mockStatic(FileUtil.class);

        provider = new GitToolProvider();
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

    private void openProjectAt(File root) {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] { project });
        when(project.getProjectDirectory()).thenReturn(projectDir);
        fileUtilMock.when(() -> FileUtil.toFile(projectDir)).thenReturn(root);
    }

    private void noOpenProjects() {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[0]);
    }

    private File initGitRepo(File dir) throws Exception {
        assertTrue(dir.mkdirs() || dir.isDirectory());
        ProcessBuilder pb = new ProcessBuilder("git", "init", "-q");
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "git init timed out");
        assertEquals(0, proc.exitValue());
        assertTrue(new File(dir, ".git").exists());
        return dir;
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<GitStatusInput, Map<String, Object>> statusExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("git_status"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<GitDiffInput, Map<String, Object>> diffExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("git_diff"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void gitStatusRunsInProjectRootByDefault() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File untracked = new File(repo, "notes.txt");
        assertTrue(untracked.createNewFile());

        Map<String, Object> result = statusExecutor().execute(new GitStatusInput(null));

        assertEquals("ok", result.get("status"));
        // The untracked file is only listed when git ran with the project
        // root as its working directory.
        assertTrue(((String) result.get("output")).contains("?? notes.txt"),
                "git status output should list the untracked file: " + result.get("output"));
    }

    @Test
    void gitStatusWithoutOpenProjectReportsMissingRepository() throws Exception {
        noOpenProjects();

        Map<String, Object> result = statusExecutor().execute(new GitStatusInput(null));

        assertEquals("error", result.get("status"));
        assertEquals("No git repository found", result.get("message"));
    }

    @Test
    void findGitRootWalksUpToNearestRepositoryAncestor() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        File projectRoot = new File(repo, "sub");
        assertTrue(projectRoot.mkdirs());
        openProjectAt(projectRoot);

        Method findGitRoot = GitToolProvider.class.getDeclaredMethod("findGitRoot");
        findGitRoot.setAccessible(true);
        String root = (String) findGitRoot.invoke(provider);

        assertEquals(repo.getCanonicalPath(), root);
    }

    @Test
    void gitStatusRejectsRepositoryOutsideOpenProject() throws Exception {
        // Project is a subdirectory of the repo: the walked-up repo root is
        // outside the open project, so the containment guard must reject it.
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        File projectRoot = new File(repo, "sub");
        assertTrue(projectRoot.mkdirs());
        openProjectAt(projectRoot);

        Map<String, Object> result = statusExecutor().execute(new GitStatusInput(null));

        assertEquals("error", result.get("status"));
        assertEquals("Path is outside the open projects: " + repo.getAbsolutePath(),
                result.get("message"));
    }

    @Test
    void gitDiffRejectsOptionLikeTarget() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = diffExecutor().execute(new GitDiffInput(null, "--output=/tmp/x"));

        assertEquals("error", result.get("status"));
        assertEquals("Invalid diff target: --output=/tmp/x", result.get("message"));
    }
}
