package github.anandb.netbeans.support;

/**
 * Whether the running harness implements Grok Build's {@code x.ai/interject} extension.
 * Interject is not advertised in the {@code initialize} handshake, so
 * {@code ServerProcessLifecycle} probes it once per process: a bogus-session interject
 * answers {@code -32601} when the build predates the method and {@code invalid_params}
 * when it exists. Until the probe answers, the route is treated as unavailable so
 * mid-turn input queues rather than failing.
 */
public final class InterjectSupport {
    private static volatile Boolean supported;

    private InterjectSupport() {
    }

    /** Clears the cached answer; called when a new server process starts or stops. */
    public static void reset() {
        supported = null;
    }

    public static void set(boolean value) {
        supported = Boolean.valueOf(value);
    }

    /** True only once the probe confirmed the method exists; {@code null} and false are both "queue". */
    public static boolean isSupported() {
        return Boolean.TRUE.equals(supported);
    }
}
