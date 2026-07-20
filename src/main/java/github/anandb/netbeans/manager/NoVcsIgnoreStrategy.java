package github.anandb.netbeans.manager;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import github.anandb.netbeans.contract.VcsIgnoreStrategy;

/**
 * Fallback strategy when no VCS is detected. Walks the filesystem and
 * excludes common junk patterns (editor swap files, OS metadata, build
 * output directories).
 */
public class NoVcsIgnoreStrategy implements VcsIgnoreStrategy {

    /** Matches vim swap/backup files: *.swp, *.swo, *~ */
    private static final Pattern SWAP_PATTERN = Pattern.compile(".*\\.(?:swp|swo)$|.*~$");

    /** Directories to skip during filesystem walk. */
    private static final Set<String> SKIP_DIRS = Set.of(
            "target", "build", "node_modules", ".git", ".svn", ".hg",
            "bin", "out", ".idea", ".vscode", "__pycache__",
            ".opencode", ".mvn", ".settings"
    );

    @Override
    public boolean isAvailable(File projectRoot) {
        return true; // always available as fallback
    }

    @Override
    public Set<String> listNonIgnoredFiles(File projectRoot) {
        Set<String> result = new LinkedHashSet<>();
        walk(projectRoot, projectRoot, result);
        return result;
    }

    @Override
    public boolean isIgnored(File projectRoot, File file) {
        String name = file.getName();
        // Hidden files
        if (name.startsWith(".")) return true;
        // Swap files
        if (SWAP_PATTERN.matcher(name).matches()) return true;
        // Skip known junk directories
        File parent = file.getParentFile();
        while (parent != null && !parent.equals(projectRoot)) {
            if (SKIP_DIRS.contains(parent.getName())) return true;
            parent = parent.getParentFile();
        }
        return false;
    }

    private void walk(File root, File current, Set<String> result) {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                if (name.startsWith(".") || SKIP_DIRS.contains(name)) continue;
                walk(root, child, result);
            } else {
                if (SWAP_PATTERN.matcher(name).matches()) continue;
                String rel = relativize(root, child);
                if (rel != null) {
                    result.add(rel);
                }
            }
        }
    }

    private static String relativize(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) return null;
        String rel = filePath.substring(rootPath.length());
        if (rel.startsWith(File.separator)) {
            rel = rel.substring(1);
        }
        return rel.replace(File.separatorChar, '/');
    }
}
