package github.anandb.netbeans.support;

import java.io.File;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Native OS form of a NetBeans project directory.
 *
 * <p>ACP's {@code cwd} and every directory a session is compared against must be an OS path.
 * {@link FileObject#getPath()} is a virtual path: forward slashes everywhere, and a Windows UNC
 * root comes out as {@code //host/share/...}. Harnesses treat that as a POSIX path (OpenCode 2.x
 * collapses it to {@code /host/share/...}), so the session points at a directory that does not
 * exist. {@link FileUtil#toFile} gives the native file where the directory is local; non-local
 * filesystems keep the virtual path, as before.</p>
 */
public final class ProjectPaths {

    private ProjectPaths() {
    }

    /** Absolute OS path of {@code directory}, or its virtual path when it has no local file. */
    public static String nativePath(FileObject directory) {
        if (directory == null) {
            return null;
        }
        File file = FileUtil.toFile(directory);
        return file != null ? file.getAbsolutePath() : directory.getPath();
    }

    /** Native path of a project's directory. */
    public static String nativePath(Project project) {
        return project != null ? nativePath(project.getProjectDirectory()) : null;
    }

    /**
     * Repairs a path that reached us in NetBeans' virtual UNC form ({@code //host/share/...}) so
     * a caller that forgot {@link #nativePath} cannot send one to the harness. Only the leading
     * {@code //} is rewritten: the harness picks its path flavor from that character, and its
     * Windows normalizer converts the remaining separators. Idempotent and a no-op off Windows.
     */
    public static String nativeForm(String path) {
        return nativeForm(path, File.separatorChar);
    }

    static String nativeForm(String path, char separator) {
        if (path == null || separator != '\\' || !path.startsWith("//")) {
            return path;
        }
        return "\\\\" + path.substring(2);
    }
}
