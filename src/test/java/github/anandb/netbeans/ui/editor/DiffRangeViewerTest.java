package github.anandb.netbeans.ui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Error and early-return paths of the {@link DiffRangeViewer} port adapter.
 * The success path opens a Swing TopComponent and is covered manually.
 */
class DiffRangeViewerTest {

    @TempDir
    Path tempDir;

    private final DiffRangeViewer viewer = new DiffRangeViewer();

    @Test
    void rejectsMissingRepository() {
        assertEquals("Repository directory is required",
                viewer.openDiffRange(null, "HEAD~1", null, null));
    }

    @Test
    void rejectsDirectoryWithoutVcs() {
        File plain = tempDir.resolve("plain").toFile();
        assertTrue(plain.mkdirs());

        assertEquals("Not a Git or Mercurial repository: " + plain,
                viewer.openDiffRange(plain, "HEAD~1", null, null));
    }

    @Test
    void reportsUnknownRevision() throws Exception {
        File repo = initGitRepo();

        assertEquals("Unknown Git revision: nope",
                viewer.openDiffRange(repo, "nope", null, null));
    }

    @Test
    void reportsZeroLastCommits() throws Exception {
        File repo = initGitRepo();

        assertEquals("lastCommits must be at least 1",
                viewer.openDiffRange(repo, null, null, 0));
    }

    @Test
    void returnsNoChangesForIdenticalRange() throws Exception {
        File repo = initGitRepo();

        assertEquals("No changes between HEAD and HEAD",
                viewer.openDiffRange(repo, "HEAD", "HEAD", null));
    }

    @Test
    void returnsNoChangesForCleanWorkingTree() throws Exception {
        File repo = initGitRepo();

        assertEquals("No changes between HEAD and working tree",
                viewer.openDiffRange(repo, "HEAD", null, null));
    }

    private File initGitRepo() throws Exception {
        assumeTrue(isBinaryAvailable("git", "--version"), "git not available on this machine");
        File repo = tempDir.resolve("repo").toFile();
        assertTrue(repo.mkdirs());
        run(repo, "git", "init", "-q", "-b", "main");
        Files.writeString(new File(repo, "a.txt").toPath(), "one\n");
        run(repo, "git", "add", "-A");
        run(repo, "git", "-c", "user.email=test@example.com",
                "-c", "user.name=test", "commit", "-q", "-m", "first");
        return repo;
    }

    private boolean isBinaryAvailable(String... cmd) {
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor(30, TimeUnit.SECONDS) && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void run(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "timed out: " + String.join(" ", cmd));
        assertEquals(0, proc.exitValue(), String.join(" ", cmd) + " failed");
    }
}
