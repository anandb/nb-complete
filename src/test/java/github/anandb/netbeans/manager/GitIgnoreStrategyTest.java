package github.anandb.netbeans.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitIgnoreStrategyTest {

    private final GitIgnoreStrategy strategy = new GitIgnoreStrategy();

    @Test
    void findGitRootReturnsNullForDirWithoutGit(@TempDir Path temp) throws IOException {
        File dir = temp.resolve("workspace/src/main").toFile();
        assertTrue(dir.mkdirs());

        assertNull(GitIgnoreStrategy.findGitRoot(dir));
    }

    @Test
    void findGitRootReturnsDirWithGitSubdir() throws IOException {
        File dir = Files.createTempDirectory("has-git").toFile();
        File gitDir = new File(dir, ".git");
        assertTrue(new File(gitDir, "refs").mkdirs());
        try {
            assertEquals(dir, GitIgnoreStrategy.findGitRoot(dir));
        } finally {
            new File(gitDir, "refs").delete();
            gitDir.delete();
            dir.delete();
        }
    }

    @Test
    void findGitRootWalksUp(@TempDir Path temp) throws IOException {
        // Create: temp/parent/.git/refs and temp/parent/child
        File parent = temp.resolve("parent").toFile();
        parent.mkdirs();
        File gitDir = new File(parent, ".git");
        assertTrue(new File(gitDir, "refs").mkdirs());
        File child = temp.resolve("parent/child").toFile();
        child.mkdirs();

        File result = GitIgnoreStrategy.findGitRoot(child);
        assertNotNull(result);
        assertEquals(parent, result);

        new File(gitDir, "refs").delete();
        gitDir.delete();
        child.delete();
        parent.delete();
    }

    @Test
    void isAvailableReturnsTrueWhenGitDirExists() throws IOException {
        File dir = Files.createTempDirectory("git-avail").toFile();
        assertTrue(new File(dir, ".git/refs").mkdirs());
        try {
            assertTrue(strategy.isAvailable(dir));
        } finally {
            new File(dir, ".git/refs").delete();
            new File(dir, ".git").delete();
            dir.delete();
        }
    }

    @Test
    void isAvailableReturnsFalseWhenNoGitDir(@TempDir Path temp) throws IOException {
        File dir = temp.resolve("workspace/src/main").toFile();
        assertTrue(dir.mkdirs());

        assertFalse(strategy.isAvailable(dir));
    }

    @Test
    void listNonIgnoredFilesReturnsEmptyForNonGitDir(@TempDir Path temp) throws IOException {
        File dir = temp.resolve("workspace/src/main").toFile();
        assertTrue(dir.mkdirs());

        // Not a git repo — ls-files fails, returns empty
        assertTrue(strategy.listNonIgnoredFiles(dir).isEmpty());
    }

    @Test
    void isIgnoredReturnsFalseForNonGitDir(@TempDir Path temp) throws IOException {
        File dir = temp.resolve("workspace/src/main").toFile();
        assertTrue(dir.mkdirs());
        File file = new File(dir, "test.txt");
        file.createNewFile();

        // git check-ignore fails (not a repo) → returns false
        assertFalse(strategy.isIgnored(dir, file));
    }

    @Test
    void listNonIgnoredFilesWorksWithRealGitRepo(@TempDir Path temp) throws Exception {
        File repoDir = temp.toFile();
        // Init git repo
        ProcessBuilder initPb = new ProcessBuilder("git", "init");
        initPb.directory(repoDir);
        initPb.redirectErrorStream(true);
        Process initProc = initPb.start();
        initProc.waitFor();

        // Create a tracked file
        File tracked = new File(repoDir, "tracked.txt");
        Files.writeString(tracked.toPath(), "hello");

        ProcessBuilder addPb = new ProcessBuilder("git", "add", "tracked.txt");
        addPb.directory(repoDir);
        addPb.start().waitFor();

        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", "init");
        commitPb.directory(repoDir);
        Process commitProc = commitPb.start();
        commitProc.waitFor();

        // Create an ignored file
        File ignored = new File(repoDir, "ignored.log");
        Files.writeString(ignored.toPath(), "log data");
        Files.writeString(new File(repoDir, ".gitignore").toPath(), "*.log");

        var files = strategy.listNonIgnoredFiles(repoDir);
        assertTrue(files.contains("tracked.txt"),
                "Tracked file should be listed: " + files);
        assertFalse(files.contains("ignored.log"),
                "Ignored file should not be listed: " + files);
    }

    @Test
    void isIgnoredDetectsGitIgnoredFile(@TempDir Path temp) throws Exception {
        File repoDir = temp.toFile();
        new ProcessBuilder("git", "init").directory(repoDir).start().waitFor();
        Files.writeString(new File(repoDir, ".gitignore").toPath(), "*.log");

        File ignoredFile = new File(repoDir, "test.log");
        Files.writeString(ignoredFile.toPath(), "data");
        File normalFile = new File(repoDir, "test.txt");
        Files.writeString(normalFile.toPath(), "data");

        assertTrue(strategy.isIgnored(repoDir, ignoredFile));
        assertFalse(strategy.isIgnored(repoDir, normalFile));
    }
}
