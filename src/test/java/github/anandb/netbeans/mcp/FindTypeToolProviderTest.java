package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.TypeElement;

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

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.project.Project;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.ProjectQuery;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FindTypeToolProviderTest {

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

    @Mock
    private ClasspathInfo classpathInfo;

    @Mock
    private ClassIndex classIndex;

    @Mock
    private ClassPath sourcePath;

    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<ClasspathInfo> classpathInfoMock;
    private MockedStatic<ClassPath> classPathMock;
    private MockedStatic<FileUtil> fileUtilMock;
    private Path projectRoot;
    private ToolExecutor<FindTypeInput, Map<String, Object>> executor;

    @BeforeEach
    void setUp() {
        projectRoot = tempDir.resolve("project");
        assertTrue(projectRoot.toFile().mkdirs());

        lookupMock = mockStatic(Lookup.class);
        Lookup lookup = mock(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(lookup);
        when(lookup.lookup(ProjectQuery.class)).thenReturn(projectQuery);
        when(projectQuery.getAllOpenProjects()).thenReturn(new Project[] {project});
        when(project.getProjectDirectory()).thenReturn(projectDir);

        fileUtilMock = mockStatic(FileUtil.class);
        fileUtilMock.when(() -> FileUtil.toFile(projectDir)).thenReturn(projectRoot.toFile());

        classpathInfoMock = mockStatic(ClasspathInfo.class);
        classpathInfoMock.when(() -> ClasspathInfo.create(projectDir)).thenReturn(classpathInfo);
        when(classpathInfo.getClassIndex()).thenReturn(classIndex);

        classPathMock = mockStatic(ClassPath.class);
        classPathMock.when(() -> ClassPath.getClassPath(projectDir, ClassPath.SOURCE)).thenReturn(sourcePath);

        FindTypeToolProvider provider = new FindTypeToolProvider();
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools, times(1)).registerTool(eq("find_java_type"), any(), any(), captor.capture());
        executor = captor.getValue();
    }

    @AfterEach
    void tearDown() {
        fileUtilMock.close();
        classPathMock.close();
        classpathInfoMock.close();
        lookupMock.close();
    }

    @Test
    void registersFindTypeWithItsInputRecord() {
        assertEquals(FindTypeInput.class, executor.getArgClass());
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        Map<String, Object> result = execute(new FindTypeInput(" ", null, null, null, null));
        assertEquals("error", result.get("status"));
        assertEquals("query is required", result.get("message"));
    }

    @Test
    void rejectsUnknownKind() throws Exception {
        Map<String, Object> result = execute(new FindTypeInput("Bar", "fuzzy", null, null, null));
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).startsWith("kind must be one of"));
    }

    @Test
    void rejectsInvalidRegexpQuery() throws Exception {
        Map<String, Object> result = execute(new FindTypeInput("Foo[", "regexp", null, null, null));
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).startsWith("query is not a valid regular expression"));
    }

    @Test
    void rejectsUnknownScope() throws Exception {
        Map<String, Object> result = execute(new FindTypeInput("Bar", null, "jdk", null, null));
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).startsWith("scope must be one of"));
    }

    @Test
    void rejectsProjectPathThatIsNotOpen() throws Exception {
        Map<String, Object> result = execute(
                new FindTypeInput("Bar", null, null, tempDir.resolve("elsewhere").toString(), null));
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).startsWith("Not an open project"));
    }

    @Test
    void returnsSortedMatchesWithPathsAndDeduplicates() throws Exception {
        ElementHandle<TypeElement> bar1 = handle("com.foo.Bar");
        ElementHandle<TypeElement> bar2 = handle("com.foo.Bar");
        ElementHandle<TypeElement> util = handle("org.acme.Util");
        when(classIndex.getDeclaredTypes(eq("Bar"), eq(ClassIndex.NameKind.CAMEL_CASE_INSENSITIVE), any()))
                .thenReturn(Set.of(bar1, bar2, util));

        FileObject barFile = mock(FileObject.class);
        when(sourcePath.findResource("com/foo/Bar.java")).thenReturn(barFile);
        File barPath = projectRoot.resolve("src/com/foo/Bar.java").toFile();
        fileUtilMock.when(() -> FileUtil.toFile(barFile)).thenReturn(barPath);

        Map<String, Object> result = execute(new FindTypeInput("Bar", null, null, null, null));

        assertEquals("ok", result.get("status"));
        assertEquals(2, result.get("count"));
        assertFalse(result.containsKey("truncated"));
        List<Map<String, Object>> matches = matches(result);
        assertEquals("com.foo.Bar", matches.get(0).get("name"));
        assertEquals(barPath.getAbsolutePath(), matches.get(0).get("path"));
        assertEquals(projectRoot.toFile().getAbsolutePath(), matches.get(0).get("project"));
        assertEquals("org.acme.Util", matches.get(1).get("name"));
        assertFalse(matches.get(1).containsKey("path"));
    }

    @Test
    void truncatesBeyondTheLimit() throws Exception {
        ElementHandle<TypeElement> bar = handle("com.foo.Bar");
        ElementHandle<TypeElement> util = handle("org.acme.Util");
        when(classIndex.getDeclaredTypes(eq("Bar"), eq(ClassIndex.NameKind.CAMEL_CASE_INSENSITIVE), any()))
                .thenReturn(Set.of(bar, util));

        Map<String, Object> result = execute(new FindTypeInput("Bar", null, null, null, 1));

        assertEquals("ok", result.get("status"));
        assertEquals(1, result.get("count"));
        assertEquals(true, result.get("truncated"));
        assertEquals("com.foo.Bar", matches(result).get(0).get("name"));
    }

    @Test
    void treatsCancelledIndexAsNoMatches() throws Exception {
        when(classIndex.getDeclaredTypes(eq("Bar"), eq(ClassIndex.NameKind.CAMEL_CASE_INSENSITIVE), any()))
                .thenReturn(null);

        Map<String, Object> result = execute(new FindTypeInput("Bar", null, null, null, null));

        assertEquals("ok", result.get("status"));
        assertEquals(0, result.get("count"));
        assertNotNull(result.get("matches"));
    }

    @SuppressWarnings("unchecked")
    private static ElementHandle<TypeElement> handle(String fqn) {
        ElementHandle<TypeElement> handle = mock(ElementHandle.class);
        when(handle.getQualifiedName()).thenReturn(fqn);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(FindTypeInput input) throws Exception {
        return (Map<String, Object>) executor.execute(input);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> matches(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("matches");
    }
}
