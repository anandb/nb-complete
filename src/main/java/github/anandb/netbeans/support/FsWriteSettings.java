package github.anandb.netbeans.support;

/**
 * Reads the {@link PreferenceKeys#FS_WRITE_ENABLED_PROP} system property once at
 * class-load time and exposes it as a boolean. <b>Enabled by default</b>: only
 * {@code -Dbeanbot.fs.write.enabled=false} turns it off, in which case the plugin
 * neither advertises the {@code fs/writeTextFile} capability nor performs writes.
 * Reading is done once (system properties are set before JVM startup and do not
 * change at runtime) to keep the capability advertisement and per-request guard
 * on a cheap constant read.
 *
 * <p>This flag gates writing, not <em>what</em> may be written: {@code
 * fs/writeTextFile} accepts any absolute path, in or outside the open projects
 * (see {@code AGENTS.md}, "ACP fs Tools Are Not Project-Confined"). With the flag
 * on by default, the per-write {@code session/request_permission} prompt raised by
 * the harness is the only path-level gate — pass {@code
 * -Dbeanbot.fs.write.enabled=false} to deny writes at the IDE level.</p>
 */
public final class FsWriteSettings {

    private FsWriteSettings() {}

    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty(PreferenceKeys.FS_WRITE_ENABLED_PROP, "true"));

    /** @return {@code true} iff {@code fs/write*} tools are enabled. */
    public static boolean isEnabled() {
        return ENABLED;
    }
}