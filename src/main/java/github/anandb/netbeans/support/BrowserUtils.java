package github.anandb.netbeans.support;

import org.apache.commons.lang3.exception.ExceptionUtils;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BiConsumer;

import javax.swing.SwingUtilities;

import org.openide.util.RequestProcessor;

/**
 * Opens a URL in the browser, or copies to clipboard as fallback.
 * All I/O runs on a background thread; fallback callbacks are delivered on the EDT.
 */
public final class BrowserUtils {
    private static final Logger LOG = Logger.from(BrowserUtils.class);

    /** Serialises browser/clipboard work so a burst of clicks cannot launch several browsers. */
    private static final RequestProcessor BROWSER_IO = new RequestProcessor("beanbot-browser-io", 1);

    private BrowserUtils() {}

    /**
     * Attempts to open the URL in the system browser without blocking the caller. On failure the
     * URL is copied to the clipboard instead.
     *
     * @param url         the URL to open
     * @param onFallback  callback invoked on the EDT with (url, statusKey) on fallback. May be null.
     * @param statusKey   optional status message key for the fallback path
     */
    public static void openOrCopyUrl(String url, String statusKey, BiConsumer<String, String> onFallback) {
        BROWSER_IO.post(() -> openOrCopyUrlOnWorker(url, statusKey, onFallback));
    }

    private static void openOrCopyUrlOnWorker(String url, String statusKey, BiConsumer<String, String> onFallback) {
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
                return;
            } catch (IOException | URISyntaxException ex) {
                LOG.fine("Browser open failed, falling back to clipboard: {0}", ExceptionUtils.getMessage(ex));
                // fall through to clipboard
            }
        }
        // Fallback: copy URL to clipboard
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(url), null);
            if (onFallback != null) {
                SwingUtilities.invokeLater(() -> onFallback.accept(url, statusKey));
            }
        } catch (Exception ex) {
            LOG.warn("Clipboard access failed: {0}", ExceptionUtils.getMessage(ex));
        }
    }
}
