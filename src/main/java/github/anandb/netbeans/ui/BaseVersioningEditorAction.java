package github.anandb.netbeans.ui;

import java.io.File;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.actions.Presenter;

import github.anandb.netbeans.support.PluginSettings;

/**
 * Shared base for editor context menu actions that operate on the file in the
 * current editor via the versioning SPI. Provides the toggle-gated popup
 * presenter and resolution of the editor document's backing file.
 */
public abstract class BaseVersioningEditorAction extends AbstractAction implements Presenter.Popup {

    /** Bundle key of this action's display name. */
    protected abstract String getDisplayNameKey();

    /** Performs the action on the file backing the current editor document. */
    protected abstract void perform(File file, java.awt.event.ActionEvent e);

    @Override
    public JMenuItem getPopupPresenter() {
        if (!PluginSettings.isContextMenuEnabled()) {
            JMenuItem item = new JMenuItem();
            item.setVisible(false);
            return item;
        }
        JMenuItem item = new JMenuItem(
                org.openide.util.NbBundle.getMessage(getClass(), getDisplayNameKey()));
        item.addActionListener(this);
        return item;
    }

    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        if (!PluginSettings.isContextMenuEnabled()) {
            return;
        }
        File file = currentFile();
        if (file != null) {
            perform(file, e);
        }
    }

    /** Returns the file backing the current editor document, or {@code null}. */
    protected static File currentFile() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return null;
        }
        Document doc = editor.getDocument();
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        if (fo == null) {
            return null;
        }
        return FileUtil.toFile(fo);
    }
}
