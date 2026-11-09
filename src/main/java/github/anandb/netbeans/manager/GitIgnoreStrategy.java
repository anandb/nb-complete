package github.anandb.netbeans.manager;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import github.anandb.netbeans.contract.VcsIgnoreStrategy;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.VcsUtils;

/**
 * Git ignore strategy using {@code git ls-files} for bulk listing
 * and {@code git check-ignore} for single-file queries.
 *
 * <p>Detects availability by checking for the {@code .git/refs} directory.</p>
 */
public class GitIgnoreStrategy implements VcsIgnoreStrategy {

    private static final Logger LOG = Logger.from(GitIgnoreStrategy.class);
    private static final int CMD_TIMEOUT_SEC = 30;

    @Override
    public boolean isAvailable(File projectRoot) {
        return findGitRoot(projectRoot) != null;
    }

    /**
     * Walks up from {@code dir} looking for a {@code .git/refs} directory.
     * Returns the git repository root, or {@code null} if none found.
     */
    static File findGitRoot(File dir) {
        File current = dir;
        while (current != null) {
            if (new File(current, ".git/refs").isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    @Override
    public Set<String> listNonIgnoredFiles(File projectRoot) {
        File gitRoot = findGitRoot(projectRoot);
        File workDir = gitRoot != null ? gitRoot : projectRoot;
        try {
            // git ls-files returns all tracked + untracked (non-ignored) files
            VcsUtils.CapturedCommand result = VcsUtils.runCaptured(workDir, CMD_TIMEOUT_SEC,
                    "git", "ls-files", "--cached", "--others", "--exclude-standard",
                    "--", projectRoot.getAbsolutePath());
            if (result.timedOut()) {
                LOG.log(Level.WARNING, "git ls-files timed out in {0}", projectRoot);
                return Set.of();
            }
            if (result.exitCode() != 0) {
                LOG.log(Level.WARNING, "git ls-files failed (exit {0}) in {1}",
                        new Object[]{result.exitCode(), projectRoot});
                return Set.of();
            }
            Set<String> files = new LinkedHashSet<>();
            result.output().lines().filter(line -> !line.isEmpty()).forEach(files::add);
            return files;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "git ls-files failed in " + projectRoot, e);
            return Set.of();
        }
    }

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        File gitRoot = findGitRoot(projectRoot);
        File workDir = gitRoot != null ? gitRoot : projectRoot;
        try {
            VcsUtils.CapturedCommand result = VcsUtils.runCaptured(workDir, CMD_TIMEOUT_SEC,
                    "git", "check-ignore", "-q", file.getAbsolutePath());
            // git check-ignore exits 0 if ignored, 1 if not
            return !result.timedOut() && result.exitCode() == 0;
        } catch (Exception e) {
            LOG.log(Level.FINE, "git check-ignore failed for {0}", file);
            return false;
        }
    }
}
