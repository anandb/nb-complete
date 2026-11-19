package github.anandb.netbeans.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests commit-range support against real git and Mercurial repositories:
 * repository detection, revision validation, "last N commits" resolution with
 * its short-history fallback, changed-file listing, and content lookup.
 * Each half skips cleanly when its binary is unavailable.
 */
class VcsDiffRangesTest {

    @TempDir
    Path tempDir;

    private boolean isBinaryAvailable(String... cmd) {
        try {
            Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor(30, TimeUnit.SECONDS) && proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void run(File dir, Map<String, String> env, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        if (env != null) {
            pb.environment().putAll(env);
        }
        Process proc = pb.start();
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "timed out: " + String.join(" ", cmd));
        assertEquals(0, proc.exitValue(), String.join(" ", cmd) + " failed");
    }

    private String capture(File dir, Map<String, String> env, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        if (env != null) {
            pb.environment().putAll(env);
        }
        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "timed out: " + String.join(" ", cmd));
        return out.toString().trim();
    }

    // ---- git ----------------------------------------------------------------

    private File initGit(String name) throws Exception {
        assumeTrue(isBinaryAvailable("git", "--version"), "git not available on this machine");
        File repo = tempDir.resolve(name).toFile();
        assertTrue(repo.mkdirs() || repo.isDirectory());
        run(repo, null, "git", "init", "-q", "-b", "main");
        return repo;
    }

    private String commitGit(File repo, String message) throws Exception {
        run(repo, null, "git", "add", "-A");
        run(repo, null, "git", "-c", "user.email=test@example.com",
                "-c", "user.name=test", "commit", "-q", "--allow-empty", "-m", message);
        return capture(repo, null, "git", "rev-parse", "HEAD");
    }

    private List<String> gitHistoryTwoCommits(File repo) throws Exception {
        Files.writeString(new File(repo, "a.txt").toPath(), "one\n");
        Files.writeString(new File(repo, "b.txt").toPath(), "bee\n");
        List<String> commits = new ArrayList<>();
        commits.add(commitGit(repo, "first"));
        Files.writeString(new File(repo, "a.txt").toPath(), "two\n");
        Files.delete(new File(repo, "b.txt").toPath());
        Files.writeString(new File(repo, "c.txt").toPath(), "see\n");
        commits.add(commitGit(repo, "second"));
        return commits;
    }

    @Test
    void findRepoDetectsGitAndWalksUp() throws Exception {
        File repo = initGit("git-repo");
        File nested = new File(repo, "src/deep");
        assertTrue(nested.mkdirs());

        VcsUtils.VcsInfo info = VcsDiffRanges.findRepo(nested.getAbsolutePath());

        assertEquals(VcsUtils.VcsType.GIT, info.type());
        assertEquals(repo.getCanonicalPath(), info.rootDir().getCanonicalPath());
    }

    @Test
    void findRepoAcceptsGitWorktreeFileMarker() throws Exception {
        File worktree = tempDir.resolve("worktree").toFile();
        assertTrue(worktree.mkdirs());
        Files.writeString(new File(worktree, ".git").toPath(), "gitdir: /elsewhere\n");

        VcsUtils.VcsInfo info = VcsDiffRanges.findRepo(worktree.getAbsolutePath());

        assertEquals(VcsUtils.VcsType.GIT, info.type());
        assertEquals(worktree.getCanonicalPath(), info.rootDir().getCanonicalPath());
    }

    @Test
    void findRepoReturnsNullWithoutMarker() throws Exception {
        File plain = tempDir.resolve("plain").toFile();
        assertTrue(plain.mkdirs());

        assertNull(VcsDiffRanges.findRepo(plain.getAbsolutePath()));
        assertNull(VcsDiffRanges.findRepo(null));
        assertNull(VcsDiffRanges.findRepo(" "));
    }

    @Test
    void validateGitRevision() throws Exception {
        File repo = initGit("git-repo");
        commitGit(repo, "first");

        assertNull(VcsDiffRanges.validateRevision(repo, VcsUtils.VcsType.GIT, "HEAD"));
        assertTrue(VcsDiffRanges.validateRevision(repo, VcsUtils.VcsType.GIT, "nope")
                .contains("Unknown Git revision: nope"));
    }

    @Test
    void resolveGitFromRevisionFallsBackToRoot() throws Exception {
        File repo = initGit("git-repo");
        List<String> commits = gitHistoryTwoCommits(repo);

        // lastCommits=1 is the tip itself; lastCommits=2 is HEAD~1.
        assertEquals(commits.get(1),
                VcsDiffRanges.resolveFromRevision(repo, VcsUtils.VcsType.GIT, "HEAD", 1));
        assertEquals(commits.get(0),
                VcsDiffRanges.resolveFromRevision(repo, VcsUtils.VcsType.GIT, "HEAD", 2));
        assertEquals(commits.get(0),
                VcsDiffRanges.resolveFromRevision(repo, VcsUtils.VcsType.GIT, "HEAD", 10));
        assertThrows(IOException.class,
                () -> VcsDiffRanges.resolveFromRevision(repo, VcsUtils.VcsType.GIT, "HEAD", 0));
    }

    @Test
    void listsGitChangesAndReadsBothSides() throws Exception {
        File repo = initGit("git-repo");
        List<String> commits = gitHistoryTwoCommits(repo);
        String from = commits.get(0);
        String to = commits.get(1);

        List<VcsDiffRanges.ChangedFile> changed =
                VcsDiffRanges.listChanges(repo, VcsUtils.VcsType.GIT, from, to);

        assertEquals(Map.of("a.txt", 'M', "b.txt", 'D', "c.txt", 'A'), byPath(changed));
        assertEquals("one\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, from, "a.txt").text());
        assertEquals("two\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, to, "a.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, to, "b.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, from, "c.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, to, "missing.txt").text());
        assertFalse(VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, to, "a.txt").binary());
        assertTrue(VcsDiffRanges.listChanges(repo, VcsUtils.VcsType.GIT, to, to).isEmpty());
    }

    @Test
    void listsGitWorkingTreeChangesIncludingUntrackedFiles() throws Exception {
        File repo = initGit("git-repo");
        File tracked = new File(repo, "a.txt");
        File deleted = new File(repo, "b.txt");
        Files.writeString(tracked.toPath(), "one\n");
        Files.writeString(deleted.toPath(), "bee\n");
        String base = commitGit(repo, "first");
        Files.writeString(tracked.toPath(), "changed\n");
        Files.delete(deleted.toPath());
        Files.writeString(new File(repo, "c.txt").toPath(), "new\n");

        List<VcsDiffRanges.ChangedFile> changed =
                VcsDiffRanges.listChanges(repo, VcsUtils.VcsType.GIT, base, null);

        assertEquals(Map.of("a.txt", 'M', "b.txt", 'D', "c.txt", 'A'), byPath(changed));
        assertEquals("changed\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, null, "a.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, null, "b.txt").text());
        assertEquals("new\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, null, "c.txt").text());
        assertEquals("one\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, base, "a.txt").text());
    }

    @Test
    void gitNameStatusParsingKeepsRawPaths() {
        // -z records: status, NUL, path, NUL. No quoting, no trimming — a path's
        // unicode, trailing space and embedded tab must all survive verbatim.
        String records = "M\0na\u00efve \u00fcn\u00efcode.txt\0A\0dir/trailing \0D\0tab\tname\0";

        assertEquals(Map.of(
                "na\u00efve \u00fcn\u00efcode.txt", 'M',
                "dir/trailing ", 'A',
                "tab\tname", 'D'), byPath(VcsDiffRanges.parseGitNameStatus(records)));
    }

    @Test
    void gitUnicodePathsSurviveQuoting() throws Exception {
        File repo = initGit("git-unicode-paths");
        String unicode = "na\u00efve \u00fcn\u00efcode.txt";
        Files.writeString(new File(repo, unicode).toPath(), "base\n");
        Files.writeString(new File(repo, "plain.txt").toPath(), "base\n");
        String head = commitGit(repo, "base");
        Files.writeString(new File(repo, unicode).toPath(), "mod\n");

        assertEquals(Map.of(unicode, 'M'), byPath(
                VcsDiffRanges.listChanges(repo, VcsUtils.VcsType.GIT, head, null)));
        assertEquals("base\n",
                VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, head, unicode).text());
    }

    @Test
    void flagsBinaryContentAtARevisionAndInTheWorkingTree() throws Exception {
        File repo = initGit("git-repo");
        Files.writeString(new File(repo, "a.txt").toPath(), "one\n");
        Files.write(new File(repo, "committed.bin").toPath(), new byte[] {1, 0, 2});
        commitGit(repo, "first");
        Files.write(new File(repo, "working.bin").toPath(), new byte[] {3, 0, 4});

        assertTrue(VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, "HEAD", "committed.bin").binary());
        VcsDiffRanges.FileContent working =
                VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.GIT, null, "working.bin");
        assertTrue(working.binary());
        assertEquals("", working.text());
        assertEquals('A', byPath(VcsDiffRanges.listChanges(
                repo, VcsUtils.VcsType.GIT, "HEAD", null)).get("working.bin"));
    }

    @Test
    void listChangesReportsGitFailure() throws Exception {
        File repo = initGit("git-repo");
        commitGit(repo, "first");

        IOException ex = assertThrows(IOException.class,
                () -> VcsDiffRanges.listChanges(repo, VcsUtils.VcsType.GIT, "nope", "HEAD"));
        assertTrue(ex.getMessage().contains("nope"), "unexpected message: " + ex.getMessage());
    }

    @Test
    void shortRevisionAbbreviatesAndFallsBack() throws Exception {
        File repo = initGit("git-repo");
        String full = commitGit(repo, "first");

        String shortHash = VcsDiffRanges.shortRevision(repo, VcsUtils.VcsType.GIT, "HEAD");

        assertTrue(full.startsWith(shortHash), shortHash + " should abbreviate " + full);
        assertEquals("nope", VcsDiffRanges.shortRevision(repo, VcsUtils.VcsType.GIT, "nope"));
    }

    @Test
    void detectsBinaryContent() {
        assertTrue(VcsDiffRanges.isBinary("a\0b"));
        assertFalse(VcsDiffRanges.isBinary("plain text"));
        assertFalse(VcsDiffRanges.isBinary(null));
    }

    @Test
    void defaultsTipPerVcs() {
        assertEquals("HEAD", VcsDiffRanges.defaultTip(VcsUtils.VcsType.GIT));
        assertEquals(".", VcsDiffRanges.defaultTip(VcsUtils.VcsType.HG));
    }

    @Test
    void missingRepositoryYieldsEmptyContentAndShortFallback() {
        File missing = tempDir.resolve("missing").toFile();

        assertEquals("", VcsDiffRanges.loadContent(
                missing, VcsUtils.VcsType.GIT, "HEAD", "a.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(
                missing, VcsUtils.VcsType.GIT, null, "a.txt").text());
        assertEquals("HEAD", VcsDiffRanges.shortRevision(missing, VcsUtils.VcsType.GIT, "HEAD"));
    }

    @Test
    void findRepoWalksUpFromAFile() throws Exception {
        File repo = initGit("git-repo");
        File file = new File(repo, "a.txt");
        Files.writeString(file.toPath(), "one\n");

        assertEquals(VcsUtils.VcsType.GIT,
                VcsDiffRanges.findRepo(file.getAbsolutePath()).type());
    }

    // ---- Mercurial ----------------------------------------------------------

    private static final Map<String, String> HG_ENV = Map.of("HGRCPATH", "");

    private File initHg(String name) throws Exception {
        assumeTrue(isBinaryAvailable("hg", "--version"), "hg not available on this machine");
        File repo = tempDir.resolve(name).toFile();
        assertTrue(repo.mkdirs() || repo.isDirectory());
        run(repo, HG_ENV, "hg", "init", "-q");
        return repo;
    }

    private String commitHg(File repo, String message) throws Exception {
        run(repo, HG_ENV, "hg", "commit", "-q", "-A", "-u", "test <test@example.com>", "-m", message);
        return capture(repo, HG_ENV, "hg", "log", "-r", ".", "--template", "{node}");
    }

    @Test
    void findRepoDetectsMercurial() throws Exception {
        File repo = initHg("hg-repo");
        File nested = new File(repo, "src");
        assertTrue(nested.mkdirs());

        VcsUtils.VcsInfo info = VcsDiffRanges.findRepo(nested.getAbsolutePath());

        assertEquals(VcsUtils.VcsType.HG, info.type());
        assertEquals(repo.getCanonicalPath(), info.rootDir().getCanonicalPath());
    }

    @Test
    void validateHgRevision() throws Exception {
        File repo = initHg("hg-repo");
        Files.writeString(new File(repo, "tracked.txt").toPath(), "one\n");
        commitHg(repo, "first");

        assertNull(VcsDiffRanges.validateRevision(repo, VcsUtils.VcsType.HG, "tip"));
        assertTrue(VcsDiffRanges.validateRevision(repo, VcsUtils.VcsType.HG, "no-such-rev")
                .contains("Unknown Mercurial revision: no-such-rev"));
    }

    @Test
    void resolvesAndListsHgRange() throws Exception {
        File repo = initHg("hg-repo");
        Files.writeString(new File(repo, "a.txt").toPath(), "one\n");
        Files.writeString(new File(repo, "b.txt").toPath(), "bee\n");
        String first = commitHg(repo, "first");
        Files.writeString(new File(repo, "a.txt").toPath(), "two\n");
        Files.delete(new File(repo, "b.txt").toPath());
        Files.writeString(new File(repo, "c.txt").toPath(), "see\n");
        String second = commitHg(repo, "second");

        assertEquals(second,
                VcsDiffRanges.resolveFromRevision(repo, VcsUtils.VcsType.HG, ".", 1));
        assertEquals(first,
                VcsDiffRanges.resolveFromRevision(repo, VcsUtils.VcsType.HG, ".", 2));
        assertEquals(first,
                VcsDiffRanges.resolveFromRevision(repo, VcsUtils.VcsType.HG, ".", 10));

        List<VcsDiffRanges.ChangedFile> changed =
                VcsDiffRanges.listChanges(repo, VcsUtils.VcsType.HG, first, second);

        assertEquals(Map.of("a.txt", 'M', "b.txt", 'D', "c.txt", 'A'), byPath(changed));
        assertEquals("one\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, first, "a.txt").text());
        assertEquals("two\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, second, "a.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, second, "b.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, first, "c.txt").text());
        assertTrue(VcsDiffRanges.shortRevision(repo, VcsUtils.VcsType.HG, second)
                .length() < second.length());
    }

    @Test
    void listsHgWorkingTreeChangesIncludingUntrackedFiles() throws Exception {
        File repo = initHg("hg-repo");
        File tracked = new File(repo, "a.txt");
        File deleted = new File(repo, "b.txt");
        Files.writeString(tracked.toPath(), "one\n");
        Files.writeString(deleted.toPath(), "bee\n");
        String base = commitHg(repo, "first");
        Files.writeString(tracked.toPath(), "changed\n");
        Files.delete(deleted.toPath());
        Files.writeString(new File(repo, "c.txt").toPath(), "new\n");

        List<VcsDiffRanges.ChangedFile> changed =
                VcsDiffRanges.listChanges(repo, VcsUtils.VcsType.HG, base, null);

        assertEquals(Map.of("a.txt", 'M', "b.txt", 'D', "c.txt", 'A'), byPath(changed));
        assertEquals("changed\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, null, "a.txt").text());
        assertEquals("", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, null, "b.txt").text());
        assertEquals("new\n", VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, null, "c.txt").text());
    }

    @Test
    void flagsBinaryHgContent() throws Exception {
        File repo = initHg("hg-repo");
        Files.writeString(new File(repo, "a.txt").toPath(), "one\n");
        Files.write(new File(repo, "bin.dat").toPath(), new byte[] {1, 0, 2});
        commitHg(repo, "first");

        VcsDiffRanges.FileContent committed =
                VcsDiffRanges.loadContent(repo, VcsUtils.VcsType.HG, "tip", "bin.dat");
        assertTrue(committed.binary());
        assertEquals("", committed.text());
    }

    private static Map<String, Character> byPath(List<VcsDiffRanges.ChangedFile> changed) {
        Map<String, Character> map = new HashMap<>();
        for (VcsDiffRanges.ChangedFile file : changed) {
            map.put(file.path(), file.status());
        }
        return map;
    }
}
