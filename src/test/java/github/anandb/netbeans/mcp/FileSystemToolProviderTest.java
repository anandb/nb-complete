package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.contract.SessionQuery;

/**
 * Tests the {@code FileSystemToolProvider} tool executors: happy paths for
 * read/write/replace/insert/delete/search/list/run/patch, and — most
 * importantly — the ProjectPathGuard containment contract that every
 * path-taking tool must enforce (a rogue MCP client must not reach files
 * outside the open projects).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileSystemToolProviderTest {

    @TempDir
    Path tempDir;

    @Mock
    private McpTools mcpTools;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private SessionQuery sessionQuery;

    @Mock
    private Project project;

    @Mock
    private FileObject projectDir;

    private Lookup mockLookup;
    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<FileUtil> fileUtilMock;
    private FileSystemToolProvider provider;
    private Map<String, ToolExecutor> executors;

    @BeforeEach
    void setUp() throws Exception {
        lookupMock = mockStatic(Lookup.class);
        mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);
        when(mockLookup.lookup(SessionQuery.class)).thenReturn(sessionQuery);

        fileUtilMock = mockStatic(FileUtil.class);

        provider = new FileSystemToolProvider();
        executors = registerAll();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (lookupMock != null) {
            lookupMock.close();
        }
        if (fileUtilMock != null) {
            fileUtilMock.close();
        }
    }

    private Map<String, ToolExecutor> registerAll() {
        provider.registerTools(mcpTools);
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools, times(9)).registerTool(names.capture(), any(), any(), captor.capture());
        Map<String, ToolExecutor> map = new HashMap<>();
        List<String> nameList = names.getAllValues();
        List<ToolExecutor> execList = captor.getAllValues();
        for (int i = 0; i < nameList.size(); i++) {
            map.put(nameList.get(i), execList.get(i));
        }
        return map;
    }

    private void openProjectAt(File root) {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] { project });
        when(project.getProjectDirectory()).thenReturn(projectDir);
        fileUtilMock.when(() -> FileUtil.toFile(projectDir)).thenReturn(root);
    }

    private File writeFile(String name, String content) throws Exception {
        File f = tempDir.resolve(name).toFile();
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        return f;
    }

    // ---- read_file ----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<ReadFileInput, Map<String, Object>> readFile() {
        return executors.get("read_file");
    }

    @Test
    void readFileReturnsWholeFile() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("a.txt", "one\ntwo\nthree\n");

        Map<String, Object> result = readFile().execute(new ReadFileInput(f.getAbsolutePath(), null, null));

        assertEquals("ok", result.get("status"));
        assertEquals("one\ntwo\nthree", result.get("content"));
        assertEquals(3, result.get("totalLines"));
        assertEquals(3, result.get("linesReturned"));
    }

    @Test
    void readFileHonorsLineWindow() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("a.txt", "1\n2\n3\n4\n5\n");

        Map<String, Object> result = readFile().execute(new ReadFileInput(f.getAbsolutePath(), 2, 4));

        assertEquals("ok", result.get("status"));
        assertEquals("2\n3\n4", result.get("content"));
        assertEquals(5, result.get("totalLines"));
        assertEquals(3, result.get("linesReturned"));
    }

    @Test
    void readFileRejectsBlankPath() throws Exception {
        Map<String, Object> result = readFile().execute(new ReadFileInput("  ", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("filePath is required", result.get("message"));
    }

    @Test
    void readFileRejectsMissingFile() throws Exception {
        openProjectAt(tempDir.toFile());
        String missing = tempDir.resolve("nope.txt").toString();

        Map<String, Object> result = readFile().execute(new ReadFileInput(missing, null, null));

        assertEquals("error", result.get("status"));
        assertEquals("File not found: " + missing, result.get("message"));
    }

    @Test
    void readFileRejectsDirectory() throws Exception {
        openProjectAt(tempDir.toFile());
        String dir = tempDir.toString();

        Map<String, Object> result = readFile().execute(new ReadFileInput(dir, null, null));

        assertEquals("error", result.get("status"));
        assertEquals("Not a file: " + dir, result.get("message"));
    }

    // ---- write_to_file ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<WriteToFileInput, Map<String, Object>> writeFileTool() {
        return executors.get("write_to_file");
    }

    @Test
    void writeToFileCreatesFileAndParents() throws Exception {
        openProjectAt(tempDir.toFile());
        String target = tempDir.resolve("sub/dir/b.txt").toString();

        Map<String, Object> result = writeFileTool().execute(new WriteToFileInput(target, "hello\n"));

        assertEquals("ok", result.get("status"));
        assertEquals(6, result.get("bytesWritten"));
        assertEquals("hello\n", Files.readString(Path.of(target), StandardCharsets.UTF_8));
    }

    @Test
    void writeToFileRejectsBlankPath() throws Exception {
        Map<String, Object> result = writeFileTool().execute(new WriteToFileInput(null, "x"));

        assertEquals("error", result.get("status"));
        assertEquals("filePath is required", result.get("message"));
    }

    @Test
    void writeToFileRejectsNullContent() throws Exception {
        Map<String, Object> result = writeFileTool().execute(new WriteToFileInput("/tmp/x", null));

        assertEquals("error", result.get("status"));
        assertEquals("content is required", result.get("message"));
    }

    // ---- containment matrix: every path tool rejects outside paths ----------

    @Test
    void everyPathToolRejectsPathsOutsideOpenProjects() throws Exception {
        openProjectAt(tempDir.toFile());
        String outside = tempDir.resolveSibling("outside-secret.txt").toString();
        String rejected = "Path is outside the open projects: " + outside;

        assertEquals(rejected,
                readFile().execute(new ReadFileInput(outside, null, null)).get("message"));
        assertEquals(rejected,
                writeFileTool().execute(new WriteToFileInput(outside, "x")).get("message"));
        assertEquals(rejected,
                replaceLines().execute(new ReplaceLinesInput(outside, 1, 1, "x")).get("message"));
        assertEquals(rejected,
                insertInFile().execute(new InsertInFileInput(outside, 1, "x")).get("message"));
        assertEquals(rejected,
                deleteLines().execute(new DeleteLinesInput(outside, 1, 1)).get("message"));
        assertEquals(rejected,
                listDirectory().execute(new ListDirectoryInput(outside, false)).get("message"));
        assertEquals(rejected,
                applyPatch().execute(
                        new ApplyPatchInput(outside, "--- a\n+++ b\n@@ -0,0 +1 @@\n+x\n", null))
                        .get("message"));
        assertEquals(rejected,
                runCommand().execute(new RunCommandInput("echo hi", outside, 5)).get("message"));

        // The rejected write must not have created the file.
        assertFalse(new File(outside).exists(), "outside write must not create a file");
    }

    // ---- replace_lines ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<ReplaceLinesInput, Map<String, Object>> replaceLines() {
        return executors.get("replace_lines");
    }

    @Test
    void replaceLinesSwapsRange() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("r.txt", "a\nb\nc\n");

        Map<String, Object> result = replaceLines()
                .execute(new ReplaceLinesInput(f.getAbsolutePath(), 2, 2, "B1\nB2"));

        assertEquals("ok", result.get("status"));
        assertEquals(1, result.get("linesReplaced"));
        assertEquals("a\nB1\nB2\nc", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void replaceLinesRejectsInvertedRange() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("r.txt", "a\nb\nc\n");

        Map<String, Object> result = replaceLines()
                .execute(new ReplaceLinesInput(f.getAbsolutePath(), 3, 1, "x"));

        assertEquals("error", result.get("status"));
        assertEquals("startLine > endLine", result.get("message"));
    }

    // ---- insert_in_file -----------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<InsertInFileInput, Map<String, Object>> insertInFile() {
        return executors.get("insert_in_file");
    }

    @Test
    void insertInFileInsertsBeforeLine() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("i.txt", "a\nb\nc\n");

        Map<String, Object> result = insertInFile()
                .execute(new InsertInFileInput(f.getAbsolutePath(), 2, "X\nY"));

        assertEquals("ok", result.get("status"));
        assertEquals(2, result.get("linesInserted"));
        assertEquals("a\nX\nY\nb\nc", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void insertInFileClampsLinePastEofToEnd() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("i.txt", "a\nb\n");

        Map<String, Object> result = insertInFile()
                .execute(new InsertInFileInput(f.getAbsolutePath(), 99, "end"));

        assertEquals("ok", result.get("status"));
        assertEquals("a\nb\nend", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    // ---- delete_lines -------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<DeleteLinesInput, Map<String, Object>> deleteLines() {
        return executors.get("delete_lines");
    }

    @Test
    void deleteLinesRemovesRange() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("d.txt", "a\nb\nc\nd\n");

        Map<String, Object> result = deleteLines()
                .execute(new DeleteLinesInput(f.getAbsolutePath(), 2, 3));

        assertEquals("ok", result.get("status"));
        assertEquals(2, result.get("linesDeleted"));
        assertEquals("a\nd\n", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void deleteLinesPreservesCrlfSeparators() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("d.txt", "a\r\nb\r\nc\r\n");

        Map<String, Object> result = deleteLines()
                .execute(new DeleteLinesInput(f.getAbsolutePath(), 2, 2));

        assertEquals("ok", result.get("status"));
        assertEquals("a\r\nc\r\n", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void deleteLinesRejectsInvertedRange() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("d.txt", "a\nb\nc\n");

        Map<String, Object> result = deleteLines()
                .execute(new DeleteLinesInput(f.getAbsolutePath(), 3, 1));

        assertEquals("error", result.get("status"));
        assertEquals("startLine (3) > endLine (1)", result.get("message"));
    }

    @Test
    void deleteLinesRejectsMissingFile() throws Exception {
        openProjectAt(tempDir.toFile());
        String missing = tempDir.resolve("nope.txt").toString();

        Map<String, Object> result = deleteLines().execute(new DeleteLinesInput(missing, 1, 1));

        assertEquals("error", result.get("status"));
        assertEquals("File not found: " + missing, result.get("message"));
    }

    // ---- search_project -----------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<SearchProjectInput, Map<String, Object>> searchProject() {
        return executors.get("search_project");
    }

    @Test
    void searchProjectFindsMatchesCaseInsensitive() throws Exception {
        openProjectAt(tempDir.toFile());
        writeFile("src/A.java", "public class A { /* needle here */ }\n");
        writeFile("README.md", "NEEDLE in markdown\n");

        Map<String, Object> result = searchProject().execute(new SearchProjectInput("needle", null, null));

        assertEquals("ok", result.get("status"));
        assertEquals(2, result.get("totalMatches"));
    }

    @Test
    void searchProjectHonorsGlobFilter() throws Exception {
        openProjectAt(tempDir.toFile());
        writeFile("src/A.java", "needle in java\n");
        writeFile("README.md", "needle in markdown\n");

        Map<String, Object> result = searchProject().execute(new SearchProjectInput("needle", "*.java", null));

        assertEquals("ok", result.get("status"));
        assertEquals(1, result.get("totalMatches"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertTrue(matches.get(0).get("file").toString().endsWith("A.java"),
                "glob filter should only match .java files: " + matches);
    }

    @Test
    void searchProjectRejectsEscapingDirectory() throws Exception {
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = searchProject().execute(new SearchProjectInput("x", null, "../../.."));

        assertEquals("error", result.get("status"));
        assertEquals("directory escapes the project root: ../../..", result.get("message"));
    }

    @Test
    void searchProjectRejectsBlankQuery() throws Exception {
        Map<String, Object> result = searchProject().execute(new SearchProjectInput("  ", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("query is required", result.get("message"));
    }

    @Test
    void searchProjectRejectsInvalidRegex() throws Exception {
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = searchProject().execute(new SearchProjectInput("[unclosed", null, null));

        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).startsWith("Invalid regex:"),
                "should report the regex parse error: " + result.get("message"));
    }

    @Test
    void searchProjectReportsNoOpenProjects() throws Exception {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[0]);

        Map<String, Object> result = searchProject().execute(new SearchProjectInput("x", null, null));

        assertEquals("error", result.get("status"));
        assertEquals("No open projects", result.get("message"));
    }

    // ---- list_directory -----------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<ListDirectoryInput, Map<String, Object>> listDirectory() {
        return executors.get("list_directory");
    }

    @Test
    void listDirectoryListsTopLevelEntries() throws Exception {
        openProjectAt(tempDir.toFile());
        writeFile("a.txt", "x");
        assertTrue(tempDir.resolve("sub").toFile().mkdirs());

        Map<String, Object> result = listDirectory()
                .execute(new ListDirectoryInput(tempDir.toString(), false));

        assertEquals("ok", result.get("status"));
        assertEquals(2, result.get("totalEntries"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entries");
        Map<String, String> types = new HashMap<>();
        for (Map<String, Object> e : entries) {
            types.put(new File((String) e.get("path")).getName(), (String) e.get("type"));
        }
        assertEquals("file", types.get("a.txt"));
        assertEquals("directory", types.get("sub"));
    }

    @Test
    void listDirectoryRecursesIntoSubdirectories() throws Exception {
        openProjectAt(tempDir.toFile());
        assertTrue(tempDir.resolve("sub/deep").toFile().mkdirs());
        writeFile("sub/deep/n.txt", "x");

        Map<String, Object> result = listDirectory()
                .execute(new ListDirectoryInput(tempDir.toString(), true));

        assertEquals("ok", result.get("status"));
        assertTrue((Integer) result.get("totalEntries") >= 3,
                "recursive listing should include sub and deep: " + result.get("totalEntries"));
    }

    @Test
    void listDirectoryRejectsMissingDirectory() throws Exception {
        openProjectAt(tempDir.toFile());
        String missing = tempDir.resolve("nope").toString();

        Map<String, Object> result = listDirectory().execute(new ListDirectoryInput(missing, false));

        assertEquals("error", result.get("status"));
        assertEquals("Directory not found: " + missing, result.get("message"));
    }

    @Test
    void listDirectoryRejectsFilePath() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("f.txt", "x");

        Map<String, Object> result = listDirectory()
                .execute(new ListDirectoryInput(f.getAbsolutePath(), false));

        assertEquals("error", result.get("status"));
        assertEquals("Not a directory: " + f.getAbsolutePath(), result.get("message"));
    }

    @Test
    void listDirectoryRejectsBlankPath() throws Exception {
        Map<String, Object> result = listDirectory().execute(new ListDirectoryInput(null, false));

        assertEquals("error", result.get("status"));
        assertEquals("dirPath is required", result.get("message"));
    }

    // ---- run_command --------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<RunCommandInput, Map<String, Object>> runCommand() {
        return executors.get("run_command");
    }

    @Test
    void runCommandRunsInExplicitWorkingDirectory() throws Exception {
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = runCommand()
                .execute(new RunCommandInput("echo hi", tempDir.toString(), 30));

        assertEquals("ok", result.get("status"));
        assertEquals(0, result.get("exitCode"));
        assertEquals("hi", result.get("output"));
    }

    @Test
    void runCommandDefaultsToSessionDirectory() throws Exception {
        openProjectAt(tempDir.toFile());
        when(sessionQuery.getCurrentSessionDirectory()).thenReturn(tempDir.toString());

        Map<String, Object> result = runCommand().execute(new RunCommandInput("pwd", null, 30));

        assertEquals("ok", result.get("status"));
        assertEquals(tempDir.toFile().getCanonicalPath(),
                ((String) result.get("output")).trim());
    }

    @Test
    void runCommandRejectsOutsideWorkingDirectory() throws Exception {
        openProjectAt(tempDir.toFile());
        String outside = tempDir.resolveSibling("elsewhere").toString();

        Map<String, Object> result = runCommand().execute(new RunCommandInput("echo hi", outside, 30));

        assertEquals("error", result.get("status"));
        assertEquals("Path is outside the open projects: " + outside, result.get("message"));
    }

    @Test
    void runCommandRejectsBlankCommand() throws Exception {
        Map<String, Object> result = runCommand().execute(new RunCommandInput("  ", null, 30));

        assertEquals("error", result.get("status"));
        assertEquals("command is required", result.get("message"));
    }

    @Test
    void runCommandReportsMissingWorkingDirectory() throws Exception {
        openProjectAt(tempDir.toFile());
        when(sessionQuery.getCurrentSessionDirectory()).thenReturn(null);

        Map<String, Object> result = runCommand().execute(new RunCommandInput("echo hi", null, 30));

        assertEquals("error", result.get("status"));
        assertEquals("No working directory specified and no active session", result.get("message"));
    }

    @Test
    void runCommandTimesOut() throws Exception {
        openProjectAt(tempDir.toFile());

        Map<String, Object> result = runCommand()
                .execute(new RunCommandInput("sleep 5", tempDir.toString(), 1));

        assertEquals("error", result.get("status"));
        assertEquals("Command timed out after 1s", result.get("message"));
    }

    // ---- apply_patch --------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<ApplyPatchInput, Map<String, Object>> applyPatch() {
        return executors.get("apply_patch");
    }

    private static final String SIMPLE_PATCH =
            "--- a/f.txt\n"
            + "+++ b/f.txt\n"
            + "@@ -1,3 +1,3 @@\n"
            + " one\n"
            + "-two\n"
            + "+TWO\n"
            + " three";

    @Test
    void applyPatchAppliesUnifiedDiff() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("f.txt", "one\ntwo\nthree\n");

        Map<String, Object> result = applyPatch()
                .execute(new ApplyPatchInput(f.getAbsolutePath(), SIMPLE_PATCH, null));

        assertEquals("ok", result.get("status"));
        assertEquals(1, result.get("hunksApplied"));
        assertEquals(false, result.get("dryRun"));
        assertEquals("one\nTWO\nthree", Files.readString(f.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void applyPatchDryRunLeavesOriginalUntouched() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("f.txt", "one\ntwo\nthree\n");

        Map<String, Object> result = applyPatch()
                .execute(new ApplyPatchInput(f.getAbsolutePath(), SIMPLE_PATCH, true));

        assertEquals("ok", result.get("status"));
        assertEquals(true, result.get("dryRun"));
        assertEquals("one\ntwo\nthree\n", Files.readString(f.toPath(), StandardCharsets.UTF_8),
                "dryRun must not modify the original file");
        String tempPath = (String) result.get("filePath");
        assertNotEquals(f.getAbsolutePath(), tempPath);
        assertEquals("one\nTWO\nthree", Files.readString(Path.of(tempPath), StandardCharsets.UTF_8));
        Files.deleteIfExists(Path.of(tempPath));
    }

    @Test
    void applyPatchRejectsNonApplyingContext() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("f.txt", "completely\ndifferent\ncontent\n");
        String patch = "--- a/f.txt\n+++ b/f.txt\n@@ -1,1 +1,1 @@\n-nope\n+yes\n";

        Map<String, Object> result = applyPatch()
                .execute(new ApplyPatchInput(f.getAbsolutePath(), patch, null));

        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).startsWith("Patch does not apply:"),
                "should report the conflict: " + result.get("message"));
    }

    @Test
    void applyPatchRejectsBlankPatch() throws Exception {
        openProjectAt(tempDir.toFile());
        File f = writeFile("f.txt", "x\n");

        Map<String, Object> result = applyPatch()
                .execute(new ApplyPatchInput(f.getAbsolutePath(), "  ", null));

        assertEquals("error", result.get("status"));
        assertEquals("patch is required", result.get("message"));
    }

    @Test
    void applyPatchRejectsMissingFile() throws Exception {
        openProjectAt(tempDir.toFile());
        String missing = tempDir.resolve("nope.txt").toString();

        Map<String, Object> result = applyPatch()
                .execute(new ApplyPatchInput(missing, SIMPLE_PATCH, null));

        assertEquals("error", result.get("status"));
        assertEquals("File not found: " + missing, result.get("message"));
    }

    // ---- guard: no open projects --------------------------------------------

    @Test
    void fileToolsRejectWhenNoProjectsAreOpen() throws Exception {
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[0]);
        String outside = tempDir.resolve("a.txt").toString();
        String rejected = "Path is outside the open projects: " + outside;

        // With zero open projects every containment check fails, so even a
        // path that happens to exist on disk must be refused.
        assertEquals(rejected,
                readFile().execute(new ReadFileInput(outside, null, null)).get("message"));
        verify(sessionQuery, never()).getCurrentSessionDirectory();
    }
}
