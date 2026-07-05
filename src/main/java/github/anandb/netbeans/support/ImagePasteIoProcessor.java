package github.anandb.netbeans.support;

import org.openide.util.RequestProcessor;

/**
 * Owns the shared RequestProcessor for image paste background I/O.
 * Extracted from ui/ImagePasteTransferHandler so that project/ACPShutdown
 * can shut down the thread pool without importing from the ui/ layer.
 */
public final class ImagePasteIoProcessor {

    private static final RequestProcessor IO_RP = new RequestProcessor("ImagePaste-IO", 2, true);

    private ImagePasteIoProcessor() {
        // utility class
    }

    /** Returns the shared RequestProcessor for image paste I/O tasks. */
    public static RequestProcessor get() {
        return IO_RP;
    }

    /** Shuts down the background I/O thread pool. Called at IDE shutdown. */
    public static void shutdown() {
        IO_RP.stop();
    }
}
