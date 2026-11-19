package github.anandb.netbeans.support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;

import github.anandb.netbeans.pattern.FatalPrefixPattern;

/**
 * Read-only commit-range and working-tree support shared by git and Mercurial
 * diff tools.
 *
 * <p>Resolves the starting revision for "last N commits", lists the files
 * changed between two revisions or between a revision and the working tree,
 * and reads both sides. The multi-file viewer itself lives in the UI layer;
 * every command here is bounded by {@link VcsUtils#runCaptured}.
 */
public final class VcsDiffRanges {

    private static final Logger LOG = Logger.from(VcsDiffRanges.class);
    private static final int TIMEOUT_SECONDS = 60;
    /** Working-tree files larger than this are flagged binary instead of loaded. */
    private static final long MAX_WORKING_FILE_BYTES = 2L * 1024 * 1024;

    private VcsDiffRanges() {
    }

    /** A file changed between two revisions, carrying the VCS status code. */
    public record ChangedFile(String path, char status) {
    }

    /** One side of a file: its text and whether it cannot be shown as text. */
    public record FileContent(String text, boolean binary) {
        /** Absent side (added/deleted file). */
        public static final FileContent EMPTY = new FileContent("", false);
    }

    /**
     * Walks up from {@code startDir} to the nearest repository root. Unlike
     * {@link VcsUtils#findVcsInfo} this accepts a worktree/submodule where
     * {@code .git} is a file rather than a directory.
     */
    public static VcsUtils.VcsInfo findRepo(String startDir) {
        if (startDir == null || startDir.isBlank()) {
            return null;
        }
        File current = new File(startDir);
        while (current != null) {
            if (current.isDirectory()) {
                if (new File(current, ".git").exists()) {
                    return new VcsUtils.VcsInfo(current, VcsUtils.VcsType.GIT);
                }
                if (new File(current, ".hg").exists()) {
                    return new VcsUtils.VcsInfo(current, VcsUtils.VcsType.HG);
                }
            }
            current = current.getParentFile();
        }
        return null;
    }

    /** The current commit tip, used to resolve {@code lastCommits}. */
    public static String defaultTip(VcsUtils.VcsType type) {
        return type == VcsUtils.VcsType.HG ? "." : "HEAD";
    }

    /** @return {@code null} when {@code revision} resolves, otherwise a user-facing error */
    public static String validateRevision(File repoDir, VcsUtils.VcsType type, String revision) {
        try {
            VcsUtils.CapturedCommand result = type == VcsUtils.VcsType.HG
                    ? run(repoDir, "hg", "log", "-r", revision, "--template", "{node}")
                    : run(repoDir, "git", "rev-parse", "--verify", "--quiet", revision + "^{commit}");
            if (result.exitCode() != 0 || result.output().isBlank()) {
                return (type == VcsUtils.VcsType.HG ? "Unknown Mercurial revision: "
                        : "Unknown Git revision: ") + revision;
            }
            return null;
        } catch (Exception e) {
            return message(e);
        }
    }

    /**
     * Resolves the oldest commit of the last {@code lastCommits} commits: the
     * {@code (lastCommits - 1)}th ancestor of {@code tip}, so
     * {@code lastCommits=5} returns {@code tip~4}. A history shorter than
     * requested falls back to its root commit.
     */
    public static String resolveFromRevision(File repoDir, VcsUtils.VcsType type,
            String tip, int lastCommits) throws Exception {
        if (lastCommits < 1) {
            throw new IOException("lastCommits must be at least 1");
        }
        if (type == VcsUtils.VcsType.HG) {
            VcsUtils.CapturedCommand result = run(repoDir, "hg", "log", "-r",
                    "sort(ancestors(" + tip + "), -rev)",
                    "-l", String.valueOf(lastCommits), "--template", "{node}\n");
            requireSuccess(result, "hg log");
            List<String> lines = nonBlankLines(result.output());
            if (lines.isEmpty()) {
                throw new IOException("No commits found for " + tip);
            }
            return lines.get(lines.size() - 1);
        }
        VcsUtils.CapturedCommand result = run(repoDir, "git", "rev-parse", "--verify", "--quiet",
                tip + "~" + (lastCommits - 1) + "^{commit}");
        if (result.exitCode() == 0 && !result.output().isBlank()) {
            return result.output().trim();
        }
        VcsUtils.CapturedCommand roots = run(repoDir, "git", "rev-list", "--max-parents=0", tip);
        requireSuccess(roots, "git rev-list");
        List<String> lines = nonBlankLines(roots.output());
        if (lines.isEmpty()) {
            throw new IOException("No commits found for " + tip);
        }
        return lines.get(0);
    }

    /**
     * Lists the files changed between {@code from} and {@code to}. When
     * {@code to} is {@code null}, the working tree (including untracked files)
     * is compared against {@code from}.
     */
    public static List<ChangedFile> listChanges(File repoDir, VcsUtils.VcsType type,
            String from, String to) throws Exception {
        boolean workingTree = to == null;
        if (type == VcsUtils.VcsType.HG) {
            VcsUtils.CapturedCommand result = workingTree
                    ? run(repoDir, "hg", "status", "--rev", from, "-mardu")
                    : run(repoDir, "hg", "status", "--rev", from, "--rev", to);
            requireSuccess(result, "hg status");
            return parseHgStatus(result.output());
        }
        List<String> command = new ArrayList<>(List.of("git", "diff", "--name-status",
                "--no-renames", "--no-ext-diff", from));
        if (!workingTree) {
            command.add(to);
        }
        VcsUtils.CapturedCommand result = run(repoDir, command.toArray(String[]::new));
        requireSuccess(result, "git diff");
        List<ChangedFile> files = parseGitNameStatus(result.output());
        if (workingTree) {
            files.addAll(untrackedGitFiles(repoDir));
        }
        return files;
    }

    /**
     * Content of {@code path} at {@code revision}, or of the working copy when
     * {@code revision} is {@code null}. Missing sides yield empty text; binary
     * or oversized working files are flagged instead of decoded.
     */
    public static FileContent loadContent(File repoDir, VcsUtils.VcsType type,
            String revision, String path) {
        if (revision == null) {
            return readWorkingCopy(repoDir, path);
        }
        try {
            VcsUtils.CapturedCommand result = type == VcsUtils.VcsType.HG
                    ? run(repoDir, "hg", "cat", "-r", revision, "--", path)
                    : run(repoDir, "git", "show", revision + ":" + path);
            if (result.exitCode() != 0) {
                return FileContent.EMPTY;
            }
            if (result.truncated()) {
                return new FileContent("", true);
            }
            return content(FatalPrefixPattern.stripFrom(result.output()));
        } catch (Exception e) {
            LOG.warn("Failed to read {0} at {1}: {2}", path, revision, ExceptionUtils.getMessage(e));
            return FileContent.EMPTY;
        }
    }

    private static FileContent content(String text) {
        return isBinary(text) ? new FileContent("", true) : new FileContent(text, false);
    }

    private static FileContent readWorkingCopy(File repoDir, String path) {
        File file = new File(repoDir, path);
        if (!file.isFile()) {
            return FileContent.EMPTY;
        }
        if (file.length() > MAX_WORKING_FILE_BYTES) {
            return new FileContent("", true);
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return content(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.warn("Failed to read working file {0}: {1}", file, ExceptionUtils.getMessage(e));
            return FileContent.EMPTY;
        }
    }

    private static List<ChangedFile> untrackedGitFiles(File repoDir) throws Exception {
        VcsUtils.CapturedCommand result = run(repoDir, "git", "ls-files",
                "--others", "--exclude-standard");
        requireSuccess(result, "git ls-files");
        List<ChangedFile> files = new ArrayList<>();
        for (String line : result.output().split("\n")) {
            String path = line.trim();
            if (!path.isEmpty()) {
                files.add(new ChangedFile(path, 'A'));
            }
        }
        return files;
    }

    /** Abbreviated revision for display, falling back to the caller's text. */
    public static String shortRevision(File repoDir, VcsUtils.VcsType type, String revision) {
        try {
            VcsUtils.CapturedCommand result = type == VcsUtils.VcsType.HG
                    ? run(repoDir, "hg", "log", "-r", revision, "--template", "{node|short}")
                    : run(repoDir, "git", "rev-parse", "--short", revision);
            if (result.exitCode() == 0 && !result.output().isBlank()) {
                return result.output().trim();
            }
        } catch (Exception e) {
            LOG.fine("Failed to shorten {0}: {1}", revision, ExceptionUtils.getMessage(e));
        }
        return revision;
    }

    /** Binary content (a NUL byte) cannot be rendered by the text diff view. */
    public static boolean isBinary(String content) {
        return content != null && content.indexOf('\0') >= 0;
    }

    private static List<ChangedFile> parseGitNameStatus(String output) {
        List<ChangedFile> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\t", 2);
            if (parts.length == 2 && !parts[1].isBlank()) {
                files.add(new ChangedFile(parts[1].trim(), parts[0].trim().charAt(0)));
            }
        }
        return files;
    }

    private static List<ChangedFile> parseHgStatus(String output) {
        List<ChangedFile> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() < 3 || trimmed.charAt(1) != ' ') {
                continue;
            }
            char status = switch (trimmed.charAt(0)) {
                case 'A', '?' -> 'A';
                case 'M' -> 'M';
                // Mercurial reports removed files as R (or ! when missing).
                case 'R', '!' -> 'D';
                default -> 'M';
            };
            files.add(new ChangedFile(trimmed.substring(2).trim(), status));
        }
        return files;
    }

    private static List<String> nonBlankLines(String output) {
        List<String> lines = new ArrayList<>();
        for (String line : output.split("\n")) {
            if (!line.isBlank()) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private static VcsUtils.CapturedCommand run(File dir, String... command) throws Exception {
        return VcsUtils.runCaptured(dir, TIMEOUT_SECONDS, command);
    }

    private static void requireSuccess(VcsUtils.CapturedCommand result, String label) throws IOException {
        if (result.timedOut()) {
            throw new IOException(label + " timed out");
        }
        if (result.truncated()) {
            throw new IOException(label + " output exceeded the capture limit");
        }
        if (result.exitCode() != 0) {
            String detail = FatalPrefixPattern.stripFrom(result.output()).trim();
            int newline = detail.indexOf('\n');
            if (newline >= 0) {
                detail = detail.substring(0, newline).trim();
            }
            throw new IOException(detail.isEmpty() ? label + " failed" : detail);
        }
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return message != null && !message.isBlank() ? message : e.getClass().getSimpleName();
    }
}
