package github.anandb.netbeans.support;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Paths the agent must never modify, even inside an open project: VCS metadata
 * and Maven wrapper configuration execute code on the user's next git/hg/build
 * action, so a write there is code execution by proxy.
 *
 * <p>Applies to writes only — reading these paths stays allowed (git/hg tools
 * read repository state themselves).</p>
 */
public final class ProtectedPaths {

    private static final Set<String> PROTECTED_DIRECTORIES = Set.of(".git", ".hg", ".mvn");

    private ProtectedPaths() {
    }

    /** True when {@code path} is inside (or is) a protected VCS/build directory. */
    public static boolean isProtected(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        File file;
        try {
            file = new File(path).getCanonicalFile();
        } catch (IOException e) {
            file = new File(path).getAbsoluteFile();
        }
        for (File part = file; part != null; part = part.getParentFile()) {
            String name = part.getName();
            if (name != null && PROTECTED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
