package github.anandb.netbeans.manager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NoVcsIgnoreStrategy} — filesystem-based file listing
 * with common junk exclusions (swap files, hidden dirs, build output).
 */
class NoVcsIgnoreStrategyTest {

    @TempDir
    Path tempDir;

    private NoVcsIgnoreStrategy strategy;
    private File root;

    @BeforeEach
    void setUp() {
        strategy = new NoVcsIgnoreStrategy();
        root = tempDir.toFile();
    }

    // --- isAvailable ---

    @Test
    void alwaysAvailable() {
        assertTrue(strategy.isAvailable(root));
    }

    // --- listNonIgnoredFiles: basic file listing ---

    @Test
    void listsRegularFiles() throws IOException {
        createFile("src/Main.java");
        createFile("src/Util.java");
        createFile("README.md");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(3, files.size());
        assertTrue(files.contains("src/Main.java"));
        assertTrue(files.contains("src/Util.java"));
        assertTrue(files.contains("README.md"));
    }

    @Test
    void emptyDirectoryReturnsEmptySet() {
        Set<String> files = strategy.listNonIgnoredFiles(root);
        assertTrue(files.isEmpty());
    }

    // --- Swap file exclusions ---

    @Test
    void excludesVimSwapFiles() throws IOException {
        createFile("src/Main.java");
        createFile("src/Main.java.swp");
        createFile("src/Main.java.swo");
        createFile("src/Main.java~");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("src/Main.java"));
    }

    @Test
    void excludesTildeBackupFiles() throws IOException {
        createFile("config.properties");
        createFile("config.properties~");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("config.properties"));
    }

    // --- Hidden directory exclusions ---

    @Test
    void excludesHiddenDirectories() throws IOException {
        createFile("src/Main.java");
        createFile(".hidden/config.json");
        createFile(".git/config");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("src/Main.java"));
    }

    // --- Build/junk directory exclusions ---

    @Test
    void excludesTargetDirectory() throws IOException {
        createFile("src/Main.java");
        createFile("target/classes/Main.class");
        createFile("target/dependency/lib.jar");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("src/Main.java"));
    }

    @Test
    void excludesBuildDirectory() throws IOException {
        createFile("src/Main.java");
        createFile("build/output.bin");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("src/Main.java"));
    }

    @Test
    void excludesNodeModules() throws IOException {
        createFile("src/index.js");
        createFile("node_modules/lodash/index.js");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("src/index.js"));
    }

    @Test
    void excludesAllJunkDirectories() throws IOException {
        createFile("src/Main.java");
        createFile("target/x");
        createFile("build/x");
        createFile("node_modules/x");
        createFile(".git/x");
        createFile(".svn/x");
        createFile("bin/x");
        createFile("out/x");
        createFile(".idea/x");
        createFile(".vscode/x");
        createFile("__pycache__/x");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("src/Main.java"));
    }

    // --- Nested directory structure (parent-child POM simulation) ---

    @Test
    void parentChildPomStructure() throws IOException {
        // Simulate: parent project with child module
        createFile("pom.xml");
        createFile("parent-api/src/main/java/com/example/api/Service.java");
        createFile("parent-impl/pom.xml");
        createFile("parent-impl/src/main/java/com/example/impl/ServiceImpl.java");
        createFile("parent-impl/target/classes/com/example/impl/ServiceImpl.class");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(4, files.size());
        assertTrue(files.contains("pom.xml"));
        assertTrue(files.contains("parent-api/src/main/java/com/example/api/Service.java"));
        assertTrue(files.contains("parent-impl/pom.xml"));
        assertTrue(files.contains("parent-impl/src/main/java/com/example/impl/ServiceImpl.java"));
        // target/classes should be excluded
        assertFalse(files.stream().anyMatch(f -> f.contains("target")));
    }

    @Test
    void childModuleSourceRootListing() throws IOException {
        // List from child module's root directly
        File childRoot = createDir("child-module").toFile();
        createFile("child-module/src/main/java/com/example/App.java");
        createFile("child-module/src/main/java/com/example/App.java.swp");
        createFile("child-module/target/classes/App.class");

        Set<String> files = strategy.listNonIgnoredFiles(childRoot);

        // Should find App.java but not the swap file or target
        assertTrue(files.contains("src/main/java/com/example/App.java"),
                "Should contain App.java, got: " + files);
        assertFalse(files.stream().anyMatch(f -> f.endsWith(".swp")),
                "Swap files should be excluded");
        assertFalse(files.stream().anyMatch(f -> f.contains("target")),
                "target/ should be excluded");
    }

    // --- isIgnored ---

    @Test
    void isIgnoredForHiddenFiles() {
        File hidden = new File(root, ".DS_Store");
        assertTrue(strategy.isIgnored(root, hidden));
    }

    @Test
    void isIgnoredForSwapFiles() {
        assertTrue(strategy.isIgnored(root, new File(root, "Main.java.swp")));
        assertTrue(strategy.isIgnored(root, new File(root, "Main.java~")));
    }

    @Test
    void isIgnoredForFilesInsideTarget() {
        File targetDir = new File(root, "target");
        File classFile = new File(targetDir, "Main.class");
        assertTrue(strategy.isIgnored(root, classFile));
    }

    @Test
    void isIgnoredForFilesInsideNodeModules() {
        File nmDir = new File(root, "node_modules");
        File pkg = new File(nmDir, "lodash/index.js");
        assertTrue(strategy.isIgnored(root, pkg));
    }

    @Test
    void isNotIgnoredForRegularFiles() {
        File src = new File(root, "src/Main.java");
        assertFalse(strategy.isIgnored(root, src));
    }

    @Test
    void isNotIgnoredForFilesInProjectRoot() {
        File pom = new File(root, "pom.xml");
        assertFalse(strategy.isIgnored(root, pom));
    }

    // --- Path separator normalization ---

    @Test
    void usesForwardSlashInPaths() throws IOException {
        createFile("src" + File.separator + "main" + File.separator + "Main.java");

        Set<String> files = strategy.listNonIgnoredFiles(root);

        assertEquals(1, files.size());
        assertTrue(files.contains("src/main/Main.java"));
    }

    // --- Helpers ---

    private File createFile(String relativePath) throws IOException {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "content");
        return path.toFile();
    }

    private Path createDir(String relativePath) throws IOException {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path);
        return path;
    }
}
