package github.anandb.netbeans.manager;

import github.anandb.netbeans.support.Logger;

/**
 * The harness this process is talking to, as reported by the ACP server.
 *
 * <p>Two writers, in order: {@code ServerProcessLifecycle} seeds it from the
 * configured binary before the server is contacted, so the debug wire log can be
 * named from the first write; the {@code initialize} response then replaces that
 * with the agent's own reported name and version.</p>
 *
 * <p>Session state, so it lives in {@code manager/} rather than {@code support/}.
 * It deliberately holds no model information: a model belongs to a session, and a
 * process-global model would let one session's choice be stamped onto another
 * session's captured usage.</p>
 */
public final class AcpSessionInfo {
    private static final AcpSessionInfo INSTANCE = new AcpSessionInfo();
    private static final Logger LOG = Logger.from(AcpSessionInfo.class);

    private volatile String harnessName;
    private volatile String harnessVersion;

    private AcpSessionInfo() {}

    public static AcpSessionInfo getInstance() { return INSTANCE; }

    /** Store harness info from initialize response. */
    public void setHarnessInfo(String name, String version) {
        this.harnessName = name;
        this.harnessVersion = version;
        LOG.fine("Harness info set: {0} v{1}", new Object[]{name, version});
    }

    /** The reported harness version, or null before the initialize response. */
    public String getHarnessVersion() { return harnessVersion; }

    /** Short harness identifier for filenames (e.g. "pi-acp"). */
    public String getHarnessId() {
        if (harnessName == null) return "unknown";
        // Strip @scope prefix if present (e.g. "@geohar/pi-acp" -> "pi-acp")
        int slash = harnessName.lastIndexOf('/');
        String raw = slash >= 0 ? harnessName.substring(slash + 1) : harnessName;
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}