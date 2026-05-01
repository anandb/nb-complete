package github.anandb.netbeans.ui;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import github.anandb.netbeans.model.SessionUpdate;

class AutocompleteRenderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof SessionUpdate.AvailableCommand cmd) {
            setText(" /" + cmd.name() + (cmd.description() != null ? "  - " + cmd.description() : ""));
            setFont(ThemeManager.getFont().deriveFont(13f));
            setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        }
        return this;
    }
}
