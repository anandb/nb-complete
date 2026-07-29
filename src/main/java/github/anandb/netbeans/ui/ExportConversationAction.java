package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;

@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.ExportConversationAction")
@ActionRegistration(displayName = "#CTL_ExportConversationAction")
public class ExportConversationAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent tc = AssistantTopComponent.findInstance();
        if (tc != null) {
            tc.exportConversation();
        }
    }
}
