package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

@ActionID(category = "Window", id = "github.anandb.netbeans.ui.ToggleAssistantAction")
@ActionReferences({
    @ActionReference(path = "Menu/Window", position = 334)
})
@NbBundle.Messages({
    "CTL_ToggleAssistantAction=Toggle Assistant"
})
public class ToggleAssistantAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        TopComponent tc = TopComponent.getRegistry().getActivated();
        AssistantTopComponent assistant = AssistantTopComponent.findInstance();

        if (assistant != null && assistant.isOpened()) {
            assistant.close();
        } else {
            if (assistant == null) {
                assistant = new AssistantTopComponent();
            }
            assistant.open();
            assistant.requestActive();
        }
    }
}
