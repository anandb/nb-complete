package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;

import github.anandb.netbeans.support.PluginSettings;

public abstract class BaseSortLinesAction implements ActionListener {

    protected abstract Comparator<String> getComparator();

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!PluginSettings.isSortLinesEnabled()) return;
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            return;
        }

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

        Comparator<String> comp = getComparator();
        if (comp != null) {
            Arrays.sort(lines, comp);
        } else {
            Arrays.sort(lines);
        }

        String sorted = String.join("\n", lines);

        try {
            doc.remove(start, end - start);
            doc.insertString(start, sorted, null);
        } catch (BadLocationException ex) {
            // not expected
        }
    }
}
