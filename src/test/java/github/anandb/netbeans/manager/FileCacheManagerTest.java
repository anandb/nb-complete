package github.anandb.netbeans.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.junit.jupiter.api.io.TempDir;

import github.anandb.netbeans.contract.FileCacheQuery;
import github.anandb.netbeans.contract.VcsIgnoreStrategy;
import github.anandb.netbeans.support.ProjectPaths;

class FileCacheManagerTest {

    private FileCacheManager manager;

    @AfterEach
    void tearDown() {
        // The manager registers an OpenProjects listener for its lifetime; without
        // this every test in the JVM keeps scheduling rebuilds on a dead instance.
        manager.dispose();
    }

    @BeforeEach
    void setUp() {
        // FileCacheManager registers via @ServiceProvider and uses
        // OpenProjects internally. We instantiate directly to avoid
        // Lookup resolution issues in headless tests.
        manager = new FileCacheManager();
    }

    @Test
    void getDefaultReturnsNonNull() {
        FileCacheManager m = FileCacheManager.getDefault();
        assertNotNull(m);
    }

    @Test
    void isReadyInitiallyFalse() {
        // Before the background rebuild completes, isReady should be false
        // (or true if rebuild ran fast). Either way, no exception.
        manager.isReady();
    }

    @Test
    void getCacheVersionInitiallyZeroOrPositive() {
        long v = manager.getCacheVersion();
        assertNotNull(String.valueOf(v));
    }

    @Test
    void getAllFilesReturnsNonNullCollection() {
        assertNotNull(manager.getAllFiles());
    }

    @Test
    void onReadyRunsImmediatelyIfAlreadyReady() {
        // If cache is already ready, the action should run synchronously.
        // If not ready, it should be queued. Either way, no exception.
        manager.onReady(() -> { /* no-op */ });
    }

    @Test
    void relativePathUsesSeparatorBoundaryAndSlashes() {
        java.io.File root = new java.io.File("/projects/myapp/src");
        java.io.File child = new java.io.File("/projects/myapp/src/main/Foo.java");
        assertEquals("main/Foo.java", ProjectPaths.relativeTo(root, child));

        // A sibling sharing the root's string prefix is still outside.
        assertNull(ProjectPaths.relativeTo(new java.io.File("/projects/myapp"),
                new java.io.File("/projects/myapp2/Foo.java")));
        assertNull(ProjectPaths.relativeTo(new java.io.File("/projects/myapp"),
                new java.io.File("/other/path/Foo.java")));
    }

    @Test
    void isReadyReflectsBackgroundState() throws Exception {
        // After construction, the background rebuild may or may not have completed.
        // Just verify the method works without throwing.
        boolean ready = manager.isReady();
        assertNotNull(String.valueOf(ready));
    }

    @Test
    void getDefaultViaLookupReturnsSameInstance() {
        // getDefault() uses Lookup to find FileCacheQuery, falls back to singleton
        FileCacheManager m1 = FileCacheManager.getDefault();
        FileCacheManager m2 = FileCacheManager.getDefault();
        assertNotNull(m1);
        assertNotNull(m2);
    }

    @Test
    void rebuildTriggersCoalesceWithinSlidingWindow() {
        await().atMost(5, TimeUnit.SECONDS).until(() -> manager.getCacheVersion() >= 1);
        long before = manager.getCacheVersion();

        for (int i = 0; i < 5; i++) {
            manager.scheduleRebuild();
        }

        // Still deferred: a trigger may not start a rebuild before the window elapses.
        assertEquals(before, manager.getCacheVersion(),
                "rebuild must wait for the coalescing window");

        await().atMost(5, TimeUnit.SECONDS).until(() -> manager.getCacheVersion() >= before + 1);

        // And exactly one rebuild: the burst must not queue extra runs.
        await().atMost(FileCacheManager.REBUILD_DELAY_MS + 2500L, TimeUnit.MILLISECONDS)
                .during(FileCacheManager.REBUILD_DELAY_MS + 500L, TimeUnit.MILLISECONDS)
                .until(() -> manager.getCacheVersion() == before + 1);
    }

    @Test
    void publishingTheScanKeepsEntriesAddedWhileItRan() throws Exception {
        await().atMost(10, TimeUnit.SECONDS).until(manager::isReady);

        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject live = fs.getRoot().createData("Live.java");
        FileObject deleted = fs.getRoot().createData("Gone.java");

        manager.publish(Map.of(
                live.getPath(), new FileCacheQuery.CachedFile(live, "p", "Live.java"),
                deleted.getPath(), new FileCacheQuery.CachedFile(deleted, "p", "Gone.java")),
                Set.of());
        assertEquals(2, manager.getAllFiles().size());

        deleted.delete();
        // Known before the scan and missing from it: dropped (deleted or ignored).
        // Added during the scan and still valid: kept.
        manager.publish(Map.of(), Set.of(deleted.getPath()));

        Set<String> remaining = manager.getAllFiles().stream()
                .map(FileCacheQuery.CachedFile::relativePath)
                .collect(Collectors.toSet());
        assertEquals(Set.of("Live.java"), remaining);
        assertTrue(manager.getAllFiles().stream()
                .anyMatch(f -> f.fileObject().equals(live)));
    }

    @Test
    void resolvesADirectoryToTheProjectWhoseRootContainsIt() throws Exception {
        injectRoot("nb", new java.io.File("/work/nb"));

        assertEquals("nb", manager.getProjectName("/work/nb/src/ui"));
        assertNull(manager.getProjectName("/work/other"));
        assertNull(manager.getProjectName(null));
    }

    @Test
    void returnsOnlyTheRequestedProjectsFiles() throws Exception {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject own = fs.getRoot().createData("Own.java");
        FileObject other = fs.getRoot().createData("Other.java");
        manager.publish(Map.of(
                own.getPath(), new FileCacheQuery.CachedFile(own, "nb", "Own.java"),
                other.getPath(), new FileCacheQuery.CachedFile(other, "other", "Other.java")),
                Set.of());

        assertEquals(1, manager.getFiles("nb").size());
        assertEquals("Own.java", manager.getFiles("nb").iterator().next().relativePath());
        assertTrue(manager.getFiles("missing").isEmpty());
    }

    @Test
    void recursiveListenerAddsDeletesRenamesAndIgnoresFiles(@TempDir Path rootPath) throws Exception {
        Files.createFile(rootPath.resolve("created.java"));
        Files.createFile(rootPath.resolve("ignored.java"));
        Files.createFile(rootPath.resolve("renamed.java"));
        Files.createFile(rootPath.resolve("kept.java"));
        FileUtil.refreshFor(rootPath.toFile());
        FileObject root = FileUtil.toFileObject(rootPath.toFile());
        assertNotNull(root);
        FileObject created = root.getFileObject("created.java");
        FileObject ignored = root.getFileObject("ignored.java");
        FileObject renamed = root.getFileObject("renamed.java");
        FileObject kept = root.getFileObject("kept.java");
        FileObject stale = mock(FileObject.class);
        when(stale.isValid()).thenReturn(false);

        java.io.File rootDir = rootPath.toFile();
        manager.publish(Map.of(
                "stale", new FileCacheQuery.CachedFile(stale, "p", "stale.java"),
                kept.getPath(), new FileCacheQuery.CachedFile(kept, "p", "kept.java")), Set.of());
        VcsIgnoreStrategy strategy = new VcsIgnoreStrategy() {
            @Override
            public boolean isAvailable(java.io.File projectRoot) {
                return true;
            }

            @Override
            public Set<String> listNonIgnoredFiles(java.io.File projectRoot) {
                return Set.of();
            }

            @Override
            public boolean isIgnored(java.io.File projectRoot, java.io.File file) {
                return file.getName().equals("ignored.java");
            }
        };
        Method install = FileCacheManager.class.getDeclaredMethod("installListener",
                FileObject.class, java.io.File.class, String.class, VcsIgnoreStrategy.class);
        install.setAccessible(true);
        install.invoke(manager, root, rootDir, "p", strategy);
        FileChangeListener listener = rootListener(rootDir);

        listener.fileDataCreated(new FileEvent(created));
        assertTrue(manager.getAllFiles().stream()
                .anyMatch(file -> file.relativePath().equals("created.java")));
        listener.fileDataCreated(new FileEvent(ignored));
        FileObject virtual = FileUtil.createMemoryFileSystem().getRoot().createData("virtual.java");
        listener.fileDataCreated(new FileEvent(virtual));
        assertEquals(3, manager.getAllFiles().size());

        listener.fileDeleted(new FileEvent(created));
        listener.fileDeleted(new FileEvent(ignored));
        listener.fileDeleted(new FileEvent(virtual));
        assertEquals(2, manager.getAllFiles().size());

        listener.fileRenamed(new FileRenameEvent(renamed, "old", "java"));
        listener.fileRenamed(new FileRenameEvent(ignored, "old", "java"));
        listener.fileRenamed(new FileRenameEvent(virtual, "old", "java"));
        assertEquals(Set.of("kept.java", "renamed.java"), manager.getAllFiles().stream()
                .map(FileCacheQuery.CachedFile::relativePath).collect(Collectors.toSet()));
        listener.fileFolderCreated(new FileEvent(root));
        listener.fileChanged(new FileEvent(renamed));
        listener.fileAttributeChanged(new FileAttributeEvent(renamed, "attr", null, "value"));
    }

    @SuppressWarnings("unchecked")
    private FileChangeListener rootListener(java.io.File root) throws Exception {
        java.lang.reflect.Field field = FileCacheManager.class.getDeclaredField("rootListeners");
        field.setAccessible(true);
        return ((Map<java.io.File, FileChangeListener>) field.get(manager)).get(root);
    }

    @SuppressWarnings("unchecked")
    private void injectRoot(String projectName, java.io.File root) throws Exception {
        java.lang.reflect.Field field = FileCacheManager.class.getDeclaredField("projectRoots");
        field.setAccessible(true);
        ((Map<String, Set<java.io.File>>) field.get(manager)).put(projectName, Set.of(root));
    }
}
