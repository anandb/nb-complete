package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.StopMessageAction")
@ActionRegistration(displayName = "#CTL_StopMessageAction")
@ActionReference(path = "Actions/Assistant", position = 700)
public class StopMessageAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            tc.stopMessage();
        }
    }
}
