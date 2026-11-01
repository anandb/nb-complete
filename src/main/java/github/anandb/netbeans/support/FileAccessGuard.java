package github.anandb.netbeans.support;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

/**
 * Single enforcement point for BeanBot's file-access policy, shared by the ACP
 * fs tools ({@code fs/read_text_file}, {@code fs/write_text_file}) and the
 * internal MCP file tools.
 *
 * <p>Credential files are refused for reads and writes, VCS/build metadata is
 * refused for writes, {@link AcpFsPolicy#READ_ONLY} denies writes, and
 * {@link AcpFsPolicy#READ_ANY_WRITE_PROJECT} lifts the containment check for
 * reads only. Every other access must stay inside the caller's project root.
 * Each caller only supplies its own containment test and its own message.</p>
 */
public final class FileAccessGuard {

    /** Why an access is denied; {@link #ALLOWED} means it may proceed. */
    public enum Denial {
        ALLOWED, SECRET_FILE, PROTECTED_PATH, WRITE_DENIED, OUTSIDE_PROJECT
    }

    private FileAccessGuard() {
    }

    /**
     * Applies the policy to one path access.
     *
     * @param policy effective policy; {@code null} means {@link AcpFsPolicy#DEFAULT}
     * @param path file being accessed
     * @param write {@code true} for a write, {@code false} for a read
     * @param inProject containment test for the caller's project root; only
     *        consulted for accesses the policy confines
     * @return the denial reason, or {@link Denial#ALLOWED}
     */
    public static Denial check(AcpFsPolicy policy, String path, boolean write, Predicate<String> inProject) {
        if (SecretPaths.isSecret(path)) {
            return Denial.SECRET_FILE;
        }
        if (write && ProtectedPaths.isProtected(path)) {
            return Denial.PROTECTED_PATH;
        }
        AcpFsPolicy effective = policy != null ? policy : AcpFsPolicy.DEFAULT;
        if (write && effective == AcpFsPolicy.READ_ONLY) {
            return Denial.WRITE_DENIED;
        }
        if (!write && effective == AcpFsPolicy.READ_ANY_WRITE_PROJECT) {
            return Denial.ALLOWED;
        }
        return inProject.test(path) ? Denial.ALLOWED : Denial.OUTSIDE_PROJECT;
    }

    /** True when {@code path} is the project directory or a file below it, after
     *  resolving symlinks. An unresolvable path is treated as outside. */
    public static boolean isInsideProject(String projectDirectory, String path) {
        try {
            File root = new File(projectDirectory).getCanonicalFile();
            File target = new File(path).getCanonicalFile();
            return target.equals(root)
                    || target.getPath().startsWith(root.getPath() + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}
