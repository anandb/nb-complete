package github.anandb.netbeans.support;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Name-based guard for files that commonly hold credentials. The agent must not
 * read or write them: their content ends up in the model provider's prompt, and
 * writing one can plant credentials for a later tool to use.
 *
 * <p>This is a heuristic, not a sandbox: {@code run_command} can still print a
 * secret, and a project may use a non-standard name. Extend
 * {@link #SECRET_DIRECTORIES}, {@link #SECRET_NAMES} and {@link #SECRET_SUFFIXES}
 * when a new convention appears rather than special-casing call sites.</p>
 */
public final class SecretPaths {

    private static final Set<String> SECRET_DIRECTORIES = Set.of(
            ".ssh", ".aws", ".gnupg", ".kube", ".azure", ".docker");

    private static final Set<String> SECRET_NAMES = Set.of(
            ".env", ".netrc", "_netrc", ".npmrc", ".pypirc", ".git-credentials", ".htpasswd",
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", "kubeconfig", "credentials", "credentials.json");

    private static final Set<String> SECRET_SUFFIXES = Set.of(
            ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".ppk", ".secret", ".secrets");

    /** Committed templates that document the variables without holding values. */
    private static final Set<String> ALLOWED_ENV_TEMPLATES = Set.of(
            ".env.example", ".env.sample", ".env.template", ".env.dist");

    private SecretPaths() {
    }

    /** True when {@code path} looks like a credentials file. */
    public static boolean isSecret(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        File file;
        try {
            file = new File(path).getCanonicalFile();
        } catch (IOException e) {
            file = new File(path).getAbsoluteFile();
        }
        for (File part = file.getParentFile(); part != null; part = part.getParentFile()) {
            String name = part.getName();
            if (name != null && SECRET_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        String name = file.getName() == null ? "" : file.getName().toLowerCase(Locale.ROOT);
        if (ALLOWED_ENV_TEMPLATES.contains(name)) {
            return false;
        }
        if (SECRET_NAMES.contains(name)) {
            return true;
        }
        if (name.startsWith(".env.")) {
            return true;
        }
        for (String suffix : SECRET_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
