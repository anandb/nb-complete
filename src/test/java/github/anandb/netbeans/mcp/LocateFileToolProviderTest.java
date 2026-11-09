package github.anandb.netbeans.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.contract.SessionQuery;

/**
 * locate_file is the file index behind Jump to File, exposed to the model: same matching rules,
 * capped at five matches, with the absolute path the other tools expect.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocateFileToolProviderTest {

    @Mock
    private McpTools mcpTools;

    @Mock
    private FileCacheQuery cache;

    @Mock
    private SessionQuery sessionQuery;

    private MockedStatic<Lookup> lookupMock;
    private MockedStatic<FileUtil> fileUtilMock;
    private ToolExecutor<LocateFileInput, Map<String, Object>> executor;

    @BeforeEach
    void setUp() {
        Lookup lookup = mock(Lookup.class);
        lookupMock = mockStatic(Lookup.class);
        lookupMock.when(Lookup::getDefault).thenReturn(lookup);
        when(lookup.lookup(FileCacheQuery.class)).thenReturn(cache);
        when(lookup.lookup(SessionQuery.class)).thenReturn(sessionQuery);
        when(sessionQuery.getCurrentSessionDirectory()).thenReturn(null);
        when(cache.isReady()).thenReturn(true);
        when(cache.getAllFiles()).thenReturn(List.of());

        fileUtilMock = mockStatic(FileUtil.class);

        LocateFileToolProvider provider = new LocateFileToolProvider();
        provider.registerTools(mcpTools);
        ArgumentCaptor<ToolExecutor> captor = ArgumentCaptor.forClass(ToolExecutor.class);
        verify(mcpTools, times(1)).registerTool(eq("locate_file"), any(), any(), captor.capture());
        executor = captor.getValue();
    }

    @AfterEach
    void tearDown() {
        fileUtilMock.close();
        lookupMock.close();
    }

    @Test
    void registersLocateFileWithItsInputRecord() {
        assertEquals(LocateFileInput.class, executor.getArgClass());
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        assertEquals("error", execute(null).get("status"));
        assertEquals("query is required", execute("   ").get("message"));
    }

    @Test
    void rejectsAPlainQueryShorterThanThreeCharacters() throws Exception {
        Map<String, Object> result = execute("ab");
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).contains("3"));
    }

    @Test
    void rejectsAnUnknownScope() throws Exception {
        Map<String, Object> result = execute("chat", "everywhere");
        assertEquals("error", result.get("status"));
        assertTrue(((String) result.get("message")).startsWith("scope must be one of"));
    }
    @Test
    void acceptsAShortGlobQuery() throws Exception {
        assertEquals("ok", execute("*ab").get("status"));
    }

    @Test
    void reportsAMissingCacheAsFatal() throws Exception {
        lookupMock.when(Lookup::getDefault).thenReturn(mock(Lookup.class));
        Map<String, Object> result = execute("chat");
        assertEquals("error", result.get("status"));
        assertEquals(Boolean.TRUE, result.get("fatal"));
    }

    @Test
    void returnsMatchesWithProjectRelativeAndAbsolutePaths() throws Exception {
        StubCache stub = new StubCache();
        stub.add("ChatThreadPanel.java", "nb", "src/ui/chat/ChatThreadPanel.java", "/work/nb");
        stub.install();

        Map<String, Object> result = execute("Chat");

        assertEquals("ok", result.get("status"));
        assertEquals(1, result.get("count"));
        Map<?, ?> match = (Map<?, ?>) matches(result).get(0);
        assertEquals("ChatThreadPanel.java", match.get("name"));
        assertEquals("nb", match.get("project"));
        assertEquals("src/ui/chat/ChatThreadPanel.java", match.get("relativePath"));
        assertEquals("/work/nb/src/ui/chat/ChatThreadPanel.java", match.get("path"));
        assertNull(result.get("truncated"));
    }

    @Test
    void capsTheAnswerAtFiveMatches() throws Exception {
        StubCache stub = new StubCache();
        for (int i = 0; i < 8; i++) {
            stub.add("File" + i + ".java", "nb", "src/File" + i + ".java", "/work/nb");
        }
        stub.install();

        Map<String, Object> result = execute("File");

        assertEquals(5, result.get("count"));
        assertEquals(5, matches(result).size());
        assertEquals(Boolean.TRUE, result.get("truncated"));
    }

    @Test
    void doesNotClaimTruncationWhenEverythingFits() throws Exception {
        StubCache stub = new StubCache();
        stub.add("FileA.java", "nb", "src/FileA.java", "/work/nb");
        stub.add("FileB.java", "nb", "src/FileB.java", "/work/nb");
        stub.install();

        assertNull(execute("File").get("truncated"));
    }

    @Test
    void putsFileNameHitsBeforePathOnlyHits() throws Exception {
        StubCache stub = new StubCache();
        stub.add("MessageBubble.java", "nb", "src/ui/chat/MessageBubble.java", "/work/nb");
        stub.add("ChatPanel.java", "nb", "src/ui/ChatPanel.java", "/work/nb");
        stub.install();

        List<?> matches = matches(execute("chat"));

        Map<?, ?> first = (Map<?, ?>) matches.get(0);
        assertEquals("ChatPanel.java", first.get("name"), "a file-name hit must come first");
    }

    @Test
    void ordersEqualHitsByProjectThenPath() throws Exception {
        StubCache stub = new StubCache();
        stub.add("Log.java", "zproject", "z/Log.java", "/work/z");
        stub.add("Log.java", "aproject", "b/Log.java", "/work/a");
        stub.install();

        List<?> matches = matches(execute("Log"));
        assertEquals("aproject", ((Map<?, ?>) matches.get(0)).get("project"));
    }

    @Test
    void flagsAnIndexStillBeingBuilt() throws Exception {
        when(cache.isReady()).thenReturn(false);
        StubCache stub = new StubCache();
        stub.add("ChatPanel.java", "nb", "src/ChatPanel.java", "/work/nb");
        stub.install();

        Map<String, Object> result = execute("Chat");

        assertEquals("ok", result.get("status"));
        assertEquals(Boolean.TRUE, result.get("indexing"));
        assertEquals(1, result.get("count"));
    }

    @Test
    void omitsThePathForVirtualFiles() throws Exception {
        FileObject virtual = stub("Virtual.java", "src/Virtual.java", null);
        StubCache stub = new StubCache(virtual, "nb", "src/Virtual.java");
        stub.install();

        Map<?, ?> match = (Map<?, ?>) matches(execute("Virtual")).get(0);

        assertFalse(match.containsKey("path"), "a file with no java.io.File must not fake a path");
        assertEquals("src/Virtual.java", match.get("relativePath"));
    }

    @Test
    void scopesTheSearchToTheSessionDirectoryByDefault() throws Exception {
        when(sessionQuery.getCurrentSessionDirectory()).thenReturn("/work/nb/src/ui");
        StubCache stub = new StubCache();
        stub.add("ChatPanel.java", "nb", "src/ui/ChatPanel.java", "/work/nb");
        stub.add("ChatStore.java", "nb", "src/data/ChatStore.java", "/work/nb");
        stub.add("ChatOld.java", "nb", "src/ui-old/ChatOld.java", "/work/nb");
        stub.install();

        Map<String, Object> result = execute("Chat");

        assertEquals(Scope.SESSION, result.get("scope"));
        assertEquals("/work/nb/src/ui", result.get("directory"));
        assertEquals(1, result.get("count"), "only the file inside the working directory");
        assertEquals("ChatPanel.java", ((Map<?, ?>) matches(result).get(0)).get("name"));
    }

    @Test
    void queriesOnlyTheSessionProject() throws Exception {
        when(sessionQuery.getCurrentSessionDirectory()).thenReturn("/work/nb/src/ui");
        when(cache.getProjectName("/work/nb/src/ui")).thenReturn("nb");
        StubCache other = new StubCache();
        other.add("ChatOther.java", "other", "src/ChatOther.java", "/work/other");
        other.install();
        StubCache own = new StubCache();
        own.add("ChatPanel.java", "nb", "src/ui/ChatPanel.java", "/work/nb");
        when(cache.getFiles("nb")).thenReturn(own.entries());

        Map<String, Object> result = execute("Chat");

        assertEquals(1, result.get("count"), "only the session project is matched");
        assertEquals("ChatPanel.java", ((Map<?, ?>) matches(result).get(0)).get("name"));
        verify(cache, never()).getAllFiles();
    }

    @Test
    void searchesAllProjectsWhenTheSessionHasNoDirectory() throws Exception {
        StubCache stub = new StubCache();
        stub.add("ChatPanel.java", "nb", "src/ui/ChatPanel.java", "/work/nb");
        stub.add("ChatStore.java", "nb", "src/data/ChatStore.java", "/work/nb");
        stub.install();

        Map<String, Object> result = execute("Chat");

        assertEquals(Scope.PROJECTS, result.get("scope"));
        assertFalse(result.containsKey("directory"));
        assertEquals(2, result.get("count"));
    }

    @Test
    void scopeProjectsOverridesTheSessionDirectory() throws Exception {
        when(sessionQuery.getCurrentSessionDirectory()).thenReturn("/work/nb/src/ui");
        StubCache stub = new StubCache();
        stub.add("ChatPanel.java", "nb", "src/ui/ChatPanel.java", "/work/nb");
        stub.add("ChatStore.java", "nb", "src/data/ChatStore.java", "/work/nb");
        stub.install();

        Map<String, Object> result = execute("Chat", Scope.PROJECTS);

        assertEquals(Scope.PROJECTS, result.get("scope"));
        assertEquals(2, result.get("count"));
    }

    @Test
    void aScopedSearchDropsFilesWithNoAbsolutePath() throws Exception {
        when(sessionQuery.getCurrentSessionDirectory()).thenReturn("/work/nb");
        FileObject virtual = stub("Virtual.java", "src/Virtual.java", null);
        StubCache stub = new StubCache(virtual, "nb", "src/Virtual.java");
        stub.install();

        Map<String, Object> result = execute("Virtual");

        assertEquals(0, result.get("count"), "a file with no path cannot be proven inside the scope");
        assertEquals(Scope.SESSION, result.get("scope"));
    }

    // --- helpers ---

    /** Scope values, kept in one place so the tests read like the reply. */
    private static final class Scope {
        static final String SESSION = "session";
        static final String PROJECTS = "projects";

        private Scope() {
        }
    }

    private Map<String, Object> execute(String query) throws Exception {
        return execute(query, null);
    }

    private Map<String, Object> execute(String query, String scope) throws Exception {
        return executor.execute(new LocateFileInput(query, scope));
    }

    private static List<?> matches(Map<String, Object> result) {
        return (List<?>) result.get("matches");
    }

    /** Builds a FileObject whose name, path and optional java.io.File are stubbed. */
    private FileObject stub(String nameExt, String relativePath, String absolutePath) {
        FileObject file = mock(FileObject.class);
        when(file.getNameExt()).thenReturn(nameExt);
        if (absolutePath != null) {
            File real = new File(absolutePath);
            fileUtilMock.when(() -> FileUtil.toFile(file)).thenReturn(real);
        } else {
            fileUtilMock.when(() -> FileUtil.toFile(file)).thenReturn(null);
        }
        return file;
    }

    /** Installs a fixed set of cached files, then wires them into the mocked cache. */
    private final class StubCache {
        private final List<FileCacheQuery.CachedFile> files = new ArrayList<>();

        StubCache() {
        }

        StubCache(FileObject file, String project, String relativePath) {
            files.add(new FileCacheQuery.CachedFile(file, project, relativePath));
        }

        void add(String nameExt, String project, String relativePath, String projectRoot) {
            files.add(new FileCacheQuery.CachedFile(
                    stub(nameExt, relativePath, projectRoot + "/" + relativePath),
                    project, relativePath));
        }

        List<FileCacheQuery.CachedFile> entries() {
            return files;
        }

        void install() {
            when(cache.getAllFiles()).thenReturn(files);
        }
    }
}