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
 * Tests the Mercurial MCP tools. Mirrors {@link GitToolProviderTest}: the
 * default repo root must come from the open project's root (never
 * {@code user.dir}), argument validation must reject option injection, and
 * the containment guard must reject repositories and files outside the
 * open projects.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HgToolProviderTest {

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
    private HgToolProvider provider;

    @BeforeEach
    void assumeHgAvailable() {
        // Skip cleanly when the runner image has no Mercurial — GitHub's
        // ubuntu-latest package list is not guaranteed.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                isBinaryAvailable("hg", "--version"), "hg not available on this machine");
    }

    private boolean isBinaryAvailable(String... cmd) {
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor(30, TimeUnit.SECONDS) && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        Lookup mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);

        fileUtilMock = mockStatic(FileUtil.class);

        provider = new HgToolProvider();
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

    private File initHgRepo(File dir) throws Exception {
        assertTrue(dir.mkdirs() || dir.isDirectory());
        runHg(dir, "hg", "init", "-q");
        assertTrue(new File(dir, ".hg").exists());
        return dir;
    }

    private void runHg(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        pb.environment().put("HGRCPATH", "");
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "hg timed out: " + String.join(" ", cmd));
        assertEquals(0, proc.exitValue(), String.join(" ", cmd) + " failed");
    }

    private void commitAll(File repo, String message) throws Exception {
        // -A/--addremove stages new files in one step; stable across hg
        // versions where no-arg `hg add` behavior differs.
        runHg(repo, "hg", "commit", "-q", "-A", "-u", "test <test@example.com>", "-m", message);
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<HgStatusInput, Map<String, Object>> statusExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("hg_status"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<HgDiffInput, Map<String, Object>> diffExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("hg_diff"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<HgLogInput, Map<String, Object>> logExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("hg_log"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ToolExecutor<HgFileHistoryInput, Map<String, Object>> fileHistoryExecutor() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools).registerTool(eq("hg_file_hist"), any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void hgStatusRunsInProjectRootByDefault() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        assertTrue(new File(repo, "notes.txt").createNewFile());

        Map<String, Object> result = statusExecutor().execute(new HgStatusInput(null));

        assertEquals("ok", result.get("status"));
        // The untracked file is only listed when hg ran with the project
        // root as its working directory.
        assertTrue(((String) result.get("output")).contains("notes.txt"),
                "hg status output should list the file: " + result.get("output"));
    }

    @Test
    void hgStatusWithoutOpenProjectReportsMissingRepository() throws Exception {
        noOpenProjects();

        Map<String, Object> result = statusExecutor().execute(new HgStatusInput(null));

        assertEquals("error", result.get("status"));
        assertEquals("No Mercurial repository found", result.get("message"));
    }

    @Test
    void findHgRootWalksUpToNearestRepositoryAncestor() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        File projectRoot = new File(repo, "sub");
        assertTrue(projectRoot.mkdirs());
        openProjectAt(projectRoot);

        java.lang.reflect.Method findHgRoot = HgToolProvider.class.getDeclaredMethod("findHgRoot");
        findHgRoot.setAccessible(true);
        String root = (String) findHgRoot.invoke(provider);

        assertEquals(repo.getCanonicalPath(), root);
    }

    @Test
    void hgStatusDefaultsToNestedProjectDirectory() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        File projectRoot = new File(repo, "sub");
        assertTrue(projectRoot.mkdirs());
        openProjectAt(projectRoot);
        assertTrue(new File(projectRoot, "notes.txt").createNewFile());

        Map<String, Object> result = statusExecutor().execute(new HgStatusInput(null));

        assertEquals("ok", result.get("status"));
        assertTrue(((String) result.get("output")).contains("notes.txt"),
                "hg status from a nested NB project should list files in that directory: "
                        + result.get("output"));
    }

    @Test
    void hgStatusRejectsExplicitRepoDirOutsideOpenProject() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        File projectRoot = new File(repo, "sub");
        assertTrue(projectRoot.mkdirs());
        openProjectAt(projectRoot);

        Map<String, Object> result = statusExecutor().execute(new HgStatusInput(repo.getAbsolutePath()));

        assertEquals("error", result.get("status"));
        assertEquals("Path is outside the open projects: " + repo.getAbsolutePath(),
                result.get("message"));
    }

    @Test
    void hgDiffRejectsOptionLikeTarget() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = diffExecutor().execute(new HgDiffInput(null, "--config=x"));

        assertEquals("error", result.get("status"));
        assertEquals("Invalid diff target: --config=x", result.get("message"));
    }

    @Test
    void hgDiffShowsWorkingDirectoryChanges() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File src = new File(repo, "src.txt");
        java.nio.file.Files.writeString(src.toPath(), "one\n");
        commitAll(repo, "first");
        java.nio.file.Files.writeString(src.toPath(), "one\ntwo\n");

        Map<String, Object> result = diffExecutor().execute(new HgDiffInput(null, null));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("+two"), "working diff should include the added line: " + output);
    }

    @Test
    void hgDiffShowsRevisionChanges() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File src = new File(repo, "src.txt");
        java.nio.file.Files.writeString(src.toPath(), "one\n");
        commitAll(repo, "first");
        // Second change stays uncommitted: hg diff -r tip compares the
        // working directory against the tip revision.
        java.nio.file.Files.writeString(src.toPath(), "one\ntwo\n");

        Map<String, Object> result = diffExecutor().execute(new HgDiffInput(null, "tip"));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("+two"), "revision diff should include the change: " + output);
    }

    @Test
    void hgLogReturnsCommitHistory() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        java.nio.file.Files.writeString(new File(repo, "readme.txt").toPath(), "hello\n");
        commitAll(repo, "initial commit");

        Map<String, Object> result = logExecutor().execute(new HgLogInput(null, null, null));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("initial commit"), "log should contain the subject: " + output);
        assertTrue(output.contains("test"), "log should contain the author: " + output);
    }

    @Test
    void hgLogHonorsSinceFilter() throws Exception {
        // A commit dated well before the since filter must be excluded.
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        java.nio.file.Files.writeString(new File(repo, "old.txt").toPath(), "x\n");
        runHg(repo, "hg", "commit", "-q", "-A", "-u", "test <test@example.com>",
                "-m", "old commit", "--date", "2025-01-01 00:00");

        ToolExecutor<HgLogInput, Map<String, Object>> log = logExecutor();

        // Mercurial date filters use >{since} syntax; a cutoff after the
        // 2025 commit must exclude it.
        Map<String, Object> result = log.execute(new HgLogInput(null, null, "2025-06-01"));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.isEmpty(), "old commit must be filtered out by the date filter: " + output);
        // A cutoff before the commit must include it.
        Map<String, Object> included = log.execute(new HgLogInput(null, null, "2024-06-01"));
        assertTrue(((String) included.get("output")).contains("old commit"),
                "cutoff before the commit must include it: " + included.get("output"));
    }
    @Test
    void hgLogRejectsOptionLikeSince() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = logExecutor().execute(new HgLogInput(null, null, "--config=x"));

        assertEquals("error", result.get("status"));
        assertEquals("Invalid since filter: --config=x", result.get("message"));
    }

    @Test
    void hgLogRejectsOutOfRangeMaxCount() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = logExecutor().execute(new HgLogInput(null, 0, null));

        assertEquals("error", result.get("status"));
        assertEquals("maxCount must be between 1 and 1000", result.get("message"));
    }

    @Test
    void hgLogReportsMissingRepository() throws Exception {
        noOpenProjects();

        Map<String, Object> result = logExecutor().execute(new HgLogInput(null, null, null));

        assertEquals("error", result.get("status"));
        assertEquals("No Mercurial repository found", result.get("message"));
    }

    @Test
    void hgFileHistoryShowsPatchForFile() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File src = new File(repo, "src.txt");
        java.nio.file.Files.writeString(src.toPath(), "one\n");
        commitAll(repo, "first revision");
        java.nio.file.Files.writeString(src.toPath(), "one\ntwo\n");
        commitAll(repo, "second revision");

        Map<String, Object> result = fileHistoryExecutor().execute(new HgFileHistoryInput(null, "src.txt", null));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("first revision"), "history should list both revisions: " + output);
        assertTrue(output.contains("second revision"), "history should list both revisions: " + output);
        assertTrue(output.contains("+two"), "history should include the added line: " + output);
    }

    @Test
    void hgFileHistoryHandlesAbsolutePath() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File src = new File(repo, "abs.txt");
        java.nio.file.Files.writeString(src.toPath(), "content\n");
        commitAll(repo, "abs path commit");

        Map<String, Object> result = fileHistoryExecutor().execute(
                new HgFileHistoryInput(null, src.getAbsolutePath(), null));

        assertEquals("ok", result.get("status"));
        assertTrue(((String) result.get("output")).contains("abs path commit"));
    }

    @Test
    void hgFileHistoryResolvesNestedSubdirectoryPaths() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File nested = new File(repo, "src/deep");
        assertTrue(nested.mkdirs());
        File src = new File(nested, "deep.txt");
        java.nio.file.Files.writeString(src.toPath(), "deep\n");
        commitAll(repo, "nested file commit");

        Map<String, Object> result = fileHistoryExecutor().execute(
                new HgFileHistoryInput(null, "src/deep/deep.txt", null));

        assertEquals("ok", result.get("status"));
        String output = (String) result.get("output");
        assertTrue(output.contains("nested file commit"), "should find the nested file: " + output);
        assertTrue(output.contains("+deep"), "should include the patch: " + output);
    }

    @Test
    void hgFileHistoryRequiresPath() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);

        Map<String, Object> result = fileHistoryExecutor().execute(new HgFileHistoryInput(null, null, null));

        assertEquals("error", result.get("status"));
        assertEquals("path is required", result.get("message"));
    }

    @Test
    void hgFileHistoryRejectsPathOutsideOpenProject() throws Exception {
        File repo = initHgRepo(tempDir.resolve("repo").toFile());
        openProjectAt(repo);
        File outside = tempDir.resolve("outside.txt").toFile();
        assertTrue(outside.createNewFile());

        Map<String, Object> result = fileHistoryExecutor().execute(
                new HgFileHistoryInput(null, outside.getAbsolutePath(), null));

        assertEquals("error", result.get("status"));
        assertEquals("Path is outside the open projects: " + outside.getAbsolutePath(),
                result.get("message"));
    }
}
