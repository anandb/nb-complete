package github.anandb.netbeans.manager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import github.anandb.netbeans.contract.VcsIgnoreStrategy;
import github.anandb.netbeans.support.Logger;

/**
 * Display-oriented ignore strategy backed by a project-root {@code .bbignore}.
 *
 * <p>Uses the same syntax as {@code .gitignore} — comments, negation,
 * directory-only patterns, leading-slash anchoring, {@code *}, {@code ?} and
 * {@code **} — but is independent of version control. When the file exists it
 * takes precedence over {@link GitIgnoreStrategy} and {@link HgIgnoreStrategy},
 * which are not consulted at all.</p>
 */
public class BbIgnoreStrategy implements VcsIgnoreStrategy {

    static final String FILE_NAME = ".bbignore";

    private static final Logger LOG = Logger.from(BbIgnoreStrategy.class);

    /** VCS metadata is never worth showing, whatever the ignore file says. */
    private static final Set<String> VCS_DIRS = Set.of(".git", ".hg", ".svn");

    private final Map<String, CachedRules> cache = new ConcurrentHashMap<>();

    private record Rule(Pattern pattern, boolean negated, boolean dirOnly) {
    }

    private record CachedRules(long modified, long length, List<Rule> rules) {
    }

    @Override
    public boolean isAvailable(File projectRoot) {
        return projectRoot != null && new File(projectRoot, FILE_NAME).isFile();
    }

    @Override
    public Set<String> listNonIgnoredFiles(File projectRoot) {
        Set<String> result = new LinkedHashSet<>();
        if (projectRoot == null) {
            return result;
        }
        List<Rule> rules = loadRules(projectRoot);
        walk(projectRoot, projectRoot, rules, result);
        return result;
    }

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        if (projectRoot == null || file == null) {
            return false;
        }
        String rel = relativize(projectRoot, file);
        if (rel == null || rel.isEmpty()) {
            return false;
        }
        return matches(rel, file.isDirectory(), loadRules(projectRoot));
    }

    private void walk(File root, File current, List<Rule> rules, Set<String> result) {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String rel = relativize(root, child);
            if (rel == null) {
                continue;
            }
            if (child.isDirectory()) {
                if (VCS_DIRS.contains(child.getName()) || matches(rel, true, rules)) {
                    continue;
                }
                walk(root, child, rules, result);
            } else if (!matches(rel, false, rules)) {
                result.add(rel);
            }
        }
    }

    /** Applies the rules in order; the last matching rule wins. */
    private static boolean matches(String rel, boolean directory, List<Rule> rules) {
        String[] parts = rel.split("/");
        boolean ignored = false;
        for (Rule rule : rules) {
            int limit = rule.dirOnly && !directory ? parts.length - 1 : parts.length;
            if (matchesAnyPrefix(rule.pattern, parts, limit)) {
                ignored = !rule.negated;
            }
        }
        return ignored;
    }

    /**
     * Tests the pattern against the path and each of its directory prefixes,
     * so ignoring a directory also ignores everything beneath it.
     */
    private static boolean matchesAnyPrefix(Pattern pattern, String[] parts, int limit) {
        StringBuilder candidate = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                candidate.append('/');
            }
            candidate.append(parts[i]);
            if (pattern.matcher(candidate).matches()) {
                return true;
            }
        }
        return false;
    }

    private List<Rule> loadRules(File projectRoot) {
        File ignoreFile = new File(projectRoot, FILE_NAME);
        if (!ignoreFile.isFile()) {
            return List.of();
        }
        String key = projectRoot.getAbsolutePath();
        long modified = ignoreFile.lastModified();
        long length = ignoreFile.length();
        CachedRules cached = cache.get(key);
        if (cached != null && cached.modified() == modified && cached.length() == length) {
            return cached.rules();
        }
        List<Rule> rules = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(ignoreFile.toPath(), StandardCharsets.UTF_8)) {
                Rule rule = parse(line);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        } catch (IOException e) {
            LOG.log(java.util.logging.Level.FINE, "Could not read .bbignore; listing all files", e);
            return List.of();
        }
        List<Rule> immutable = List.copyOf(rules);
        cache.put(key, new CachedRules(modified, length, immutable));
        return immutable;
    }

    /** Parses one .gitignore-style line, returning {@code null} for blanks/comments. */
    private static Rule parse(String rawLine) {
        String line = stripTrailingSpaces(rawLine);
        if (line.isEmpty()) {
            return null;
        }
        boolean negated = false;
        if (line.startsWith("\\!")) {
            line = line.substring(1);
        } else if (line.startsWith("!")) {
            negated = true;
            line = line.substring(1);
        }
        if (line.startsWith("\\#")) {
            line = line.substring(1);
        } else if (line.startsWith("#")) {
            return null;
        }
        if (line.isEmpty()) {
            return null;
        }

        boolean dirOnly = line.endsWith("/");
        if (dirOnly) {
            line = line.substring(0, line.length() - 1);
        }
        boolean anchored;
        if (line.startsWith("**/")) {
            line = line.substring(3);
            anchored = false;
        } else {
            anchored = line.startsWith("/") || line.contains("/");
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
        }
        if (line.isEmpty()) {
            return null;
        }
        try {
            return new Rule(compile(line, anchored), negated, dirOnly);
        } catch (PatternSyntaxException e) {
            // A malformed class (e.g. "[]" or "[z-a]") must not abort the scan;
            // gitignore implementations treat such a line as non-matching.
            LOG.log(java.util.logging.Level.FINE, "Skipping invalid .bbignore pattern: {0}", rawLine);
            return null;
        }
    }

    /** Converts a gitignore glob to a regex matched against a slash-separated path. */
    private static Pattern compile(String glob, boolean anchored) {
        StringBuilder regex = new StringBuilder("^");
        if (!anchored) {
            regex.append("(?:.*/)?");
        }
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        if (i + 2 < glob.length() && glob.charAt(i + 2) == '/') {
                            regex.append("(?:.*/)?");
                            i += 2;
                        } else {
                            regex.append(".*");
                            i++;
                        }
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                case '[' -> {
                    int close = glob.indexOf(']', i + 1);
                    if (close < 0) {
                        regex.append("\\[");
                    } else {
                        String content = glob.substring(i + 1, close);
                        if (content.startsWith("!")) {
                            content = "^" + content.substring(1);
                        }
                        regex.append('[').append(content).append(']');
                        i = close;
                    }
                }
                case '\\' -> {
                    if (i + 1 < glob.length()) {
                        regex.append(Pattern.quote(String.valueOf(glob.charAt(i + 1))));
                        i++;
                    } else {
                        regex.append("\\\\");
                    }
                }
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    /** Removes trailing whitespace unless it is escaped with a backslash. */
    private static String stripTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            if (end >= 2 && line.charAt(end - 2) == '\\') {
                break;
            }
            end--;
        }
        return line.substring(0, end);
    }

    private static String relativize(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!filePath.startsWith(prefix)) {
            return null;
        }
        String rel = filePath.substring(prefix.length());
        return rel.replace(File.separatorChar, '/');
    }
}
