package github.anandb.netbeans.support;

/**
 * Reads the {@link PreferenceKeys#FS_WRITE_ENABLED_PROP} system property once at
 * class-load time and exposes it as a boolean. Disabled by default, so the plugin
 * does not advertise the {@code fs/writeTextFile} capability nor perform writes.
 * Reading is done once (system properties are set before JVM startup and do not
 * change at runtime) to keep the capability advertisement and per-request guard
 * on a cheap constant read.
 */
public final class FsWriteSettings {

    private FsWriteSettings() {}

    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty(PreferenceKeys.FS_WRITE_ENABLED_PROP, "false"));

    /** @return {@code true} iff {@code fs/write*} tools are enabled. */
    public static boolean isEnabled() {
        return ENABLED;
    }
}