package github.anandb.netbeans.support;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.BiConsumer;

/**
 * Opens a URL in the browser, or copies to clipboard as fallback.
 * Pure utility with no Swing or application-layer dependencies.
 */
public final class BrowserUtils {

    private BrowserUtils() {}

    /**
     * Attempts to open the URL in the system browser.
     * Falls back to copying the URL to the clipboard.
     *
     * @param url         the URL to open
     * @param onFallback  callback invoked on fallback with (url, statusKey). May be null.
     * @param statusKey   optional status message key for the fallback path
     */
    public static void openOrCopyUrl(String url, String statusKey, BiConsumer<String, String> onFallback) {
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(url));
                return;
            } catch (IOException | URISyntaxException ex) {
                // fall through to clipboard
            }
        }
        // Fallback: copy URL to clipboard
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(url), null);
            if (onFallback != null) {
                onFallback.accept(url, statusKey);
            }
        } catch (Exception ex) {
            // Clipboard access may fail in restricted environments — silently ignore
        }
    }
}
