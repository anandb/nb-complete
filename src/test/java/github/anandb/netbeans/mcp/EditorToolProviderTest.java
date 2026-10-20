package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

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
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.project.Project;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.EditorContextQuery;
import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.contract.SessionControl;

/**
 * Tests the {@code EditorToolProvider} tool executors: current-file context
 * capture, open-tab filtering by project containment, {@code open_pos}
 * navigation (including the no-focus default), and {@code rename_session}.
 * The NetBeans editor/file statics are mocked; no windows are created, so the
 * tests run headless.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EditorToolProviderTest {

    @TempDir
    Path tempDir;

    @Mock
    private McpTools mcpTools;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private Project project;

    @Mock
    private FileObject projectDirFO;

    @Mock
    private EditorContextQuery editorContextQuery;

    @Mock
    private SessionControl sessionControl;

    private Lookup mockLookup;
    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<FileUtil> fileUtilMock;
    private MockedStatic<DataObject> dataObjectMock;
    private MockedStatic<NbEditorUtilities> nbEditorUtilitiesMock;
    private MockedStatic<EditorRegistry> editorRegistryMock;
    private Map<String, ToolExecutor> executors;

    @BeforeEach
    void setUp() {
        lookupMock = mockStatic(Lookup.class);
        mockLookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(mockLookup);
        when(mockLookup.lookup(EditorContextQuery.class)).thenReturn(editorContextQuery);
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);
        when(mockLookup.lookup(SessionControl.class)).thenReturn(sessionControl);
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[0]);

        fileUtilMock = mockStatic(FileUtil.class);
        dataObjectMock = mockStatic(DataObject.class);
        nbEditorUtilitiesMock = mockStatic(NbEditorUtilities.class);
        editorRegistryMock = mockStatic(EditorRegistry.class);

        EditorToolProvider provider = new EditorToolProvider();
        provider.registerTools(mcpTools);
        executors = captureExecutors();
    }

    @AfterEach
    void tearDown() {
        closeQuietly(lookupMock);
        closeQuietly(fileUtilMock);
        closeQuietly(dataObjectMock);
        closeQuietly(nbEditorUtilitiesMock);
        closeQuietly(editorRegistryMock);
    }

    private static void closeQuietly(MockedStatic<?> mock) {
        if (mock != null) {
            mock.close();
        }
    }

    private Map<String, ToolExecutor> captureExecutors() {
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools, times(4)).registerTool(names.capture(), any(), any(), captor.capture());
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
        when(project.getProjectDirectory()).thenReturn(projectDirFO);
        fileUtilMock.when(() -> FileUtil.toFile(projectDirFO)).thenReturn(root);
    }

    // ---- get_current_file_context ------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<EmptyToolInput, Map<String, Object>> currentFileContext() {
        return executors.get("get_current_file_context");
    }

    @Test
    void currentFileContextReportsMissingService() throws Exception {
        when(mockLookup.lookup(EditorContextQuery.class)).thenReturn(null);

        Map<String, Object> result = currentFileContext().execute(new EmptyToolInput());

        assertEquals("error", result.get("status"));
        assertEquals("Editor context service not available", result.get("message"));
    }

    @Test
    void currentFileContextReturnsCapturedMap() throws Exception {
        Map<String, Object> captured = Map.of("filePath", "/proj/A.java", "line", 7);
        when(editorContextQuery.capture()).thenReturn(captured);

        Map<String, Object> result = currentFileContext().execute(new EmptyToolInput());

        assertEquals(captured, result);
    }

    // ---- get_tabs -----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<EmptyToolInput, List<String>> getTabs() {
        return executors.get("get_tabs");
    }

    private JTextComponent editorWithDocument() {
        JTextComponent editor = mock(JTextComponent.class);
        Document doc = mock(Document.class);
        when(editor.getDocument()).thenReturn(doc);
        editorRegistryMock.when(EditorRegistry::componentList)
                .thenReturn(List.<JTextComponent>of(editor));
        return editor;
    }

    @Test
    void getTabsIncludesFilesInsideOpenProject() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        JTextComponent editor = editorWithDocument();
        FileObject fo = mock(FileObject.class);
        nbEditorUtilitiesMock.when(() -> NbEditorUtilities.getFileObject(editor.getDocument()))
                .thenReturn(fo);
        fileUtilMock.when(() -> FileUtil.toFile(fo)).thenReturn(inside);

        List<String> paths = getTabs().execute(new EmptyToolInput());

        assertEquals(List.of(inside.getAbsolutePath()), paths);
    }

    @Test
    void getTabsSkipsFilesOutsideOpenProject() throws Exception {
        openProjectAt(tempDir.toFile());
        File outside = tempDir.resolveSibling("outside.java").toFile();
        JTextComponent editor = editorWithDocument();
        FileObject fo = mock(FileObject.class);
        nbEditorUtilitiesMock.when(() -> NbEditorUtilities.getFileObject(editor.getDocument()))
                .thenReturn(fo);
        fileUtilMock.when(() -> FileUtil.toFile(fo)).thenReturn(outside);

        List<String> paths = getTabs().execute(new EmptyToolInput());

        assertTrue(paths.isEmpty(), "outside files must be filtered out: " + paths);
    }

    @Test
    void getTabsSkipsEditorWithoutFileObject() throws Exception {
        openProjectAt(tempDir.toFile());
        JTextComponent editor = editorWithDocument();
        nbEditorUtilitiesMock.when(() -> NbEditorUtilities.getFileObject(editor.getDocument()))
                .thenReturn(null);

        List<String> paths = getTabs().execute(new EmptyToolInput());

        assertTrue(paths.isEmpty());
    }

    @Test
    void getTabsFallsBackToFileObjectPathWhenNoDiskFile() throws Exception {
        File virtualRoot = new File("/virtual");
        openProjectAt(virtualRoot);
        JTextComponent editor = editorWithDocument();
        FileObject fo = mock(FileObject.class);
        when(fo.getPath()).thenReturn("/virtual/Path.java");
        nbEditorUtilitiesMock.when(() -> NbEditorUtilities.getFileObject(editor.getDocument()))
                .thenReturn(fo);
        fileUtilMock.when(() -> FileUtil.toFile(fo)).thenReturn(null);

        List<String> paths = getTabs().execute(new EmptyToolInput());

        assertEquals(List.of("/virtual/Path.java"), paths);
    }

    @Test
    void getTabsUsesProjectFileObjectPathWhenDirectoryUnresolvable() throws Exception {
        openProjectAt(tempDir.toFile());
        when(projectDirFO.getPath()).thenReturn("/proj");
        fileUtilMock.when(() -> FileUtil.toFile(projectDirFO)).thenReturn(null);
        JTextComponent editor = editorWithDocument();
        FileObject fo = mock(FileObject.class);
        when(fo.getPath()).thenReturn("/proj/A.java");
        nbEditorUtilitiesMock.when(() -> NbEditorUtilities.getFileObject(editor.getDocument()))
                .thenReturn(fo);
        fileUtilMock.when(() -> FileUtil.toFile(fo)).thenReturn(null);

        List<String> paths = getTabs().execute(new EmptyToolInput());

        assertEquals(List.of("/proj/A.java"), paths);
    }

    @Test
    void getTabsWithoutProjectQueryReturnsEmpty() throws Exception {
        when(mockLookup.lookup(ProjectQuery.class)).thenReturn(null);
        JTextComponent editor = editorWithDocument();
        FileObject fo = mock(FileObject.class);
        nbEditorUtilitiesMock.when(() -> NbEditorUtilities.getFileObject(editor.getDocument()))
                .thenReturn(fo);
        fileUtilMock.when(() -> FileUtil.toFile(fo)).thenReturn(tempDir.resolve("A.java").toFile());

        List<String> paths = getTabs().execute(new EmptyToolInput());

        assertTrue(paths.isEmpty(), "no ProjectQuery means no project containment: " + paths);
    }

    // ---- open_pos -----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<OpenFileInput, Map<String, Object>> openPos() {
        return executors.get("open_pos");
    }

    private LineCookie lineCookie(Line line) {
        LineCookie lc = mock(LineCookie.class);
        Line.Set lineSet = mock(Line.Set.class);
        when(lc.getLineSet()).thenReturn(lineSet);
        when(lineSet.getOriginal(org.mockito.ArgumentMatchers.anyInt())).thenReturn(line);
        return lc;
    }

    private void stubResolvableFile(File file, LineCookie lc) throws Exception {
        FileObject fo = mock(FileObject.class);
        fileUtilMock.when(() -> FileUtil.toFileObject(file)).thenReturn(fo);
        DataObject data = mock(DataObject.class);
        dataObjectMock.when(() -> DataObject.find(fo)).thenReturn(data);
        Lookup dataLookup = mock(Lookup.class);
        when(data.getLookup()).thenReturn(dataLookup);
        when(dataLookup.lookup(LineCookie.class)).thenReturn(lc);
    }

    @Test
    void openPosRejectsNullPath() throws Exception {
        Map<String, Object> result = openPos().execute(new OpenFileInput(null, 1, false));

        assertEquals("error", result.get("status"));
        assertEquals("No file path provided", result.get("message"));
    }

    @Test
    void openPosRejectsFileOutsideProject() throws Exception {
        openProjectAt(tempDir.toFile());
        File outside = tempDir.resolveSibling("outside.java").toFile();

        Map<String, Object> result = openPos()
                .execute(new OpenFileInput(outside.getAbsolutePath(), 1, false));

        assertEquals("error", result.get("status"));
        assertEquals("File is not in the current project", result.get("message"));
    }

    @Test
    void openPosReportsFileNotFoundWhenNoFileObject() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        fileUtilMock.when(() -> FileUtil.toFileObject(inside)).thenReturn(null);

        Map<String, Object> result = openPos()
                .execute(new OpenFileInput(inside.getAbsolutePath(), 1, false));

        assertEquals("error", result.get("status"));
        assertEquals("File or line not found", result.get("message"));
    }

    @Test
    void openPosReportsFileNotFoundWhenNoLineCookie() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        FileObject fo = mock(FileObject.class);
        fileUtilMock.when(() -> FileUtil.toFileObject(inside)).thenReturn(fo);
        DataObject data = mock(DataObject.class);
        dataObjectMock.when(() -> DataObject.find(fo)).thenReturn(data);
        Lookup dataLookup = mock(Lookup.class);
        when(data.getLookup()).thenReturn(dataLookup);
        when(dataLookup.lookup(LineCookie.class)).thenReturn(null);

        Map<String, Object> result = openPos()
                .execute(new OpenFileInput(inside.getAbsolutePath(), 1, false));

        assertEquals("error", result.get("status"));
        assertEquals("File or line not found", result.get("message"));
    }

    @Test
    void openPosOpensWithoutFocusByDefault() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        Line line = mock(Line.class);
        stubResolvableFile(inside, lineCookie(line));

        Map<String, Object> result = openPos()
                .execute(new OpenFileInput(inside.getAbsolutePath(), 1, false));

        assertEquals("ok", result.get("status"));
        verify(line, timeout(5000)).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.NONE);
    }

    @Test
    void openPosTakesFocusOnlyWhenRequested() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        Line line = mock(Line.class);
        stubResolvableFile(inside, lineCookie(line));

        Map<String, Object> result = openPos()
                .execute(new OpenFileInput(inside.getAbsolutePath(), 1, true));

        assertEquals("ok", result.get("status"));
        verify(line, timeout(5000)).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
    }

    @Test
    void openPosClampsZeroAndNegativeLinesToFirstLine() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        Line line = mock(Line.class);
        LineCookie lc = lineCookie(line);
        stubResolvableFile(inside, lc);

        openPos().execute(new OpenFileInput(inside.getAbsolutePath(), 0, false));

        var lineSet = lc.getLineSet();
        verify(lineSet).getOriginal(0);
        verify(lineSet, never()).getOriginal(-1);
    }

    @Test
    void openPosReportsFailureWhenResolutionThrows() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        fileUtilMock.when(() -> FileUtil.toFileObject(inside))
                .thenThrow(new IllegalStateException("boom"));

        Map<String, Object> result = openPos()
                .execute(new OpenFileInput(inside.getAbsolutePath(), 1, false));

        assertEquals("error", result.get("status"));
        assertEquals("Failed to open file", result.get("message"));
    }

    @Test
    void openPosSwallowsEditorShowFailure() throws Exception {
        openProjectAt(tempDir.toFile());
        File inside = tempDir.resolve("A.java").toFile();
        Line line = mock(Line.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("no editor"))
                .when(line).show(any(), any());
        stubResolvableFile(inside, lineCookie(line));

        Map<String, Object> result = openPos()
                .execute(new OpenFileInput(inside.getAbsolutePath(), 1, false));

        assertEquals("ok", result.get("status"));
        // Flush the EDT so the posted show() runs and its failure is swallowed.
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        verify(line, timeout(5000)).show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.NONE);
    }

        // ---- rename_session -----------------------------------------------------

    @SuppressWarnings("unchecked")
    private ToolExecutor<RenameSessionInput, Map<String, Object>> renameSession() {
        return executors.get("rename_session");
    }

    @Test
    void renameSessionRequiresTitle() throws Exception {
        Map<String, Object> result = renameSession().execute(new RenameSessionInput(null, "  "));

        assertEquals("error", result.get("status"));
        assertEquals("title is required", result.get("message"));
    }

    @Test
    void renameSessionUsesExplicitIdAndTrimsTitle() throws Exception {
        Map<String, Object> result = renameSession()
                .execute(new RenameSessionInput("sess-1", "  New Title  "));

        assertEquals("ok", result.get("status"));
        verify(sessionControl).renameSession("sess-1", "New Title");
    }

    @Test
    void renameSessionDefaultsToCurrentSession() throws Exception {
        when(sessionControl.getCurrentSessionId()).thenReturn("current-1");

        Map<String, Object> result = renameSession()
                .execute(new RenameSessionInput(null, "Renamed"));

        assertEquals("ok", result.get("status"));
        verify(sessionControl).renameSession("current-1", "Renamed");
    }

    @Test
    void renameSessionReportsNoActiveSession() throws Exception {
        when(sessionControl.getCurrentSessionId()).thenReturn(null);

        Map<String, Object> result = renameSession()
                .execute(new RenameSessionInput("  ", "Renamed"));

        assertEquals("error", result.get("status"));
        assertEquals("No active session", result.get("message"));
        verify(sessionControl, never()).renameSession(any(), any());
    }
}
