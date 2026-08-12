package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;

import org.openide.util.actions.Presenter;
import javax.swing.JMenuItem;
import javax.swing.AbstractAction;

import github.anandb.netbeans.support.PluginSettings;

public abstract class BaseSortLinesAction extends AbstractAction implements Presenter.Popup {

    protected abstract Comparator<String> getComparator();
    protected abstract String getDisplayName();

    @Override
    public JMenuItem getPopupPresenter() {
        if (!PluginSettings.isSortLinesEnabled()) {
            JMenuItem item = new JMenuItem();
            item.setVisible(false);
            return item;
        }
        JMenuItem item = new JMenuItem(getDisplayName());
        item.addActionListener(this);
        return item;
    }

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
