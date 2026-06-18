package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionReference;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.ExportConversationAction")
@ActionRegistration(displayName = "#CTL_ExportConversationAction")
@ActionReference(path = "Actions/Assistant", position = 900)
public class ExportConversationAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            tc.exportConversation();
        }
    }
}
