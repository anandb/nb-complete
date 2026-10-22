package github.anandb.netbeans.support;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;

/**
 * Opens the Twine user guide bundled in the module jar (generated at build time by
 * {@code docs/stitch.py}) in the system browser. The HTML is self-contained (base64
 * images, inlined story engine), so a plain local file renders fully offline. When the
 * resource is missing (stitch skipped at build time) or the temp copy fails, the
 * caller-supplied remote-URL fallback runs instead — always on the EDT, because the
 * existing fallback path updates a status label.
 */
public final class UserGuideOpener {
    private static final Logger LOG = Logger.from(UserGuideOpener.class);

    private static final String GUIDE_RESOURCE = "/github/anandb/netbeans/docs/UserGuide.html";

    private UserGuideOpener() {}

    /**
     * Opens the bundled guide in the system browser, or invokes {@code urlFallback} when the
     * bundled copy is unavailable. The copy runs on a background thread — never call this
     * expecting synchronous completion; both outcomes notify the user via the browser or
     * the fallback, not a return value.
     *
     * @param urlFallback remote-guide fallback, invoked on the EDT when the bundled
     *                    guide cannot be served
     */
    public static void open(Runnable urlFallback) {
        CompletableFuture.runAsync(() -> {
            try (InputStream in = UserGuideOpener.class.getResourceAsStream(GUIDE_RESOURCE)) {
                if (in == null) {
                    LOG.fine("Bundled user guide not found at {0}; falling back to the remote URL", GUIDE_RESOURCE);
                    runFallback(urlFallback);
                    return;
                }
                Path dir = Path.of(System.getProperty("java.io.tmpdir"), "beanbot");
                Files.createDirectories(dir);
                Path file = dir.resolve("UserGuide.html");
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(file.toUri());
                } else {
                    runFallback(urlFallback);
                }
            } catch (IOException ex) {
                LOG.warn("Failed to serve the bundled user guide: {0}", ex.getMessage());
                runFallback(urlFallback);
            }
        });
    }

    private static void runFallback(Runnable urlFallback) {
        SwingUtilities.invokeLater(urlFallback);
    }
}
