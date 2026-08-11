package github.anandb.netbeans.support;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openide.modules.ModuleInfo;
import org.openide.modules.Modules;

public final class AgentUtils {
    private static final Logger LOG = Logger.from(AgentUtils.class);

    private AgentUtils() {}

    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to close", e);
        }
    }

    /**
     * Reads a bundled classpath resource (relative to this class) as a UTF-8
     * string. Returns {@code null} if the resource is absent.
     */
    public static String readResource(String name) {
        try (InputStream in = AgentUtils.class.getResourceAsStream(name)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to read resource {0}: {1}",
                    new Object[]{name, ExceptionUtils.getMessage(e)});
        }
        return null;
    }

    public static String getVersion() {
        try {
            ModuleInfo m = Modules.getDefault()
                    .findCodeNameBase("io.github.anandb.beanbot");
            if (m != null && m.getSpecificationVersion() != null) {
                return m.getSpecificationVersion().toString();
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to get module version", e);
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
            LOG.log(Level.WARNING, "Failed to get version from manifest", e);
        }

        return "0.0.0";
    }

}
