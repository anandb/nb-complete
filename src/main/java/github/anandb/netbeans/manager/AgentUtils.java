package github.anandb.netbeans.manager;

import java.io.Closeable;
import java.io.IOException;

import github.anandb.netbeans.support.Logger;

public final class AgentUtils {
    private static final Logger LOG = new Logger(AgentUtils.class);

    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            LOG.fine("Failed to close", e);
        }
    }

    public static String getVersion() {
        try {
            org.openide.modules.ModuleInfo m = org.openide.modules.Modules.getDefault()
                    .findCodeNameBase("github.anandb.beanbot");
            if (m != null && m.getSpecificationVersion() != null) {
                return m.getSpecificationVersion().toString();
            }
        } catch (Exception e) {
            LOG.fine("Failed to get module version", e);
        }
        try {
            java.io.InputStream is = AgentUtils.class.getResourceAsStream("/META-INF/MANIFEST.MF");
            if (is != null) {
                java.util.Properties p = new java.util.Properties();
                p.load(is);
                String v = p.getProperty("OpenIDE-Module-Specification-Version");
                if (v != null) {
                    return v;
                }
            }
        } catch (Exception e) {
            LOG.fine("Failed to get version from manifest", e);
        }

        return "0.0.0";
    }

}
