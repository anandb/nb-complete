package github.anandb.netbeans.contract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.openide.filesystems.FileObject;

/**
 * Query interface for the cached file index built by {@code FileCacheManager}.
 * UI layer reads from this; the manager writes to it.
 *
 * <p>Layer: contract (lowest) — imports only java.util and openide.filesystems.</p>
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

    /**
     * Returns the cached files of one project. Callers that already know the project (for
     * example {@code locate_file}, whose scope is the session's directory) use this instead of
     * matching every open project's files and discarding the misses afterwards.
     *
     * @param projectName the project name stored on {@link CachedFile#projectName()}
     */
    default Collection<CachedFile> getFiles(String projectName) {
        List<CachedFile> scoped = new ArrayList<>();
        for (CachedFile file : getAllFiles()) {
            if (file.projectName().equals(projectName)) {
                scoped.add(file);
            }
        }
        return scoped;
    }

    /**
     * Returns the name of the cached project whose source root contains {@code directory}, or
     * {@code null} when the directory belongs to no open project.
     */
    default String getProjectName(String directory) {
        return null;
    }

    /** Registers a listener that fires once when the cache first becomes ready. */
    void onReady(Runnable action);

    /** Returns the current version of the cache (increments on mutation). */
    default long getCacheVersion() { return 0L; }

    /**
     * Releases listeners held for the cache's lifetime. Called on module shutdown,
     * and by tests so a manager instance does not outlive its test.
     */
    default void dispose() { }

    /** A single entry in the file cache. */
    record CachedFile(FileObject fileObject, String projectName, String relativePath) {}
}
