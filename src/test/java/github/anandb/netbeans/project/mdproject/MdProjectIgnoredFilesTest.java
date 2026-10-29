package github.anandb.netbeans.project.mdproject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

/**
 * Pattern parsing, caching and matching of {@code .mdproject-ignore}. Uses mocked
 * {@link Project}/{@link FileObject} — no IDE project system or display needed.
 */
class MdProjectIgnoredFilesTest {

    private static final String IGNORE = ".mdproject-ignore";

    private Project project;
    private FileObject projectDir;
    private FileObject ignoreFile;

    @BeforeEach
    void setUp() {
        project = mock(Project.class);
        projectDir = mock(FileObject.class);
        ignoreFile = mock(FileObject.class);
        when(project.getProjectDirectory()).thenReturn(projectDir);
        when(projectDir.getFileObject(IGNORE)).thenReturn(ignoreFile);
        when(ignoreFile.lastModified()).thenReturn(new Date(1_000L));
    }

    // ---- pattern parsing ----------------------------------------------------

    @Test
    void missingIgnoreFileYieldsNoPatterns() {
        when(projectDir.getFileObject(IGNORE)).thenReturn(null);

        assertTrue(MdProjectIgnoredFiles.getIgnoredPatterns(project).isEmpty());
    }

    @Test
    void commentsBlanksAndPaddingAreDropped() throws IOException {
        stubContent("# comment\n\n  build  \n*.tmp\n   \n# another\n");

        assertEquals(List.of("build", "*.tmp"), MdProjectIgnoredFiles.getIgnoredPatterns(project));
    }

    @Test
    void unreadableFileYieldsNoPatterns() throws IOException {
        when(ignoreFile.asBytes()).thenThrow(new IOException("boom"));

        assertTrue(MdProjectIgnoredFiles.getIgnoredPatterns(project).isEmpty());
    }

    @Test
    void patternsAreCachedUntilTheFileChanges() throws IOException {
        stubContent("build\n");

        MdProjectIgnoredFiles.getIgnoredPatterns(project);
        MdProjectIgnoredFiles.getIgnoredPatterns(project);
        verify(ignoreFile, times(1)).asBytes();

        when(ignoreFile.lastModified()).thenReturn(new Date(2_000L));
        MdProjectIgnoredFiles.getIgnoredPatterns(project);
        verify(ignoreFile, times(2)).asBytes();
    }

    @Test
    void removingTheFileDropsTheCachedPatterns() throws IOException {
        stubContent("build\n");
        assertEquals(List.of("build"), MdProjectIgnoredFiles.getIgnoredPatterns(project));

        when(projectDir.getFileObject(IGNORE)).thenReturn(null);

        assertTrue(MdProjectIgnoredFiles.getIgnoredPatterns(project).isEmpty());
    }

    // ---- matching -----------------------------------------------------------

    @Test
    void matchesExactFileName() throws IOException {
        stubContent("secrets.md\n");
        FileObject file = fileUnder(projectDir, "secrets.md");

        assertTrue(MdProjectIgnoredFiles.isIgnored(project, file));
    }

    @Test
    void matchesDirectoryPrefix() throws IOException {
        stubContent("node_modules\n");
        FileObject dir = dirUnder(projectDir, "node_modules");
        FileObject nested = fileUnder(dir, "deep.md");

        assertTrue(MdProjectIgnoredFiles.isIgnored(project, nested));
    }

    @Test
    void doubleStarMatchesByNameAnywhere() throws IOException {
        stubContent("**/vendor\n");
        FileObject dir = dirUnder(projectDir, "third_party");
        FileObject vendor = dirUnder(dir, "vendor");

        assertTrue(MdProjectIgnoredFiles.isIgnored(project, vendor));
    }

    @Test
    void unrelatedFileIsNotIgnored() throws IOException {
        stubContent("build\nsecrets.md\n");
        FileObject file = fileUnder(projectDir, "README.md");

        assertFalse(MdProjectIgnoredFiles.isIgnored(project, file));
    }

    @Test
    void fileOutsideTheProjectRootDoesNotMatchAPathPrefix() throws IOException {
        stubContent("build\n");
        FileObject orphan = mock(FileObject.class);
        when(orphan.getNameExt()).thenReturn("build");
        when(orphan.getParent()).thenReturn(null);

        // Falls back to the name match, so it is ignored even without a relative path.
        assertTrue(MdProjectIgnoredFiles.isIgnored(project, orphan));
    }

    @Test
    void noPatternsMeansNothingIsIgnored() {
        when(projectDir.getFileObject(IGNORE)).thenReturn(null);
        FileObject file = fileUnder(projectDir, "anything.md");

        assertFalse(MdProjectIgnoredFiles.isIgnored(project, file));
    }

    // ---- writing ------------------------------------------------------------

    @Test
    void rewriteKeepsHeaderAndTrimsPatterns() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        when(ignoreFile.getOutputStream()).thenReturn(sink);

        MdProjectIgnoredFiles.setIgnoredPatterns(project, List.of("  build  ", "", "*.tmp"));

        String written = sink.toString(StandardCharsets.UTF_8);
        assertTrue(written.startsWith("# Markdown Project ignored files"), written);
        assertTrue(written.contains("\nbuild\n"), written);
        assertTrue(written.contains("\n*.tmp\n"), written);
        assertFalse(written.contains("  build  "), written);
    }

    @Test
    void createsTheIgnoreFileWhenAbsent() throws IOException {
        FileObject created = mock(FileObject.class);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        when(projectDir.getFileObject(IGNORE)).thenReturn(null);
        when(projectDir.createData(IGNORE)).thenReturn(created);
        when(created.getOutputStream()).thenReturn(sink);

        MdProjectIgnoredFiles.setIgnoredPatterns(project, List.of("build"));

        verify(projectDir).createData(IGNORE);
        assertTrue(sink.toString(StandardCharsets.UTF_8).contains("\nbuild\n"));
    }

    // ---- helpers ------------------------------------------------------------

    private void stubContent(String content) throws IOException {
        when(ignoreFile.asBytes()).thenReturn(content.getBytes(StandardCharsets.UTF_8));
    }

    private static FileObject fileUnder(FileObject parent, String name) {
        FileObject file = mock(FileObject.class);
        when(file.getNameExt()).thenReturn(name);
        when(file.getParent()).thenReturn(parent);
        return file;
    }

    private static FileObject dirUnder(FileObject parent, String name) {
        FileObject dir = mock(FileObject.class);
        when(dir.getNameExt()).thenReturn(name);
        when(dir.getParent()).thenReturn(parent);
        when(dir.getFileObject(any())).thenReturn(null);
        return dir;
    }
}
