package github.anandb.netbeans.ui.composer;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import github.anandb.netbeans.model.AttachedFile;
import github.anandb.netbeans.support.PluginSettings;
import github.anandb.netbeans.ui.UIBundle;
import github.anandb.netbeans.ui.rendering.ThemeManager;
import github.anandb.netbeans.ui.components.UIUtils;
import github.anandb.netbeans.ui.editor.GoToFileDialog;

/**
 * Self-contained paperclip attachment control: toolbar button, popup menu
 * (select files, remove attached), file chooser, and paste wiring. Owns its
 * {@link AttachmentManager}; surface-specific status feedback flows through
 * the caller-supplied {@link PasteCallback} so the same control works in the
 * main composer and the Mini Assistant.
 */
// DSL-LEAF: not a controller — builds the paperclip toolbar button + popup menu
// for file attachments. The JFileChooser + AttachmentManager state wiring stays
// imperative; extracted from AttachmentUiHandler for reuse in the Mini dialog.
public class AttachmentButton {

    private final AttachmentManager attachmentManager;
    private final PasteCallback pasteCallback;
    private final Component parentWindow;
    private final JButton paperclipBtn;

    /**
     * @param attachmentManager the manager this control owns and mutates
     * @param pasteCallback outcome sink: paste adds, limit reached, errors
     * @param parentWindow window used to anchor the file chooser dialog
     */
    public AttachmentButton(
            AttachmentManager attachmentManager,
            PasteCallback pasteCallback,
            Component parentWindow) {
        this.attachmentManager = attachmentManager;
        this.pasteCallback = pasteCallback;
        this.parentWindow = parentWindow;

        this.paperclipBtn = UIUtils.createToolbarButton("paperclip.svg",
                NbBundle.getMessage(UIBundle.class, "HINT_AttachFiles"), null);
        this.paperclipBtn.addActionListener(this::showPaperclipMenu);
        updateTooltip();
    }

    public JButton getButton() {
        return paperclipBtn;
    }

    /** Installs image/file paste handling on the given input. Paste adds are
     *  owned here (single mutation point); the surface callback only reacts. */
    public void setupPasteOn(JTextComponent inputArea) {
        inputArea.setTransferHandler(new ImagePasteTransferHandler(new PasteCallback() {
            @Override
            public boolean canAddAttachment() {
                return attachmentManager.canAdd();
            }

            @Override
            public void onAttachmentAdded(AttachedFile file) {
                if (attachmentManager.add(file)) {
                    updateTooltip();
                    pasteCallback.onAttachmentAdded(file);
                } else if (file.size() > AttachmentManager.maxAttachmentSize()) {
                    pasteCallback.onAttachmentTooLarge();
                } else {
                    pasteCallback.onAttachmentLimitReached();
                }
            }

            @Override
            public void onError(String message) {
                pasteCallback.onError(message);
            }

            @Override
            public void onAttachmentLimitReached() {
                pasteCallback.onAttachmentLimitReached();
            }
        }));
    }

    /** Updates the paperclip button tooltip and icon based on attachment count. */
    public void updateTooltip() {
        if (attachmentManager.getAttachments().isEmpty()) {
            paperclipBtn.setToolTipText(NbBundle.getMessage(UIBundle.class, "HINT_AttachFiles"));
            Icon icon = ThemeManager.getIcon("paperclip.svg", PluginSettings.getToolbarIconSize());
            paperclipBtn.setIcon(icon);
            if (icon != null) {
                paperclipBtn.setDisabledIcon(ImageUtilities.createDisabledIcon(icon));
            }
        } else {
            paperclipBtn.setToolTipText(NbBundle.getMessage(UIBundle.class, "HINT_FilesAttached", attachmentManager.size()));
            Icon base = ThemeManager.getIcon("paperclip.svg", PluginSettings.getToolbarIconSize());
            Icon icon = new AttachmentBadgeIcon(base, attachmentManager.size());
            paperclipBtn.setIcon(icon);
            if (icon != null) {
                paperclipBtn.setDisabledIcon(ImageUtilities.createDisabledIcon(icon));
            }
        }
    }

    /** Programmatic attach (drag targets, "send file to assistant" actions). Skips
     *  oversize files and honors the count cap, mirroring the chooser path.
     *  Notifies {@link PasteCallback#onFilesAttached} with the files that were
     *  added (they are already in the manager — do not add them again).
     *  @return how many files were actually attached. */
    public int attachFiles(File[] files) {
        if (files == null || files.length == 0) {
            return 0;
        }
        if (!attachmentManager.canAdd()) {
            pasteCallback.onAttachmentLimitReached();
            return 0;
        }
        int before = attachmentManager.size();
        attachmentManager.addFromFiles(files);
        int added = attachmentManager.size() - before;
        if (added > 0) {
            List<AttachedFile> addedFiles = attachmentManager.getAttachments();
            pasteCallback.onFilesAttached(addedFiles.subList(addedFiles.size() - added, addedFiles.size()));
            updateTooltip();
        }
        return added;
    }

    /** Programmatic attach of a single pre-read file (external "send to assistant").
     *  Reference-only files carry no bytes, so the size cap and the embedded count
     *  cap do not apply to them (mirrors {@link AttachmentManager#add}).
     *  Notifies too-large vs limit distinctly, mirroring the chooser path.
     *  @return true when the file was attached. */
    public boolean attachFile(AttachedFile file) {
        if (file.reference()) {
            if (!attachmentManager.canAddReference()) {
                pasteCallback.onReferenceLimitReached();
                return false;
            }
        } else {
            if (file.size() > AttachmentManager.MAX_ATTACHMENT_SIZE) {
                pasteCallback.onAttachmentTooLarge();
                return false;
            }
            if (!attachmentManager.canAdd()) {
                pasteCallback.onAttachmentLimitReached();
                return false;
            }
        }
        if (!attachmentManager.add(file)) {
            return false;
        }
        updateTooltip();
        pasteCallback.onFilesAttached(List.of(file));
        return true;
    }

    /** Removes one attached file (menu uncheck) and refreshes the badge. */
    public void removeAttachment(AttachedFile file) {
        attachmentManager.remove(file);
        updateTooltip();
    }

    private void showPaperclipMenu(ActionEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem addItem = new JMenuItem(NbBundle.getMessage(UIBundle.class, "BTN_SelectFile"));
        addItem.addActionListener(ev -> selectFiles());
        menu.add(addItem);
        JMenuItem referenceItem = new JMenuItem(
                NbBundle.getMessage(UIBundle.class, "BTN_ReferenceProjectFile"));
        referenceItem.addActionListener(ev -> selectProjectFiles());
        menu.add(referenceItem);
        List<AttachedFile> embedded = new ArrayList<>();
        List<AttachedFile> references = new ArrayList<>();
        for (AttachedFile af : attachmentManager.getAttachments()) {
            (af.reference() ? references : embedded).add(af);
        }
        if (!embedded.isEmpty()) {
            menu.addSeparator();
            menu.add(menuHeader("LBL_MenuAttachments"));
            for (AttachedFile af : embedded) {
                menu.add(attachmentCheckbox(af));
            }
        }
        if (!references.isEmpty()) {
            menu.addSeparator();
            menu.add(menuHeader("LBL_MenuReferences"));
            for (AttachedFile af : references) {
                menu.add(attachmentCheckbox(af));
            }
        }
        menu.show(paperclipBtn, 0, paperclipBtn.getHeight());
    }

    /** Muted, non-clickable group caption for the attachment menu. */
    private JMenuItem menuHeader(String bundleKey) {
        JMenuItem header = new JMenuItem(NbBundle.getMessage(UIBundle.class, bundleKey));
        header.setEnabled(false);
        return header;
    }

    private JCheckBoxMenuItem attachmentCheckbox(AttachedFile af) {
        JCheckBoxMenuItem cb = new JCheckBoxMenuItem(af.filename(), true);
        cb.addActionListener(ev -> {
            if (!cb.isSelected()) {
                removeAttachment(af);
            }
        });
        return cb;
    }

    /** Opens the project file picker (Go To File reuse); the chosen files are
     *  attached as reference-only attachments the agent reads via its fs tools. */
    private void selectProjectFiles() {
        if (!attachmentManager.canAddReference()) {
            pasteCallback.onReferenceLimitReached();
            return;
        }
        Window owner = parentWindow instanceof Window w ? w : SwingUtilities.getWindowAncestor(parentWindow);
        new GoToFileDialog(owner, this::attachReferences).setVisible(true);
    }

    private void attachReferences(List<FileObject> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        attachmentManager.getAttachments().forEach(a -> seen.add(a.uri()));
        List<AttachedFile> added = new ArrayList<>();
        for (FileObject fo : files) {
            File f = FileUtil.toFile(fo);
            if (f == null) {
                continue; // non-local FileObject cannot be referenced
            }
            AttachedFile ref = AttachedFile.reference(f);
            if (!seen.add(ref.uri())) {
                continue; // already attached — ignore duplicates silently
            }
            if (!attachmentManager.add(ref)) {
                pasteCallback.onReferenceLimitReached();
                break;
            }
            added.add(ref);
        }
        if (!added.isEmpty()) {
            updateTooltip();
            pasteCallback.onFilesAttached(added);
        }
    }

    private void selectFiles() {
        if (!attachmentManager.canAdd()) {
            pasteCallback.onAttachmentLimitReached();
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(parentWindow) == JFileChooser.APPROVE_OPTION) {
            attachFiles(fc.getSelectedFiles());
        }
    }
}
