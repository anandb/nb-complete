package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;

@ActionID(category = "Window", id = "github.anandb.netbeans.ui.ToggleMiniAssistantAction")
public class ToggleMiniAssistantAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        MiniAssistantDialog dialog = MiniAssistantDialog.getInstance();
        dialog.toggleVisibility();
    }
}
