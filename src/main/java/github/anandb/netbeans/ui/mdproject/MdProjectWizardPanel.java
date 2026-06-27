package github.anandb.netbeans.ui.mdproject;

import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;

/**
 * Wizard panel for choosing a project name and location.
 */
final class MdProjectWizardPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    private MdProjectPanelVisual component;

    @Override
    public JComponent getComponent() {
        if (component == null) {
            component = new MdProjectPanelVisual();
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
        getComponent();
        return component.valid();
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        getComponent();
        component.addChangeListener(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        getComponent();
        component.removeChangeListener(l);
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        getComponent();
        component.read(wiz);
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        getComponent();
        component.store(wiz);
    }
}
