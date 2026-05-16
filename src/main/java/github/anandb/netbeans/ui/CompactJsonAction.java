package github.anandb.netbeans.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import github.anandb.netbeans.support.MapperSupplier;

@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.CompactJsonAction")
@ActionRegistration(displayName = "#CTL_CompactJsonAction")
@ActionReference(path = "Editors/Popup", position = 400)
@NbBundle.Messages("CTL_CompactJsonAction=Compact")
public class CompactJsonAction implements ActionListener {

    private static final ObjectMapper MAPPER = MapperSupplier.get();

    @Override
    public void actionPerformed(ActionEvent e) {
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
            // invalid JSON or editor error — do nothing
        }
    }
}
