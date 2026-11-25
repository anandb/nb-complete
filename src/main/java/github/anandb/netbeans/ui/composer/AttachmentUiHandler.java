package github.anandb.netbeans.ui.composer;

import java.awt.Component;
import javax.swing.SwingUtilities;
import java.util.List;
import javax.swing.JButton;
import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.ui.session.StatusController;

/**
 * Main-composer adapter over {@link AttachmentButton}: forwards attachment
 * outcomes into {@link StatusController} messages and keeps the paperclip's
 * enabled state in sync with composer input availability.
 */
// DSL-LEAF: thin bridge — all button/menu/chooser/paste mechanics live in
// AttachmentButton; this class only maps outcomes to status bar keys.
public class AttachmentUiHandler {

    private final AttachmentButton attachmentButton;
    private final StatusController statusController;

    public AttachmentUiHandler(
            AttachmentManager attachmentManager,
            StatusController statusController,
            PlaceholderTextArea inputArea,
            Component parentWindow) {
        this.statusController = statusController;
        this.attachmentButton = new AttachmentButton(attachmentManager, pasteBridge(), parentWindow);
        // The paperclip follows the input text area's enabled state (disabled
        // while no session is active or a message is in flight).
        statusController.addInputEnabledListener(enabled -> getButton().setEnabled(enabled));
        attachmentButton.setupPasteOn(inputArea);
    }

    /** Maps AttachmentButton outcomes onto the composer's status bar + tooltip. */
    private PasteCallback pasteBridge() {
        return new PasteCallback() {
            @Override
            public boolean canAddAttachment() {
                return true; // AttachmentButton owns the cap check before notifying
            }

            @Override
            public void onAttachmentAdded(AttachedFile file) {
                SwingUtilities.invokeLater(() -> {
                    updateTooltip();
                    statusController.setStatus("STATUS_Attached", file.filename());
                    statusController.scheduleReset();
                });
            }

            @Override
            public void onAttachmentTooLarge() {
                SwingUtilities.invokeLater(() -> {
                    statusController.setStatus("STATUS_FileTooLarge");
                    statusController.scheduleReset();
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusController.setStatusText(message);
                    statusController.scheduleReset();
                });
            }

            @Override
            public void onAttachmentLimitReached() {
                SwingUtilities.invokeLater(() -> {
                    statusController.setStatus("STATUS_MaxFiles", AttachmentManager.MAX_ATTACHMENTS);
                    statusController.scheduleReset();
                });
            }

            @Override
            public void onReferenceLimitReached() {
                SwingUtilities.invokeLater(() -> {
                    statusController.setStatus("STATUS_MaxReferences", AttachmentManager.MAX_REFERENCES);
                    statusController.scheduleReset();
                });
            }

            @Override
            public void onFilesAttached(List<AttachedFile> added) {
                SwingUtilities.invokeLater(() -> {
                    String lastName = added.get(added.size() - 1).filename();
                    if (added.size() == 1) {
                        statusController.setStatus("STATUS_Attached", lastName);
                    } else {
                        statusController.setStatus("STATUS_AttachedMore", lastName, added.size() - 1);
                    }
                    statusController.scheduleReset();
                });
            }
        };
    }

    public JButton getButton() {
        return attachmentButton.getButton();
    }

    /** Updates the paperclip button tooltip and icon based on attachment count. */
    public void updateTooltip() {
        attachmentButton.updateTooltip();
    }

    /** Programmatic single-file attach; outcome status flows through the bridge. */
    public boolean attachFile(AttachedFile file) {
        return attachmentButton.attachFile(file);
    }
}
