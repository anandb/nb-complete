package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;

import github.anandb.netbeans.support.Logger;
import github.anandb.netbeans.support.PluginSettings;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Editor context menu action: sends the current selection to the Mini
 * Assistant input. When nothing is selected, sends the file path instead.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SendToAssistantEditorAction")
@ActionRegistration(displayName = "#CTL_SendToAssistantAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 410)
public final class SendToAssistantEditorAction extends AbstractAction implements Presenter.Popup {

    private static final Logger LOG = Logger.from(SendToAssistantEditorAction.class);

    @Override
    public JMenuItem getPopupPresenter() {
        if (!PluginSettings.isSortLinesEnabled()) {
            JMenuItem item = new JMenuItem();
            item.setVisible(false);
            return item;
        }
        JMenuItem item = new JMenuItem(NbBundle.getMessage(SendToAssistantEditorAction.class, "CTL_SendToAssistantAction"));
        item.addActionListener(this);
        return item;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!PluginSettings.isSortLinesEnabled()) {
            return;
        }
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return;
        }
        String selection = editor.getSelectedText();
        if (isBlank(selection)) {
            Document doc = editor.getDocument();
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if (fo != null) {
                MiniAssistantDialog.getInstance().showWithText(fo.getPath());
            } else {
                LOG.fine("No file associated with the editor document");
            }
            return;
        }
        MiniAssistantDialog.getInstance().showWithText(selection);
    }
}
