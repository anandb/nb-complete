package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SortLinesAction")
@ActionRegistration(displayName = "#CTL_SortLinesAction")
@ActionReference(path = "Editors/Popup", position = 300)
@NbBundle.Messages("CTL_SortLinesAction=Sort Lines")
public class SortLinesAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) return;

        String selection = editor.getSelectedText();
        if (selection == null || selection.isEmpty()) return;

        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();

        String[] lines = selection.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].stripLeading();
        }
        Arrays.sort(lines);
        String sorted = String.join("\n", lines);

        Document doc = editor.getDocument();
        try {
            doc.remove(start, end - start);
            doc.insertString(start, sorted, null);
        } catch (BadLocationException ex) {
            // not expected
        }
    }
}
