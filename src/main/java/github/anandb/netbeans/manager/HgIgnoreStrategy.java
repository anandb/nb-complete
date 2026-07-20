package github.anandb.netbeans.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import github.anandb.netbeans.contract.VcsIgnoreStrategy;

/**
 * Mercurial ignore strategy using {@code hg files} for bulk listing
 * and {@code hg status -i} for single-file queries.
 *
 * <p>Detects availability by checking for a {@code .hg} directory.</p>
 */
public class HgIgnoreStrategy implements VcsIgnoreStrategy {

    private static final Logger LOG = Logger.getLogger(HgIgnoreStrategy.class.getName());
    private static final long CMD_TIMEOUT_SEC = 30;
    private static final org.openide.util.RequestProcessor HG_RP =
            new org.openide.util.RequestProcessor("HgIgnore-Reader", 1);

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
        try {
            File hgRoot = findHgRoot(projectRoot);
            File workDir = hgRoot != null ? hgRoot : projectRoot;
            // hg files lists versioned (non-ignored) files relative to repo root
            ProcessBuilder pb = new ProcessBuilder("hg", "files");
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            Set<String> files = Collections.synchronizedSet(new LinkedHashSet<>());
            org.openide.util.RequestProcessor.Task readerTask = HG_RP.post(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        files.add(line);
                    }
                } catch (Exception e) {
                    // Expected when process is closed/destroyed
                }
            });

            try {
                boolean ok = proc.waitFor(CMD_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (!ok) {
                    proc.destroyForcibly();
                    LOG.log(Level.WARNING, "hg files timed out in {0}", projectRoot);
                    return Collections.emptySet();
                }
                readerTask.waitFinished(1000);
                int exitCode = proc.exitValue();
                if (exitCode != 0) {
                    LOG.log(Level.WARNING, "hg files failed (exit {0}) in {1}",
                            new Object[]{exitCode, projectRoot});
                    return Collections.emptySet();
                }
                return files;
            } finally {
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                }
                readerTask.waitFinished(1000);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "hg files failed in " + projectRoot, e);
            return Collections.emptySet();
        }
    }

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        try {
            File hgRoot = findHgRoot(projectRoot);
            File workDir = hgRoot != null ? hgRoot : projectRoot;
            // hg status -i outputs ignored files; empty output means not ignored
            ProcessBuilder pb = new ProcessBuilder(
                "hg", "status", "-i", file.getAbsolutePath()
            );
            pb.directory(workDir);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process proc = pb.start();

            Set<String> output = Collections.synchronizedSet(new LinkedHashSet<>());
            org.openide.util.RequestProcessor.Task readerTask = HG_RP.post(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        output.add(line);
                    }
                } catch (Exception e) {
                    // Expected when process is closed/destroyed
                }
            });

            boolean ok = proc.waitFor(CMD_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!ok) {
                proc.destroyForcibly();
                return false; // don't ignore on timeout
            }
            readerTask.waitFinished(1000);
            // hg status -i exits 0 and outputs ignored files; empty = not ignored
            return !output.isEmpty();
        } catch (Exception e) {
            LOG.log(Level.FINE, "hg status -i failed for {0}", file);
            return false;
        }
    }
}
