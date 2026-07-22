package github.anandb.netbeans.contract;

import java.util.Collection;
import org.openide.filesystems.FileObject;

/**
 * Query interface for the cached file index built by {@code FileCacheManager}.
 * UI layer reads from this; the manager writes to it.
 *
 * <p>Layer: contract (lowest) — imports only java.io and openide.filesystems.</p>
 */
public interface FileCacheQuery {

    /** Returns {@code true} if the initial cache build has completed. */
    boolean isReady();

    /**
     * Returns all cached, non-ignored files across all open projects.
     * Each entry is a {@link FileObject} mapped to its source root's project name.
     *
     * @return collection of (FileObject, projectName, relativePath) tuples
     */
    Collection<CachedFile> getAllFiles();

    /** Registers a listener that fires once when the cache first becomes ready. */
    void onReady(Runnable action);

    /** Returns a version number that increments every time the entire cache is rebuilt. */
    long getCacheVersion();

    /** A single entry in the file cache. */
    record CachedFile(FileObject fileObject, String projectName, String relativePath) {}
}
