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
 * Tests the auto-backup engine for Mercurial repositories: VCS detection,
 * uncommitted-change detection, and shelve-based backup. The shelf is
 * created with --keep, so the working directory stays intact after a
 * backup — the user's changes must never be lost by the backup itself.
 * Skips cleanly when hg is unavailable.
 */
class VcsUtilsTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void assumeHgAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                isBinaryAvailable("hg", "--version"), "hg not available on this machine");
    }

    private boolean isBinaryAvailable(String... cmd) {
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor(30, TimeUnit.SECONDS) && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void runHg(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        pb.environment().put("HGRCPATH", "");
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "hg timed out: " + String.join(" ", cmd));
        assertEquals(0, proc.exitValue(), String.join(" ", cmd) + " failed");
    }

    private File initHgRepo() throws Exception {
        File repo = tempDir.resolve("repo").toFile();
        assertTrue(repo.mkdirs() || repo.isDirectory());
        runHg(repo, "hg", "init", "-q");
        assertTrue(new File(repo, ".hg").exists());
        return repo;
    }

    private void commitAll(File repo, String message) throws Exception {
        runHg(repo, "hg", "commit", "-q", "-A", "-u", "test <test@example.com>", "-m", message);
    }

    @Test
    void findVcsInfoDetectsMercurialRoot() throws Exception {
        File repo = initHgRepo();
        File nested = new File(repo, "src/deep");
        assertTrue(nested.mkdirs());

        VcsUtils.VcsInfo info = VcsUtils.findVcsInfo(nested.getAbsolutePath());

        assertEquals(VcsUtils.VcsType.HG, info.type());
        assertEquals(repo.getCanonicalPath(), info.rootDir().getCanonicalPath());
    }

    @Test
    void findVcsInfoPrefersMercurialRootOverAncestors() throws Exception {
        // A .git ancestor must not mask a nearer .hg root: the walk checks
        // each level for both VCS markers and returns the nearest hit.
        File hgRepo = initHgRepo();
        File nested = new File(hgRepo, "src");
        assertTrue(nested.mkdirs());

        VcsUtils.VcsInfo info = VcsUtils.findVcsInfo(nested.getAbsolutePath());

        assertEquals(VcsUtils.VcsType.HG, info.type());
        assertEquals(hgRepo.getCanonicalPath(), info.rootDir().getCanonicalPath());
    }

    @Test
    void findVcsInfoReturnsNullWhenNoRepository() {
        File plain = tempDir.resolve("plain").toFile();
        assertTrue(plain.mkdirs());

        assertNull(VcsUtils.findVcsInfo(plain.getAbsolutePath()));
    }

    @Test
    void hasUncommittedChangesFalseWhenRepositoryClean() throws Exception {
        File repo = initHgRepo();
        java.nio.file.Files.writeString(new File(repo, "tracked.txt").toPath(), "one\n");
        commitAll(repo, "initial");

        assertFalse(VcsUtils.hasUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath())));
    }

    @Test
    void hasUncommittedChangesTrueForModifiedFile() throws Exception {
        File repo = initHgRepo();
        File tracked = new File(repo, "tracked.txt");
        java.nio.file.Files.writeString(tracked.toPath(), "one\n");
        commitAll(repo, "initial");
        java.nio.file.Files.writeString(tracked.toPath(), "one\ntwo\n");

        assertTrue(VcsUtils.hasUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath())));
    }

    @Test
    void hasUncommittedChangesTrueForUntrackedFile() throws Exception {
        File repo = initHgRepo();
        java.nio.file.Files.writeString(new File(repo, "tracked.txt").toPath(), "one\n");
        commitAll(repo, "initial");
        assertTrue(new File(repo, "untracked.txt").createNewFile());

        assertTrue(VcsUtils.hasUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath())));
    }

    @Test
    void backupShelvesChangesAndKeepsWorkingDirectory() throws Exception {
        File repo = initHgRepo();
        File tracked = new File(repo, "tracked.txt");
        java.nio.file.Files.writeString(tracked.toPath(), "one\n");
        commitAll(repo, "initial");
        java.nio.file.Files.writeString(tracked.toPath(), "one\ntwo\n");
        assertTrue(new File(repo, "untracked.txt").createNewFile());

        VcsUtils.VcsInfo info = VcsUtils.findVcsInfo(repo.getAbsolutePath());
        String backupMsg = VcsUtils.backupUncommittedChanges(info);

        // The backup is recorded, names the shelved file, and the working
        // directory is left untouched (--keep semantics).
        assertTrue(backupMsg != null && backupMsg.contains("tracked.txt"),
                "backup message should name the changed file: " + backupMsg);
        assertEquals("one\ntwo\n", java.nio.file.Files.readString(tracked.toPath()),
                "backup must not remove working-directory changes");
        assertTrue(new File(repo, "untracked.txt").exists(),
                "untracked files must survive the backup");
        // The shelf is queryable under the fixed beanbot-backup name.
        String shelves = runHgCapture(repo, "hg", "shelve", "--list");
        assertTrue(shelves.contains("beanbot-backup"),
                "shelf should exist after backup: " + shelves);
    }

    @Test
    void backupReturnsNullWhenRepositoryClean() throws Exception {
        File repo = initHgRepo();
        java.nio.file.Files.writeString(new File(repo, "tracked.txt").toPath(), "one\n");
        commitAll(repo, "initial");

        assertNull(VcsUtils.backupUncommittedChanges(
                VcsUtils.findVcsInfo(repo.getAbsolutePath())));
    }

    @Test
    void backupReplacesPreviousBackupShelf() throws Exception {
        File repo = initHgRepo();
        File tracked = new File(repo, "tracked.txt");
        java.nio.file.Files.writeString(tracked.toPath(), "one\n");
        commitAll(repo, "initial");

        // First backup, then a further change and a second backup: the
        // second must succeed without a duplicate-shelf error.
        java.nio.file.Files.writeString(tracked.toPath(), "one\ntwo\n");
        String first = VcsUtils.backupUncommittedChanges(VcsUtils.findVcsInfo(repo.getAbsolutePath()));
        assumeTrue(first != null, "first backup should shelve something");
        java.nio.file.Files.writeString(tracked.toPath(), "one\ntwo\nthree\n");
        String second = VcsUtils.backupUncommittedChanges(VcsUtils.findVcsInfo(repo.getAbsolutePath()));

        assertTrue(second != null && second.contains("tracked.txt"));
        // Only one beanbot-backup shelf remains after the replace.
        String shelves = runHgCapture(repo, "hg", "shelve", "--list");
        assertEquals(1, shelves.split("beanbot-backup", -1).length - 1,
                "duplicate shelf must be replaced, shelves: " + shelves);
    }

    private String runHgCapture(File dir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        pb.environment().put("HGRCPATH", "");
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
