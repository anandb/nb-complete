package github.anandb.netbeans.support;

/**
 * Singleton storing ACP session metadata (harness info + model name).
 * Populated from initialize response and set_model calls.
 */
public final class AcpSessionInfo {
    private static final AcpSessionInfo INSTANCE = new AcpSessionInfo();
    private static final Logger LOG = Logger.from(AcpSessionInfo.class);

    private volatile String harnessName;
    private volatile String harnessVersion;
    private volatile String modelName;

    private AcpSessionInfo() {}

    public static AcpSessionInfo getInstance() { return INSTANCE; }

    /** Store harness info from initialize response. */
    public void setHarnessInfo(String name, String version) {
        this.harnessName = name;
        this.harnessVersion = version;
        LOG.fine("Harness info set: {0} v{1}", new Object[]{name, version});
    }

    /** Store model name from set_model call. */
    public void setModelName(String modelName) {
        this.modelName = modelName;
        LOG.fine("Model name set: {0}", modelName);
    }

    public String getHarnessName() { return harnessName; }
    public String getHarnessVersion() { return harnessVersion; }
    public String getModelName() { return modelName; }

    /** Short harness identifier for filenames (e.g. "pi-acp"). */
    public String getHarnessId() {
        if (harnessName == null) return "unknown";
        // Strip @scope prefix if present (e.g. "@geohar/pi-acp" -> "pi-acp")
        int slash = harnessName.lastIndexOf('/');
        String raw = slash >= 0 ? harnessName.substring(slash + 1) : harnessName;
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /** Short model identifier for filenames (e.g. "claude-sonnet-4-20250514"). */
    public String getModelId() {
        if (modelName == null) return "unknown";
        return modelName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
