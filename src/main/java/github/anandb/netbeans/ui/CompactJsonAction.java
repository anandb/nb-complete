package github.anandb.netbeans.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.event.ActionEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import javax.swing.JMenuItem;
import javax.swing.AbstractAction;

import github.anandb.netbeans.support.MapperSupplier;
import github.anandb.netbeans.support.PluginSettings;
import org.openide.awt.StatusDisplayer;

@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.CompactJsonAction")
@ActionRegistration(displayName = "#CTL_CompactJsonAction", lazy = false)
@ActionReference(path = "Editors/Popup", position = 400)
public class CompactJsonAction extends AbstractAction implements Presenter.Popup {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    @Override
    public JMenuItem getPopupPresenter() {
        if (!PluginSettings.isSortLinesEnabled()) {
            JMenuItem item = new JMenuItem();
            item.setVisible(false);
            return item;
        }
        JMenuItem item = new JMenuItem(NbBundle.getMessage(CompactJsonAction.class, "CTL_CompactJsonAction"));
        item.addActionListener(this);
        return item;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!PluginSettings.isSortLinesEnabled()) return;
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) return;

        Document doc = editor.getDocument();
        Object mimeType = doc.getProperty("mimeType");
        if (mimeType == null || !mimeType.toString().toLowerCase().contains("json")) return;

        String selection = editor.getSelectedText();
        int start;
        int end;
        if (selection == null || selection.isEmpty()) {
            start = 0;
            end = doc.getLength();
            try {
                selection = doc.getText(start, end);
            } catch (BadLocationException ex) {
                return;
            }
        } else {
            start = editor.getSelectionStart();
            end = editor.getSelectionEnd();
        }

        try {
            JsonNode node = MAPPER.readTree(selection);
            String compacted = MAPPER.writeValueAsString(node);
            if (compacted.equals(selection)) return;
            doc.remove(start, end - start);
            doc.insertString(start, compacted, null);
        } catch (Exception ex) {
            StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(CompactJsonAction.class, "ERR_InvalidJson", ex.getMessage()));
        }
    }
}
