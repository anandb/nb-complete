package github.anandb.netbeans.contract;

import github.anandb.netbeans.model.AttachedFile;

public interface PasteCallback {

    boolean canAddAttachment();

    void onAttachmentAdded(AttachedFile file);

    void onError(String message);

    void onAttachmentLimitReached();

}
