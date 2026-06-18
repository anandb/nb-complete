package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.ReloadSessionAction")
@ActionRegistration(displayName = "#CTL_ReloadSessionAction")
@ActionReference(path = "Actions/Assistant", position = 200)
public class ReloadSessionAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            tc.reloadCurrentSession();
        }
    }
}
