package github.anandb.netbeans.tasks;

import javax.swing.JComponent;
import javax.swing.JLabel;
import org.netbeans.modules.bugtracking.spi.QueryController;
import org.openide.util.HelpCtx;

/**
 * No-op {@link QueryController}: queries are fixed ("All tasks") and not
 * user-editable, so no edit UI is provided.
 */
public final class TaskQueryController implements QueryController {

    private JComponent component;

    @Override
    public boolean providesMode(QueryMode mode) {
        return false;
    }

    @Override
    public JComponent getComponent(QueryMode mode) {
        if (component == null) {
            component = new JLabel("Queries for this repository are fixed.");
        }
        return component;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void opened() {
    }

    @Override
    public void closed() {
    }

    @Override
    public boolean saveChanges(String name) {
        return false;
    }

    @Override
    public boolean discardUnsavedChanges() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return false;
    }

    @Override
    public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
    }

    @Override
    public void removePropertyChangeListener(java.beans.PropertyChangeListener l) {
    }
}