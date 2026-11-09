package github.anandb.netbeans.mcp;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.openide.util.Lookup;

import github.anandb.netbeans.contract.ProjectQuery;
import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.ProjectPaths;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

/**
 * Shared path-containment guard for MCP tools. Restricts filesystem-backed
 * tools (read/write/list/search/git) to paths inside the currently open
 * projects so a rogue MCP client cannot reach arbitrary user files.
 */
final class ProjectPathGuard {

    private static final Logger LOG = Logger.from(ProjectPathGuard.class);

    private ProjectPathGuard() {
    }

    /**
     * Returns true when {@code path} lies inside one of the currently open
     * projects. Both the requested path and the project roots are
     * canonicalized, so relative escapes ({@code ../}) and symlinks pointing
     * outside a project are rejected.
     */
    static boolean isInOpenProject(String path) {
        ProjectQuery pq = Lookup.getDefault().lookup(ProjectQuery.class);
        Project[] openProjects = pq == null ? new Project[0] : pq.getAllOpenProjects();
        if (openProjects.length == 0) {
            return false;
        }
        try {
            String canonical = new File(path).getCanonicalPath();
            for (Project p : openProjects) {
                File projectDirFile = org.openide.filesystems.FileUtil.toFile(p.getProjectDirectory());
                if (projectDirFile == null) {
                    continue;
                }
                String projectRoot = projectDirFile.getCanonicalPath();
                if (canonical.equals(projectRoot) || canonical.startsWith(projectRoot + File.separator)) {
                    return true;
                }
            }
        } catch (IOException e) {
            LOG.warn("Path containment check failed for {0}: {1}", path, e.getMessage());
        }
        return false;
    }

    /**
     * Returns the canonical root of the first currently open project, or
     * {@code null} when no project is open. Used as the default working
     * directory for git-backed MCP tools (never {@code user.dir}, which
     * points at the IDE launcher directory).
     */
    static String firstOpenProjectRoot() {
        ProjectQuery pq = Lookup.getDefault().lookup(ProjectQuery.class);
        Project[] openProjects = pq == null ? new Project[0] : pq.getAllOpenProjects();
        for (Project p : openProjects) {
            File projectDirFile = org.openide.filesystems.FileUtil.toFile(p.getProjectDirectory());
            if (projectDirFile == null) {
                continue;
            }
            try {
                return projectDirFile.getCanonicalPath();
            } catch (IOException e) {
                LOG.warn("Failed to canonicalize project root: {0}", e.getMessage());
            }
        }
        return null;
    }
    /**
     * Like {@link #isInOpenProject} but also accepts a project whose directory has no local
     * file: the comparison falls back to the project's virtual path. Used only by tools that
     * list editor tabs, never by file access.
     */
    static boolean isInOpenProjectOrVirtual(String path) {
        if (path == null) {
            return false;
        }
        ProjectQuery pq = Lookup.getDefault().lookup(ProjectQuery.class);
        Project[] openProjects = pq == null ? new Project[0] : pq.getAllOpenProjects();
        for (Project p : openProjects) {
            String root = ProjectPaths.nativePath(p);
            if (root != null
                    && (path.equals(root) || path.startsWith(root + '/')
                        || path.startsWith(root + File.separator))) {
                return true;
            }
        }
        return false;
    }

    /** Native absolute path of a local {@link FileObject}, or {@code null} for a non-local one. */
    static String absolutePath(FileObject fileObject) {
        File file = org.openide.filesystems.FileUtil.toFile(fileObject);
        return file == null ? null : file.getAbsolutePath();
    }

    /** Standard rejection response for paths outside the open projects. */
    static Map<String, Object> outsideProjectError(String path) {
        return Map.of("status", "error", "message", "Path is outside the open projects: " + path);
    }
}
