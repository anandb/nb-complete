package github.anandb.netbeans.ui.composer;

import github.anandb.netbeans.model.AttachedFile;
import java.util.List;

/**
 * Callback interface for paste/attachment operations. Implementations handle
 * the lifecycle of adding file attachments to the active chat session.
 */
public interface PasteCallback {

    /** Returns true if the session can accept another attachment. */
    boolean canAddAttachment();

    /** Called when an attachment has been successfully added. */
    void onAttachmentAdded(AttachedFile file);

    /** Called when the attachment operation fails with the given error message. */
    void onError(String message);

    /** Called when the session's attachment limit has been reached. */
    void onAttachmentLimitReached();

    /**
     * Called after one or more files were added programmatically or via the
     * file chooser (paste uses {@link #onAttachmentAdded} instead). The files
     * are already in the manager; implementations only react (status, tooltip).
     */
    default void onFilesAttached(List<AttachedFile> added) {
    }

    /** Called when a paste-provided file was rejected by the size cap. */
    default void onAttachmentTooLarge() {
    }
}
