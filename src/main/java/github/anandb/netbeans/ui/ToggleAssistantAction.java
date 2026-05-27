package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;

@ActionID(category = "Window", id = "github.anandb.netbeans.ui.ToggleAssistantAction")
public class ToggleAssistantAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent assistant = AssistantTopComponent.findInstance();
        if (assistant != null) {
            assistant.toggleVisibility();
        }
    }
}
