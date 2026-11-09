package github.anandb.netbeans.support;

import java.io.File;
import java.io.IOException;
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

    /**
     * Relative slash-separated path of {@code file} under {@code root}, or {@code null}
     * when the file is outside it. The comparison uses a separator boundary, so a sibling
     * that only shares the root's string prefix is outside.
     */
    public static String relativeTo(File root, File file) {
        if (root == null || file == null) {
            return null;
        }
        String rootPath = root.getAbsolutePath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(prefix)) {
            return null;
        }
        return filePath.substring(prefix.length()).replace(File.separatorChar, '/');
    }

    /** Canonical path of {@code path}, falling back to the normalized absolute path. */
    public static String canonicalPath(String path) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        try {
            return file.getCanonicalPath();
        } catch (IOException ex) {
            return file.getAbsoluteFile().toPath().normalize().toString();
        }
    }
}
