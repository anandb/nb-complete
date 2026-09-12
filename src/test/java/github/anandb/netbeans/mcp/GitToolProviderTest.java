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
    void gitStatusDefaultsToNestedProjectDirectory() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        File projectRoot = new File(repo, "sub");
        assertTrue(projectRoot.mkdirs());
        openProjectAt(projectRoot);
        assertTrue(new File(projectRoot, "notes.txt").createNewFile());

        Map<String, Object> result = statusExecutor().execute(new GitStatusInput(null));

        assertEquals("ok", result.get("status"));
        assertTrue(((String) result.get("output")).contains("??"),
                "git status from a nested NB project should succeed (cwd is the project, not the parent repo root): "
                        + result.get("output"));
    }

    @Test
    void gitStatusRejectsExplicitRepoDirOutsideOpenProject() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        File projectRoot = new File(repo, "sub");
        assertTrue(projectRoot.mkdirs());
        openProjectAt(projectRoot);

        Map<String, Object> result = statusExecutor().execute(new GitStatusInput(repo.getAbsolutePath()));

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

    private void runGit(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "git timed out: " + String.join(" ", cmd));
        assertEquals(0, proc.exitValue(), String.join(" ", cmd) + " failed");
    }

    /** Runs git with extra environment entries (e.g. fixed commit dates). */
    private void runGitWithEnv(File dir, Map<String, String> env, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        pb.environment().putAll(env);
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "git timed out: " + String.join(" ", cmd));
        assertEquals(0, proc.exitValue(), String.join(" ", cmd) + " failed");
    }

    private void commitAll(File repo, String message) throws Exception {
        runGit(repo, "git", "add", "-A");
        runGit(repo, "git", "-c", "user.email=test@example.com", "-c", "user.name=test",
                "commit", "-q", "-m", message);
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<GitLogInput, Map<String, Object>> logExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("git_log"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<FileHistoryInput, Map<String, Object>> fileHistoryExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("file_history"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void gitLogReturnsCommitHistory() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        java.nio.file.Files.writeString(new File(repo, "readme.txt").toPath(), "hello\n");
        commitAll(repo, "initial commit");

        Map<String, Object> result = logExecutor().execute(new GitLogInput(null, null, null));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("initial commit"), "log should contain the subject: " + output);
        assertTrue(output.contains("test"), "log should contain the author: " + output);
    }

    @Test
    void gitLogRejectsOptionLikeSince() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = logExecutor().execute(new GitLogInput(null, null, "--exec=rm"));

        assertEquals("error", result.get("status"));
        assertEquals("Invalid since filter: --exec=rm", result.get("message"));
    }

    @Test
    void gitLogRejectsOutOfRangeMaxCount() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = logExecutor().execute(new GitLogInput(null, 0, null));

        assertEquals("error", result.get("status"));
        assertEquals("maxCount must be between 1 and 1000", result.get("message"));
    }

    @Test
    void fileHistoryShowsPatchForFile() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File src = new File(repo, "src.txt");
        java.nio.file.Files.writeString(src.toPath(), "one\n");
        commitAll(repo, "first revision");
        java.nio.file.Files.writeString(src.toPath(), "one\ntwo\n");
        commitAll(repo, "second revision");

        Map<String, Object> result = fileHistoryExecutor().execute(new FileHistoryInput(null, "src.txt", null));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("first revision"), "history should list both revisions: " + output);
        assertTrue(output.contains("second revision"), "history should list both revisions: " + output);
        assertTrue(output.contains("+two"), "history should include the added line: " + output);
    }

    @Test
    void fileHistoryRequiresPath() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = fileHistoryExecutor().execute(new FileHistoryInput(null, null, null));

        assertEquals("error", result.get("status"));
        assertEquals("path is required", result.get("message"));
    }

    @Test
    void fileHistoryRejectsPathOutsideOpenProject() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File outside = tempDir.resolve("outside.txt").toFile();
        assertTrue(outside.createNewFile());

        Map<String, Object> result = fileHistoryExecutor().execute(
                new FileHistoryInput(null, outside.getAbsolutePath(), null));

        assertEquals("error", result.get("status"));
        assertEquals("Path is outside the open projects: " + outside.getAbsolutePath(),
                result.get("message"));
    }

    @Test
    void gitLogHonorsSinceFilter() throws Exception {
        // A commit dated well before the since filter must be excluded.
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        java.nio.file.Files.writeString(new File(repo, "old.txt").toPath(), "x\n");
        runGitWithEnv(repo, Map.of(
                "GIT_AUTHOR_DATE", "2025-01-01T00:00:00",
                "GIT_COMMITTER_DATE", "2025-01-01T00:00:00"),
                "git", "add", "-A");
        runGitWithEnv(repo, Map.of(
                "GIT_AUTHOR_DATE", "2025-01-01T00:00:00",
                "GIT_COMMITTER_DATE", "2025-01-01T00:00:00"),
                "git", "-c", "user.email=test@example.com", "-c", "user.name=test",
                "commit", "-q", "-m", "old commit");

        Map<String, Object> result = logExecutor().execute(new GitLogInput(null, null, "2 weeks ago"));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.isEmpty(), "old commit must be filtered out by --since: " + output);
    }

    @Test
    void gitLogAcceptsExplicitRepoDir() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        java.nio.file.Files.writeString(new File(repo, "f.txt").toPath(), "x\n");
        commitAll(repo, "explicit dir commit");

        Map<String, Object> result = logExecutor().execute(new GitLogInput(repo.getAbsolutePath(), null, null));

        assertEquals("ok", result.get("status"));
        assertTrue(((String) result.get("output")).contains("explicit dir commit"));
    }

    @Test
    void gitLogReportsMissingRepository() throws Exception {
        noOpenProjects();

        Map<String, Object> result = logExecutor().execute(new GitLogInput(null, null, null));

        assertEquals("error", result.get("status"));
        assertEquals("No git repository found", result.get("message"));
    }

    @Test
    void fileHistoryHandlesAbsolutePath() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File src = new File(repo, "abs.txt");
        java.nio.file.Files.writeString(src.toPath(), "content\n");
        commitAll(repo, "abs path commit");

        Map<String, Object> result = fileHistoryExecutor().execute(
                new FileHistoryInput(null, src.getAbsolutePath(), null));

        assertEquals("ok", result.get("status"));
        assertTrue(((String) result.get("output")).contains("abs path commit"));
    }
    @Test
    void fileHistoryResolvesNestedSubdirectoryPaths() throws Exception {
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File nested = new File(repo, "src/deep");
        assertTrue(nested.mkdirs());
        File src = new File(nested, "deep.txt");
        java.nio.file.Files.writeString(src.toPath(), "deep\n");
        commitAll(repo, "nested file commit");

        Map<String, Object> result = fileHistoryExecutor().execute(
                new FileHistoryInput(null, "src/deep/deep.txt", null));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("nested file commit"), "should find the nested file: " + output);
        assertTrue(output.contains("+deep"), "should include the patch: " + output);
    }

    @Test
    void fileHistoryReportsUntrackedFileAsGitError() throws Exception {
        // The tool surfaces git's own exit code; an untracked file produces
        // a fatal error rather than a synthetic error result.
        File repo = initGitRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        assertTrue(new File(repo, "untracked.txt").createNewFile());

        Map<String, Object> result = fileHistoryExecutor().execute(new FileHistoryInput(null, "untracked.txt", null));

        assertEquals("ok", result.get("status"));
        assertTrue((Integer) result.get("exitCode") != 0,
                "git must report failure for an untracked path");
        assertTrue(((String) result.get("output")).contains("fatal"),
                "git error should surface in the output: " + result.get("output"));
    }
}
