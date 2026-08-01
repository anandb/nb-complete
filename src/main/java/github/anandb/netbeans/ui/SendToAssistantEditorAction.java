package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;

import github.anandb.netbeans.support.Logger;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Editor context menu action: sends the current selection (or the whole
 * document when nothing is selected) to the Mini Assistant input.
 */
@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SendToAssistantEditorAction")
@ActionRegistration(displayName = "#CTL_SendToAssistantAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 255)
public final class SendToAssistantEditorAction extends AbstractAction implements Presenter.Popup {

    private static final Logger LOG = Logger.from(SendToAssistantEditorAction.class);

    @Override
    public JMenuItem getPopupPresenter() {
        JMenuItem item = new JMenuItem(NbBundle.getMessage(SendToAssistantEditorAction.class, "CTL_SendToAssistantAction"));
        item.addActionListener(this);
        return item;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return;
        }
        String selection = editor.getSelectedText();
        if (isBlank(selection)) {
            Document doc = editor.getDocument();
            try {
                selection = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                LOG.fine("Cannot read document: {0}", ex.getMessage());
                return;
            }
        }
        MiniAssistantDialog.getInstance().showWithText(selection);
    }
}
