package github.anandb.netbeans.manager;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import github.anandb.netbeans.contract.VcsIgnoreStrategy;

/**
 * Fallback strategy when no VCS is detected. Includes all files.
 */
public class NoOpIgnoreStrategy implements VcsIgnoreStrategy {

    @Override
    public boolean isAvailable(File projectRoot) {
        return true; // always available as fallback
    }

    @Override
    public Set<String> listNonIgnoredFiles(File projectRoot) {
        // Caller (FileCacheManager) walks the filesystem when this returns empty
        return Collections.emptySet();
    }

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        return false; // never ignore
    }
}
