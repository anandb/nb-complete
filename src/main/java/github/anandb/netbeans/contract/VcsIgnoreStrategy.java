package github.anandb.netbeans.contract;

import java.io.File;
import java.util.Set;

/**
 * Strategy interface for VCS-aware file ignore filtering.
 * Implementations detect a specific VCS (Git, Mercurial, SVN) and
 * provide the list of tracked/ignorable files under a root directory.
 *
 * <p>Layer: contract (lowest) — imports only java.io and java.util.</p>
 */
public interface VcsIgnoreStrategy {

    /** Returns {@code true} if this strategy can handle the given project root. */
    boolean isAvailable(File projectRoot);

    /**
     * Returns the set of relative paths (from {@code projectRoot}) of files
     * that are NOT ignored by the VCS. The initial scan uses a bulk command
     * for efficiency; incremental updates go through {@link #isIgnored}.
     *
     * @param projectRoot the project root directory
     * @return unmodifiable set of non-ignored relative paths (slash-separated)
     */
    Set<String> listNonIgnoredFiles(File projectRoot);

    /**
     * Checks whether a single file is ignored by the VCS.
     * Used for incremental updates after the initial bulk scan.
     *
     * @param projectRoot the project root directory
     * @param file        the file to check (absolute path)
     * @return {@code true} if the file should be excluded
     */
    boolean isIgnored(File projectRoot, File file);
}
