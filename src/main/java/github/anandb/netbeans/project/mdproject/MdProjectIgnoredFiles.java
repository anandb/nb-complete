package github.anandb.netbeans.project.mdproject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Utility for reading and writing the {@code .mdproject-ignore} file
 * in a markdown project's root directory. One glob-like pattern per line;
 * lines starting with {@code #} are comments.
 * <p>
 * Patterns are matched against the file/folder name and the relative path
 * from the project root. A file is hidden from the project tree when any
 * pattern matches.
 */
public final class MdProjectIgnoredFiles {

    private static final String IGNORE_FILE = ".mdproject-ignore";

    private MdProjectIgnoredFiles() {}

    private static final Map<Project, CachedPatterns> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static class CachedPatterns {
        final long lastModified;
        final List<String> patterns;

        CachedPatterns(long lastModified, List<String> patterns) {
            this.lastModified = lastModified;
            this.patterns = patterns;
        }
    }

    /** Returns the list of non-comment, non-empty patterns from the ignore file. */
    public static List<String> getIgnoredPatterns(Project project) {
        FileObject dir = project.getProjectDirectory();
        FileObject ignoreFile = dir.getFileObject(IGNORE_FILE);
        if (ignoreFile == null) {
            CACHE.remove(project);
            return Collections.emptyList();
        }

        long currentModified = ignoreFile.lastModified().getTime();
        CachedPatterns cached = CACHE.get(project);
        if (cached != null && cached.lastModified == currentModified) {
            return cached.patterns;
        }

        try {
            String content = new String(ignoreFile.asBytes(), StandardCharsets.UTF_8);
            List<String> patterns = new ArrayList<>();
            for (String line : content.split("\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    patterns.add(line);
                }
            }
            List<String> unmodifiable = Collections.unmodifiableList(patterns);
            CACHE.put(project, new CachedPatterns(currentModified, unmodifiable));
            return unmodifiable;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /** Overwrites the ignore file with the given patterns. */
    public static void setIgnoredPatterns(Project project, List<String> patterns) throws IOException {
        FileObject dir = project.getProjectDirectory();
        FileObject ignoreFile = dir.getFileObject(IGNORE_FILE);
        StringBuilder sb = new StringBuilder();
        sb.append("# Markdown Project ignored files\n")
          .append("# One pattern per line. Patterns are matched against file/folder names\n")
          .append("# and relative paths from the project root.\n\n");
        for (String p : patterns) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                sb.append(trimmed).append('\n');
            }
        }
        if (ignoreFile == null) {
            ignoreFile = dir.createData(IGNORE_FILE);
        }
        try (OutputStream out = ignoreFile.getOutputStream()) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns {@code true} if the given file object matches any of the
     * project's ignored patterns.
     */
    public static boolean isIgnored(Project project, FileObject fo) {
        List<String> patterns = getIgnoredPatterns(project);
        if (patterns.isEmpty()) {
            return false;
        }
        FileObject dir = project.getProjectDirectory();
        String relPath = getRelativePath(dir, fo);
        String name = fo.getNameExt();
        for (String p : patterns) {
            if (matches(p, name, relPath)) {
                return true;
            }
        }
        return false;
    }

    // ---- matching ----

    private static boolean matches(String pattern, String name, String relPath) {
        // Exact name match
        if (pattern.equals(name)) {
            return true;
        }
        // Relative path prefix match (pattern acts as a directory/prefix)
        if (relPath != null
                && (relPath.equals(pattern)
                 || relPath.startsWith(pattern + "/")
                 || relPath.startsWith(pattern + "\\"))) {
            return true;
        }
        // Glob: **/pattern matches anywhere in the path
        if (pattern.startsWith("**/")) {
            String suffix = pattern.substring(3);
            if (name.equals(suffix)) {
                return true;
            }
            if (relPath != null && relPath.contains(suffix)) {
                return true;
            }
        }
        return false;
    }

    /** Computes the relative path of {@code target} under {@code base}. */
    private static String getRelativePath(FileObject base, FileObject target) {
        StringBuilder path = new StringBuilder();
        FileObject current = target;
        while (current != null && !current.equals(base)) {
            if (path.length() > 0) {
                path.insert(0, "/");
            }
            path.insert(0, current.getNameExt());
            current = current.getParent();
        }
        if (current == null) {
            return null; // not under base
        }
        return path.toString();
    }
}
