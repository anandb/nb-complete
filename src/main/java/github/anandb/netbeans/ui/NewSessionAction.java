package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.NewSessionAction")
@ActionRegistration(displayName = "#CTL_NewSessionAction")
public class NewSessionAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            tc.createNewSession();
        }
    }
}
