package github.anandb.netbeans.manager;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import github.anandb.netbeans.contract.VcsIgnoreStrategy;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.VcsUtils;

/**
 * Mercurial ignore strategy using {@code hg files} for bulk listing
 * and {@code hg status -i} for single-file queries.
 *
 * <p>Detects availability by checking for a {@code .hg} directory.</p>
 */
public class HgIgnoreStrategy implements VcsIgnoreStrategy {

    private static final Logger LOG = Logger.from(HgIgnoreStrategy.class);
    private static final int CMD_TIMEOUT_SEC = 30;

    @Override
    public boolean isAvailable(File projectRoot) {
        return findHgRoot(projectRoot) != null;
    }

    /**
     * Walks up from {@code dir} looking for a {@code .hg} directory.
     * Returns the Mercurial repository root, or {@code null} if none found.
     */
    static File findHgRoot(File dir) {
        File current = dir;
        while (current != null) {
            if (new File(current, ".hg").isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    @Override
    public Set<String> listNonIgnoredFiles(File projectRoot) {
        File hgRoot = findHgRoot(projectRoot);
        File workDir = hgRoot != null ? hgRoot : projectRoot;
        try {
            // hg files lists versioned (non-ignored) files relative to repo root
            VcsUtils.CapturedCommand result = VcsUtils.runCaptured(workDir, CMD_TIMEOUT_SEC,
                    "hg", "files");
            if (result.timedOut()) {
                LOG.log(Level.WARNING, "hg files timed out in {0}", projectRoot);
                return Set.of();
            }
            if (result.exitCode() != 0) {
                LOG.log(Level.WARNING, "hg files failed (exit {0}) in {1}",
                        new Object[]{result.exitCode(), projectRoot});
                return Set.of();
            }
            Set<String> files = new LinkedHashSet<>();
            result.output().lines().filter(line -> !line.isEmpty()).forEach(files::add);
            return files;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "hg files failed in " + projectRoot, e);
            return Set.of();
        }
    }

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        File hgRoot = findHgRoot(projectRoot);
        File workDir = hgRoot != null ? hgRoot : projectRoot;
        try {
            // hg status -i exits 0 and outputs ignored files; empty output = not ignored
            VcsUtils.CapturedCommand result = VcsUtils.runCaptured(workDir, CMD_TIMEOUT_SEC,
                    "hg", "status", "-i", file.getAbsolutePath());
            return !result.timedOut() && result.exitCode() == 0 && !result.output().isBlank();
        } catch (Exception e) {
            LOG.log(Level.FINE, "hg status -i failed for {0}", file);
            return false;
        }
    }
}
