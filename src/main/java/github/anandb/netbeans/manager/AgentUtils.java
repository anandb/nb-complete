package github.anandb.netbeans.manager;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.openide.modules.ModuleInfo;
import org.openide.modules.Modules;

import github.anandb.netbeans.support.Logger;

public final class AgentUtils {
    private static final Logger LOG = new Logger(AgentUtils.class);

    private AgentUtils() {}

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
            ModuleInfo m = Modules.getDefault()
                    .findCodeNameBase("io.github.anandb.beanbot");
            if (m != null && m.getSpecificationVersion() != null) {
                return m.getSpecificationVersion().toString();
            }
        } catch (Exception e) {
            LOG.fine("Failed to get module version", e);
        }
        
        try (InputStream is = AgentUtils.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (is != null) {
                Properties p = new Properties();
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
