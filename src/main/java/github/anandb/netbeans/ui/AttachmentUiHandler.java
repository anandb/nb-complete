package github.anandb.netbeans.ui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.support.PluginSettings;

/**
 * Handles all attachment-related UI: paperclip button, file selection popup,
 * attachment tooltip updates, and image paste handling.
 */
// DSL-LEAF: not a controller — builds the paperclip toolbar button + popup menu
// for file attachments. Migration target: AttachmentButtonSpec; the JFileChooser
// + AttachmentManager state wiring stays imperative.
public class AttachmentUiHandler {

    private final AttachmentManager attachmentManager;
    private final StatusController statusController;
    private final Component parentWindow;
    private final JButton paperclipBtn;

    public AttachmentUiHandler(
            AttachmentManager attachmentManager,
            StatusController statusController,
            PlaceholderTextArea inputArea,
            Component parentWindow) {
        this.attachmentManager = attachmentManager;
        this.statusController = statusController;
        this.parentWindow = parentWindow;

        this.paperclipBtn = UIUtils.createToolbarButton("paperclip.svg",
                NbBundle.getMessage(AssistantTopComponent.class, "HINT_AttachFiles"), null);
        this.paperclipBtn.addActionListener(e -> showPaperclipMenu(e));

        setupImagePasteHandler(inputArea);
    }

    public JButton getButton() {
        return paperclipBtn;
    }

    /** Updates the paperclip button tooltip and icon based on attachment count. */
    public void updateTooltip() {
        if (attachmentManager.getAttachments().isEmpty()) {
            paperclipBtn.setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_AttachFiles"));
            paperclipBtn.setIcon(ThemeManager.getIcon("paperclip.svg", PluginSettings.getToolbarIconSize()));
        } else {
            paperclipBtn.setToolTipText(NbBundle.getMessage(AssistantTopComponent.class, "HINT_FilesAttached", attachmentManager.size()));
            paperclipBtn.setIcon(ThemeManager.getIcon("paperclip-dot.svg", PluginSettings.getToolbarIconSize()));
        }
    }

    private void setupImagePasteHandler(PlaceholderTextArea inputArea) {
        ImagePasteTransferHandler.PasteCallback callback = new ImagePasteTransferHandler.PasteCallback() {
            @Override
            public boolean canAddAttachment() {
                return attachmentManager.canAdd();
            }

            @Override
            public void onAttachmentAdded(AttachedFile file) {
                SwingUtilities.invokeLater(() -> {
                    if (!attachmentManager.add(file)) {
                        statusController.setStatus("STATUS_FileTooLarge");
                        statusController.scheduleReset();
                        return;
                    }
                    updateTooltip();
                    statusController.setStatus("STATUS_Attached", file.filename());
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
        };

        ImagePasteTransferHandler handler = new ImagePasteTransferHandler(callback);
        inputArea.setTransferHandler(handler);
    }

    private void showPaperclipMenu(ActionEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem addItem = new JMenuItem(NbBundle.getMessage(AssistantTopComponent.class, "BTN_SelectFile"));
        addItem.addActionListener(ev -> selectFiles());
        menu.add(addItem);
        List<AttachedFile> currentFiles = attachmentManager.getAttachments();
        if (!currentFiles.isEmpty()) {
            menu.addSeparator();
            for (AttachedFile af : currentFiles) {
                JCheckBoxMenuItem cb = new JCheckBoxMenuItem(af.filename(), true);
                cb.addActionListener(ev -> {
                    if (!cb.isSelected()) {
                        attachmentManager.remove(af);
                        updateTooltip();
                    }
                });
                menu.add(cb);
            }
        }
        menu.show(paperclipBtn, 0, paperclipBtn.getHeight());
    }

    private void selectFiles() {
        if (!attachmentManager.canAdd()) {
            statusController.setStatus("STATUS_MaxFiles", AttachmentManager.MAX_ATTACHMENTS);
            statusController.scheduleReset();
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
            int oldSize = attachmentManager.size();
            attachmentManager.addFromFiles(fc.getSelectedFiles());
            int added = attachmentManager.size() - oldSize;
            if (added > 0) {
                List<AttachedFile> files = attachmentManager.getAttachments();
                String lastName = files.get(files.size() - 1).filename();
                if (added == 1) {
                    statusController.setStatus("STATUS_Attached", lastName);
                } else {
                    statusController.setStatus("STATUS_AttachedMore", lastName, added - 1);
                }
                statusController.scheduleReset();
            }
            updateTooltip();
        }
    }
}
