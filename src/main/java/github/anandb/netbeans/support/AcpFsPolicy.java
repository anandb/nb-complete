package github.anandb.netbeans.support;

import java.util.Locale;

/**
 * Policy for the ACP file-system tools ({@code fs/read_text_file} and
 * {@code fs/write_text_file}). Reads are either confined to the session's
 * project directory or, in {@link #READ_ANY_WRITE_PROJECT}, allowed anywhere;
 * writes are confined to the project or denied. Credential files and VCS/build
 * metadata stay refused regardless of the mode.
 *
 * <p>Stored as a plain string in preferences so a downgrade to a build without
 * this setting falls back to the default rather than failing to parse.</p>
 */
public enum AcpFsPolicy {

    /** Read and write files inside the session's project directory. */
    ALLOW("allow"),

    /** Read any file; writes stay confined to the session's project directory. */
    READ_ANY_WRITE_PROJECT("read_any_write_project"),

    /** Read files inside the session's project directory; writes are denied. */
    READ_ONLY("read_only");

    /** Value that makes the policy the default: access works as before, confined to the project. */
    public static final AcpFsPolicy DEFAULT = ALLOW;

    private final String storedValue;

    AcpFsPolicy(String storedValue) {
        this.storedValue = storedValue;
    }

    /** Returns the value written to preferences. */
    public String storedValue() {
        return storedValue;
    }

    /** Parses a stored value, falling back to {@link #DEFAULT} for null or unknown text. */
    public static AcpFsPolicy fromStoredValue(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            for (AcpFsPolicy policy : values()) {
                if (policy.storedValue.equals(normalized)) {
                    return policy;
                }
            }
        }
        return DEFAULT;
    }
}
