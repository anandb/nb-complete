package github.anandb.netbeans.manager;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AgentUtils {
    private static final Logger LOG = Logger.getLogger(AgentUtils.class.getName());

    public static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to close", e);
        }
    }
}
