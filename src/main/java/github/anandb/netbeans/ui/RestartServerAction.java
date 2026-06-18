package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.RestartServerAction")
@ActionRegistration(displayName = "#CTL_RestartServerAction")
@ActionReference(path = "Actions/Assistant", position = 500)
public class RestartServerAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            tc.componentLifecycleHandler.promptRestartServer();
        }
    }
}
