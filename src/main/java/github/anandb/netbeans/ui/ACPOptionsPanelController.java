package github.anandb.netbeans.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

@OptionsPanelController.TopLevelRegistration(
        categoryName = "#OptionsCategory_Name_Assistant",
        iconBase = "github/anandb/netbeans/ui/icons/logo.svg",
        keywords = "#OptionsCategory_Keywords_Assistant",
        keywordsCategory = "Assistant",
        position = 1100
)
@NbBundle.Messages({
    "OptionsCategory_Name_Assistant=Assistant", 
    "OptionsCategory_Keywords_Assistant=assistant, ai, chatbot, path, acp"
})
public final class ACPOptionsPanelController extends OptionsPanelController {

    private ACPOptionsPanel panel;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private boolean changed;

    @Override
    public void update() {
        getPanel().load();
        changed = false;
    }

    @Override
    public void applyChanges() {
        getPanel().store();
        changed = false;
    }

    @Override
    public void cancel() {
        // need not do anything special, the changes of the swing components are lost anyway
    }

    @Override
    public boolean isValid() {
        return getPanel().valid();
    }

    @Override
    public boolean isChanged() {
        return changed;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return null; // NO_HELP_BASED_ON_REQUIREMENTS
    }

    @Override
    public JComponent getComponent(Lookup lxp) {
        return getPanel();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private ACPOptionsPanel getPanel() {
        if (panel == null) {
            panel = new ACPOptionsPanel(this);
        }
        return panel;
    }

    void changed() {
        if (!changed) {
            changed = true;
            pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, false, true);
        }
        pcs.firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }
}
