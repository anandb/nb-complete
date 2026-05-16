package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

@ActionID(category = "Edit", id = "github.anandb.netbeans.ui.SortLinesDescAction")
@ActionRegistration(displayName = "#CTL_SortLinesDescAction")
@ActionReference(path = "Editors/Popup", position = 301)
@NbBundle.Messages("CTL_SortLinesDescAction=Sort Lines Descending")
public class SortLinesDescAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) return;

        Document doc = editor.getDocument();
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

        String[] lines = selection.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].stripLeading();
        }
        Arrays.sort(lines, Comparator.reverseOrder());
        String sorted = String.join("\n", lines);

        try {
            doc.remove(start, end - start);
            doc.insertString(start, sorted, null);
        } catch (BadLocationException ex) {
            // not expected
        }
    }
}
