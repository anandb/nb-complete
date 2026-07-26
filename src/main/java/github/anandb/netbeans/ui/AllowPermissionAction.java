package github.anandb.netbeans.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;

/**
 * Global action triggered by Ctrl+Alt+A to allow a permission request.
 * Remappable via Tools > Keymap in NetBeans.
 */
@ActionID(category = "Assistant", id = "github.anandb.netbeans.ui.AllowPermissionAction")
public final class AllowPermissionAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        AssistantTopComponent atc = AssistantTopComponent.findInstance();
        if (atc == null) return;
        PermissionRequestPanel panel = atc.getPermissionPanel();
        if (panel != null) {
            panel.triggerAllow();
        }
    }
}
