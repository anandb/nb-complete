package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.AttachedFile;

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
}
