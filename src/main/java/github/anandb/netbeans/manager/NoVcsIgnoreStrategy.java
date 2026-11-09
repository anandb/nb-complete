package github.anandb.netbeans.manager;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import github.anandb.netbeans.contract.VcsIgnoreStrategy;
import github.anandb.netbeans.pattern.SwapFilePattern;
import github.anandb.netbeans.support.ProjectPaths;

/**
 * Fallback strategy when no VCS is detected. Walks the filesystem and
 * excludes common junk patterns (editor swap files, OS metadata, build
 * output directories).
 */
public class NoVcsIgnoreStrategy implements VcsIgnoreStrategy {

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
        if (SwapFilePattern.matches(name)) return true;
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
                if (SwapFilePattern.matches(name)) continue;
                String rel = ProjectPaths.relativeTo(root, child);
                if (rel != null) {
                    result.add(rel);
                }
            }
        }
    }
}
