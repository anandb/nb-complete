package github.anandb.netbeans.pattern;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Case-insensitive matching over the file index, shared by "Navigate &gt; Jump to File" and the
 * {@code locate_file} MCP tool so both search the same way.
 *
 * <p>A plain query matches a file-name prefix or any substring of the project-relative path. A
 * query containing glob characters ({@code *}, {@code ?}, {@code [}, <code>{</code>) is matched
 * bash-style against the file name and the relative path, with an implicit trailing {@code *} so
 * {@code *Test} also hits {@code MyTestFile.java}, and is accepted at any length. A plain query
 * shorter than {@link #MIN_PLAIN_LENGTH} is not usable — it matches nearly everything.</p>
 *
 * <p>Compile once per search and reuse: a scan over the whole index calls {@link Query#matches}
 * for every entry and must not allocate.</p>
 */
public final class FileNameMatcher {

    /** Shortest plain-text query worth searching for. */
    public static final int MIN_PLAIN_LENGTH = 3;

    /** Detects glob metacharacters: * ? [ { */
    private static final Pattern GLOB_CHARS = Pattern.compile("[*?\\[{]");

    private FileNameMatcher() {
    }

    /** Compiles {@code rawQuery} into a reusable matcher. */
    public static Query compile(String rawQuery) {
        String text = rawQuery == null ? "" : rawQuery.trim();
        if (text.isEmpty()) {
            return new Query(text, null);
        }
        PathMatcher glob = compileGlob(text);
        return new Query(text, glob);
    }

    /** Returns null when the query is not a parseable glob. */
    private static PathMatcher compileGlob(String text) {
        if (!GLOB_CHARS.matcher(text).find()) {
            return null;
        }
        String pattern = text.toLowerCase(Locale.ROOT);
        char last = pattern.charAt(pattern.length() - 1);
        if (last != '*' && last != '?') {
            pattern += "*";
        }
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (RuntimeException ex) {
            // An invalid glob is not an error: the plain-text rules still apply.
            return null;
        }
    }

    /** One compiled query, applied to every entry of the index. Immutable. */
    public static final class Query {

        private final String text;
        private final PathMatcher glob;
        private final boolean usable;

        private Query(String text, PathMatcher glob) {
            this.text = text;
            this.glob = glob;
            this.usable = !text.isEmpty() && (glob != null || text.length() >= MIN_PLAIN_LENGTH);
        }

        /** False for a blank query, or a plain one shorter than {@link #MIN_PLAIN_LENGTH}. */
        public boolean usable() {
            return usable;
        }

        /** True when the file name itself starts with the query — a stronger hit than a path match. */
        public boolean namePrefix(String fileName) {
            return !text.isEmpty() && fileName.regionMatches(true, 0, text, 0, text.length());
        }

        /** File-name prefix, path substring, then the glob against either. */
        public boolean matches(String fileName, String relativePath) {
            if (!usable) {
                return false;
            }
            if (namePrefix(fileName)) {
                return true;
            }
            if (containsIgnoreCase(relativePath, text)) {
                return true;
            }
            if (glob != null) {
                // The bare file name covers single-element globs (*.java, *Test*); the relative
                // path covers multi-segment ones (**/main/**).
                if (glob.matches(Path.of(fileName.toLowerCase(Locale.ROOT)))) {
                    return true;
                }
                return glob.matches(Path.of(relativePath.toLowerCase(Locale.ROOT)));
            }
            return false;
        }
    }

    /** Case-insensitive {@code contains}, allocation-free. */
    private static boolean containsIgnoreCase(String haystack, String needle) {
        int last = haystack.length() - needle.length();
        for (int i = 0; i <= last; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }
}
