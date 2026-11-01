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
 * images, inlined story engine) and packaged GZIPPED ({@code UserGuide.html.gz}) so the
 * IDE's HTML validator never scans the megabyte-sized artifact; opening inflates it to a
 * temp file first. When the resource is missing (stitch skipped at build time) or the
 * temp extract fails, the caller-supplied fallback runs instead — always on the EDT,
 * because the fallback path updates a status label or opens a custom URL.
 */
public final class UserGuideOpener {
    private static final Logger LOG = Logger.from(UserGuideOpener.class);

    private static final String GUIDE_RESOURCE = "/github/anandb/netbeans/docs/UserGuide.html.gz";

    private UserGuideOpener() {}

    /**
     * Opens the bundled guide in the system browser, or invokes {@code unavailableFallback}
     * when the bundled copy cannot be served. The copy runs on a background thread — never
     * call this expecting synchronous completion; both outcomes notify the user via the
     * browser or the fallback, not a return value.
     *
     * @param unavailableFallback invoked on the EDT when the bundled guide cannot be served
     *                            (a custom URL when configured, otherwise a status message)
     */
    public static void open(Runnable unavailableFallback) {
        CompletableFuture.runAsync(() -> {
            try (InputStream in = UserGuideOpener.class.getResourceAsStream(GUIDE_RESOURCE)) {
                if (in == null) {
                    LOG.fine("Bundled user guide not found at {0}; running the fallback", GUIDE_RESOURCE);
                    runFallback(unavailableFallback);
                    return;
                }
                Path dir = Path.of(System.getProperty("java.io.tmpdir"), "beanbot");
                Files.createDirectories(dir);
                Path file = dir.resolve("UserGuide.html");
                // the bundled copy is gzip; inflate through it so the temp file is plain HTML
                try (java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(in)) {
                    Files.copy(gzip, file, StandardCopyOption.REPLACE_EXISTING);
                }
                if (Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(file.toUri());
                } else {
                    runFallback(unavailableFallback);
                }
            } catch (IOException ex) {
                LOG.warn("Failed to serve the bundled user guide: {0}", ex.getMessage());
                runFallback(unavailableFallback);
            }
        });
    }

    private static void runFallback(Runnable unavailableFallback) {
        SwingUtilities.invokeLater(unavailableFallback);
    }
}
