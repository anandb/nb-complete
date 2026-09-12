package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the git half of the auto-backup engine: VCS detection, uncommitted-
 * change detection, and the manual-stash backup. The stash is built without
 * resetting the working tree, so the user's changes (including untracked
 * files) must never be lost by the backup itself. Also covers the
 * duplicate-skip and the safe-failure paths (no HEAD, broken .git).
 * Skips cleanly when git is unavailable.
 */
class VcsUtilsGitTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void assumeGitAvailable() {
        assumeTrue(isBinaryAvailable("git", "--version"),
                "git not available on this machine");
    }

    private boolean isBinaryAvailable(String... cmd) {
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor(30, TimeUnit.SECONDS) && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runGit(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS),
                "git timed out: " + String.join(" ", cmd));
        assertEquals(0, proc.exitValue(), String.join(" ", cmd) + " failed");
    }

    private File initGitRepo() throws Exception {
        File repo = tempDir.resolve("gitrepo").toFile();
        assertTrue(repo.mkdirs() || repo.isDirectory());
        runGit(repo, "git", "init", "-q", "-b", "main");
        assertTrue(new File(repo, ".git").exists());
        return repo;
    }

    private void commitAllGit(File repo, String message) throws Exception {
        runGit(repo, "git", "add", "-A");
        runGit(repo, "git", "-c", "user.email=test@example.com",
                "-c", "user.name=test", "commit", "-q", "-m", message);
    }

    @Test
    void findVcsInfoDetectsGitRoot() throws Exception {
        File repo = initGitRepo();
        File nested = new File(repo, "src/deep");
        assertTrue(nested.mkdirs());

        VcsUtils.VcsInfo info = VcsUtils.findVcsInfo(nested.getAbsolutePath());

        assertEquals(VcsUtils.VcsType.GIT, info.type());
        assertEquals(repo.getCanonicalPath(), info.rootDir().getCanonicalPath());
    }

    @Test
    void findVcsInfoPrefersGitMarkerAtSameLevel() throws Exception {
        // The walk checks .git before .hg at each level: a directory
        // holding both markers is reported as a git repository.
        File repo = initGitRepo();
        assertTrue(new File(repo, ".hg").mkdirs());

        VcsUtils.VcsInfo info = VcsUtils.findVcsInfo(repo.getAbsolutePath());

        assertEquals(VcsUtils.VcsType.GIT, info.type());
    }

    @Test
    void hasUncommittedChangesFalseWhenRepositoryClean() throws Exception {
        File repo = initGitRepo();
        java.nio.file.Files.writeString(new File(repo, "tracked.txt").toPath(), "one\n");
        commitAllGit(repo, "initial");

        assertFalse(VcsUtils.hasUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath())));
    }

    @Test
    void hasUncommittedChangesTrueForModifiedAndUntrackedFiles() throws Exception {
        File repo = initGitRepo();
        File tracked = new File(repo, "tracked.txt");
        java.nio.file.Files.writeString(tracked.toPath(), "one\n");
        commitAllGit(repo, "initial");
        java.nio.file.Files.writeString(tracked.toPath(), "one\ntwo\n");
        assertTrue(new File(repo, "untracked.txt").createNewFile());

        assertTrue(VcsUtils.hasUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath())));
    }

    @Test
    void backupStashesChangesAndKeepsWorkingDirectory() throws Exception {
        File repo = initGitRepo();
        File tracked = new File(repo, "tracked.txt");
        java.nio.file.Files.writeString(tracked.toPath(), "one\n");
        commitAllGit(repo, "initial");
        java.nio.file.Files.writeString(tracked.toPath(), "one\ntwo\n");
        assertTrue(new File(repo, "untracked.txt").createNewFile());

        String backupMsg = VcsUtils.backupUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath()));

        assertTrue(backupMsg != null && backupMsg.contains("tracked.txt"),
                "backup message should name the changed file: " + backupMsg);
        // The manual stash never resets the working tree: both the
        // modification and the untracked file must survive.
        assertEquals("one\ntwo\n", java.nio.file.Files.readString(tracked.toPath()),
                "backup must not remove working-directory changes");
        assertTrue(new File(repo, "untracked.txt").exists(),
                "untracked files must survive the backup");
        String stashes = runGitCapture(repo, "git", "stash", "list");
        assertTrue(stashes.contains("beanbot:"), "stash should exist: " + stashes);
    }

    @Test
    void backupReturnsNullWhenRepositoryClean() throws Exception {
        File repo = initGitRepo();
        java.nio.file.Files.writeString(new File(repo, "tracked.txt").toPath(), "one\n");
        commitAllGit(repo, "initial");

        assertNull(VcsUtils.backupUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath())));
    }

    @Test
    void backupSkipsDuplicateWhenWorkingTreeUnchanged() throws Exception {
        File repo = initGitRepo();
        File tracked = new File(repo, "tracked.txt");
        java.nio.file.Files.writeString(tracked.toPath(), "one\n");
        commitAllGit(repo, "initial");
        java.nio.file.Files.writeString(tracked.toPath(), "one\ntwo\n");
        assertTrue(new File(repo, "untracked.txt").createNewFile());

        String first = VcsUtils.backupUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath()));
        assumeTrue(first != null, "first backup should stash something");

        // No new changes: the same working tree is already stored, so
        // the duplicate-skip must return null instead of re-stashing.
        String second = VcsUtils.backupUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath()));

        assertNull(second, "identical working tree must skip the backup");
    }

    @Test
    void backupReturnsNullWhenRepositoryHasNoCommits() throws Exception {
        // A repo without any commit has no HEAD: git diff --name-only
        // HEAD and rev-parse fail, and the backup must fail safely.
        File repo = initGitRepo();
        assertTrue(new File(repo, "newfile.txt").createNewFile());

        VcsUtils.VcsInfo info = VcsUtils.findVcsInfo(repo.getAbsolutePath());
        assertTrue(VcsUtils.hasUncommittedChanges(info),
                "untracked file counts as a change");
        assertNull(VcsUtils.backupUncommittedChanges(info),
                "backup must fail safely without a HEAD");
    }

    @Test
    void backupReturnsNullWhenRepositoryCorrupted() throws Exception {
        // A broken .git (no HEAD): git status still reports changes,
        // but the backup's HEAD lookups fail and it must return null
        // instead of crashing the caller.
        File repo = initGitRepo();
        java.nio.file.Files.writeString(new File(repo, "tracked.txt").toPath(), "one\n");
        commitAllGit(repo, "initial");
        assertTrue(new File(repo, ".git/HEAD").delete());

        VcsUtils.VcsInfo info = VcsUtils.findVcsInfo(repo.getAbsolutePath());
        assertTrue(VcsUtils.hasUncommittedChanges(info),
                "status still reports changes without a HEAD");
        assertNull(VcsUtils.backupUncommittedChanges(info),
                "backup must fail safely without a HEAD");
    }

    private String runGitCapture(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append("\n");
                out.append(line);
            }
        }
        proc.waitFor(30, TimeUnit.SECONDS);
        return out.toString();
    }
}
