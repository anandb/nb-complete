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
 * Git ignore strategy using {@code git ls-files} for bulk listing
 * and {@code git check-ignore} for single-file queries.
 *
 * <p>Detects availability by checking for a {@code .git} directory.</p>
 */
public class GitIgnoreStrategy implements VcsIgnoreStrategy {

    private static final Logger LOG = Logger.getLogger(GitIgnoreStrategy.class.getName());
    private static final long CMD_TIMEOUT_SEC = 30;

    @Override
    public boolean isAvailable(File projectRoot) {
        return new File(projectRoot, ".git").isDirectory();
    }

    @Override
    public Set<String> listNonIgnoredFiles(File projectRoot) {
        try {
            // git ls-files returns all tracked + untracked (non-ignored) files
            ProcessBuilder pb = new ProcessBuilder(
                "git", "ls-files", "--cached", "--others", "--exclude-standard",
                "--", projectRoot.getAbsolutePath()
            );
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            Set<String> files = new LinkedHashSet<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    files.add(line);
                }
            }

            boolean ok = proc.waitFor(CMD_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!ok) {
                proc.destroyForcibly();
                LOG.log(Level.WARNING, "git ls-files timed out in {0}", projectRoot);
                return Collections.emptySet();
            }
            int exitCode = proc.exitValue();
            if (exitCode != 0) {
                LOG.log(Level.WARNING, "git ls-files failed (exit {0}) in {1}",
                        new Object[]{exitCode, projectRoot});
                return Collections.emptySet();
            }
            return files;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "git ls-files failed in " + projectRoot, e);
            return Collections.emptySet();
        }
    }

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "git", "check-ignore", "-q", file.getAbsolutePath()
            );
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean ok = proc.waitFor(CMD_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!ok) {
                proc.destroyForcibly();
                return false; // don't ignore on timeout
            }
            // git check-ignore exits 0 if ignored, 1 if not
            return proc.exitValue() == 0;
        } catch (Exception e) {
            LOG.log(Level.FINE, "git check-ignore failed for {0}", file);
            return false;
        }
    }
}
