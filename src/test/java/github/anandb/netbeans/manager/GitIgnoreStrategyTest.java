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
    void findGitRootReturnsNullForDirWithoutGit() throws IOException {
        File dir = Files.createTempDirectory("no-git").toFile();
        try {
            assertNull(GitIgnoreStrategy.findGitRoot(dir));
        } finally {
            dir.delete();
        }
    }

    @Test
    void findGitRootReturnsDirWithGitSubdir() throws IOException {
        File dir = Files.createTempDirectory("has-git").toFile();
        File gitDir = new File(dir, ".git");
        gitDir.mkdirs();
        try {
            assertEquals(dir, GitIgnoreStrategy.findGitRoot(dir));
        } finally {
            gitDir.delete();
            dir.delete();
        }
    }

    @Test
    void findGitRootWalksUp(@TempDir Path temp) throws IOException {
        // Create: temp/parent/.git  and  temp/parent/child
        File parent = temp.resolve("parent").toFile();
        parent.mkdirs();
        File gitDir = new File(parent, ".git");
        gitDir.mkdirs();
        File child = temp.resolve("parent/child").toFile();
        child.mkdirs();

        File result = GitIgnoreStrategy.findGitRoot(child);
        assertNotNull(result);
        assertEquals(parent, result);

        gitDir.delete();
        child.delete();
        parent.delete();
    }

    @Test
    void isAvailableReturnsTrueWhenGitDirExists() throws IOException {
        File dir = Files.createTempDirectory("git-avail").toFile();
        new File(dir, ".git").mkdirs();
        try {
            assertTrue(strategy.isAvailable(dir));
        } finally {
            new File(dir, ".git").delete();
            dir.delete();
        }
    }

    @Test
    void isAvailableReturnsFalseWhenNoGitDir() throws IOException {
        File dir = Files.createTempDirectory("no-git-avail").toFile();
        try {
            assertFalse(strategy.isAvailable(dir));
        } finally {
            dir.delete();
        }
    }

    @Test
    void listNonIgnoredFilesReturnsEmptyForNonGitDir() throws IOException {
        File dir = Files.createTempDirectory("no-git-list").toFile();
        try {
            // Not a git repo — ls-files fails, returns empty
            assertTrue(strategy.listNonIgnoredFiles(dir).isEmpty());
        } finally {
            dir.delete();
        }
    }

    @Test
    void isIgnoredReturnsFalseForNonGitDir() throws IOException {
        File dir = Files.createTempDirectory("no-git-check").toFile();
        File file = new File(dir, "test.txt");
        file.createNewFile();
        try {
            // git check-ignore fails (not a repo) → returns false
            assertFalse(strategy.isIgnored(dir, file));
        } finally {
            file.delete();
            dir.delete();
        }
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
